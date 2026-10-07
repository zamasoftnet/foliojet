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
 * Freeze/materialize handling for fields shared by {@code Params}/{@code AbstractTextParams}
 * (introduced on 2026-07-22, M6d-A3b Stage1; package-private).
 *
 * <p>
 * Originally held solely by {@link LineParamsFields}. Adding a template for {@code InlineParams}
 * (which directly extends `AbstractTextParams` and has none of the text-align or other fields of
 * `AbstractLineParams`) required sharing only this ancestor portion, so it was extracted
 * (to avoid duplicating the inheritance hierarchy: removing real duplication only after a second
 * concrete need arose). {@link LineParamsFields} composes this with four additional line-specific
 * fields (textAlign/textAlignLast/textIndent/lineHeight).
 * </p>
 *
 * <p>
 * Delegates ancestor ({@code Params}) fields to {@link ParamsFields}
 * (also shared with `InnerTableParamsTemplate`; composition, extracted on 2026-07-22 when another
 * second concrete need arose).
 * </p>
 *
 * <p>
 * For {@code textShadows} (an array, a mutable reference), the compact constructor calls
 * {@code clone()} on freeze, and each {@link #materializeInto} call writes back a fresh
 * {@code clone()} (2026-07-22 Stage2, replaced with an immutable record).
 * </p>
 */
record TextParamsFields(ParamsFields common, FontStyle fontStyle, WritingMode flow,
		WritingModeVariant writingModeVariant, byte direction, byte unicodeBidi,
		boolean bidiSemanticAlias, boolean strictLineBox,
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
		// Clone the mutable array reference on freeze (its TextShadow elements
		// have only final fields and are effectively immutable).
		textShadows = textShadows == null ? null : textShadows.clone();
	}

	static TextParamsFields freeze(final AbstractTextParams source) {
		return new TextParamsFields(ParamsFields.freeze(source), source.fontStyle, source.flow,
				source.writingModeVariant, source.direction, source.unicodeBidi,
				source.bidiSemanticAlias, source.strictLineBox,
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
	 * Writes all fields back to {@code target}. Allocates fresh
	 * {@code AffineTransform}/{@code TextShadow[]} instances on each call, so multiple materializations
	 * do not affect one another (the most important M6d-A contract).
	 */
	void materializeInto(final AbstractTextParams target) {
		this.common.materializeInto(target);
		target.fontStyle = this.fontStyle;
		target.flow = this.flow;
		target.writingModeVariant = this.writingModeVariant;
		target.direction = this.direction;
		target.unicodeBidi = this.unicodeBidi;
		target.bidiSemanticAlias = this.bidiSemanticAlias;
		// Preserve the strut of textless lines in standards mode during range replay too.
		target.strictLineBox = this.strictLineBox;
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
		// Decoration line style, thickness, and underline position (2026-08-29). Omitting these from
		// freezing would restore solid lines and default thickness during replay or restyle.
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
