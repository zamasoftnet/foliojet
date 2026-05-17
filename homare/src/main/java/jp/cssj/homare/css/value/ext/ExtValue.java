package jp.cssj.homare.css.value.ext;

import jp.cssj.homare.css.value.Value;

public interface ExtValue extends Value {
	// 見出し
	public static final short TYPE_CSSJ_LAST_HEADING = 2001;
	public static final short TYPE_CSSJ_FIRST_HEADING = 2009;

	// ページコンテンツ
	public static final short TYPE_CSSJ_PAGE_CONTENT = 2002;

	// フォントポリシー
	public static final short TYPE_CSSJ_FONT_POLICY = 2003;

	// ページ番号
	public static final short TYPE_CSSJ_PAGE_REF = 2004;

	// 回転
	public static final short TYPE_CSSJ_DIRECTION_MODE = 2005;

	// ルビ
	public static final short TYPE_CSSJ_RUBY = 2006;

	// 禁則文字
	public static final short TYPE_CSSJ_NO_BREAK_RULE = 2007;

	// タイトル
	public static final short TYPE_CSSJ_TITLE = 2008;

}
