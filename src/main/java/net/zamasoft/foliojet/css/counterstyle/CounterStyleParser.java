package net.zamasoft.foliojet.css.counterstyle;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.counterstyle.CounterStyleDef.System;

/**
 * {@code @counter-style}の本体(記述子の並び)を{@link CounterStyleDef}へ
 * 読み取ります(2026-08-02)。
 *
 * <p>
 * 記述子はカスケードも継承もしないため、CSSプロパティの機構ではなく
 * ここで直接読む({@link CounterStyleDef}のjavadoc参照)。値は
 * 「文字列・識別子・整数・記号」の単純な並びなので、字句もこの中で
 * 完結させている。
 * </p>
 *
 * <p>
 * <b>サブセット</b>: {@code speak-as}(音声のみ)は無視する。
 * </p>
 */
public final class CounterStyleParser {

	private CounterStyleParser() {
		// インスタンス化しない
	}

	/** {@code additive-symbols}の1対です。 */
	private record Additive(int weight, String symbol) {
	}

	/**
	 * 記述子の並び(宣言名と値の対)から定義を作ります。表現できない
	 * 定義(記号がない等)ならnullを返します。
	 */
	public static CounterStyleDef parse(final List<String[]> descriptors) {
		System system = System.SYMBOLIC;
		int fixedFirst = 1;
		String extendsName = null;
		List<String> symbols = new ArrayList<>();
		int[] additiveWeights = new int[0];
		List<String> additiveSymbols = new ArrayList<>();
		String prefix = "";
		String suffix = ".";
		String negativePrefix = "-";
		String negativeSuffix = "";
		int rangeMin = CounterStyleDef.INFINITE_MIN;
		int rangeMax = CounterStyleDef.INFINITE_MAX;
		int padLength = 0;
		String padSymbol = "";
		String fallbackName = "decimal";
		boolean suffixSpecified = false;

		for (final String[] descriptor : descriptors) {
			final String name = descriptor[0].toLowerCase();
			final String value = descriptor[1];
			switch (name) {
			case "system": {
				final List<String> parts = tokens(value);
				if (parts.isEmpty()) {
					break;
				}
				switch (parts.get(0).toLowerCase()) {
				case "cyclic" -> system = System.CYCLIC;
				case "numeric" -> system = System.NUMERIC;
				case "alphabetic" -> system = System.ALPHABETIC;
				case "symbolic" -> system = System.SYMBOLIC;
				case "additive" -> system = System.ADDITIVE;
				case "fixed" -> {
					system = System.FIXED;
					if (parts.size() > 1) {
						final Integer first = toInteger(parts.get(1));
						if (first != null) {
							fixedFirst = first;
						}
					}
				}
				case "extends" -> {
					system = System.EXTENDS;
					if (parts.size() > 1) {
						extendsName = parts.get(1);
					}
				}
				default -> {
					// 未知のsystemは無視(既定のsymbolicのまま)
				}
				}
				break;
			}

			case "symbols":
				symbols = symbols(value);
				break;

			case "additive-symbols": {
				final List<Additive> pairs = new ArrayList<>();
				for (final String pair : split(value, ',')) {
					final List<String> parts = tokens(pair);
					if (parts.size() < 2) {
						continue;
					}
					final Integer weight = toInteger(parts.get(0));
					if (weight != null) {
						pairs.add(new Additive(weight, unquote(parts.get(1))));
					}
				}
				// 重みの降順に並べる(加算記数は大きい方から使う)
				pairs.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
				additiveWeights = new int[pairs.size()];
				additiveSymbols = new ArrayList<>(pairs.size());
				for (int i = 0; i < pairs.size(); ++i) {
					additiveWeights[i] = pairs.get(i).weight();
					additiveSymbols.add(pairs.get(i).symbol());
				}
				break;
			}

			case "prefix":
				prefix = unquote(first(value));
				break;

			case "suffix":
				suffix = unquote(first(value));
				suffixSpecified = true;
				break;

			case "negative": {
				final List<String> parts = tokens(value);
				if (!parts.isEmpty()) {
					negativePrefix = unquote(parts.get(0));
					negativeSuffix = parts.size() > 1 ? unquote(parts.get(1)) : "";
				}
				break;
			}

			case "range": {
				final String trimmed = value.trim();
				if (trimmed.equalsIgnoreCase("auto")) {
					break;
				}
				// 最初の範囲だけを採る(複数範囲は印刷実務で必要性が薄い)
				final List<String> parts = tokens(split(trimmed, ',').get(0));
				if (parts.size() >= 2) {
					rangeMin = bound(parts.get(0), CounterStyleDef.INFINITE_MIN);
					rangeMax = bound(parts.get(1), CounterStyleDef.INFINITE_MAX);
				}
				break;
			}

			case "pad": {
				final List<String> parts = tokens(value);
				if (parts.size() >= 2) {
					final Integer length = toInteger(parts.get(0));
					if (length != null) {
						padLength = length;
						padSymbol = unquote(parts.get(1));
					}
				}
				break;
			}

			case "fallback":
				fallbackName = first(value);
				break;

			default:
				// speak-as等は無視
				break;
			}
		}

		if (system == System.EXTENDS && !suffixSpecified) {
			// extendsは基底の記述子を継ぐ——suffixは基底(組み込み)に任せる
			suffix = ".";
		}

		final CounterStyleDef def = new CounterStyleDef(system, symbols, additiveWeights, additiveSymbols, fixedFirst,
				extendsName, prefix, suffix, negativePrefix, negativeSuffix, rangeMin, rangeMax, padLength, padSymbol,
				fallbackName);
		return def.isValid() ? def : null;
	}

