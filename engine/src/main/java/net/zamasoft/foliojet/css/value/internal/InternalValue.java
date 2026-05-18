package net.zamasoft.foliojet.css.value.internal;

import net.zamasoft.foliojet.css.value.Value;

public interface InternalValue extends Value {
	/**
	 * 画像
	 */
	public static final short TYPE_CSSJ_IMAGE = 3001;

	/**
	 * HTMLアラインメント
	 */
	public static final short TYPE_CSSJ_HTML_ALIGN = 3002;

	/**
	 * HTMLテーブル境界
	 */
	public static final short TYPE_CSSJ_HTML_TABLE_BORDER = 3003;
}
