package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.TextShadow;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
import net.zamasoft.pdfg2d.gc.text.pipeline.Hyphenator;

/**
 * {@code Params}/{@code AbstractTextParams}が共通して持つフィールドの
 * freeze/materialize処理です(2026-07-22新設、M6d-A3b Stage1、
 * package-private)。
 *
 * <p>
 * 元は{@link LineParamsFields}が単独で持っていたが、{@code InlineParams}
 * (`AbstractTextParams`を直接継承し、`AbstractLineParams`のtext-align等
 * を持たない)のテンプレートを追加する際に、この祖先部分だけを共有する
 * 必要が生じたため切り出した(継承階層の重複実装を避ける、real
 * duplicationの解消——2つ目の具体的な必要が生じてからの抽出)。
 * {@link LineParamsFields}はこれを合成して、line固有の4フィールド
 * (textAlign/textAlignLast/textIndent/lineHeight)を追加で持つ。
 * </p>
 *
 * <p>
 * 祖先({@code Params})のフィールドは{@link ParamsFields}
 * (`InnerTableParamsTemplate`とも共有する)へ委譲する(合成、
 * さらに2つ目の具体的な必要が生じたため2026-07-22に抽出)。
 * </p>
 *
 * <p>
 * {@code textShadows}(配列、mutableな参照)はコンパクトコンストラクタで
 * freeze時に{@code clone()}し、{@link #materializeInto}呼び出しごとに
 * 新品の{@code clone()}を書き戻す(2026-07-22 Stage2、不変recordへ置換)。
 * </p>
 */
record TextParamsFields(ParamsFields common, FontStyle fontStyle, WritingMode flow,
		WritingModeVariant writingModeVariant, byte direction, byte unicodeBidi, boolean paragraphBidi,
		boolean bidiSemanticAlias,
		FontManager fontManager, TextBreakingRules lineBreakRules, Length letterSpacing, double wordSpacing,
		byte textTransform, byte whiteSpace, byte wordWrap, byte textWrapStyle, byte hyphens, String hyphenateCharacter,
		Hyphenator hyphenator, Color color, byte decoration, double decorationThickness, Color decorationColor,
		byte decorationStyle, double decorationThicknessLength, double underlineOffset, byte underlinePosition,
		double textStrokeWidth,
		Color textStrokeColor, boolean strokeBeforeFill,
		TextShadow[] textShadows, byte rubyRole, boolean warichu, net.zamasoft.foliojet.css.value.RubyAlignValue rubyAlign,
		net.zamasoft.foliojet.css.value.RubyMergeValue rubyMerge, boolean rubyOverhang,
		net.zamasoft.foliojet.css.value.RubyPositionValue rubyPosition, byte textAutospace, boolean textSpacingTrimOff,
		boolean textSpacingTrimStart, boolean textSpacingTrimEnd, boolean textSpacingSpaceFirst,
		boolean hangingPunctuationEnd, boolean hangingPunctuationFirst, boolean hangingPunctuationForceEnd,
		byte textCombine, double tabSize, boolean tabSizeIsMultiple) {
	TextParamsFields {
		// 配列参照自体がmutableなため、freeze時にclone()する(要素の
		// TextShadowはfinalフィールドのみで実質不変)
		textShadows = textShadows == null ? null : textShadows.clone();
	}

	static TextParamsFields freeze(final AbstractTextParams source) {
		return new TextParamsFields(ParamsFields.freeze(source), source.fontStyle, source.flow,
				source.writingModeVariant, source.direction, source.unicodeBidi, source.paragraphBidi,
				source.bidiSemanticAlias,
				source.fontManager, source.lineBreakRules, source.letterSpacing, source.wordSpacing,
				source.textTransform, source.whiteSpace, source.wordWrap, source.textWrapStyle, source.hyphens,
				source.hyphenateCharacter,
				source.hyphenator, source.color, source.decoration, source.decorationThickness,
				source.decorationColor, source.decorationStyle, source.decorationThicknessLength,
				source.underlineOffset, source.underlinePosition, source.textStrokeWidth,
				source.textStrokeColor, source.strokeBeforeFill, source.textShadows, source.rubyRole, source.warichu,
				source.rubyAlign, source.rubyMerge,
				source.rubyOverhang, source.rubyPosition, source.textAutospace,
				source.textSpacingTrimOff, source.textSpacingTrimStart, source.textSpacingTrimEnd,
				source.textSpacingSpaceFirst, source.hangingPunctuationEnd, source.hangingPunctuationFirst,
				source.hangingPunctuationForceEnd,
				source.textCombine, source.tabSize, source.tabSizeIsMultiple);
	}

	/**
	 * {@code target}へ全フィールドを書き戻す。呼び出しごとに新品の
	 * {@code AffineTransform}/{@code TextShadow[]}を割り当てるため、
	 * 複数回materializeしても互いに影響しない(M6d-Aの最重要契約)。
	 */
	void materializeInto(final AbstractTextParams target) {
		this.common.materializeInto(target);
		target.fontStyle = this.fontStyle;
		target.flow = this.flow;
		target.writingModeVariant = this.writingModeVariant;
		target.direction = this.direction;
		target.unicodeBidi = this.unicodeBidi;
		target.paragraphBidi = this.paragraphBidi;
		target.bidiSemanticAlias = this.bidiSemanticAlias;
		target.fontManager = this.fontManager;
		target.lineBreakRules = this.lineBreakRules;
		target.letterSpacing = this.letterSpacing;
		target.wordSpacing = this.wordSpacing;
		target.textTransform = this.textTransform;
		target.whiteSpace = this.whiteSpace;
		target.wordWrap = this.wordWrap;
		target.textWrapStyle = this.textWrapStyle;
		target.hyphens = this.hyphens;
		target.hyphenateCharacter = this.hyphenateCharacter;
		target.hyphenator = this.hyphenator;
		target.color = this.color;
		target.decoration = this.decoration;
		target.decorationThickness = this.decorationThickness;
		target.decorationColor = this.decorationColor;
		// 装飾線の線種・太さ・下線位置(2026-08-29)。凍結から漏らすと再生・
		// restyleで実線・既定太さへ戻る
		target.decorationStyle = this.decorationStyle;
		target.decorationThicknessLength = this.decorationThicknessLength;
		target.underlineOffset = this.underlineOffset;
		target.underlinePosition = this.underlinePosition;
		target.textStrokeWidth = this.textStrokeWidth;
		target.textStrokeColor = this.textStrokeColor;
		target.strokeBeforeFill = this.strokeBeforeFill;
		target.textShadows = this.textShadows == null ? null : this.textShadows.clone();
		target.rubyRole = this.rubyRole;
		target.warichu = this.warichu;
		target.rubyAlign = this.rubyAlign;
		target.rubyMerge = this.rubyMerge;
		target.rubyOverhang = this.rubyOverhang;
		target.rubyPosition = this.rubyPosition;
		target.textAutospace = this.textAutospace;
		target.textSpacingTrimOff = this.textSpacingTrimOff;
		target.textSpacingTrimStart = this.textSpacingTrimStart;
		target.textSpacingTrimEnd = this.textSpacingTrimEnd;
		target.textSpacingSpaceFirst = this.textSpacingSpaceFirst;
		target.hangingPunctuationEnd = this.hangingPunctuationEnd;
		target.hangingPunctuationFirst = this.hangingPunctuationFirst;
		target.hangingPunctuationForceEnd = this.hangingPunctuationForceEnd;
		target.textCombine = this.textCombine;
		target.tabSize = this.tabSize;
		target.tabSizeIsMultiple = this.tabSizeIsMultiple;
	}
}
