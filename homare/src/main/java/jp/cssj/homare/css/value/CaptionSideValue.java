package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CaptionSideValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CaptionSideValue implements Value {
	public static final byte CAPTION_SIDE_TOP = 1;
	public static final byte CAPTION_SIDE_BOTTOM = 2;
	public static final byte CAPTION_SIDE_BEFORE = 3;
	public static final byte CAPTION_SIDE_AFTER = 4;

	public static final CaptionSideValue TOP_VALUE = new CaptionSideValue(CAPTION_SIDE_TOP);

	public static final CaptionSideValue BOTTOM_VALUE = new CaptionSideValue(CAPTION_SIDE_BOTTOM);

	public static final CaptionSideValue BEFORE_VALUE = new CaptionSideValue(CAPTION_SIDE_BEFORE);

	public static final CaptionSideValue AFTER_VALUE = new CaptionSideValue(CAPTION_SIDE_AFTER);

	private final byte captionSide;

	private CaptionSideValue(byte captionSide) {
		this.captionSide = captionSide;
	}

	public short getValueType() {
		return TYPE_CAPTION_SIDE;
	}

	public byte getCaptionSide() {
		return this.captionSide;
	}

	public String toString() {
		switch (this.captionSide) {
		case CAPTION_SIDE_TOP:
			return "top";

		case CAPTION_SIDE_BOTTOM:
			return "bottom";

		case CAPTION_SIDE_BEFORE:
			return "before";

		case CAPTION_SIDE_AFTER:
			return "after";

		default:
			throw new IllegalStateException();
		}
	}
}