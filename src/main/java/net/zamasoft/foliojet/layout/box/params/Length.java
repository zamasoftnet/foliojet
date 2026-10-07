package net.zamasoft.foliojet.layout.box.params;

/**
 * An object representing a length.
 *
 * @author MIYABE Tatsuhiko
 */
public class Length {
	public static final Length ZERO_LENGTH = new Length(0, 0, LengthType.ABSOLUTE);
	public static final Length AUTO_LENGTH = new Length(0, 0, LengthType.AUTO);

	private final double length;
	private final double ratio;
	private final LengthType type;

	public static Length create(double length, LengthType type) {
		if (type == LengthType.AUTO) {
			return AUTO_LENGTH;
		}
		if (length == 0) {
			return ZERO_LENGTH;
		}
		return new Length(length, 0, type);
	}

	/**
	 * Creates a mixed absolute-length/percentage value from calc() (e.g., calc(50% + 10px)).
	 * Reduces to ordinary ABSOLUTE/RELATIVE if either component is 0.
	 */
	public static Length createMixed(double absolute, double ratio) {
		if (ratio == 0) {
			return create(absolute, LengthType.ABSOLUTE);
		}
		if (absolute == 0) {
			return create(ratio, LengthType.RELATIVE);
		}
		return new Length(absolute, ratio, LengthType.MIXED);
	}

	private Length(double length, double ratio, LengthType type) {
		this.length = length;
		this.ratio = ratio;
		this.type = type;
	}

	public LengthType getType() {
		return this.type;
	}

	/** Absolute length for ABSOLUTE, percentage for RELATIVE, absolute component for MIXED. */
	public double getLength() {
		return this.length;
	}

	/** Percentage component, meaningful only for MIXED (always 0 otherwise). */
	public double getRatio() {
		return this.ratio;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		buff.append("[length=");
		switch (this.getType()) {
		case ABSOLUTE:
			buff.append(this.length);
			break;
		case RELATIVE:
			buff.append(this.length * 100).append('%');
			break;
		case AUTO:
			buff.append("auto");
			break;
		case MIXED:
			buff.append(this.length).append('+').append(this.ratio * 100).append('%');
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(']');
		return buff.toString();
	}
}
