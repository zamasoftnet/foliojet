package jp.cssj.homare.style.box.params;

public interface Pos {
	public static final byte TYPE_PAGE = 1;
	public static final byte TYPE_LINE = 2;
	public static final byte TYPE_TEXT_BLOCK = 3;
	public static final byte TYPE_FLOW = 4;
	public static final byte TYPE_INLINE = 5;
	public static final byte TYPE_FLOAT = 6;
	public static final byte TYPE_ABSOLUTE = 7;
	public static final byte TYPE_TABLE = 8;
	public static final byte TYPE_TABLE_CAPTION = 9;
	public static final byte TYPE_TABLE_COLUMN = 10;
	public static final byte TYPE_TABLE_ROW_GROUP = 11;
	public static final byte TYPE_TABLE_ROW = 12;
	public static final byte TYPE_TABLE_CELL = 13;

	public byte getType();
}
