package net.zamasoft.foliojet.layout.box.params;

/**
 * 長さを表すオブジェクトです。
 *
 * @author MIYABE Tatsuhiko
 */
public class Length {
	public static final Length ZERO_LENGTH = new Length(0, LengthType.ABSOLUTE);
	public static final Length AUTO_LENGTH = new Length(0, LengthType.AUTO);

	private final double length;
	private final LengthType type;

	public static Length create(double length, LengthType type) {
		if (type == LengthType.AUTO) {
			return AUTO_LENGTH;
		}
		if (length == 0) {
			return ZERO_LENGTH;
		}
		return new Length(length, type);
	}

	private Length(double length, LengthType type) {
		this.length = length;
		this.type = type;
	}

	public LengthType getType() {
		return this.type;
	}

	public double getLength() {
		return this.length;
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
		default:
			throw new IllegalStateException();
		}
		buff.append(']');
		return buff.toString();
	}
}
