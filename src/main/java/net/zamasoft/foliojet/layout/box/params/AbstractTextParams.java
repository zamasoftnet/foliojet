package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.layout.text.breaking.LineBreakRules;
import net.zamasoft.pdfg2d.gc.text.pipeline.Hyphenator;

public abstract class AbstractTextParams extends Params {
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

	/**
	 * {@code text-wrap-style: auto}(貪欲法。既定)です。
	 * {@code balance}/{@code stable}も未対応のためこの値へ落とします。
	 */
	public static final byte TEXT_WRAP_STYLE_AUTO = 1;

	/**
	 * {@code text-wrap-style: pretty}(Knuth-Plass全体最適)です。
	 */
	public static final byte TEXT_WRAP_STYLE_PRETTY = 2;

	public static final byte HYPHENS_NONE = 1;

	public static final byte HYPHENS_MANUAL = 2;

	public static final byte HYPHENS_AUTO = 3;

	public static final byte DECORATION_UNDERLINE = 0x01;

	public static final byte DECORATION_OVERLINE = 0x02;

	public static final byte DECORATION_LINE_THROUGH = 0x04;

	/**
	 * ルビ役割なし(既定)です(2026-07-25、注釈付きテキスト方式)。
	 */
	public static final byte RUBY_NONE = 0;

	/**
	 * ルビコンテナ(ruby要素相当)です。
	 */
	public static final byte RUBY_CONTAINER = 1;

	/**
	 * ルビ親文字(rb要素相当)です。
	 */
	public static final byte RUBY_BASE = 2;

	/**
	 * ふりがな(rt要素相当)です。
	 */
	public static final byte RUBY_TEXT = 3;

	/**
	 * ルビ役割マーカーです(2026-07-25、注釈付きテキスト方式の仕様裁定
	 * ——docs/history/2026-07-25-ruby-annotation-spec-decision.md)。
	 * ルビ関連要素(ruby/rb/rt)で{@code RUBY_*}が設定され、文字処理層
	 * ({@code StyledTextUnitizer})が注釈付きテキスト単位を組み立てる
	 * 手掛かりにします。
	 */
	public byte rubyRole = RUBY_NONE;

	/**
	 * フォントのスタイル。
	 */
	public FontStyle fontStyle;

	public WritingMode flow = WritingMode.TB;

	public byte direction = DIRECTION_LTR;

	/**
	 * フォント管理オブジェクト。
	 */
	public FontManager fontManager;

	/**
	 * 行分割規則(禁則)。
	 */
	public LineBreakRules lineBreakRules;

	/**
	 * 文字間
	 */
	public Length letterSpacing = Length.ZERO_LENGTH;

	/**
	 * 和欧文間スペース(text-autospace)の実効フラグです(和文詰めA1。
	 * {@code TextAutospaceValue.ALPHA}|{@code NUMERIC}のbit——0=なし。
	 * 幾何はA2で配線)。
	 */
	public byte textAutospace = 0;

	/**
	 * 連続約物の詰め(text-spacing-trim)の無効化です(和文詰めT1b。
	 * {@code true}=space-all=詰めなし。既定false=normal=T1aで
	 * font層から移管した詰め)。
	 */
	public boolean textSpacingTrimOff = false;

	/**
	 * 行末句読点のぶら下げ(hanging-punctuation: allow-end)です
	 * (和文詰めH1。既定false=none)。
	 */
	public boolean hangingPunctuationEnd = false;

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
	 * 行分割戦略(CSS {@code text-wrap-style})です(2026-07-25)。
	 * {@link #TEXT_WRAP_STYLE_PRETTY}のときだけKnuth-Plass全体最適
	 * ({@code TotalFitSession})を試み、既定の{@link #TEXT_WRAP_STYLE_AUTO}
	 * では貪欲法で組みます。K-Pの適用単位が段落のため、実際に読まれるのは
	 * 段落を確立するブロック({@code BlockParams})の値だけです。
	 */
	public byte textWrapStyle = TEXT_WRAP_STYLE_AUTO;

	/**
	 * 単語内分綴(CSS hyphens)。
	 */
	public byte hyphens = HYPHENS_MANUAL;

	/**
	 * hyphens:auto時の言語別分綴器。パターンが無い言語ではnull。
	 */
	public Hyphenator hyphenator;

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
				+ ",whiteSpace=" + this.whiteSpace + ",wordWrap=" + this.wordWrap + ",textWrapStyle="
				+ this.textWrapStyle + ",color=" + this.color
				+ ",decoration=" + this.decoration + ",decorationThickness=" + this.decorationThickness
				+ ",textStrokeWidth=" + this.textStrokeWidth + ",textStrokeColor=" + this.textStrokeColor + "]";
	}
}
