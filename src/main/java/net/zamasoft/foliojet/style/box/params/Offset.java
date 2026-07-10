package net.zamasoft.foliojet.style.box.params;

/**
 * 位置を表すオブジェクトです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Offset.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Offset {
	public static final short TYPE_ABSOLUTE = 1;
	public static final short TYPE_RELATIVE = 2;
	public static final short TYPE_AUTO = 3;

	public static final Offset ZERO_OFFSET = new Offset(0, 0, TYPE_ABSOLUTE, TYPE_ABSOLUTE);
	public static final Offset HALF_OFFSET = new Offset(.5, .5, TYPE_RELATIVE, TYPE_RELATIVE);
	public static final Offset AUTO_OFFSET = new Offset(0, 0, TYPE_AUTO, TYPE_AUTO);

	private final double x;
	private final double y;
	private final byte flags;

	public static Offset create(double x, double y, short xType, short yType) {
		if (xType == TYPE_AUTO && yType == TYPE_AUTO) {
			return AUTO_OFFSET;
		}
		if (xType != TYPE_AUTO && yType != TYPE_AUTO && x == 0 && y == 0) {
			return ZERO_OFFSET;
		}
		return new Offset(x, y, xType, yType);
	}

	private Offset(double x, double y, short xType, short yType) {
		assert xType >= 1 && xType <= 3;
		assert yType >= 1 && yType <= 3;
		this.x = x;
		this.y = y;
		this.flags = (byte) (xType | (yType << 4));
	}

	public short getXType() {
		return (short) (this.flags & 3);
	}

	public short getYType() {
		return (short) ((this.flags >> 4) & 3);
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
		case TYPE_ABSOLUTE:
			buff.append(this.x);
			break;
		case TYPE_RELATIVE:
			buff.append(this.x * 100).append('%');
			break;
		case TYPE_AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(",y=");
		switch (this.getYType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.y);
			break;
		case TYPE_RELATIVE:
			buff.append(this.y * 100).append('%');
			break;
		case TYPE_AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(']');
		return buff.toString();
	}
}
