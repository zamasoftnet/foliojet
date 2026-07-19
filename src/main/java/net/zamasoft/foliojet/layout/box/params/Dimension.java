package net.zamasoft.foliojet.layout.box.params;

/**
 * サイズを表すオブジェクトです。
 *
 * @author MIYABE Tatsuhiko
 */
public class Dimension {
	public static final Dimension ZERO_DIMENSION = new Dimension(0, 0, 0, 0, LengthType.ABSOLUTE,
			LengthType.ABSOLUTE);
	public static final Dimension AUTO_DIMENSION = new Dimension(0, 0, 0, 0, LengthType.AUTO, LengthType.AUTO);

	private final double width;
	private final double height;
	/** MIXED(calc()の絶対+割合混在)の場合のみ意味を持つ割合成分。それ以外は常に0。 */
	private final double widthRatio;
	private final double heightRatio;
	private final byte flags;

	public static Dimension create(double width, double height, LengthType widthType, LengthType heightType) {
		return create(width, 0, height, 0, widthType, heightType);
	}

	/** widthType/heightTypeがMIXEDの場合のwidthRatio/heightRatio付きの生成。 */
	public static Dimension create(double width, double widthRatio, double height, double heightRatio,
			LengthType widthType, LengthType heightType) {
		if (widthType == LengthType.AUTO && heightType == LengthType.AUTO) {
			return AUTO_DIMENSION;
		}
		if (widthType != LengthType.AUTO && heightType != LengthType.AUTO && widthType != LengthType.MIXED
				&& heightType != LengthType.MIXED && width == 0 && height == 0) {
			return ZERO_DIMENSION;
		}
		return new Dimension(width, widthRatio, height, heightRatio, widthType, heightType);
	}

	private Dimension(double width, double widthRatio, double height, double heightRatio, LengthType widthType,
			LengthType heightType) {
		this.width = width;
		this.height = height;
		this.widthRatio = widthRatio;
		this.heightRatio = heightRatio;
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

	public double getWidthRatio() {
		return this.widthRatio;
	}

	public double getHeightRatio() {
		return this.heightRatio;
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

	/**
	 * 与えられた書字方向での行方向のMIXED割合成分を返します(それ以外の型では0)。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向の割合成分
	 */
	public double getLineRatio(WritingMode flow) {
		return flow.isVertical() ? this.getHeightRatio() : this.getWidthRatio();
	}

	/**
	 * 与えられた書字方向でのページ方向のMIXED割合成分を返します(それ以外の型では0)。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向の割合成分
	 */
	public double getPageRatio(WritingMode flow) {
		return flow.isVertical() ? this.getWidthRatio() : this.getHeightRatio();
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
		case MIXED:
			buff.append(this.width).append('+').append(this.widthRatio * 100).append('%');
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
		case MIXED:
			buff.append(this.height).append('+').append(this.heightRatio * 100).append('%');
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
