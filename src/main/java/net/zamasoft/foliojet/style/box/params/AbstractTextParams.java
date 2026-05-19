package net.zamasoft.foliojet.style.box.params;

import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.pdfg2d.text.hyphenation.Hyphenation;

public abstract class AbstractTextParams extends Params {
	public static final byte FLOW_TB = 1;

	public static final byte FLOW_RL = 2;

	public static final byte FLOW_LR = 3;

	public static final byte DIRECTION_LTR = 1;

	public static final byte DIRECTION_RTL = 2;

	public static final byte TEXT_TRANSFORM_NONE = 0;

	public static final byte TEXT_TRANSFORM_CAPITALIZE = 1;

	public static final byte TEXT_TRANSFORM_UPPERCASE = 2;

	public static final byte TEXT_TRANSFORM_LOWERCASE = 3;

	public static final byte WHITE_SPACE_NORMAL = 1;

	public static final byte WHITE_SPACE_PRE = 2;

	public static final byte WHITE_SPACE_NOWRAP = 3;

	public static final byte WHITE_SPACE_PRE_WRAP = 4;

	public static final byte WHITE_SPACE_PRE_LINE = 5;

	public static final byte WORD_WRAP_NORMAL = 1;

	public static final byte WORD_WRAP_BREAK_WORD = 2;

	public static final byte DECORATION_UNDERLINE = 0x01;

	public static final byte DECORATION_OVERLINE = 0x02;

	public static final byte DECORATION_LINE_THROUGH = 0x04;

	/**
	 * フォントのスタイル。
	 */
	public FontStyle fontStyle;

	public byte flow = FLOW_TB;

	public byte direction = DIRECTION_LTR;

	/**
	 * フォント管理オブジェクト。
	 */
	public FontManager fontManager;

	/**
	 * ハイフネーション。
	 */
	public Hyphenation hyphenation;

	/**
	 * 文字間
	 */
	public Length letterSpacing = Length.ZERO_LENGTH;

	/**
	 * 単語間
	 */
	public double wordSpacing = 0;

	/**
	 * テキストの大文字変換
	 */
	public byte textTransform = TEXT_TRANSFORM_NONE;

	/**
	 * 空白の扱い。
	 */
	public byte whiteSpace = WHITE_SPACE_NORMAL;

	/**
	 * 折り返し方法
	 */
	public byte wordWrap = WORD_WRAP_NORMAL;

	/**
	 * 文字色。
	 */
	public Color color = null;

	/**
	 * 文字装飾
	 */
	public byte decoration = 0;

	/**
	 * 文字装飾の太さ
	 */
	public double decorationThickness = 0;

	/**
	 * 文字の枠の太さ
	 */
	public double textStrokeWidth = 0;

	/**
	 * 文字の枠の色
	 */
	public Color textStrokeColor = null;

	/**
	 * 文字の影
	 */
	public TextShadow[] textShadows = null;

	public FontListMetrics getFontListMetrics() {
		return this.fontManager.getFontListMetrics(this.fontStyle);
	}

	public String toString() {
		return super.toString() + "[fontStyle=" + this.fontStyle + ",letterSpacing=" + this.letterSpacing
				+ ",whiteSpace=" + this.whiteSpace + ",wordWrap=" + this.wordWrap + ",color=" + this.color
				+ ",decoration=" + this.decoration + ",decorationThickness=" + this.decorationThickness
				+ ",textStrokeWidth=" + this.textStrokeWidth + ",textStrokeColor=" + this.textStrokeColor + "]";
	}
}
