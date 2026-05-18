package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

public interface CSS3Value extends Value {
	// Unicode-Range
	public static final short TYPE_UNICODE_RANGE = 3001;
	// @font-face src
	public static final short TYPE_SRC = 3002;

	public static final short TYPE_COLUMN_FILL = 3003;
	public static final short TYPE_COLUMN_SPAN = 3004;

	public static final short TYPE_RADIUS = 3005;

	public static final short TYPE_TRANSFORM = 3006;

	public static final short TYPE_TEXT_SHADOW = 3007;

	public static final short TYPE_REM_LENGTH = 3008;

	public static final short TYPE_CH_LENGTH = 3009;

	public static final short TYPE_BOX_SIZING = 3010;

	// 単語の分割
	public static final short TYPE_WORD_WRAP = 3011;

	public static final short TYPE_WORD_BREAK = 3012;

	public static final short TYPE_BACKGROUND_CLIP = 3013;
}