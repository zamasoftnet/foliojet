package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum CaptionSideValue implements Value {
	TOP_VALUE(CaptionSideValue.CAPTION_SIDE_TOP),

	BOTTOM_VALUE(CaptionSideValue.CAPTION_SIDE_BOTTOM),

	BEFORE_VALUE(CaptionSideValue.CAPTION_SIDE_BEFORE),

	AFTER_VALUE(CaptionSideValue.CAPTION_SIDE_AFTER);
	public static final byte CAPTION_SIDE_TOP = 1;
	public static final byte CAPTION_SIDE_BOTTOM = 2;
	public static final byte CAPTION_SIDE_BEFORE = 3;
	public static final byte CAPTION_SIDE_AFTER = 4;

	private final byte captionSide;

	private CaptionSideValue(byte captionSide) {
		this.captionSide = captionSide;
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