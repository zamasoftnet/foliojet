package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
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

	/** {@code text-justify: auto}——言語で決める(和文=JLREQ、韓国語=語間、他=従来)。 */
	public static final byte TEXT_JUSTIFY_AUTO = 1;
	/** {@code text-justify: none}——両端揃えをしない。 */
	public static final byte TEXT_JUSTIFY_NONE = 2;
	/** {@code text-justify: inter-word}——語間だけを伸ばす。 */
	public static final byte TEXT_JUSTIFY_INTER_WORD = 3;
	/** {@code text-justify: inter-character}({@code distribute})——文字間にも配る。 */
	public static final byte TEXT_JUSTIFY_INTER_CHARACTER = 4;

	public static final byte HYPHENS_NONE = 1;

	public static final byte HYPHENS_MANUAL = 2;

	public static final byte HYPHENS_AUTO = 3;

	public static final byte DECORATION_UNDERLINE = 0x01;

	public static final byte DECORATION_OVERLINE = 0x02;

	public static final byte DECORATION_LINE_THROUGH = 0x04;

	/** {@code text-decoration-style: solid}(既定)。2026-08-29。 */
	public static final byte DECORATION_STYLE_SOLID = 0;

	public static final byte DECORATION_STYLE_DOUBLE = 1;

	public static final byte DECORATION_STYLE_DOTTED = 2;

	public static final byte DECORATION_STYLE_DASHED = 3;

	public static final byte DECORATION_STYLE_WAVY = 4;

	/** {@code text-underline-position: auto}(既定)。2026-08-29。 */
	public static final byte UNDERLINE_POSITION_AUTO = 0;

	/** 下線をフォントの下端(ディセント)の下に置く。 */
	public static final byte UNDERLINE_POSITION_UNDER = 1;

	/** 縦書きで文字の左側(既定側)。 */
	public static final byte UNDERLINE_POSITION_LEFT = 2;

	/** 縦書きで文字の右側。 */
	public static final byte UNDERLINE_POSITION_RIGHT = 3;

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

	/** ルビ注釈コンテナ(rtc要素相当)です。 */
	public static final byte RUBY_TEXT_CONTAINER = 4;

	/**
	 * ルビ役割マーカーです(2026-07-25、注釈付きテキスト方式の仕様裁定
	 * ——docs/history/2026-07-25-ruby-annotation-spec-decision.md)。
	 * ルビ関連要素(ruby/rb/rt)で{@code RUBY_*}が設定され、文字処理層
	 * ({@code StyledTextUnitizer})が注釈付きテキスト単位を組み立てる
	 * 手掛かりにします。
	 */
	public byte rubyRole = RUBY_NONE;

	/** {@code -cssj-warichu:auto}の割注コンテナ。 */
	public boolean warichu = false;

	/** ルビ箱内の文字配置。CSS {@code ruby-align}の計算値です。 */
	public net.zamasoft.foliojet.css.value.RubyAlignValue rubyAlign = net.zamasoft.foliojet.css.value.RubyAlignValue.SPACE_AROUND;

	/** 熟語ルビの共有方法。CSS {@code ruby-merge}の計算値です。 */
	public net.zamasoft.foliojet.css.value.RubyMergeValue rubyMerge = net.zamasoft.foliojet.css.value.RubyMergeValue.SEPARATE;

	/** ルビの張り出し可否。CSS {@code ruby-overhang}の計算値です。 */
	public boolean rubyOverhang = true;

	/** 注釈レベルの配置側。CSS {@code ruby-position}の計算値です。 */
	public net.zamasoft.foliojet.css.value.RubyPositionValue rubyPosition = net.zamasoft.foliojet.css.value.RubyPositionValue.ALTERNATE;

	/**
	 * フォントのスタイル。
	 */
	public FontStyle fontStyle;

	public WritingMode flow = WritingMode.TB;

	/** 書字方向の字形回転種別。進行方向と独立して保持します。 */
	public WritingModeVariant writingModeVariant = WritingModeVariant.NORMAL;

	/** 水平組版(sideways を含む)なら {@code true}。 */
	public final boolean isHorizontalTypesetting() {
		return TypesettingMode.isHorizontal(this.flow, this.writingModeVariant);
	}

	/** 通常の {@code vertical-*} による縦組版だけなら {@code true}。 */
	public final boolean isVerticalTypesetting() {
		return TypesettingMode.isVertical(this.flow, this.writingModeVariant);
	}

	/** sideways 行へ適用する字形列の回転です。 */
	public final WritingModeVariant getGlyphRotation() {
		return TypesettingMode.glyphRotation(this.writingModeVariant);
	}

	/** flow・direction・sideways 回転から導いた物理的な行内進行です。 */
	public final TypesettingMode.InlineProgression getInlineProgression() {
		return TypesettingMode.inlineProgression(this.flow, this.writingModeVariant, this.direction);
	}

	/** 行内進行が物理軸の正方向(右または下)なら {@code 1}、負方向なら {@code -1}。 */
	public final int getInlineProgressionSign() {
		return TypesettingMode.inlineProgressionSign(this.flow, this.writingModeVariant, this.direction);
	}

	/** 回転後の水平組版 baseline に対する over(ascent)側の物理辺です。 */
	public final TypesettingMode.PhysicalSide getTypesettingOverSide() {
		return TypesettingMode.overSide(this.flow, this.writingModeVariant);
	}

	public byte direction = DIRECTION_LTR;

	/**
	 * {@code unicode-bidi}(css-writing-modes-3 §2.2、2026-09-04)。値は
	 * {@link net.zamasoft.foliojet.css.value.UnicodeBidiValue}の6値。段落単位の
	 * UBA(bidi-isolation-design.md、flag {@code layout.bidi.paragraph})が使う。
	 * flag OFFの旧経路は参照しない。
	 */
	public byte unicodeBidi = net.zamasoft.foliojet.css.value.UnicodeBidiValue.NORMAL;

	/** {@code layout.bidi.paragraph} の計算時スナップショット。 */
	public boolean paragraphBidi = true;

	/** {@code output.pdf.bidi.actual-text} の計算時スナップショット。 */
	public boolean bidiSemanticAlias = false;

	/**
	 * フォント管理オブジェクト。
	 */
	public FontManager fontManager;

	/**
	 * 行分割規則(禁則)。
	 */
	public TextBreakingRules lineBreakRules;

	/**
	 * {@code tab-size}(css-text-3、2026-08-29)。{@link #tabSizeIsMultiple}
	 * なら空白1文字の送り幅の倍数、でなければ絶対長さ(pt)。
	 */
	public double tabSize = 8;

	public boolean tabSizeIsMultiple = true;

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
	 * 行頭の始め括弧を天付きにするかです。
	 * {@code true}=text-spacing-trim: trim-start。既定のnormalとspace-allは
	 * JLREQのもう一つの方式である行頭二分アキを残す。
	 */
	public boolean textSpacingTrimStart = false;

	/** text-spacing-trim: trim-both/autoの常時行末詰め。 */
	public boolean textSpacingTrimEnd = false;

	/** text-spacing-trim: space-firstの初行・強制改行直後例外。 */
	public boolean textSpacingSpaceFirst = false;

	/**
	 * 縦中横の種別です({@link net.zamasoft.foliojet.css.value.TextCombineValue}
	 * の定数。既定NONE)。{@code ALL}のときだけ、縦組みの行の中で
	 * <b>1em幅へ水平圧縮</b>する(css-writing-modes-3 §9.1)。
	 * 従来の{@code horizontal}は自然幅のまま(はみ出す)——既存の
	 * 出力を変えないため意図的に分けている。
	 */
	public byte textCombine = net.zamasoft.foliojet.css.value.TextCombineValue.NONE;

	/**
	 * 行末句読点のぶら下げ(hanging-punctuation: allow-end)です
	 * (和文詰めH1。既定false=none)。
	 */
	public boolean hangingPunctuationEnd = false;

	/** 最初の整形行の先頭約物を字下げ内へぶら下げる。 */
	public boolean hangingPunctuationFirst = false;

	/** 行末句読点を常にぶら下げる(hanging-punctuation: force-end)。 */
	public boolean hangingPunctuationForceEnd = false;

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
	 * 両端揃えの配分(CSS {@code text-justify})です(2026-09-02)。
	 * {@link #TEXT_JUSTIFY_AUTO}は言語で決める。
	 */
	public byte textJustify = TEXT_JUSTIFY_AUTO;

	/**
	 * 行ボックスに strut(ブロックのフォントと line-height の幅 0 のインライン箱、
	 * CSS 2.1 §10.8)を必ず含めるか(2026-09-02)。ブラウザと同じく、DOCTYPE のある
	 * 標準モードの文書だけ真。quirks(DOCTYPE 無し)では画像だけの行は画像の高さに
	 * 縮む(従来の挙動)。{@code DocumentContext.getCompatibleMode()} から写す。
	 */
	public boolean strictLineBox = false;

	/**
	 * 単語内分綴(CSS hyphens)。
	 */
	public byte hyphens = HYPHENS_MANUAL;

	/**
	 * 分綴時に行末へ表示する文字列(CSS {@code hyphenate-character})。
	 * nullは{@code auto}で、従来のU+2010を使う。
	 */
	public String hyphenateCharacter;

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
	 * 文字装飾の色(text-decoration-color、2026-08-29)。nullなら文字色。
	 */
	public Color decorationColor = null;

	/**
	 * 文字装飾の線種({@code text-decoration-style}、{@code DECORATION_STYLE_*}。
	 * 2026-08-29)。
	 */
	public byte decorationStyle = DECORATION_STYLE_SOLID;

	/**
	 * 文字装飾の太さの指定値({@code text-decoration-thickness}の絶対長。
	 * 0なら{@code auto}/{@code from-font}=フォントサイズ×
	 * {@link #decorationThickness}。2026-08-29)。割合は1emに対して解決済み。
	 */
	public double decorationThicknessLength = 0;

	/**
	 * 下線の位置のずらし({@code text-underline-offset}の絶対長。NaNなら
	 * {@code auto}。2026-08-29)。正で文字から遠ざかる。割合は1emに対して
	 * 解決済み。
	 */
	public double underlineOffset = Double.NaN;

	/**
	 * 下線の位置({@code text-underline-position}、{@code UNDERLINE_POSITION_*}。
	 * 2026-08-29)。
	 */
	public byte underlinePosition = UNDERLINE_POSITION_AUTO;

	/**
	 * 文字の枠の太さ
	 */
	public double textStrokeWidth = 0;

	/**
	 * 文字の枠の色
	 */
	public Color textStrokeColor = null;

	/** {@code paint-order}でストロークを塗りより先に描くか。 */
	public boolean strokeBeforeFill = false;

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
				+ ",decorationStyle=" + this.decorationStyle + ",decorationThicknessLength="
				+ this.decorationThicknessLength + ",underlineOffset=" + this.underlineOffset
				+ ",underlinePosition=" + this.underlinePosition
				+ ",textStrokeWidth=" + this.textStrokeWidth + ",textStrokeColor=" + this.textStrokeColor
				+ ",strokeBeforeFill=" + this.strokeBeforeFill + "]";
	}
}
