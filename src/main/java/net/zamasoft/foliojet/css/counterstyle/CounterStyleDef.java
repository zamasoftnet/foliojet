package net.zamasoft.foliojet.css.counterstyle;

import java.util.List;

/**
 * An author-defined counter style defined by {@code @counter-style}
 * (the subset of CSS Counter Styles Level 3 relevant to print;
 * 2026-08-02, ranked fifth in PLAN §2). Motivated by Japanese needs such as kanji
 * numerals and iroha, and compatibility with CSS input from the Web.
 *
 * <p>
 * Descriptors ({@code system}/{@code symbols}, etc.) are <b>not properties</b>.
 * They neither cascade nor inherit; each rule is an independent definition.
 * Therefore they do not use CSSStyle's property mechanism ({@code PropertySet});
 * {@link CounterStyleParser} reads directly into this record.
 * </p>
 *
 * <p>
 * {@link #format(int, CounterStyles, int)} returns only the numeric representation;
 * the caller adds marker prefix/suffix
 * (because the specification excludes prefix/suffix from {@code counter()}).
 * </p>
 */
public final class CounterStyleDef {

	/** How symbols are arranged (CSS Counter Styles Level 3 §3). */
	public enum System {
		CYCLIC, NUMERIC, ALPHABETIC, SYMBOLIC, ADDITIVE, FIXED, EXTENDS
	}

	/** Represents infinity in a range. */
	public static final int INFINITE_MIN = Integer.MIN_VALUE;

	/** Represents infinity in a range. */
	public static final int INFINITE_MAX = Integer.MAX_VALUE;

	private static final int MAX_REPEAT = 1000;

	public final System system;

	/** {@code symbols}(cyclic/numeric/alphabetic/symbolic/fixed)。 */
	public final List<String> symbols;

	/** Weights for {@code additive-symbols} (descending order). */
	public final int[] additiveWeights;

	/** Symbols for {@code additive-symbols} (same order as the weights). */
	public final List<String> additiveSymbols;

	/** Starting value for {@code system: fixed <first>}. */
	public final int fixedFirst;

	/** Base style name for {@code system: extends <name>}. */
	public final String extendsName;

	public final String prefix;

	public final String suffix;

	public final String negativePrefix;

	public final String negativeSuffix;

	public final int rangeMin;

	public final int rangeMax;

	public final int padLength;

	public final String padSymbol;

	/** {@code fallback} ({@code decimal} if unspecified). */
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
	 * Whether this definition can represent numbers (e.g., {@code symbols} with no symbols
	 * is invalid and invalidates the entire rule per the specification).
	 */
	public boolean isValid() {
		return switch (this.system) {
		case ADDITIVE -> this.additiveWeights.length > 0;
		case EXTENDS -> this.extendsName != null;
		case ALPHABETIC, NUMERIC -> this.symbols.size() >= 2;
		default -> !this.symbols.isEmpty();
		};
	}

	/** Whether {@code number} is within this definition's range. */
	private boolean inRange(final int number) {
		if (this.rangeMin != INFINITE_MIN || this.rangeMax != INFINITE_MAX) {
			return number >= this.rangeMin && number <= this.rangeMax;
		}
		// Default range (§6.2)
		return switch (this.system) {
		case ALPHABETIC, SYMBOLIC -> number >= 1;
		case ADDITIVE -> number >= 0;
		case FIXED -> number >= this.fixedFirst && number < this.fixedFirst + this.symbols.size();
		default -> true;
		};
	}

	/**
	 * Converts a number to its representation (without prefix/suffix). Delegates
	 * to {@code fallback} if unrepresentable, and returns null if that also fails.
	 *
	 * @param depth recursion depth (extends/fallback), as a safeguard against runaway recursion
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

	/** Adds the {@code negative} prefix and suffix symbols to a negative number. */
	private String negate(final int number, final String core) {
		if (core == null || number >= 0) {
			return core;
		}
		return this.negativePrefix + core + this.negativeSuffix;
	}

	/** Applies {@code pad} (prepends until there are enough symbols). */
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
			// Pad inside the negative sign (§6.4).
			if (core.startsWith(this.negativePrefix) && !this.negativePrefix.isEmpty()) {
				return this.negativePrefix + buff + core.substring(this.negativePrefix.length());
			}
		}
		return buff + core;
	}

	/** Bijective numeration (a, b, ..., z, aa, ab, ...). */
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

	/** Positional numeration (symbols.get(0) is 0). */
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

	/** Additive numeration (Roman numeral style). Returns null if unrepresentable. */
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
