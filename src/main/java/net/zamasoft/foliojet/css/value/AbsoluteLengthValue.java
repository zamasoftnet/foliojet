package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * An absolute length (including device-relative lengths).
 */
public abstract class AbsoluteLengthValue implements LengthValue, Comparable<AbsoluteLengthValue> {
	public abstract Unit getUnit();

	/**
	 * Returns the length in the specified unit.
	 */
	public abstract double getLength(Unit unit);

	/**
	 * Returns the length in PT units.
	 */
	public abstract double getLength();

	public static final AbsoluteLengthValue ZERO = new Zero();

	/**
	 * The {@code auto} of min-width, min-height, min-inline-size and min-block-size (2026-10-09): 0, as {@link #ZERO}
	 * is in normal flow, but a value of its own, so a flex or grid item keeps its automatic minimum size under it and
	 * loses it only under an authored length ({@code min-width: 0}). Parsed to {@link #ZERO}, an explicit
	 * {@code min-width: auto} removed the automatic minimum, and the items shrank below their words.
	 */
	public static final AbsoluteLengthValue AUTO_MIN_SIZE = new Zero();

	private static final class Zero extends AbsoluteLengthValue {
		public Unit getUnit() {
			return Unit.PT;
		}

		public double getLength(Unit unit) {
			return 0;
		}

		public double getLength() {
			return 0;
		}

		public int compareTo(AbsoluteLengthValue length) {
			if (length.isZero()) {
				return 0;
			}
			if (length.isNegative()) {
				return 1;
			}
			return -1;
		}

		public boolean isNegative() {
			return false;
		}

		public boolean isZero() {
			return true;
		}
	}

	public static AbsoluteLengthValue create(UserAgent ua, double value, Unit unit) {
		if (value == 0) {
			return ZERO;
		}
		return new AbsoluteLengthValueImpl(ua, unit, value);
	}

	public static AbsoluteLengthValue create(UserAgent ua, double value) {
		if (value == 0) {
			return ZERO;
		}
		return new AbsoluteLengthValueImpl(ua, Unit.PT, value);
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		return this;
	}

	public String toString() {
		return this.getLength(this.getUnit()) + this.getUnit().name().toLowerCase();
	}
}
