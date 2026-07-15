package net.zamasoft.foliojet.style.box.params;

/**
 * 位置を表すオブジェクトです。
 *
 * @author MIYABE Tatsuhiko
 */
public class Offset {
	public static final Offset ZERO_OFFSET = new Offset(0, 0, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
	public static final Offset HALF_OFFSET = new Offset(.5, .5, LengthType.RELATIVE, LengthType.RELATIVE);
	public static final Offset AUTO_OFFSET = new Offset(0, 0, LengthType.AUTO, LengthType.AUTO);

	private final double x;
	private final double y;
	private final byte flags;

	public static Offset create(double x, double y, LengthType xType, LengthType yType) {
		if (xType == LengthType.AUTO && yType == LengthType.AUTO) {
			return AUTO_OFFSET;
		}
		if (xType != LengthType.AUTO && yType != LengthType.AUTO && x == 0 && y == 0) {
			return ZERO_OFFSET;
		}
		return new Offset(x, y, xType, yType);
	}

	private Offset(double x, double y, LengthType xType, LengthType yType) {
		this.x = x;
		this.y = y;
		this.flags = (byte) (xType.ordinal() | (yType.ordinal() << 2));
	}

	public LengthType getXType() {
		return LengthType.VALUES[this.flags & 3];
	}

	public LengthType getYType() {
		return LengthType.VALUES[(this.flags >> 2) & 3];
	}

	public double getX() {
		return this.x;
	}

	public double getY() {
		return this.y;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		buff.append("[x=");
		switch (this.getXType()) {
		case ABSOLUTE:
			buff.append(this.x);
			break;
		case RELATIVE:
			buff.append(this.x * 100).append('%');
			break;
		case AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(",y=");
		switch (this.getYType()) {
		case ABSOLUTE:
			buff.append(this.y);
			break;
		case RELATIVE:
			buff.append(this.y * 100).append('%');
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
