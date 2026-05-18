package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: DisplayValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class DisplayValue implements Value {
	public static final byte NONE = 0;

	public static final byte BLOCK = 1;

	public static final byte INLINE = 2;

	public static final byte INLINE_BLOCK = 3;

	public static final byte LIST_ITEM = 4;

	public static final byte RUN_IN = 5;

	public static final byte TABLE = 6;

	public static final byte INLINE_TABLE = 7;

	public static final byte TABLE_ROW_GROUP = 8;

	public static final byte TABLE_COLUMN = 9;

	public static final byte TABLE_COLUMN_GROUP = 10;

	public static final byte TABLE_HEADER_GROUP = 11;

	public static final byte TABLE_FOOTER_GROUP = 12;

	public static final byte TABLE_ROW = 13;

	public static final byte TABLE_CELL = 14;

	public static final byte TABLE_CAPTION = 15;

	public static final DisplayValue NONE_VALUE = new DisplayValue(NONE);

	public static final DisplayValue BLOCK_VALUE = new DisplayValue(BLOCK);

	public static final DisplayValue INLINE_VALUE = new DisplayValue(INLINE);

	public static final DisplayValue INLINE_BLOCK_VALUE = new DisplayValue(INLINE_BLOCK);

	public static final DisplayValue LIST_ITEM_VALUE = new DisplayValue(LIST_ITEM);

	public static final DisplayValue RUN_IN_VALUE = new DisplayValue(RUN_IN);

	public static final DisplayValue TABLE_VALUE = new DisplayValue(TABLE);

	public static final DisplayValue INLINE_TABLE_VALUE = new DisplayValue(INLINE_TABLE);

	public static final DisplayValue TABLE_ROW_GROUP_VALUE = new DisplayValue(TABLE_ROW_GROUP);

	public static final DisplayValue TABLE_COLUMN_VALUE = new DisplayValue(TABLE_COLUMN);

	public static final DisplayValue TABLE_COLUMN_GROUP_VALUE = new DisplayValue(TABLE_COLUMN_GROUP);

	public static final DisplayValue TABLE_HEADER_GROUP_VALUE = new DisplayValue(TABLE_HEADER_GROUP);

	public static final DisplayValue TABLE_FOOTER_GROUP_VALUE = new DisplayValue(TABLE_FOOTER_GROUP);

	public static final DisplayValue TABLE_ROW_VALUE = new DisplayValue(TABLE_ROW);

	public static final DisplayValue TABLE_CELL_VALUE = new DisplayValue(TABLE_CELL);

	public static final DisplayValue TABLE_CAPTION_VALUE = new DisplayValue(TABLE_CAPTION);

	private final byte display;

	private DisplayValue(byte display) {
		this.display = display;
	}

	public short getValueType() {
		return TYPE_DISPLAY;
	}

	public byte getDisplay() {
		return this.display;
	}

	public String toString() {
		switch (this.display) {
		case NONE:
			return "none";
		case BLOCK:
			return "block";
		case INLINE:
			return "inline";
		case INLINE_BLOCK:
			return "inline-block";
		case LIST_ITEM:
			return "list-item";
		case RUN_IN:
			return "run-in";
		case TABLE:
			return "table";
		case INLINE_TABLE:
			return "inline-table";
		case TABLE_ROW_GROUP:
			return "table-row-group";
		case TABLE_COLUMN:
			return "table-column";
		case TABLE_COLUMN_GROUP:
			return "table-column-group";
		case TABLE_HEADER_GROUP:
			return "table-header-group";
		case TABLE_FOOTER_GROUP:
			return "table-footer-group";
		case TABLE_ROW:
			return "table-row";
		case TABLE_CELL:
			return "table-cell";
		case TABLE_CAPTION:
			return "table-caption";
		default:
			throw new IllegalStateException();
		}
	}
}