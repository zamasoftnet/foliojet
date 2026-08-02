package net.zamasoft.foliojet.css.counterstyle;

import java.util.List;

/**
 * {@code @counter-style}で定義された著者定義カウンタスタイルです
 * (CSS Counter Styles Level 3のうち印刷で意味のあるサブセット、
 * 2026-08-02——PLAN §2の5位。漢数字・いろは等の和文実需と、Web由来CSSの
 * 入力互換が動機)。
 *
 * <p>
 * 記述子({@code system}/{@code symbols}等)は<b>プロパティではない</b>
 * ——カスケードも継承もせず、規則ごとに独立した定義になる。そのため
 * CSSStyleのプロパティ機構({@code PropertySet})には載せず、
 * {@link CounterStyleParser}が本レコードへ直接読み取る。
 * </p>
 *
 * <p>
 * 数値の表現だけを{@link #format(int, CounterStyles, int)}が返し、
 * マーカーの前後(prefix/suffix)は呼び出し側が付ける
 * ({@code counter()}は仕様上prefix/suffixを含めないため)。
 * </p>
 */
public final class CounterStyleDef {

	/** 記号の並べ方です(CSS Counter Styles Level 3 §3)。 */
	public enum System {
		CYCLIC, NUMERIC, ALPHABETIC, SYMBOLIC, ADDITIVE, FIXED, EXTENDS
	}

	/** 範囲の無限を表します。 */
	public static final int INFINITE_MIN = Integer.MIN_VALUE;

	/** 範囲の無限を表します。 */
	public static final int INFINITE_MAX = Integer.MAX_VALUE;

	private static final int MAX_REPEAT = 1000;

	public final System system;

	/** {@code symbols}(cyclic/numeric/alphabetic/symbolic/fixed)。 */
	public final List<String> symbols;

	/** {@code additive-symbols}の重み(降順)。 */
	public final int[] additiveWeights;

	/** {@code additive-symbols}の記号(重みと同順)。 */
	public final List<String> additiveSymbols;

	/** {@code system: fixed <first>}の開始値です。 */
	public final int fixedFirst;

	/** {@code system: extends <name>}の基底スタイル名です。 */
	public final String extendsName;

	public final String prefix;

	public final String suffix;

	public final String negativePrefix;

	public final String negativeSuffix;

	public final int rangeMin;

	public final int rangeMax;

	public final int padLength;

	public final String padSymbol;

	/** {@code fallback}(未指定なら{@code decimal})。 */
	public final String fallbackName;

	CounterStyleDef(final System system, final List<String> symbols, final int[] additiveWeights,
			final List<String> additiveSymbols, final int fixedFirst, final String extendsName, final String prefix,
			final String suffix, final String negativePrefix, final String negativeSuffix, final int rangeMin,
			final int rangeMax, final int padLength, final String padSymbol, final String fallbackName) {
		this.system = system;
		this.symbols = symbols;
		this.additiveWeights = additiveWeights;
		this.additiveSymbols = additiveSymbols;
		this.fixedFirst = fixedFirst;
		this.extendsName = extendsName;
		this.prefix = prefix;
		this.suffix = suffix;
		this.negativePrefix = negativePrefix;
		this.negativeSuffix = negativeSuffix;
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.padLength = padLength;
		this.padSymbol = padSymbol;
		this.fallbackName = fallbackName;
	}

	/**
	 * 定義が実際に数を表現できるか(記号が空の{@code symbols}等は不正で、
	 * 仕様上その規則自体が無効になる)。
	 */
	public boolean isValid() {
		return switch (this.system) {
		case ADDITIVE -> this.additiveWeights.length > 0;
		case EXTENDS -> this.extendsName != null;
		case ALPHABETIC, NUMERIC -> this.symbols.size() >= 2;
		default -> !this.symbols.isEmpty();
		};
	}

	/** この定義の範囲に{@code number}が入るか。 */
	private boolean inRange(final int number) {
		if (this.rangeMin != INFINITE_MIN || this.rangeMax != INFINITE_MAX) {
			return number >= this.rangeMin && number <= this.rangeMax;
		}
		// 既定の範囲(§6.2)
		return switch (this.system) {
		case ALPHABETIC, SYMBOLIC -> number >= 1;
		case ADDITIVE -> number >= 0;
		case FIXED -> number >= this.fixedFirst && number < this.fixedFirst + this.symbols.size();
		default -> true;
		};
	}

