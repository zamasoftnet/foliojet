package net.zamasoft.foliojet.layout.box.params;

/**
 * 位置を表すオブジェクトです。
 *
 * @author MIYABE Tatsuhiko
 */
public class Offset {
	public static final Offset ZERO_OFFSET = new Offset(0, 0, 0, 0, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
	public static final Offset HALF_OFFSET = new Offset(.5, 0, .5, 0, LengthType.RELATIVE, LengthType.RELATIVE);
	public static final Offset AUTO_OFFSET = new Offset(0, 0, 0, 0, LengthType.AUTO, LengthType.AUTO);

	private final double x;
	private final double y;
	/** MIXED(calc()の絶対+割合混在)の場合のみ意味を持つ割合成分。それ以外は常に0。 */
	private final double xRatio;
	private final double yRatio;
	private final byte flags;

	public static Offset create(double x, double y, LengthType xType, LengthType yType) {
		return create(x, 0, y, 0, xType, yType);
	}

	/** xType/yTypeがMIXEDの場合のxRatio/yRatio付きの生成。 */
	public static Offset create(double x, double xRatio, double y, double yRatio, LengthType xType,
			LengthType yType) {
		if (xType == LengthType.AUTO && yType == LengthType.AUTO) {
			return AUTO_OFFSET;
		}
		if (xType != LengthType.AUTO && yType != LengthType.AUTO && xType != LengthType.MIXED
				&& yType != LengthType.MIXED && x == 0 && y == 0) {
			return ZERO_OFFSET;
		}
		return new Offset(x, xRatio, y, yRatio, xType, yType);
	}

	private Offset(double x, double xRatio, double y, double yRatio, LengthType xType, LengthType yType) {
		this.x = x;
		this.y = y;
		this.xRatio = xRatio;
		this.yRatio = yRatio;
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

	public double getXRatio() {
		return this.xRatio;
	}

	public double getYRatio() {
		return this.yRatio;
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
		case MIXED:
			buff.append(this.x).append('+').append(this.xRatio * 100).append('%');
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
		case MIXED:
			buff.append(this.y).append('+').append(this.yRatio * 100).append('%');
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
