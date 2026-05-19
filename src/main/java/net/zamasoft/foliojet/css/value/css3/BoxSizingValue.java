package net.zamasoft.foliojet.css.value.css3;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnFillValue.java 1474 2016-04-23 06:22:56Z miyabe $
 */
public class BoxSizingValue implements CSS3Value {
	public static final byte CONTENT_BOX = 1;

	public static final byte BORDER_BOX = 2;

	public static final BoxSizingValue CONTENT_BOX_VALUE = new BoxSizingValue(CONTENT_BOX);

	public static final BoxSizingValue BORDER_BOX_VALUE = new BoxSizingValue(BORDER_BOX);

	private final byte boxSizing;

	private BoxSizingValue(byte boxSizing) {
		this.boxSizing = boxSizing;
	}

	public short getValueType() {
		return TYPE_BOX_SIZING;
	}

	public byte getBoxSizing() {
		return this.boxSizing;
	}

	public String toString() {
		switch (this.boxSizing) {
		case CONTENT_BOX:
			return "content-box";

		case BORDER_BOX:
			return "border-box";

		default:
			throw new IllegalStateException();
		}
	}
}