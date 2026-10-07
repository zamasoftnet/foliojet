package net.zamasoft.foliojet.css.counterstyle;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.counterstyle.CounterStyleDef.System;

/**
 * Reads the body of {@code @counter-style} (a sequence of descriptors) into
 * {@link CounterStyleDef} (2026-08-02).
 *
 * <p>
 * Descriptors neither cascade nor inherit, so read them directly here
 * instead of using the CSS property mechanism (see the {@link CounterStyleDef} Javadoc).
 * Values are simple sequences of strings, identifiers, integers, and symbols,
 * so lexical analysis is also handled entirely here.
 * </p>
 *
 * <p>
 * <b>Subset</b>: ignores {@code speak-as} (speech only).
 * </p>
 */
public final class CounterStyleParser {

	private CounterStyleParser() {
		// Do not instantiate
	}

	/** One {@code additive-symbols} pair. */
	private record Additive(int weight, String symbol) {
	}

	/**
	 * Builds a definition from a sequence of descriptors (name/value pairs). Returns null
	 * if the definition cannot represent numbers (e.g., no symbols).
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
					// Ignore unknown systems (retain the default symbolic).
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
				// Sort by descending weight (additive numeration uses larger weights first).
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
				// Take only the first range (multiple ranges have little practical value for print).
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
				// Ignore speak-as, etc.
				break;
			}
		}

		if (system == System.EXTENDS && !suffixSpecified) {
			// extends inherits the base descriptors; leave suffix to the base (built-in) style.
			suffix = ".";
		}

		final CounterStyleDef def = new CounterStyleDef(system, symbols, additiveWeights, additiveSymbols, fixedFirst,
				extendsName, prefix, suffix, negativePrefix, negativeSuffix, rangeMin, rangeMax, padLength, padSymbol,
				fallbackName);
		return def.isValid() ? def : null;
	}

	/** The value of {@code symbols} (a sequence of strings, identifiers, and symbols). */
	private static List<String> symbols(final String value) {
		final List<String> result = new ArrayList<>();
		for (final String token : tokens(value)) {
			result.add(unquote(token));
		}
		return result;
	}

	/** Range endpoint (an integer or {@code infinite}). */
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

	/** Removes quotes (does not handle escapes). */
	private static String unquote(final String token) {
		if (token.length() >= 2) {
			final char quote = token.charAt(0);
			if ((quote == '"' || quote == '\'') && token.charAt(token.length() - 1) == quote) {
				return token.substring(1, token.length() - 1);
			}
		}
		return token;
	}

	/** Splits on whitespace outside quotes. */
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

	/** Splits on delimiters outside quotes. */
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
