package net.zamasoft.foliojet.style.box.params;

/**
 * サイズを表すオブジェクトです。
 *
 * @author MIYABE Tatsuhiko
 */
public class Dimension {
	public static final Dimension ZERO_DIMENSION = new Dimension(0, 0, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
	public static final Dimension AUTO_DIMENSION = new Dimension(0, 0, LengthType.AUTO, LengthType.AUTO);

	private final double width;
	private final double height;
	private final byte flags;

	public static Dimension create(double width, double height, LengthType widthType, LengthType heightType) {
		if (widthType == LengthType.AUTO && heightType == LengthType.AUTO) {
			return AUTO_DIMENSION;
		}
		if (widthType != LengthType.AUTO && heightType != LengthType.AUTO && width == 0 && height == 0) {
			return ZERO_DIMENSION;
		}
		return new Dimension(width, height, widthType, heightType);
	}

	private Dimension(double width, double height, LengthType widthType, LengthType heightType) {
		this.width = width;
		this.height = height;
		this.flags = (byte) (widthType.ordinal() | (heightType.ordinal() << 2));
	}

	public LengthType getWidthType() {
		return LengthType.VALUES[this.flags & 3];
	}

	public LengthType getHeightType() {
		return LengthType.VALUES[(this.flags >> 2) & 3];
	}

	public double getWidth() {
		return this.width;
	}

	public double getHeight() {
		return this.height;
	}

	/**
	 * 与えられた書字方向での行方向寸法の型を返します(横書き=幅、縦書き=高さ)。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向寸法の型
	 */
	public LengthType getLineType(WritingMode flow) {
		return flow.isVertical() ? this.getHeightType() : this.getWidthType();
	}

	/**
	 * 与えられた書字方向でのページ方向寸法の型を返します(横書き=高さ、縦書き=幅)。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向寸法の型
	 */
	public LengthType getPageType(WritingMode flow) {
		return flow.isVertical() ? this.getWidthType() : this.getHeightType();
	}

	/**
	 * 与えられた書字方向での行方向の寸法値を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向の寸法値
	 */
	public double getLineLength(WritingMode flow) {
		return flow.isVertical() ? this.getHeight() : this.getWidth();
	}

	/**
	 * 与えられた書字方向でのページ方向の寸法値を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向の寸法値
	 */
	public double getPageLength(WritingMode flow) {
		return flow.isVertical() ? this.getWidth() : this.getHeight();
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		buff.append("[width=");
		switch (this.getWidthType()) {
		case ABSOLUTE:
			buff.append(this.width);
			break;
		case RELATIVE:
			buff.append(this.width * 100).append('%');
			break;
		case AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(",height=");
		switch (this.getHeightType()) {
		case ABSOLUTE:
			buff.append(this.height);
			break;
		case RELATIVE:
			buff.append(this.height * 100).append('%');
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