	/**
	 * 数値を表現へ変換します(prefix/suffixは含まない)。表現できない
	 * ときは{@code fallback}へ委ね、それも失敗すればnullを返します。
	 *
	 * @param depth 再帰(extends/fallback)の深さ。暴走を止めるための保険。
	 */
	public String format(final int number, final CounterStyles styles, final int depth) {
		if (depth > 8) {
			return null;
		}
		if (!this.inRange(number)) {
			return styles.formatByName(this.fallbackName, number, depth + 1);
		}
		final String core = this.represent(number, styles, depth);
		if (core == null) {
			return styles.formatByName(this.fallbackName, number, depth + 1);
		}
		return this.pad(core, number);
	}

	private String represent(final int number, final CounterStyles styles, final int depth) {
		switch (this.system) {
		case CYCLIC:
			return this.symbols.get(Math.floorMod(number - 1, this.symbols.size()));

		case FIXED: {
			final int index = number - this.fixedFirst;
			return index >= 0 && index < this.symbols.size() ? this.symbols.get(index) : null;
		}

		case EXTENDS:
			return styles.formatByName(this.extendsName, number, depth + 1);

		case SYMBOLIC: {
			if (number < 1) {
				return null;
			}
			final int size = this.symbols.size();
			final int count = (number - 1) / size + 1;
			if (count > MAX_REPEAT) {
				return null;
			}
			return this.symbols.get((number - 1) % size).repeat(count);
		}

		case ALPHABETIC: {
			if (number < 1) {
				return null;
			}
			return this.negate(number, alphabetic(Math.abs((long) number), this.symbols));
		}

		case NUMERIC:
			return this.negate(number, numeric(Math.abs((long) number), this.symbols));

		case ADDITIVE:
			return this.negate(number, this.additive(Math.abs((long) number)));

		default:
			throw new IllegalStateException(String.valueOf(this.system));
		}
	}

	/** 負数へ{@code negative}の前後記号を付けます。 */
	private String negate(final int number, final String core) {
		if (core == null || number >= 0) {
			return core;
		}
		return this.negativePrefix + core + this.negativeSuffix;
	}

	/** {@code pad}を適用します(記号数が足りるまで前置)。 */
	private String pad(final String core, final int number) {
		if (this.padLength <= 0) {
			return core;
		}
		final StringBuilder buff = new StringBuilder();
		int length = core.codePointCount(0, core.length());
		while (length < this.padLength) {
			buff.append(this.padSymbol);
			++length;
		}
		if (number < 0) {
			// 負符号の内側にパディングする(§6.4)
			if (core.startsWith(this.negativePrefix) && !this.negativePrefix.isEmpty()) {
				return this.negativePrefix + buff + core.substring(this.negativePrefix.length());
			}
		}
		return buff + core;
	}

	/** 双射基数(a, b, ..., z, aa, ab, ...)。 */
	private static String alphabetic(long number, final List<String> symbols) {
		final int base = symbols.size();
		final StringBuilder buff = new StringBuilder();
		long n = number;
		while (n > 0) {
			--n;
			buff.insert(0, symbols.get((int) (n % base)));
			n /= base;
		}
		return buff.length() == 0 ? null : buff.toString();
	}

	/** 位取り記数(symbols.get(0)が0)。 */
	private static String numeric(long number, final List<String> symbols) {
		final int base = symbols.size();
		if (number == 0) {
			return symbols.get(0);
		}
		final StringBuilder buff = new StringBuilder();
		long n = number;
		while (n > 0) {
			buff.insert(0, symbols.get((int) (n % base)));
			n /= base;
		}
		return buff.toString();
	}

	/** 加算記数(ローマ数字方式)。表現できなければnull。 */
	private String additive(long number) {
		if (number == 0) {
			for (int i = 0; i < this.additiveWeights.length; ++i) {
				if (this.additiveWeights[i] == 0) {
					return this.additiveSymbols.get(i);
				}
			}
			return null;
		}
		final StringBuilder buff = new StringBuilder();
		long rest = number;
		int repeats = 0;
		for (int i = 0; i < this.additiveWeights.length && rest > 0; ++i) {
			final int weight = this.additiveWeights[i];
			if (weight <= 0) {
				continue;
			}
			final long count = rest / weight;
			if (count == 0) {
				continue;
			}
			repeats += count;
			if (repeats > MAX_REPEAT) {
				return null;
			}
			buff.append(this.additiveSymbols.get(i).repeat((int) count));
			rest -= count * weight;
		}
		return rest == 0 ? buff.toString() : null;
	}
}
