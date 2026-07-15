package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum DisplayValue implements Value {
	NONE_VALUE(DisplayValue.NONE),

	BLOCK_VALUE(DisplayValue.BLOCK),

	INLINE_VALUE(DisplayValue.INLINE),

	INLINE_BLOCK_VALUE(DisplayValue.INLINE_BLOCK),

	LIST_ITEM_VALUE(DisplayValue.LIST_ITEM),

	RUN_IN_VALUE(DisplayValue.RUN_IN),

	TABLE_VALUE(DisplayValue.TABLE),

	INLINE_TABLE_VALUE(DisplayValue.INLINE_TABLE),

	TABLE_ROW_GROUP_VALUE(DisplayValue.TABLE_ROW_GROUP),

	TABLE_COLUMN_VALUE(DisplayValue.TABLE_COLUMN),

	TABLE_COLUMN_GROUP_VALUE(DisplayValue.TABLE_COLUMN_GROUP),

	TABLE_HEADER_GROUP_VALUE(DisplayValue.TABLE_HEADER_GROUP),

	TABLE_FOOTER_GROUP_VALUE(DisplayValue.TABLE_FOOTER_GROUP),

	TABLE_ROW_VALUE(DisplayValue.TABLE_ROW),

	TABLE_CELL_VALUE(DisplayValue.TABLE_CELL),

	TABLE_CAPTION_VALUE(DisplayValue.TABLE_CAPTION);
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

	private final byte display;

	private DisplayValue(byte display) {
		this.display = display;
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