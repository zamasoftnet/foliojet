package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Value.java 1570 2018-07-11 05:50:15Z miyabe $
 */
public interface Value {
	// RGBカラー
	public static final short TYPE_COLOR = LexicalUnit.SAC_RGBCOLOR;

	// 絶対/デバイス相対
	public static final short TYPE_ABSOLUTE_LENGTH = 1000;

	// フォント相対
	public static final short TYPE_EM_LENGTH = TYPE_ABSOLUTE_LENGTH + 1;

	public static final short TYPE_EX_LENGTH = TYPE_EM_LENGTH + 1;

	// パーセント
	public static final short TYPE_PERCENTAGE = LexicalUnit.SAC_PERCENTAGE;

	// 継承
	public static final short TYPE_INHERIT = TYPE_EX_LENGTH + 1;

	// 表示レベル
	public static final short TYPE_DISPLAY = TYPE_INHERIT + 1;

	// 位置
	public static final short TYPE_POSITION = TYPE_DISPLAY + 1;

	// 自動
	public static final short TYPE_AUTO = TYPE_POSITION + 1;

	// なし
	public static final short TYPE_NONE = TYPE_AUTO + 1;

	// 普通
	public static final short TYPE_NORMAL = TYPE_NONE + 1;

	// 透明
	public static final short TYPE_TRANSPARENT = TYPE_NORMAL + 1;

	// デフォルト
	public static final short TYPE_DEFAULT = TYPE_TRANSPARENT + 1;

	// ボーダースタイル
	public static final short TYPE_BORDER_STYLE = TYPE_DEFAULT + 1;

	// 浮動体タイプ
	public static final short TYPE_FLOAT = TYPE_BORDER_STYLE + 1;

	// 浮動体クリア
	public static final short TYPE_CLEAR = TYPE_FLOAT + 1;

	// フォントの相対サイズ指定
	public static final short TYPE_RELATIVE_SIZE = TYPE_CLEAR + 1;

	// 垂直位置
	public static final short TYPE_VERTICAL_ALIGN = TYPE_RELATIVE_SIZE + 1;

	// 整数
	public static final short TYPE_INTEGER = TYPE_VERTICAL_ALIGN + 1;

	// 実数
	public static final short TYPE_REAL = TYPE_INTEGER + 1;

	// 文字列
	public static final short TYPE_STRING = TYPE_REAL + 1;

	// 矩形
	public static final short TYPE_RECT = TYPE_STRING + 1;

	// URI
	public static final short TYPE_URI = TYPE_RECT + 1;

	// 可視性
	public static final short TYPE_VISIBILITY = TYPE_URI + 1;

	// はみ出し部分
	public static final short TYPE_OVERFLOW = TYPE_VISIBILITY + 1;

	// キャプションの位置
	public static final short TYPE_CAPTION_SIDE = TYPE_OVERFLOW + 1;

	// テーブルのレイアウト方法
	public static final short TYPE_TABLE_LAYOUT = TYPE_CAPTION_SIDE + 1;

	// テーブルの境界モデル
	public static final short TYPE_BORDER_COLLAPSE = TYPE_TABLE_LAYOUT + 1;

	// テーブルの空セルの表示
	public static final short TYPE_EMPTY_CELLS = TYPE_BORDER_COLLAPSE + 1;

	// 背景の繰り返し方法
	public static final short TYPE_BACKGROUND_REPEAT = TYPE_EMPTY_CELLS + 1;

	// 背景の固定
	public static final short TYPE_BACKGROUND_ATTACHMENT = TYPE_BACKGROUND_REPEAT + 1;

	// フォントファミリ
	public static final short TYPE_FONT_FAMILY = TYPE_BACKGROUND_ATTACHMENT + 1;

	// フォントスタイル
	public static final short TYPE_FONT_STYLE = TYPE_FONT_FAMILY + 1;

	// フォントバリアント
	public static final short TYPE_FONT_VARIANT = TYPE_FONT_STYLE + 1;

	// フォントウエイト
	public static final short TYPE_FONT_WEIGHT = TYPE_FONT_VARIANT + 1;

	// テキストの揃え
	public static final short TYPE_TEXT_ALIGN = TYPE_FONT_WEIGHT + 1;

	// テキストの装飾
	public static final short TYPE_TEXT_DECORATION = TYPE_TEXT_ALIGN + 1;

	// テキストの変換
	public static final short TYPE_TEXT_TRANSFORM = TYPE_TEXT_DECORATION + 1;

	// ページの進行方向
	public static final short TYPE_BLOCK_FLOW = TYPE_TEXT_TRANSFORM + 1;

	// 空白の扱い
	public static final short TYPE_WHITE_SPACE = TYPE_BLOCK_FLOW + 1;

	// リストのスタイル
	public static final short TYPE_LIST_STYLE_TYPE = TYPE_WHITE_SPACE + 1;

	// リストの位置
	public static final short TYPE_LIST_STYLE_POSITION = TYPE_LIST_STYLE_TYPE + 1;

	// 引用符
	public static final short TYPE_QUOTE = TYPE_LIST_STYLE_POSITION + 1;

	// 値の配列
	public static final short TYPE_VALUES = TYPE_QUOTE + 1;

	// カウンタ
	public static final short TYPE_COUNTER = TYPE_VALUES + 1;

	// 階層化カウンタ
	public static final short TYPE_COUNTERS = TYPE_COUNTER + 1;

	// 属性
	public static final short TYPE_ATTR = TYPE_COUNTERS + 1;

	// 引用符
	public static final short TYPE_QUOTES = TYPE_ATTR + 1;

	// カウンタの設定
	public static final short TYPE_COUNTER_SET = TYPE_QUOTES + 1;

	// 前後改ページ
	public static final short TYPE_PAGE_BREAK = TYPE_COUNTER_SET + 1;

	// 内部改ページ
	public static final short TYPE_PAGE_BREAK_INSIDE = TYPE_PAGE_BREAK + 1;

	// 行進行方向
	public static final short TYPE_DIRECTION = TYPE_PAGE_BREAK_INSIDE + 1;

	//
	public static final short TYPE_UNICODE_BIDI = TYPE_DIRECTION + 1;

	/**
	 * 値のタイプを返します。
	 * 
	 * @return
	 */
	public short getValueType();
}