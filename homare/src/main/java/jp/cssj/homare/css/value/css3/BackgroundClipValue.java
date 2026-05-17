package jp.cssj.homare.css.value.css3;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnFillValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundClipValue implements CSS3Value {
	public static final byte BORDER_BOX = 1;

	public static final byte PADDING_BOX = 2;

	public static final byte CONTENT_BOX = 3;

	public static final byte TEXT = 4;

	public static final BackgroundClipValue BORDER_BOX_VALUE = new BackgroundClipValue(BORDER_BOX);

	public static final BackgroundClipValue PADDING_BOX_VALUE = new BackgroundClipValue(PADDING_BOX);

	public static final BackgroundClipValue CONTENT_BOX_VALUE = new BackgroundClipValue(CONTENT_BOX);

	public static final BackgroundClipValue TEXT_VALUE = new BackgroundClipValue(TEXT);

	private final byte backgroundClip;

	private BackgroundClipValue(byte backgroundClip) {
		this.backgroundClip = backgroundClip;
	}

	public short getValueType() {
		return TYPE_BACKGROUND_CLIP;
	}

	public byte getBackgroundClip() {
		return this.backgroundClip;
	}

	public String toString() {
		switch (this.backgroundClip) {
		case BORDER_BOX:
			return "border-box";

		case PADDING_BOX:
			return "padding-box";

		case CONTENT_BOX:
			return "content-box";

		case TEXT:
			return "text";

		default:
			throw new IllegalStateException();
		}
	}
}