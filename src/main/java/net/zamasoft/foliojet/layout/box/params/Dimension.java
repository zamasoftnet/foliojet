package net.zamasoft.foliojet.layout.box.params;

/**
 * An object representing a size.
 *
 * @author MIYABE Tatsuhiko
 */
public class Dimension {
	public static final Dimension ZERO_DIMENSION = new Dimension(0, 0, 0, 0, LengthType.ABSOLUTE,
			LengthType.ABSOLUTE);
	public static final Dimension AUTO_DIMENSION = new Dimension(0, 0, 0, 0, LengthType.AUTO, LengthType.AUTO);

	private final double width;
	private final double height;
	/** Percentage component, meaningful only for MIXED (absolute + percentage in calc()). Always 0 otherwise. */
	private final double widthRatio;
	private final double heightRatio;
	private final byte flags;

	public static Dimension create(double width, double height, LengthType widthType, LengthType heightType) {
		return create(width, 0, height, 0, widthType, heightType);
	}

	/** Creates a dimension with widthRatio/heightRatio when widthType/heightType is MIXED. */
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
	 * Returns the line-direction size type for the given writing mode (horizontal writing = width;
	 * vertical writing = height).
	 *
	 * @param flow writing mode that determines the axes
	 * @return line-direction size type
	 */
	public LengthType getLineType(WritingMode flow) {
		return flow.isVertical() ? this.getHeightType() : this.getWidthType();
	}

	/**
	 * Returns the page-direction size type for the given writing mode (horizontal writing = height;
	 * vertical writing = width).
	 *
	 * @param flow writing mode that determines the axes
	 * @return page-direction size type
	 */
	public LengthType getPageType(WritingMode flow) {
		return flow.isVertical() ? this.getWidthType() : this.getHeightType();
	}

	/**
	 * Returns the line-direction size value for the given writing mode.
	 *
	 * @param flow writing mode that determines the axes
	 * @return line-direction size value
	 */
	public double getLineLength(WritingMode flow) {
		return flow.isVertical() ? this.getHeight() : this.getWidth();
	}

	/**
	 * Returns the page-direction size value for the given writing mode.
	 *
	 * @param flow writing mode that determines the axes
	 * @return page-direction size value
	 */
	public double getPageLength(WritingMode flow) {
		return flow.isVertical() ? this.getWidth() : this.getHeight();
	}

	/**
	 * Returns the line-direction MIXED percentage component for the given writing mode (0 for other types).
	 *
	 * @param flow writing mode that determines the axes
	 * @return line-direction percentage component
	 */
	public double getLineRatio(WritingMode flow) {
		return flow.isVertical() ? this.getHeightRatio() : this.getWidthRatio();
	}

	/**
	 * Returns the page-direction MIXED percentage component for the given writing mode (0 for other types).
	 *
	 * @param flow writing mode that determines the axes
	 * @return page-direction percentage component
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
