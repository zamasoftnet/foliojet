package jp.cssj.homare.style.box.params;

public interface Types {
	public static final byte CLEAR_NONE = 0;
	public static final byte CLEAR_START = 1;
	public static final byte CLEAR_END = 2;
	public static final byte CLEAR_BOTH = 3;

	public static final byte PAGE_BREAK_AUTO = 0;
	public static final byte PAGE_BREAK_AVOID = 1;
	// public static final byte PAGE_BREAK_AVOID_PAGE = 2;
	// public static final byte PAGE_BREAK_AVOID_COLUMN = 3;
	public static final byte PAGE_BREAK_PAGE = 4;
	public static final byte PAGE_BREAK_VERSO = 5;
	public static final byte PAGE_BREAK_RECTO = 6;
	public static final byte PAGE_BREAK_COLUMN = 7;
	public static final byte PAGE_BREAK_IF_VERSO = 8;
	public static final byte PAGE_BREAK_IF_RECTO = 9;

	public static final byte OVERFLOW_VISIBLE = 0;
	public static final byte OVERFLOW_HIDDEN = 1;
	public static final byte OVERFLOW_SCROLL = 2;
	public static final byte OVERFLOW_AUTO = 3;

	public static final byte FLOATING_START = 1;
	public static final byte FLOATING_END = 2;

	public static final byte ALIGN_START = 1;
	public static final byte ALIGN_END = 2;
	public static final byte ALIGN_CENTER = 3;

	public static final byte CAPTION_SIDE_BEFORE = 1;
	public static final byte CAPTION_SIDE_AFTER = 2;

	public static final byte EMPTY_CELLS_HIDE = 1;
	public static final byte EMPTY_CELLS_SHOW = 2;

	public static final byte VERTICAL_ALIGN_BASELINE = 1;
	public static final byte VERTICAL_ALIGN_START = 2;
	public static final byte VERTICAL_ALIGN_MIDDLE = 3;
	public static final byte VERTICAL_ALIGN_END = 4;

	public static final byte ROW_GROUP_TYPE_HEADER = 1;
	public static final byte ROW_GROUP_TYPE_BODY = 2;
	public static final byte ROW_GROUP_TYPE_FOOTER = 3;

	/** ブロックとして配置します。 */
	public static final byte AUTO_POSITION_BLOCK = 1;
	/** インラインとして配置します。 */
	public static final byte AUTO_POSITION_INLINE = 2;

	/** レイアウトコンテキストを基準に配置します。 */
	public static final byte FODUCIAL_CONTEXT = 1;
	/** 以降の全てのページに配置します。 */
	public static final byte FODUCIAL_ALL_PAGE = 2;
	/** 現在のページに即座に配置します。 */
	public static final byte FODUCIAL_CURRENT_PAGE = 3;

	public static final byte BOX_SIZING_CONTENT_BOX = 1;
	public static final byte BOX_SIZING_BORDER_BOX = 2;
}
