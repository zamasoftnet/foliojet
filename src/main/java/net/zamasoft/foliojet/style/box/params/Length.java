package net.zamasoft.foliojet.style.box.params;

/**
 * 長さを表すオブジェクトです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Length.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Length {
	public static final byte TYPE_ABSOLUTE = 1;
	public static final byte TYPE_RELATIVE = 2;
	public static final byte TYPE_AUTO = 3;

	public static final Length ZERO_LENGTH = new Length(0, TYPE_ABSOLUTE);
	public static final Length AUTO_LENGTH = new Length(0, TYPE_AUTO);

	private final double length;
	private final byte type;

	public static Length create(double length, byte type) {
		if (type == TYPE_AUTO) {
			return AUTO_LENGTH;
		}
		if (type != TYPE_AUTO && length == 0) {
			return ZERO_LENGTH;
		}
		return new Length(length, type);
	}

	private Length(double length, byte type) {
		this.length = length;
		this.type = type;
	}

	public byte getType() {
		return this.type;
	}

	public double getLength() {
		return this.length;
	}

	public String toString() {
		StringBuffer buff = new StringBuffer();
		buff.append("[length=");
		switch (this.getType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.length);
			break;
		case TYPE_RELATIVE:
			buff.append(this.length * 100).append('%');
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