	/** {@code symbols}の値(文字列・識別子・記号の並び)。 */
	private static List<String> symbols(final String value) {
		final List<String> result = new ArrayList<>();
		for (final String token : tokens(value)) {
			result.add(unquote(token));
		}
		return result;
	}

	/** 範囲の端(整数または{@code infinite})。 */
	private static int bound(final String token, final int infinite) {
		if (token.equalsIgnoreCase("infinite")) {
			return infinite;
		}
		final Integer number = toInteger(token);
		return number != null ? number : infinite;
	}

	private static Integer toInteger(final String token) {
		try {
			return Integer.valueOf(token.trim());
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	private static String first(final String value) {
		final List<String> parts = tokens(value);
		return parts.isEmpty() ? "" : parts.get(0);
	}

	/** 引用符を外します(エスケープは扱わない)。 */
	private static String unquote(final String token) {
		if (token.length() >= 2) {
			final char quote = token.charAt(0);
			if ((quote == '"' || quote == '\'') && token.charAt(token.length() - 1) == quote) {
				return token.substring(1, token.length() - 1);
			}
		}
		return token;
	}

	/** 引用符の外の空白で区切ります。 */
	private static List<String> tokens(final String value) {
		final List<String> result = new ArrayList<>();
		final StringBuilder buff = new StringBuilder();
		char quote = 0;
		for (int i = 0; i < value.length(); ++i) {
			final char c = value.charAt(i);
			if (quote != 0) {
				buff.append(c);
				if (c == quote) {
					quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
				buff.append(c);
			} else if (Character.isWhitespace(c)) {
				if (buff.length() > 0) {
					result.add(buff.toString());
					buff.setLength(0);
				}
			} else {
				buff.append(c);
			}
		}
		if (buff.length() > 0) {
			result.add(buff.toString());
		}
		return result;
	}

	/** 引用符の外の区切り文字で分割します。 */
	private static List<String> split(final String value, final char separator) {
		final List<String> result = new ArrayList<>();
		final StringBuilder buff = new StringBuilder();
		char quote = 0;
		for (int i = 0; i < value.length(); ++i) {
			final char c = value.charAt(i);
			if (quote != 0) {
				buff.append(c);
				if (c == quote) {
					quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
				buff.append(c);
			} else if (c == separator) {
				result.add(buff.toString());
				buff.setLength(0);
			} else {
				buff.append(c);
			}
		}
		result.add(buff.toString());
		return result;
	}
}
