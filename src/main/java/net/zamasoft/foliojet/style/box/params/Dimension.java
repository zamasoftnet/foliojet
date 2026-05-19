package net.zamasoft.foliojet.style.box.params;

/**
 * サイズを表すオブジェクトです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Dimension.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Dimension {
	public static final byte TYPE_ABSOLUTE = 1;
	public static final byte TYPE_RELATIVE = 2;
	public static final byte TYPE_AUTO = 3;

	public static final Dimension ZERO_DIMENSION = new Dimension(0, 0, TYPE_ABSOLUTE, TYPE_ABSOLUTE);
	public static final Dimension AUTO_DIMENSION = new Dimension(0, 0, TYPE_AUTO, TYPE_AUTO);

	private final double width;
	private final double height;
	private final byte flags;

	public static Dimension create(double width, double height, byte widthType, byte heightType) {
		if (widthType == TYPE_AUTO && heightType == TYPE_AUTO) {
			return AUTO_DIMENSION;
		}
		if (widthType != TYPE_AUTO && heightType != TYPE_AUTO && width == 0 && height == 0) {
			return ZERO_DIMENSION;
		}
		return new Dimension(width, height, widthType, heightType);
	}

	private Dimension(double width, double height, byte widthType, byte heightType) {
		this.width = width;
		this.height = height;
		this.flags = (byte) (widthType | (heightType << 2));
	}

	public byte getWidthType() {
		return (byte) (this.flags & 3);
	}

	public byte getHeightType() {
		return (byte) ((this.flags >> 2) & 3);
	}

	public double getWidth() {
		return this.width;
	}

	public double getHeight() {
		return this.height;
	}

	public String toString() {
		StringBuffer buff = new StringBuffer();
		buff.append("[width=");
		switch (this.getWidthType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.width);
			break;
		case TYPE_RELATIVE:
			buff.append(this.width * 100).append('%');
			break;
		case TYPE_AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(",height=");
		switch (this.getHeightType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.height);
			break;
		case TYPE_RELATIVE:
			buff.append(this.height * 100).append('%');
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
