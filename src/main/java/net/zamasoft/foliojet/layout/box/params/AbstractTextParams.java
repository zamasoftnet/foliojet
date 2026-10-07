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

	/** Case-conversion portion of {@link #textTransform} (low 4 bits). */
	public static final byte TEXT_TRANSFORM_CASE_MASK = 0x0F;

	/** {@code text-transform: full-width} (css-text-3, 2026-10-06). Flag combined with case conversion. */
	public static final byte TEXT_TRANSFORM_FULL_WIDTH = 0x10;

	public static final byte WHITE_SPACE_NORMAL = 1;

	public static final byte WHITE_SPACE_PRE = 2;

	public static final byte WHITE_SPACE_NOWRAP = 3;

	public static final byte WHITE_SPACE_PRE_WRAP = 4;

	public static final byte WHITE_SPACE_PRE_LINE = 5;

	public static final byte WORD_WRAP_NORMAL = 1;

	public static final byte WORD_WRAP_BREAK_WORD = 2;

	/**
	 * {@code text-wrap-style: auto} (greedy algorithm; default).
	 * Unsupported {@code balance}/{@code stable} also map to this value.
	 */
	public static final byte TEXT_WRAP_STYLE_AUTO = 1;

	/**
	 * {@code text-wrap-style: pretty} (Knuth-Plass global optimization).
	 */
	public static final byte TEXT_WRAP_STYLE_PRETTY = 2;

	/** {@code text-justify: auto}: language-dependent (Japanese text = JLREQ; Korean = inter-word; others = existing behavior). */
	public static final byte TEXT_JUSTIFY_AUTO = 1;
	/** {@code text-justify: none}: no justification. */
	public static final byte TEXT_JUSTIFY_NONE = 2;
	/** {@code text-justify: inter-word}: expands only inter-word spacing. */
	public static final byte TEXT_JUSTIFY_INTER_WORD = 3;
	/** {@code text-justify: inter-character} ({@code distribute}): also distributes space between characters. */
	public static final byte TEXT_JUSTIFY_INTER_CHARACTER = 4;

	public static final byte HYPHENS_NONE = 1;

	public static final byte HYPHENS_MANUAL = 2;

	public static final byte HYPHENS_AUTO = 3;

	public static final byte DECORATION_UNDERLINE = 0x01;

	public static final byte DECORATION_OVERLINE = 0x02;

	public static final byte DECORATION_LINE_THROUGH = 0x04;

	/** {@code text-decoration-style: solid} (default). 2026-08-29. */
	public static final byte DECORATION_STYLE_SOLID = 0;

	public static final byte DECORATION_STYLE_DOUBLE = 1;

	public static final byte DECORATION_STYLE_DOTTED = 2;

	public static final byte DECORATION_STYLE_DASHED = 3;

	public static final byte DECORATION_STYLE_WAVY = 4;

	/** {@code text-underline-position: auto} (default). 2026-08-29. */
	public static final byte UNDERLINE_POSITION_AUTO = 0;

	/** Places the underline below the font's bottom edge (descent). */
	public static final byte UNDERLINE_POSITION_UNDER = 1;

	/** To the left of characters in vertical writing (default side). */
	public static final byte UNDERLINE_POSITION_LEFT = 2;

	/** To the right of characters in vertical writing. */
	public static final byte UNDERLINE_POSITION_RIGHT = 3;

	/**
	 * No ruby role (default) (2026-07-25, annotated-text approach).
	 */
	public static final byte RUBY_NONE = 0;

	/**
	 * Ruby container (equivalent to the ruby element).
	 */
	public static final byte RUBY_CONTAINER = 1;

	/**
	 * Ruby base text (equivalent to the rb element).
	 */
	public static final byte RUBY_BASE = 2;

	/**
	 * Ruby annotation text (equivalent to the rt element).
	 */
	public static final byte RUBY_TEXT = 3;

	/** Ruby annotation container (equivalent to the rtc element). */
	public static final byte RUBY_TEXT_CONTAINER = 4;

	/**
	 * Ruby role marker (2026-07-25, specification decision for the annotated-text approach;
	 * see the development record). Ruby-related elements (ruby/rb/rt) set {@code RUBY_*}, which the
	 * text-processing layer ({@code StyledTextUnitizer}) uses to assemble annotated-text units.
	 */
	public byte rubyRole = RUBY_NONE;

	/** Warichu container for {@code -cssj-warichu:auto}. */
	public boolean warichu = false;

	/** Text alignment within ruby boxes. Computed value of CSS {@code ruby-align}. */
	public net.zamasoft.foliojet.css.value.RubyAlignValue rubyAlign = net.zamasoft.foliojet.css.value.RubyAlignValue.SPACE_AROUND;

	/** How compound-word ruby is shared. Computed value of CSS {@code ruby-merge}. */
	public net.zamasoft.foliojet.css.value.RubyMergeValue rubyMerge = net.zamasoft.foliojet.css.value.RubyMergeValue.SEPARATE;

	/** Whether ruby may overhang. Computed value of CSS {@code ruby-overhang}. */
	public boolean rubyOverhang = true;

	/** Placement side of an annotation level. Computed value of CSS {@code ruby-position}. */
	public net.zamasoft.foliojet.css.value.RubyPositionValue rubyPosition = net.zamasoft.foliojet.css.value.RubyPositionValue.ALTERNATE;

	/**
	 * Font style.
	 */
	public FontStyle fontStyle;

	public WritingMode flow = WritingMode.TB;

	/** Glyph rotation variant for the writing mode. Stored independently of the progression direction. */
	public WritingModeVariant writingModeVariant = WritingModeVariant.NORMAL;

	/** {@code true} for horizontal typesetting (including sideways). */
	public final boolean isHorizontalTypesetting() {
		return TypesettingMode.isHorizontal(this.flow, this.writingModeVariant);
	}

	/** {@code true} only for ordinary vertical typesetting with {@code vertical-*}. */
	public final boolean isVerticalTypesetting() {
		return TypesettingMode.isVertical(this.flow, this.writingModeVariant);
	}

	/** Glyph-run rotation applied to sideways lines. */
	public final WritingModeVariant getGlyphRotation() {
		return TypesettingMode.glyphRotation(this.writingModeVariant);
	}

	/** Physical inline progression derived from flow, direction, and sideways rotation. */
	public final TypesettingMode.InlineProgression getInlineProgression() {
		return TypesettingMode.inlineProgression(this.flow, this.writingModeVariant, this.direction);
	}

	/** {@code 1} for inline progression along the positive physical axis (right or down), {@code -1} for negative. */
	public final int getInlineProgressionSign() {
		return TypesettingMode.inlineProgressionSign(this.flow, this.writingModeVariant, this.direction);
	}

	/** Physical edge on the over (ascent) side of the rotated horizontal-typesetting baseline. */
	public final TypesettingMode.PhysicalSide getTypesettingOverSide() {
		return TypesettingMode.overSide(this.flow, this.writingModeVariant);
	}

	public byte direction = DIRECTION_LTR;

	/**
	 * {@code unicode-bidi} (css-writing-modes-3 §2.2, 2026-09-04). Uses the six values of
	 * {@link net.zamasoft.foliojet.css.value.UnicodeBidiValue}.
	 * Used by the paragraph-level UBA (bidi-isolation-design.md).
	 */
	public byte unicodeBidi = net.zamasoft.foliojet.css.value.UnicodeBidiValue.NORMAL;

	/** Snapshot of {@code output.pdf.bidi.actual-text} at computation time. */
	public boolean bidiSemanticAlias = false;

	/**
	 * Font management object.
	 */
	public FontManager fontManager;

	/**
	 * Kinsoku (line-breaking rules).
	 */
	public TextBreakingRules lineBreakRules;

	/**
	 * {@code tab-size} (css-text-3, 2026-08-29). A multiple of a space character's advance when
	 * {@link #tabSizeIsMultiple} is true; otherwise, an absolute length (pt).
	 */
	public double tabSize = 8;

	public boolean tabSizeIsMultiple = true;

	/**
	 * Letter spacing
	 */
	public Length letterSpacing = Length.ZERO_LENGTH;

	/**
	 * Effective flags for spacing between Japanese and Latin text (text-autospace) (Japanese spacing A1).
	 * Bits of {@code TextAutospaceValue.ALPHA}|{@code NUMERIC}; 0 = none.
	 * Geometry is wired in A2.
	 */
	public byte textAutospace = 0;

	/**
	 * Disables compression of consecutive punctuation (text-spacing-trim) (Japanese spacing T1b).
	 * {@code true} = space-all = no compression. Default false = normal = compression moved
	 * from the font layer in T1a.
	 */
	public boolean textSpacingTrimOff = false;

	/**
	 * Whether an opening bracket at line start sits flush with the line edge.
	 * {@code true} = text-spacing-trim: trim-start. The default normal and space-all retain the
	 * half-em space at line start, the other JLREQ approach.
	 */
	public boolean textSpacingTrimStart = false;

	/** Always trims line ends for text-spacing-trim: trim-both/auto. */
	public boolean textSpacingTrimEnd = false;

	/** First-line and post-forced-break exceptions for text-spacing-trim: space-first. */
	public boolean textSpacingSpaceFirst = false;

	/**
	 * Tate-chu-yoko type (a constant from {@link net.zamasoft.foliojet.css.value.TextCombineValue};
	 * default NONE). Only {@code ALL} <b>compresses horizontally to a width of 1 em</b> within a
	 * vertical-writing line (css-writing-modes-3 §9.1). The existing {@code horizontal} retains its
	 * natural width (and overflows); intentionally separate to preserve existing output.
	 */
	public byte textCombine = net.zamasoft.foliojet.css.value.TextCombineValue.NONE;

	/**
	 * Hanging punctuation at line end (hanging-punctuation: allow-end)
	 * (Japanese spacing H1; default false = none).
	 */
	public boolean hangingPunctuationEnd = false;

	/** Hangs leading punctuation of the first formatted line into the indent. */
	public boolean hangingPunctuationFirst = false;

	/** Always hangs line-end punctuation (hanging-punctuation: force-end). */
	public boolean hangingPunctuationForceEnd = false;

	/**
	 * Word spacing
	 */
	public double wordSpacing = 0;

	/**
	 * Text uppercase conversion
	 */
	public byte textTransform = TEXT_TRANSFORM_NONE;

	/**
	 * Whitespace handling.
	 */
	public byte whiteSpace = WHITE_SPACE_NORMAL;

	/**
	 * Wrapping mode
	 */
	public byte wordWrap = WORD_WRAP_NORMAL;

	/**
	 * Line-breaking strategy (CSS {@code text-wrap-style}) (2026-07-25).
	 * Attempts Knuth-Plass global optimization ({@code TotalFitSession}) only for
	 * {@link #TEXT_WRAP_STYLE_PRETTY}; the default {@link #TEXT_WRAP_STYLE_AUTO} uses greedy layout.
	 * Since K-P operates per paragraph, only the value on the block establishing the paragraph
	 * ({@code BlockParams}) is actually read.
	 */
	public byte textWrapStyle = TEXT_WRAP_STYLE_AUTO;

	/**
	 * Justification distribution (CSS {@code text-justify}) (2026-09-02).
	 * {@link #TEXT_JUSTIFY_AUTO} depends on the language.
	 */
	public byte textJustify = TEXT_JUSTIFY_AUTO;

	/**
	 * Whether line boxes always include a strut (a zero-width inline box with the block's font and
	 * line-height, CSS 2.1 §10.8) (2026-09-02). As in browsers, true only for standards-mode documents
	 * with a DOCTYPE. In quirks mode (no DOCTYPE), image-only lines shrink to the image height
	 * (existing behavior). Copied from {@code DocumentContext.getCompatibleMode()}.
	 */
	public boolean strictLineBox = false;

	/**
	 * Intra-word hyphenation (CSS hyphens).
	 */
	public byte hyphens = HYPHENS_MANUAL;

	/**
	 * String displayed at line end on hyphenation (CSS {@code hyphenate-character}).
	 * null means {@code auto}, which uses the existing U+2010.
	 */
	public String hyphenateCharacter;

	/**
	 * Language-specific hyphenator for hyphens:auto. null for languages without patterns.
	 */
	public Hyphenator hyphenator;

	/**
	 * Text color.
	 */
	public Color color = null;

	/**
	 * Text decoration
	 */
	public byte decoration = 0;

	/**
	 * Text-decoration thickness
	 */
	public double decorationThickness = 0;

	/**
	 * Text-decoration color (text-decoration-color, 2026-08-29). null means text color.
	 */
	public Color decorationColor = null;

	/**
	 * Text-decoration line style ({@code text-decoration-style}, {@code DECORATION_STYLE_*}.
	 * 2026-08-29).
	 */
	public byte decorationStyle = DECORATION_STYLE_SOLID;

	/**
	 * Specified text-decoration thickness (absolute length for {@code text-decoration-thickness};
	 * 0 means {@code auto}/{@code from-font} = font size × {@link #decorationThickness}. 2026-08-29).
	 * Percentages are already resolved against 1 em.
	 */
	public double decorationThicknessLength = 0;

	/**
	 * Underline position offset (absolute length for {@code text-underline-offset}; NaN means
	 * {@code auto}. 2026-08-29). Positive values move away from the text.
	 * Percentages are already resolved against 1 em.
	 */
	public double underlineOffset = Double.NaN;

	/**
	 * Underline position ({@code text-underline-position}, {@code UNDERLINE_POSITION_*}.
	 * 2026-08-29).
	 */
	public byte underlinePosition = UNDERLINE_POSITION_AUTO;

	/**
	 * Text-stroke width
	 */
	public double textStrokeWidth = 0;

	/**
	 * Text-stroke color
	 */
	public Color textStrokeColor = null;

	/** Whether {@code paint-order} draws the stroke before the fill. */
	public boolean strokeBeforeFill = false;

	/**
	 * Text shadow
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
