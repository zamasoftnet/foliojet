package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.content.JustificationState;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Decoration;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.TextShadow;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.draw.LineTextScope;
import net.zamasoft.foliojet.layout.draw.LogicalTextDrawable;
import net.zamasoft.foliojet.layout.text.bidi.BidiSlice;
import net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.SidewaysGeometry;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.font.ColorGlyphFont;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.pdf.font.cid.missing.MissingCIDFontSource;

public abstract class AbstractTextBox extends AbstractBox {
	/**
	 * Surrounds text with a 25% gray frame.
	 */

	/**
	 * An inline placed inside a text box.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: AbstractTextBox.java 1633 2023-02-12 03:22:32Z miyabe $
	 */
	public static class Inline {
		public final IInlineBox box;
		public double verticalAlign = 0;

		public Inline(IInlineBox box) {
			this.box = box;
		}

		public String toString() {
			return this.box.toString();
		}
	}

	protected Decoration decoration;

	/**
	 * The text and inline boxes contained within. Each element is a Text, Control, Inline, or IAbsoluteBox.
	 */
	protected List<Object> contents = null;

	/** A read-only view for bidi resolution to flatten the logical tree. */
	public final List<Object> getLogicalContents() {
		return this.contents == null ? java.util.Collections.emptyList()
				: java.util.Collections.unmodifiableList(this.contents);
	}

	/** The content used for drawing: normally the logical tree, but the visual tree for bidi lines. */
	protected List<Object> getDrawingContents() {
		return this.contents;
	}

	/** Reordered lines override this to attach their logical output sidecar. */
	protected LogicalLineEmission getLogicalLineEmission() {
		return null;
	}

	protected String getLogicalLineVisualText() {
		return null;
	}

	/** Folio-side logical range for one visual leaf. */
	protected BidiSlice getBidiSlice(final Object visualContent) {
		return null;
	}

	/**
	 * Enumerates direct child inline boxes (read-only; exposed for footnote F4 call traversal:
	 * consult-codex-2026-07-31-footnote-f4.txt).
	 * Does not descend into nested inlines (the caller descends with iterative DFS).
	 *
	 * @param action the action to apply to each child inline
	 */
	public final void forEachInlineBox(final java.util.function.Consumer<IInlineBox> action) {
		if (this.contents == null) {
			return;
		}
		for (int i = 0; i < this.contents.size(); ++i) {
			if (this.contents.get(i) instanceof Inline inline) {
				action.accept(inline.box);
			}
		}
	}

	/**
	 * Returns whether this box contains only an outside marker that overlays the following block.
	 * Used as a structural check when the first child of a list-item is a table, to keep the marker
	 * out of table cells without a marker-only line pushing the table down by one line.
	 */
	public final boolean containsOnlyOverlayOutsideMarker() {
		if (this.contents == null || this.contents.size() != 1) {
			return false;
		}
		return this.contents.get(0) instanceof Inline inline
				&& inline.box instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox marker
				&& marker.overlaysFollowingBlock();
	}

	protected double ascent = 0;

	protected double descent = 0;

	protected double lineSize;

	public abstract AbstractTextParams getTextParams();

	protected final void setDecoration(final Decoration decoration) {
		final AbstractTextParams params = this.getTextParams();
		final byte flags = (byte) (params.decoration & 7);
		Decoration.Line underline;
		Decoration.Line overline;
		Decoration.Line lineThrough;
		if (decoration == null) {
			if (flags == 0) {
				return;
			}
			underline = overline = lineThrough = null;
		} else {
			underline = decoration.underline;
			overline = decoration.overline;
			lineThrough = decoration.lineThrough;
		}
		// text-decoration-color (2026-08-29): Use the specified color for decoration lines,
		// or the text color as before if none is specified. Read line style, thickness, and underline position
		// from this element's (the line owner's) params as well, and propagate them unchanged to descendants.
		final Color color = params.decorationColor != null ? params.decorationColor : params.color;
		final Decoration.Line own = color == null ? null : Decoration.Line.of(color, params);
		underline = ((flags & AbstractTextParams.DECORATION_UNDERLINE) != 0) ? own : underline;
		overline = ((flags & AbstractTextParams.DECORATION_OVERLINE) != 0) ? own : overline;
		lineThrough = ((flags & AbstractTextParams.DECORATION_LINE_THROUGH) != 0) ? own : lineThrough;
		this.decoration = new Decoration(underline, overline, lineThrough);
	}

	/**
	 * Removes materialized line-end hyphenation hyphens **that are no longer the last content
	 * of their line** (2026-08-31).
	 *
	 * <p>
	 * When a line with a hyphen materialized at a hyphenation opportunity overflows at a page break
	 * and is laid out again, that hyphen may remain in the reflowed content. This leaves a hyphen
	 * inside a word where no break occurred: 10 occurrences in a 226-page book
	 * ({@code Bu-reau} at a line start, and {@code orga-}/{@code niza-tions} on the continuation side too).
	 * A hyphen is meaningful only at the end of a line, so others are typographical errors
	 * and can be removed. Trying to fix this by preventing materialization also removes hyphens
	 * at correct positions, turning it into a defect where words split without hyphens
	 * (149 occurrences measured).
	 * </p>
	 *
	 * @return the total width removed
	 */
	public final double removeStrayHyphens() {
		if (this.contents == null) {
			return 0;
		}
		double removed = 0;
		// Scan backward and remove only hyphens that precede the last visible content.
		boolean seenVisible = false;
		for (int i = this.contents.size() - 1; i >= 0; --i) {
			final Object content = this.contents.get(i);
			if (content instanceof AbstractTextBox nested) {
				removed += nested.removeStrayHyphens();
				seenVisible = true;
				continue;
			}
			if (content instanceof TextImpl text && text.materializedHyphen) {
				if (seenVisible) {
					this.contents.remove(i);
					removed += text.getAdvance();
				} else {
					seenVisible = true;
				}
				continue;
			}
			if (content instanceof Control control) {
				// Zero-width boundaries and collapsed spaces do not prevent a hyphen from being at the line end.
				if (control.getAdvance() != 0) {
					seenVisible = true;
				}
				continue;
			}
			seenVisible = true;
		}
		return removed;
	}

	protected final void add(Object content) {
		assert content instanceof Text || content instanceof Control || content instanceof Inline
				|| content instanceof IAbsoluteBox
				|| content instanceof net.zamasoft.foliojet.layout.text.LeaderQuad;
		if (this.contents == null) {
			this.contents = new ArrayList<Object>();
		}
		this.contents.add(content);
	}

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		if (this.contents != null) {
			for (final Object content : this.contents) {
				if (content instanceof Inline inline) {
					action.accept(inline.box);
				} else if (content instanceof IAbsoluteBox absolute) {
					action.accept(absolute);
				}
			}
		}
	}

	/**
	 * Returns the source character end (offset + character count) of the last text within
	 * (M6b v3). Used to derive the end of the content left in the preceding fragment after a split,
	 * i.e., the remainder's resume position, with structural accuracy.
	 *
	 * @return the character end of the last text, or -1 if there is no text
	 */
	public final int lastCharEnd() {
		if (this.contents != null) {
			for (int i = this.contents.size() - 1; i >= 0; --i) {
				final Object content = this.contents.get(i);
				if (content instanceof Text text && text.getCharOffset() >= 0) {
					return text.getCharOffset() + text.getCharCount();
				}
				if (content instanceof Inline inline) {
					// A ruby unit is a composite box containing already shaped text
					// (its glyphs do not appear directly in the line). Resuming partway through
					// the unit would start partial replay at a position without the ruby start event,
					// feeding the content twice. Return the source end of the entire unit
					// instead (2026-07-25).
					if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.RubyUnitBox rubyUnit) {
						final int end = rubyUnit.getSourceEnd();
						if (end >= 0) {
							return end;
						}
						continue;
					}
					if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox warichuUnit) {
						final int end = warichuUnit.getSourceEnd();
						if (end >= 0) {
							return end;
						}
						continue;
					}
					if (inline.box instanceof AbstractTextBox nested) {
						final int end = nested.lastCharEnd();
						if (end >= 0) {
							return end;
						}
					}
				}
			}
		}
		return -1;
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		return this.ascent + this.descent;
	}

	public final double getWidth() {
		if (this.getTextParams().flow.isVertical()) {
			// Vertical writing
			return this.getPageSize();
		} else {
			// Horizontal writing
			return this.lineSize;
		}
	}

	public final double getHeight() {
		if (this.getTextParams().flow.isVertical()) {
			// Vertical writing
			return this.lineSize;
		} else {
			// Horizontal writing
			return this.getPageSize();
		}
	}

	public double getInnerWidth() {
		return this.getWidth();
	}

	public double getInnerHeight() {
		return this.getHeight();
	}

	public final void addText(Text text) {
		assert text.getGlyphCount() > 0;
		this.add(text);
	}

	public final void addControl(Control control) {
		this.add(control);
	}

	/** Adds {@code leader()} (leader() L1; width already allocated). */
	public final void addLeader(final net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
		this.add(leader);
	}

	/**
	 * Adds an inline.
	 *
	 * @param box
	 */
	public final void addInline(IInlineBox box) {
		if (box.getType() == BoxType.INLINE) {
			assert this.getParams().element != box.getParams().element
					: (box.getParams().element + "\n" + this.getParams() + "\n" + box.getParams());
			InlineBox inline = (InlineBox) box;
			inline.setDecoration(this.decoration);
		}
		this.add(new Inline(box));
	}

	public final void addAbsolute(IAbsoluteBox box) {
		this.add(box);
	}

	public final void addAdvance(double advance) {
		this.lineSize += advance;
	}

	/** The expansion-priority stages of JLREQ 3.8.4. */
	protected static final int JUSTIFY_WORD_SPACE = 1;
	protected static final int JUSTIFY_AUTOSPACE = 2;
	protected static final int JUSTIFY_GENERAL = 3;
	protected static final int JUSTIFY_FALLBACK = 4;
	/**
	 * In the final stage, also distribute space between Latin characters (JLREQ 3.8.4 d;
	 * JIS X 4051 makes inclusion of Latin inter-character spacing implementation-defined).
	 * With {@code text-justify: auto}, do so only on lines with no opportunities for {@link #JUSTIFY_FALLBACK},
	 * such as Japanese inter-character or word spacing (2026-10-06, jigensha report: even Latin letters
	 * were spaced out as "T o r B r o w s e r"). {@code inter-character} includes these from the start.
	 */
	protected static final int JUSTIFY_LETTERS = 5;

	/**
	 * Returns whether this line/inline contains Japanese typesetting. Apply JLREQ's staged line-length
	 * adjustments only to Japanese lines; for purely Latin justification, distribute space to separable
	 * Latin boundaries as before.
	 */
	protected final boolean containsJapaneseComposition() {
		if (this.contents == null) {
			return false;
		}
		for (final Object content : this.contents) {
			switch (content) {
			case Text text -> {
				final char[] chars = text.getChars();
				for (int i = 0; i < text.getCharCount();) {
					final int cp = Character.codePointAt(chars, i);
					if (isJapaneseCompositionCodePoint(cp)) {
						return true;
					}
					i += Character.charCount(cp);
				}
			}
			case Inline inline -> {
				if (inline.box instanceof AbstractTextBox nested && nested.containsJapaneseComposition()
						|| inline.box instanceof net.zamasoft.foliojet.layout.box.impl.RubyUnitBox
						|| inline.box instanceof net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox) {
					return true;
				}
			}
			case Control ctrl -> {
				if (isJapaneseCompositionCodePoint(ctrl.getControlChar())) {
					return true;
				}
			}
			default -> {
				// Placed objects and leaders do not affect text typesetting classification.
			}
			}
		}
		return false;
	}

	private static boolean isJapaneseCompositionCodePoint(final int cp) {
		if (net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(cp)
				== net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH) {
			return true;
		}
		// Treat CJK punctuation, vertical compatibility forms, and fullwidth forms as Japanese typesetting too.
		return cp >= 0x3000 && cp <= 0x303F || cp >= 0xFE10 && cp <= 0xFE1F
				|| cp >= 0xFE30 && cp <= 0xFE4F || cp >= 0xFF01 && cp <= 0xFF60
				|| cp >= 0xFFE0 && cp <= 0xFFE6;
	}

	/** The existing number of justification candidates in purely Latin or general text. */
	protected final int countGeneralJustificationPoints(final JustificationState state) {
		if (this.contents == null) {
			return 0;
		}
		final TextBreakingRules rules = this.getTextParams().lineBreakRules;
		int count = 0;
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				final int glyphCount = text.getGlyphCount();
				final char[] chars = text.getChars();
				final byte[] clusterLengths = text.getClusterLengths();
				int offset = 0;
				for (int j = 0; j < glyphCount; ++j) {
					final int first = Character.codePointAt(chars, offset);
					offset += clusterLengths[j];
					final int last = Character.codePointBefore(chars, offset);
					if (isGeneralJustificationBoundary(state.prevCodePoint, first, rules)) {
						++count;
					}
					state.prevCodePoint = last;
				}
			}
			case Inline inline -> {
				if (inline.box.getType() == BoxType.INLINE) {
					count += ((InlineBox) inline.box).countGeneralJustificationPoints(state);
				}
			}
			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					state.prevCodePoint = ctrl.getControlChar();
				}
			}
			default -> {
				// Placed objects and leaders do not create expansion opportunities.
			}
			}
		}
		return count;
	}

	/**
	 * The number of word-space expansion opportunities (immediately after whitespace), used for
	 * {@code text-justify: inter-word} and the Korean default (2026-09-02). Whitespace is a
	 * {@code Control}; expand the space ({@code xadvance}) before the next glyph.
	 */
	protected final int countWordSpaceJustificationPoints(final JustificationState state) {
		if (this.contents == null) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				if (text.getGlyphCount() > 0) {
					if (state.prevCodePoint == ' ') {
						++count;
					}
					final char[] chars = text.getChars();
					state.prevCodePoint = Character.codePointBefore(chars, text.getCharCount());
				}
			}
			case Inline inline -> {
				if (inline.box.getType() == BoxType.INLINE) {
					count += ((InlineBox) inline.box).countWordSpaceJustificationPoints(state);
				}
			}
			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					state.prevCodePoint = ctrl.getControlChar();
				}
			}
			default -> {
				// Placed objects and leaders do not create expansion opportunities.
			}
			}
		}
		return count;
	}

	/**
	 * Adds equal space to each word-space expansion opportunity
	 * (paired with {@link #countWordSpaceJustificationPoints}).
	 */
	protected final void justifyWordSpaces(final double unitSpacing, final JustificationState state) {
		if (this.contents == null) {
			return;
		}
		for (int i = 0; i < this.contents.size(); ++i) {
			double advance = 0;
			switch (this.contents.get(i)) {
			case Text text -> {
				if (text.getGlyphCount() > 0) {
					if (state.prevCodePoint == ' ') {
						((net.zamasoft.pdfg2d.gc.text.TextImpl) text).addXAdvance(0, unitSpacing);
						advance += unitSpacing;
					}
					final char[] chars = text.getChars();
					state.prevCodePoint = Character.codePointBefore(chars, text.getCharCount());
				}
			}
			case Inline inline -> {
				if (inline.box.getType() == BoxType.INLINE) {
					final InlineBox inlineBox = (InlineBox) inline.box;
					advance = inlineBox.getLineSize();
					inlineBox.justifyWordSpaces(unitSpacing, state);
					advance = inlineBox.getLineSize() - advance;
				}
			}
			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					state.prevCodePoint = ctrl.getControlChar();
				}
			}
			default -> {
				// Placed objects and leaders do not expand.
			}
			}
			if (advance != 0) {
				this.addAdvance(advance);
			}
		}
	}

	/** Adds equal space to each justification candidate in purely Latin or general text. */
	protected final void justifyGeneral(final double unitSpacing, final JustificationState state) {
		if (this.contents == null) {
			return;
		}
		final TextBreakingRules rules = this.getTextParams().lineBreakRules;
		for (int i = 0; i < this.contents.size(); ++i) {
			double advance = 0;
			switch (this.contents.get(i)) {
			case Text text -> {
				final int glyphCount = text.getGlyphCount();
				final char[] chars = text.getChars();
				final byte[] clusterLengths = text.getClusterLengths();
				final net.zamasoft.pdfg2d.gc.text.TextImpl textImpl =
						(net.zamasoft.pdfg2d.gc.text.TextImpl) text;
				int offset = 0;
				for (int j = 0; j < glyphCount; ++j) {
					final int first = Character.codePointAt(chars, offset);
					offset += clusterLengths[j];
					final int last = Character.codePointBefore(chars, offset);
					if (isGeneralJustificationBoundary(state.prevCodePoint, first, rules)) {
						textImpl.addXAdvance(j, unitSpacing);
						advance += unitSpacing;
					}
					state.prevCodePoint = last;
				}
			}
			case Inline inline -> {
				if (inline.box.getType() == BoxType.INLINE) {
					final InlineBox inlineBox = (InlineBox) inline.box;
					advance = inlineBox.getLineSize();
					inlineBox.justifyGeneral(unitSpacing, state);
					advance = inlineBox.getLineSize() - advance;
				}
			}
			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					state.prevCodePoint = ctrl.getControlChar();
				}
			}
			default -> {
				// Placed objects and leaders do not expand.
			}
			}
			if (advance != 0) {
				this.addAdvance(advance);
			}
		}
	}

	private static boolean isGeneralJustificationBoundary(final int previous, final int next,
			final TextBreakingRules rules) {
		return previous >= 0 && previous <= Character.MAX_VALUE && next <= Character.MAX_VALUE
				&& rules.canSeparate((char) previous, (char) next)
				&& !rules.atomic((char) previous, (char) next);
	}

	/**
	 * Returns the total available adjustment (pt) at the specified expansion stage.
	 * Only stage 4 returns a weight for uniform distribution of 1em instead of an upper bound.
	 */
	protected final double justificationCapacity(final int priority, JustificationState state) {
		if (this.contents == null) {
			return 0;
		}
		TextBreakingRules hyph = this.getTextParams().lineBreakRules;
		double capacity = 0;
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				// Text
				int glen = text.getGlyphCount();
				if (glen <= 0) {
					break;
				}
				char[] ch = text.getChars();
				byte[] clens = text.getClusterLengths();
				int k = 0;
				for (int j = 0; j < glen; ++j) {
					final int c1 = Character.codePointAt(ch, k);
					k += clens[j];
					final int c2 = Character.codePointBefore(ch, k);
					final double fontSize = text.getFontStyle().getSize();
					capacity += justificationWeight(state, c1, fontSize, hyph, priority);
					state.prevCodePoint = c2;
					state.prevFontSize = fontSize;
					state.wordSpaceAdvance = -1;
				}
			}

			case Inline content -> {
				// Inline
				if (content.box.getType() == BoxType.INLINE) {
					InlineBox inline = (InlineBox) content.box;
					capacity += inline.justificationCapacity(priority, state);
				}
			}

			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					// Zero-width soft hyphens do not create expansion opportunities within words.
					if (ctrl instanceof net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace) {
						state.beforeWordSpaceCodePoint = state.prevCodePoint;
						state.beforeWordSpaceFontSize = state.prevFontSize;
						state.wordSpaceAdvance = ctrl.getAdvance();
					}
					state.prevCodePoint = ctrl.getControlChar();
					state.prevFontSize = this.getTextParams().fontStyle.getSize();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// Does not affect position
			}

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// A leader consumes the remaining space first, so it does not create expansion opportunities.
			}

			default -> throw new IllegalStateException();
			}
		}
		return capacity;
	}

	/** Adds stage limit (1em for stage 4) × ratio to each opportunity in the specified stage. */
	protected final void justify(final int priority, final double ratio, JustificationState state) {
		if (this.contents == null) {
			return;
		}
		TextBreakingRules hyph = this.getTextParams().lineBreakRules;
		for (int i = 0; i < this.contents.size(); ++i) {
			double da = 0;
			switch (this.contents.get(i)) {
			case Text text -> {
				// Text
				int glen = text.getGlyphCount();
				if (glen <= 0) {
					break;
				}
				char[] ch = text.getChars();
				byte[] clens = text.getClusterLengths();
				// Japanese text spacing T1a: Preserve existing adjustments (punctuation compression and autospace gaps)
				// and add uniform spacing on top (addXAdvance adds to the value; do not reset it).
				final net.zamasoft.pdfg2d.gc.text.TextImpl textImpl = (net.zamasoft.pdfg2d.gc.text.TextImpl) text;
				int k = 0;
				for (int j = 0; j < glen; ++j) {
					final int c1 = Character.codePointAt(ch, k);
					k += clens[j];
					final int c2 = Character.codePointBefore(ch, k);
					final double fontSize = text.getFontStyle().getSize();
					final double weight = justificationWeight(state, c1, fontSize, hyph, priority);
					if (weight > 0) {
						final double spacing = weight * ratio;
						textImpl.addXAdvance(j, spacing);
						da += spacing;
					}
					state.prevCodePoint = c2;
					state.prevFontSize = fontSize;
					state.wordSpaceAdvance = -1;
				}
			}

			case Inline inline -> {
				// Inline
				if (inline.box.getType() == BoxType.INLINE) {
					InlineBox inlineBox = (InlineBox) inline.box;
					da = inlineBox.getLineSize();
					inlineBox.justify(priority, ratio, state);
					da = inlineBox.getLineSize() - da;
				}
			}

			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					// Zero-width soft hyphens do not create expansion opportunities within words.
					if (ctrl instanceof net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace) {
						state.beforeWordSpaceCodePoint = state.prevCodePoint;
						state.beforeWordSpaceFontSize = state.prevFontSize;
						state.wordSpaceAdvance = ctrl.getAdvance();
					}
					state.prevCodePoint = ctrl.getControlChar();
					state.prevFontSize = this.getTextParams().fontStyle.getSize();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// Does not affect position
			}

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// Already allocated; excluded from justification expansion
			}

			default -> throw new IllegalStateException();
			}
			if (da != 0) {
				this.addAdvance(da);
			}
		}
	}

	/** The upper bound/weight (pt) of a boundary at the specified stage. */
	private static double justificationWeight(final JustificationState state, final int next,
			final double nextFontSize, final TextBreakingRules rules, final int priority) {
		final int prev = state.prevCodePoint;
		if (priority == JUSTIFY_WORD_SPACE) {
			if (state.wordSpaceAdvance < 0 || prev != ' '
					|| !isWestern(state.beforeWordSpaceCodePoint) || !isWestern(next)) {
				return 0;
			}
			final double size = Math.min(state.beforeWordSpaceFontSize > 0
					? state.beforeWordSpaceFontSize : nextFontSize, nextFontSize);
			// JLREQ 3.8.4: Expand Latin word spacing from its normal value up to half an em.
			return Math.max(0, size / 2.0 - state.wordSpaceAdvance);
		}
		if (prev < 0 || !net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver
				.allowsJustificationAfter(prev)) {
			return 0;
		}
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind pk =
				net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(prev);
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind nk =
				net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(next);
		final boolean japaneseLatin = pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
				&& (nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
						|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC)
				|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						&& (pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
								|| pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC);
		final boolean westernInterletter = (pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
				|| pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC)
				&& (nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
						|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC);
		final boolean bmpPair = prev <= Character.MAX_VALUE && next <= Character.MAX_VALUE;
		final boolean atomic = bmpPair && rules.atomic((char) prev, (char) next);
		if (atomic && !(priority == JUSTIFY_LETTERS && westernInterletter)) {
			return 0;
		}
		final boolean normal = bmpPair ? rules.canSeparate((char) prev, (char) next)
				: pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH;
		final double size = japaneseLatin
				&& pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						? (state.prevFontSize > 0 ? state.prevFontSize : nextFontSize)
						: japaneseLatin ? nextFontSize
								: Math.min(state.prevFontSize > 0 ? state.prevFontSize : nextFontSize,
										nextFontSize);
		return switch (priority) {
		case JUSTIFY_WORD_SPACE -> 0;
		case JUSTIFY_AUTOSPACE -> normal && prev != ' ' && japaneseLatin ? size / 4.0 : 0;
		case JUSTIFY_GENERAL -> normal && prev != ' ' && !japaneseLatin ? size / 4.0 : 0;
		case JUSTIFY_FALLBACK -> normal ? size : 0;
		case JUSTIFY_LETTERS -> normal || westernInterletter ? size : 0;
		default -> throw new IllegalArgumentException("priority=" + priority);
		};
	}

	private static boolean isWestern(final int codePoint) {
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind kind =
				net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(codePoint);
		return kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
				|| kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC;
	}

	public abstract boolean isContextBox();

	public void finishLayoutSelf(IFramedBox containerBox) {
	}

	public void pushFinishLayoutChildren(IFramedBox containerBox,
			final java.util.Deque<FinishLayoutStep> worklist) {
		if (this.contents == null) {
			return;
		}
		if (this.isContextBox()) {
			containerBox = (IFramedBox) this;
		}
		final IFramedBox childContainerBox = containerBox;
		// Push onto the stack in reverse order (from the end) to preserve the original traversal order (from the start).
		for (int i = this.contents.size() - 1; i >= 0; --i) {
			switch (this.contents.get(i)) {
			case IAbsoluteBox absoluteBox ->
				// Absolute positioning
				worklist.push(IBox.step(absoluteBox, childContainerBox));

			case Inline inline ->
				// Inline
				worklist.push(IBox.step(inline.box, childContainerBox));

			default -> {
				// Text
			}
			}
		}
	}

	protected void verticalAlign(AbstractLineBox lineBox, double baseline) {
		if (this.contents == null) {
			return;
		}
		final AbstractLineParams lineParams = lineBox.getLineParams();
		for (int i = 0; i < this.contents.size(); ++i) {
			if (this.contents.get(i) instanceof Inline inline) {
				// Inline
				final IInlineBox inlineBox = inline.box;
				final InlinePos pos = inlineBox.getInlinePos();
				double ascent;
				double descent;
				switch (inlineBox.getType()) {
				case INLINE: {
					// Normal inline
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
					descent = box.getDescent();
				}
					break;
				case BLOCK: {
					// Inline block
					final AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					final boolean verticalLine = lineParams.flow.isVertical();
					descent = box.inlineDescent(lineParams);
					ascent = (verticalLine ? inlineBox.getWidth() : inlineBox.getHeight()) - descent;
				}
					break;
				case REPLACED: {
					// Image
					if (lineParams.flow.isVertical()) {
						// Vertical writing
						ascent = descent = inlineBox.getWidth() / 2.0;
					} else {
						// Horizontal writing (images with a baseline, i.e., formulas, have it above the bottom edge)
						descent = ((AbstractReplacedBox) inlineBox).getBaselineDescent();
						ascent = inlineBox.getHeight() - descent;
					}
				}
					break;
				default:
					throw new IllegalStateException();
				}
				inline.verticalAlign = pos.verticalAlign.getVerticalAlign(this, lineBox, ascent, descent,
						pos.lineHeight, baseline);
				if (inlineBox.getType() == BoxType.INLINE) {
					((InlineBox) inlineBox).verticalAlign(lineBox, baseline + inline.verticalAlign);
				}
			}
		}
	}

	protected static class TextSequenceDrawable extends AbstractDrawable implements LogicalTextDrawable {
		protected final List<Object> contents;
		protected final int off, len;
		protected final AbstractTextParams params;
		protected final double ascent, descent;
		private final LogicalLineEmission logicalLine;
		private final String lineVisualText;
		private final BidiSlice[] bidiSlices;
		private net.zamasoft.pdfg2d.pdf.StructureRef structRef;
		private LineTextScope lineScope;

		public TextSequenceDrawable(PageBox pageBox, Shape clip, AffineTransform transform, List<Object> contents,
				int off, int len, AbstractTextParams params, double ascent, double descent) {
			this(pageBox, clip, transform, contents, off, len, params, ascent, descent, null, null, null);
		}

		public TextSequenceDrawable(PageBox pageBox, Shape clip, AffineTransform transform, List<Object> contents,
				int off, int len, AbstractTextParams params, double ascent, double descent,
				final LogicalLineEmission logicalLine, final String lineVisualText, final BidiSlice[] bidiSlices) {
			super(pageBox, clip, params.opacity, transform);
			this.blendMode = params.blendMode;
			this.filter = params.filter;
			this.contents = contents;
			this.off = off;
			this.len = len;
			this.params = params;
			this.ascent = ascent;
			this.descent = descent;
			this.logicalLine = logicalLine;
			this.lineVisualText = lineVisualText;
			this.bidiSlices = bidiSlices;
		}

		@Override
		public LogicalLineEmission getLogicalLineEmission() {
			return this.logicalLine;
		}

		@Override
		public String getLineVisualText() {
			return this.lineVisualText;
		}

		@Override
		public void drawLogicalText(final GC gc, final double x, final double y,
				final net.zamasoft.pdfg2d.pdf.StructureRef structRef, final LineTextScope lineScope)
				throws GraphicsException {
			this.structRef = structRef;
			this.lineScope = lineScope;
			try {
				super.draw(gc, x, y);
			} finally {
				this.structRef = null;
				this.lineScope = null;
			}
		}

		private void missingFont(Text text) {
			final String c = new String(text.getChars(), 0, text.getCharCount());
			final StringBuilder codes = new StringBuilder();
			for (int j = 0; j < c.length(); ++j) {
				codes.append("[").append(Integer.toHexString(c.charAt(j))).append("]");
			}
			this.pageBox.getUserAgent().message(MessageCodes.WARN_MISSING_FONT, c + codes.toString());
		}

		public String describe() {
			final StringBuilder text = new StringBuilder();
			double advance = 0;
			for (int i = this.off; i < this.off + this.len; ++i) {
				if (this.contents.get(i) instanceof Text t) {
					text.append(t.getChars(), 0, t.getCharCount());
					advance += t.getAdvance();
				}
			}
			final String basic = String.format(java.util.Locale.ROOT, "Text[\"%s\" asc=%.2f desc=%.2f]", text,
					this.ascent, this.descent);
			if (!net.zamasoft.foliojet.layout.draw.DisplayListDumper.currentDetailedGeometry()) {
				return basic;
			}
			final boolean vertical = this.params.flow.isVertical();
			return basic + String.format(java.util.Locale.ROOT, " w=%.2f h=%.2f", vertical ? this.ascent + this.descent : advance,
					vertical ? advance : this.ascent + this.descent);
		}

		@Override
		public String describeGeometry(final double x, final double y) {
			if (!net.zamasoft.foliojet.layout.draw.DisplayListDumper.currentDetailedGeometry()
					|| this.params.writingModeVariant == WritingModeVariant.NORMAL) {
				return "";
			}
			final double advance = this.advance();
			final AffineTransform at = SidewaysGeometry.runTransform(this.params.writingModeVariant, x, y,
					this.ascent, this.descent, advance);
			final double[] m = new double[6];
			at.getMatrix(m);
			final Rectangle2D bounds = SidewaysGeometry.bounds(this.params.writingModeVariant, x, y,
					this.ascent, this.descent, advance);
			return String.format(java.util.Locale.ROOT,
					" run-tf=[%.2f %.2f %.2f %.2f %.2f %.2f] run-bounds=[%.2f %.2f %.2f %.2f]",
					m[0], m[1], m[2], m[3], m[4], m[5], bounds.getX(), bounds.getY(), bounds.getWidth(),
					bounds.getHeight());
		}

		private double advance() {
			double advance = 0;
			for (int i = 0; i < this.len; ++i) {
				advance += ((Text) this.contents.get(this.off + i)).getAdvance();
			}
			return advance;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			// Shadow
			if (this.params.textShadows != null) {
				for (int i = this.params.textShadows.length - 1; i >= 0; --i) {
					TextShadow shadow = params.textShadows[i];
					try (final var gcState = gc.begin()) {
						gc.setFillPaint(shadow.color);
						if (shadow.blur > 0) {
							this.drawBlurredShadow(gc, shadow, x + shadow.x, y + shadow.y);
						} else {
							final GeneralPath outline = this.textOutline(x + shadow.x, y + shadow.y);
							try (final var artifact = gc.beginArtifactScope()) {
								if (outline != null) {
									gc.fill(outline);
								} else {
									// A font whose glyph data is unavailable locally. It must be drawn as text,
									// but at least mark it as decoration.
									this.drawText(gc, x + shadow.x, y + shadow.y);
								}
							}
						}
					}
				}
			}

			// Text itself
			try (final var gcState = gc.begin()) {
				if (this.params.color != null) {
					gc.setFillPaint(this.params.color);
				}
				if (this.params.textStrokeWidth != 0) {
					gc.setLineJoin(GC.LineJoin.ROUND);
					gc.setLinePattern(GC.STROKE_SOLID);
					gc.setLineWidth(this.params.textStrokeWidth);
					gc.setStrokePaint(this.params.textStrokeColor);
					if (this.params.strokeBeforeFill) {
						gc.setTextMode(GC.TextMode.STROKE);
						// The preceding outline is decoration. Draw it as an artifact to avoid inserting
						// the same body text twice into the logical text of a tagged PDF.
						try (final var artifact = gc.beginArtifactScope()) {
							this.drawText(gc, x, y);
						}
						gc.setTextMode(GC.TextMode.FILL);
					} else {
						gc.setTextMode(GC.TextMode.FILL_STROKE);
					}
				}
				this.drawMainText(gc, x, y);
			}
		}

		/** Emits the body text exactly once to the logical line's scope. */
		private void drawMainText(final GC gc, final double x, final double y) throws GraphicsException {
			if (this.logicalLine == null) {
				this.drawText(gc, x, y);
				return;
			}
			if (this.lineScope != null) {
				this.lineScope.beforeMainText(gc);
			}
			try {
				this.drawText(gc, x, y, true);
			} finally {
				if (this.lineScope != null) {
					this.lineScope.afterMainText();
				}
			}
		}

		/**
		 * A blurred shadow (2026-08-29). Java2D and SVG draw text into a group image and apply effects
		 * as before; PDF with transparency support rasterizes only the shadow from glyph outlines.
		 * If neither is available, use the same 12-layer translucent approximation as {@code box-shadow}
		 * ({@link net.zamasoft.foliojet.layout.util.BoxDecorationRenderer#BLUR_STEPS}):
		 * overlay each layer's glyphs with "fill + outward stroke of width 2d"
		 * (d = the layer edge position, σ = blur/2). Each layer's alpha is {@code 1-(1-α)^(1/N)},
		 * so the center, where all layers overlap, reaches the specified color's alpha (capped at 0.98).
		 * Inward-shrunk layers use only a fill because glyphs cannot be shrunk (the center always has
		 * the specified opacity; just inside the outline, the stroke and fill overlap and are slightly
		 * darker, but this lies under the text glyph itself and is invisible in practice).
		 * For zero blur, draw once as before (preserving existing output).
		 */
		private void drawBlurredShadow(GC gc, TextShadow shadow, double x, double y) throws GraphicsException {
			final float alpha = shadow.color.getAlpha();
			if (alpha <= 0) {
				return;
			}
			final double sigma = shadow.blur / 2;
			if (gc.supports(GC.Capability.GROUP_FILTER) && gc.supports(GC.Capability.GAUSSIAN_BLUR)
					&& !gc.rasterizesGroupEffects()) {
				this.drawExactBlurredShadow(gc, shadow, x, y);
				return;
			}
			if (gc.supports(GC.Capability.GAUSSIAN_BLUR)) {
				final GeneralPath outline = this.textOutline(x, y);
				if (outline != null) {
					gc.setFillPaint(shadow.color);
					gc.setFillAlpha(alpha);
					try (final var artifact = gc.beginArtifactScope()) {
						if (gc.tryFillBlurred(outline, sigma)) {
							return;
						}
					}
				}
			}
			net.zamasoft.foliojet.layout.util.ApproximationGC.report(gc, "text-shadow", "2822.text-blur-rings");
			final double[] steps = net.zamasoft.foliojet.layout.util.BoxDecorationRenderer.BLUR_STEPS;
			final int n = steps.length;
			// An opaque shadow (α=1) would also have alpha 1 per layer,
			// producing a solid mass all the way to the outer edge. Cap the center's composite alpha at 0.98
			// so even opaque colors fade at the edges (about 0.28 per layer).
			final float layerAlpha = (float) (1 - Math.pow(1 - Math.min(alpha, 0.98), 1.0 / n));
			gc.setStrokePaint(shadow.color);
			gc.setFillAlpha(layerAlpha);
			gc.setStrokeAlpha(layerAlpha);
			gc.setLineJoin(GC.LineJoin.ROUND);
			gc.setLineCap(GC.LineCap.ROUND);
			gc.setLinePattern(GC.STROKE_SOLID);
			final GeneralPath outline = this.textOutline(x, y);
			try (final var artifact = gc.beginArtifactScope()) {
				for (int k = 0; k < n; ++k) {
					final double d = steps[k] * sigma;
					if (d > 0) {
						gc.setLineWidth(d * 2);
						gc.setTextMode(GC.TextMode.FILL_STROKE);
					} else {
						gc.setTextMode(GC.TextMode.FILL);
					}
					if (outline != null) {
						// Fill (+ stroke) each layer. Drawing as text 12 times would insert the body text
						// into the PDF 12 times (see textOutline below).
						if (d > 0) {
							gc.fillDraw(outline);
						} else {
							gc.fill(outline);
						}
					} else {
						this.drawText(gc, x, y);
					}
				}
			}
		}

		/**
		 * An exact blurred shadow (2026-08-29). When the output supports Gaussian blur (Java2D and SVG),
		 * draw the shadow text into a group image covering the text bounds plus a 3σ margin,
		 * apply the blur from {@link GroupEffects}, and place it.
		 */
		private void drawExactBlurredShadow(GC gc, TextShadow shadow, double x, double y) throws GraphicsException {
			final double sigma = shadow.blur / 2;
			double advance = 0;
			for (int i = 0; i < this.len; ++i) {
				if (this.contents.get(i + this.off) instanceof Text t) {
					advance += t.getAdvance();
				}
			}
			final double thickness = this.ascent + this.descent;
			final boolean vertical = this.params.flow.isVertical();
			final double minX, minY, w, h;
			if (this.params.writingModeVariant != WritingModeVariant.NORMAL) {
				final Rectangle2D bounds = SidewaysGeometry.bounds(this.params.writingModeVariant, x, y,
						this.ascent, this.descent, advance);
				minX = bounds.getX();
				minY = bounds.getY();
				w = bounds.getWidth();
				h = bounds.getHeight();
			} else {
				minX = x;
				minY = vertical ? y : y - this.ascent;
				w = vertical ? thickness : advance;
				h = vertical ? advance : thickness;
			}
			// Include glyph overhang (italics and accents) in the margin as well.
			final double pad = sigma * 3 + thickness * 0.5 + 1;
			final double ox = minX - pad, oy = minY - pad;
			final net.zamasoft.pdfg2d.gc.image.GroupImageGC ggc = gc.createGroupImage(w + pad * 2, h + pad * 2);
			try (final var groupState = ggc.begin()) {
				ggc.transform(AffineTransform.getTranslateInstance(-ox, -oy));
				ggc.setFillPaint(shadow.color);
				this.drawText(ggc, x, y);
			}
			final net.zamasoft.pdfg2d.gc.image.Image image = ggc.finish();
			try (final var gcState = gc.begin()) {
				gc.transform(AffineTransform.getTranslateInstance(ox, oy));
				gc.drawImage(image, new GroupEffects(null, sigma, null, 1));
			}
		}

		/**
		 * Builds this drawing unit's text as <b>glyph outlines (paths)</b>
		 * (2026-08-30). If even one font cannot supply outlines, return {@code null};
		 * the caller then draws text as before.
		 *
		 * <p>
		 * <b>Drawing a shadow as text inserts it directly into the PDF's extracted text.</b>
		 * A sharp shadow duplicates the body text; a blurred shadow overlays 12 layers, producing 13 copies.
		 * With emphasis marks ({@code text-emphasis}), drawing units split into individual characters,
		 * so they alternate character by character as "減減税税と と…": reported in an actual document
		 * in vertical writing (2026-08-30). Shadows are decoration, not body text, so draw them as paths
		 * without character information, and also wrap them in {@code /Artifact} for tagged PDF.
		 *
		 * <p>
		 * Coordinates follow {@link #drawText} (advance from {@code x+descent} in vertical writing
		 * and from {@code y+ascent} in horizontal writing). {@code FontUtils.addTextPath} builds
		 * the outlines, handling advance, kerning, letter spacing, and rotation for vertical writing.
		 *
		 * <p>
		 * <b>When outlines are unavailable</b>: Core-14 Type1 fonts (which do not implement
		 * {@code ShapedFont}), glyphs with null outlines, and fonts with image or color glyphs.
		 * {@link FontUtils#addTextPath} silently omits missing glyphs, so check every GID beforehand
		 * and fall back to the text-drawing approximation if any qualifies.
		 */
		private GeneralPath textOutline(double x, double y) {
			final GeneralPath path = new GeneralPath();
			final boolean sideways = this.params.writingModeVariant != WritingModeVariant.NORMAL;
			final boolean vertical = this.params.flow.isVertical();
			final AffineTransform runTransform = sideways
					? SidewaysGeometry.runTransform(this.params.writingModeVariant, x, y, this.ascent, this.descent,
							this.advance())
					: null;
			double localX = 0;
			double xx = x, yy = y;
			for (int i = 0; i < this.len; ++i) {
				final Text text = (Text) this.contents.get(i + this.off);
				final Font font = ((FontMetricsImpl) text.getFontMetrics()).getFont();
				if (!(font instanceof ShapedFont shaped)) {
					return null;
				}
				final int[] gids = text.getGlyphIds();
				for (int j = 0; j < text.getGlyphCount(); ++j) {
					final int gid = gids[j];
					if (shaped.getShapeByGID(gid) == null
							|| (font instanceof ColorGlyphFont colorFont && colorFont.isColorGlyph(gid))) {
						return null;
					}
				}
				if (sideways) {
					final AffineTransform at = AffineTransform.getTranslateInstance(localX, 0);
					at.preConcatenate(runTransform);
					FontUtils.addTextPath(path, shaped, text, at);
					localX += text.getAdvance();
				} else if (vertical) {
					FontUtils.addTextPath(path, shaped, text,
							AffineTransform.getTranslateInstance(x + this.descent, yy));
					yy += text.getAdvance();
				} else {
					FontUtils.addTextPath(path, shaped, text,
							AffineTransform.getTranslateInstance(xx, y + this.ascent));
					xx += text.getAdvance();
				}
			}
			return path.getCurrentPoint() == null ? null : path;
		}

		private void drawText(final GC gc, final double x, final double y) {
			this.drawText(gc, x, y, false);
		}

		private void drawText(GC gc, double x, double y, final boolean mainText) {
			double xx = x, yy = y;
			if (this.params.writingModeVariant != WritingModeVariant.NORMAL) {
				try (final var gcState = gc.begin()) {
					gc.transform(SidewaysGeometry.runTransform(this.params.writingModeVariant, x, y,
							this.ascent, this.descent, this.advance()));
					double localX = 0;
					for (int i = 0; i < this.len; ++i) {
						final Text text = (Text) this.contents.get(i + this.off);
						if (text.getFontMetrics().getFontSource() == MissingCIDFontSource.INSTANCES_LTR) {
							this.missingFont(text);
						}
						this.drawTextLeaf(gc, text, localX, 0, i, mainText);
						localX += text.getAdvance();
					}
				}
			} else if (this.params.flow.isVertical()) {
				// Vertical writing
				for (int i = 0; i < this.len; ++i) {
					final Text text = (Text) this.contents.get(i + this.off);
					if (text.getFontMetrics().getFontSource() == MissingCIDFontSource.INSTANCES_TB) {
						this.missingFont(text);
					}
					this.drawTextLeaf(gc, text, x + this.descent, y, i, mainText);
					y += text.getAdvance();
				}
			} else {
				// Horizontal writing
				for (int i = 0; i < this.len; ++i) {
					final Text text = (Text) this.contents.get(i + this.off);
					if (text.getFontMetrics().getFontSource() == MissingCIDFontSource.INSTANCES_LTR) {
						this.missingFont(text);
					}
					this.drawTextLeaf(gc, text, x, y + this.ascent, i, mainText);
					x += text.getAdvance();
				}
			}
		}

		private void drawTextLeaf(final GC gc, final Text text, final double x, final double y,
				final int index, final boolean mainText) throws GraphicsException {
			if (!mainText || this.structRef == null) {
				gc.drawText(text, x, y);
				return;
			}
			final net.zamasoft.pdfg2d.pdf.PDFPageOutput structOut =
					net.zamasoft.foliojet.layout.util.DelegatingGC.unwrap(gc) instanceof net.zamasoft.pdfg2d.pdf.gc.PDFGC pdfgc
							&& pdfgc.getPDFGraphicsOutput() instanceof net.zamasoft.pdfg2d.pdf.PDFPageOutput out
									? out : null;
			if (structOut == null) {
				gc.drawText(text, x, y);
				return;
			}
			final BidiSlice slice = this.bidiSlices == null ? null : this.bidiSlices[index];
			if (slice == null) {
				structOut.beginStructContent(this.structRef);
			} else {
				structOut.beginStructContent(this.structRef,
						new net.zamasoft.pdfg2d.pdf.StructureOrder(slice.paragraphId(), slice.syntheticStart(),
								this.lineScope == null ? index : this.lineScope.nextPaintSequence()));
			}
			try {
				gc.drawText(text, x, y);
			} finally {
				structOut.endStructContent();
			}
		}
	}

	/**
	 * Repeated drawing of {@code leader()} (leader() L2:
	 * consult-codex-2026-07-31-leader.txt Q3). Repeat one period of the shaped pattern on a fixed
	 * grid whose origin is the logical line end (dots on lines sharing the same line-end
	 * coordinate align vertically). Draw only cells that fit completely in the grid,
	 * and wrap them as artifacts (decoration) in tagged PDF. Keep the repeated characters
	 * out of the logical text.
	 */
	protected static class LeaderDrawable extends AbstractDrawable {
		private final net.zamasoft.foliojet.layout.text.LeaderQuad leader;
		private final AbstractTextParams params;
		private final double ascent, descent;

		LeaderDrawable(PageBox pageBox, Shape clip, AffineTransform transform, AbstractTextParams params,
				net.zamasoft.foliojet.layout.text.LeaderQuad leader, double ascent, double descent) {
			super(pageBox, clip, params.opacity, transform);
			this.blendMode = params.blendMode;
			this.filter = params.filter;
			this.leader = leader;
			this.params = params;
			this.ascent = ascent;
			this.descent = descent;
		}

		/** Grid cell interval [kmin, kmax] (number of copies is kmax-kmin+1). */
		private long[] cellRange() {
			final double p = this.leader.minAdvance;
			final double end = this.leader.advance;
			final double gridOrigin = end + this.leader.endOffset;
			// Cell k: [gridOrigin-(k+1)p, gridOrigin-kp). Only cells entirely within [0,end].
			final long kmin = (long) Math.ceil((gridOrigin - end) / p - 0.0001);
			final long kmax = (long) Math.floor((gridOrigin) / p - 1 + 0.0001);
			return new long[] { kmin, kmax };
		}

		public String describe() {
			final StringBuilder pattern = new StringBuilder();
			for (final Text run : this.leader.runs) {
				pattern.append(run.getChars(), 0, run.getCharCount());
			}
			final long[] range = this.cellRange();
			final long copies = Math.max(0, range[1] - range[0] + 1);
			return String.format(java.util.Locale.ROOT, "Leader[\"%s\" advance=%.2f offset=%.2f copies=%d]", pattern,
					this.leader.advance, this.leader.endOffset, copies);
		}

		@Override
		public String describeGeometry(final double x, final double y) {
			if (!net.zamasoft.foliojet.layout.draw.DisplayListDumper.currentDetailedGeometry()
					|| this.params.writingModeVariant == WritingModeVariant.NORMAL) {
				return "";
			}
			final AffineTransform at = SidewaysGeometry.runTransform(this.params.writingModeVariant, x, y,
					this.ascent, this.descent, this.leader.advance);
			final double[] m = new double[6];
			at.getMatrix(m);
			return String.format(java.util.Locale.ROOT, " leader-tf=[%.2f %.2f %.2f %.2f %.2f %.2f]",
					m[0], m[1], m[2], m[3], m[4], m[5]);
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			final double p = this.leader.minAdvance;
			final double gridOrigin = this.leader.advance + this.leader.endOffset;
			final long[] range = this.cellRange();
			if (range[1] < range[0]) {
				return;
			}
			try (final var artifact = gc.beginArtifactScope(); final var gcState = gc.begin()) {
				if (this.params.color != null) {
					gc.setFillPaint(this.params.color);
				}
				final boolean sideways = this.params.writingModeVariant != WritingModeVariant.NORMAL;
				final boolean vertical = this.params.flow.isVertical();
				if (sideways) {
					gc.transform(SidewaysGeometry.runTransform(this.params.writingModeVariant, x, y,
							this.ascent, this.descent, this.leader.advance));
				}
				for (long k = range[0]; k <= range[1]; ++k) {
					double cell = gridOrigin - (k + 1) * p;
					for (final Text run : this.leader.runs) {
						if (sideways) {
							gc.drawText(run, cell, 0);
						} else if (vertical) {
							gc.drawText(run, x + this.descent, y + cell);
						} else {
							gc.drawText(run, x + cell, y + this.ascent);
						}
						cell += run.getAdvance();
					}
				}
			}
		}
	}

	protected static class TextDecorationDrawable extends AbstractDrawable {
		protected final AbstractTextParams params;
		protected final Decoration decoration;
		protected final double ascent, descent;
		protected final double width, height;

		public TextDecorationDrawable(PageBox pageBox, Shape clip, AffineTransform transform, AbstractTextParams params,
				Decoration decoration, double ascent, double descent, double width, double height) {
			super(pageBox, clip, params.opacity, transform);
			this.blendMode = params.blendMode;
			this.filter = params.filter;
			this.params = params;
			this.decoration = decoration;
			this.ascent = ascent;
			this.descent = descent;
			this.width = width;
			this.height = height;
		}

		@Override
		public String describeGeometry(final double x, final double y) {
			if (!net.zamasoft.foliojet.layout.draw.DisplayListDumper.currentDetailedGeometry()
					|| this.params.writingModeVariant == WritingModeVariant.NORMAL) {
				return "";
			}
			final AffineTransform at = SidewaysGeometry.runTransform(this.params.writingModeVariant, x, y,
					this.ascent, this.descent, this.height);
			final double[] m = new double[6];
			at.getMatrix(m);
			return String.format(java.util.Locale.ROOT, " decoration-tf=[%.2f %.2f %.2f %.2f %.2f %.2f]",
					m[0], m[1], m[2], m[3], m[4], m[5]);
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			try (final var gcState = gc.begin()) {

				Color color = this.params.color;
				if (color != null) {
					gc.setStrokePaint(color);
					gc.setFillPaint(color);
				}

				// Decoration. Use the specified thickness per line (text-decoration-thickness),
				// or the default font-size ratio if none is specified (2026-08-29).
				final double fontSize = this.params.fontStyle.getSize();
				final double autoThickness = fontSize * this.params.decorationThickness;
				final net.zamasoft.pdfg2d.gc.font.FontListMetrics flm = this.params.getFontListMetrics();
				if (this.params.writingModeVariant != WritingModeVariant.NORMAL) {
					gc.transform(SidewaysGeometry.runTransform(this.params.writingModeVariant, x, y,
							this.ascent, this.descent, this.height));
					final double lineAxis = this.height;
					final Decoration.Line underline = this.decoration.underline;
					if (underline != null) {
						final double t = thicknessOf(underline, autoThickness);
						final double descent = flm.getMaxDescent();
						double lineY;
						if (underline.position() == AbstractTextParams.UNDERLINE_POSITION_UNDER) {
							lineY = descent + t / 2 + (Double.isNaN(underline.offset()) ? 0 : underline.offset());
						} else if (!Double.isNaN(underline.offset())) {
							lineY = underline.offset() + t / 2;
						} else {
							lineY = Math.min(this.descent - t, descent);
						}
						drawDecorationLine(gc, underline, t, 0, lineY, lineAxis, lineY, false);
					}
					final Decoration.Line overline = this.decoration.overline;
					if (overline != null) {
						final double t = thicknessOf(overline, autoThickness);
						final double lineY = Math.max(-this.ascent + t, -flm.getMaxAscent());
						drawDecorationLine(gc, overline, t, 0, lineY, lineAxis, lineY, false);
					}
					final Decoration.Line lineThrough = this.decoration.lineThrough;
					if (lineThrough != null) {
						final double lineY = -flm.getMaxXHeight() / 2.0;
						drawDecorationLine(gc, lineThrough, thicknessOf(lineThrough, autoThickness),
								0, lineY, lineAxis, lineY, false);
					}
				} else if (this.params.flow.isVertical()) {
					// Vertical writing progression
					x += this.descent;
					final double lineAxis = this.height;
					final Decoration.Line underline = this.decoration.underline;
					if (underline != null) {
						// Underline. On the left of the text by default; on the right with text-underline-position: right.
						// text-underline-offset moves away from the text.
						final double t = thicknessOf(underline, autoThickness);
						final boolean right = underline.position() == AbstractTextParams.UNDERLINE_POSITION_RIGHT;
						double lineX = right ? x + flm.getMaxAscent() : x - flm.getMaxDescent();
						if (!Double.isNaN(underline.offset())) {
							lineX += right ? underline.offset() : -underline.offset();
						}
						drawDecorationLine(gc, underline, t, lineX, y, lineX, y + lineAxis, true);
					}
					final Decoration.Line overline = this.decoration.overline;
					if (overline != null) {
						// Overline
						final double lineX = x + flm.getMaxAscent();
						drawDecorationLine(gc, overline, thicknessOf(overline, autoThickness), lineX, y, lineX,
								y + lineAxis, true);
					}
					final Decoration.Line lineThrough = this.decoration.lineThrough;
					if (lineThrough != null) {
						// Line-through
						drawDecorationLine(gc, lineThrough, thicknessOf(lineThrough, autoThickness), x, y, x,
								y + lineAxis, true);
					}
				} else {
					// Horizontal writing progression
					y += this.ascent;
					double lineAxis = this.width;
					final Decoration.Line underline = this.decoration.underline;
					if (underline != null) {
						// Underline
						final double t = thicknessOf(underline, autoThickness);
						final double descent = flm.getMaxDescent();
						double lineY;
						if (underline.position() == AbstractTextParams.UNDERLINE_POSITION_UNDER) {
							// under: Place the line's top edge at the bottom of the descent, and move it farther down
							// by the offset if specified (css-text-decoration-4 §2.7/§2.8).
							lineY = y + descent + t / 2 + (Double.isNaN(underline.offset()) ? 0 : underline.offset());
						} else if (!Double.isNaN(underline.offset())) {
							// auto position + offset: Shift the line's top edge using the baseline as zero.
							lineY = y + underline.offset() + t / 2;
						} else {
							lineY = y + descent;
							// Clamp to one line thickness above the bottom of the line box.
							lineY = Math.min(y + this.descent - t, lineY);
						}
						drawDecorationLine(gc, underline, t, x, lineY, x + lineAxis, lineY, false);
					}
					final Decoration.Line overline = this.decoration.overline;
					if (overline != null) {
						// Overline
						final double t = thicknessOf(overline, autoThickness);
						final double ascent = flm.getMaxAscent();
						double lineY = y - ascent;
						// Clamp to one line thickness below the top of the line box.
						lineY = Math.max(y - this.ascent + t, lineY);
						drawDecorationLine(gc, overline, t, x, lineY, x + lineAxis, lineY, false);
					}
					final Decoration.Line lineThrough = this.decoration.lineThrough;
					if (lineThrough != null) {
						// Line-through
						final double xHeight = flm.getMaxXHeight();
						final double lineY = y - xHeight / 2.0;
						drawDecorationLine(gc, lineThrough, thicknessOf(lineThrough, autoThickness), x, lineY,
								x + lineAxis, lineY, false);
					}
				}

			}
		}

		private static double thicknessOf(final Decoration.Line line, final double autoThickness) {
			return line.thickness() > 0 ? line.thickness() : autoThickness;
		}

		/**
		 * Draws one decoration line according to its style (2026-08-29).
		 *
		 * <ul>
		 * <li>solid: a single line as before</li>
		 * <li>double: two lines separated by their thickness (total width is three times the thickness)</li>
		 * <li>dotted/dashed: the GC line pattern (square dots of side = thickness;
		 * dashes = three times the thickness)</li>
		 * <li>wavy: a sequence of quadratic curves with amplitude = thickness and period = four times the thickness</li>
		 * </ul>
		 * Line patterns and caps stay within this Drawable's {@code gc.begin()} block
		 * and do not leak into subsequent drawing.
		 */
		private static void drawDecorationLine(final GC gc, final Decoration.Line line, final double t,
				final double x0, final double y0, final double x1, final double y1, final boolean vertical)
				throws GraphicsException {
			gc.setStrokePaint(line.color());
			gc.setLineWidth(t);
			switch (line.style()) {
			case AbstractTextParams.DECORATION_STYLE_DOUBLE: {
				final double dx = vertical ? t : 0, dy = vertical ? 0 : t;
				gc.draw(new Line2D.Double(x0 - dx, y0 - dy, x1 - dx, y1 - dy));
				gc.draw(new Line2D.Double(x0 + dx, y0 + dy, x1 + dx, y1 + dy));
				break;
			}
			case AbstractTextParams.DECORATION_STYLE_DOTTED:
				gc.setLineCap(GC.LineCap.BUTT);
				gc.setLinePattern(new double[] { t, t });
				gc.draw(new Line2D.Double(x0, y0, x1, y1));
				break;
			case AbstractTextParams.DECORATION_STYLE_DASHED:
				gc.setLineCap(GC.LineCap.BUTT);
				gc.setLinePattern(new double[] { t * 3, t * 3 });
				gc.draw(new Line2D.Double(x0, y0, x1, y1));
				break;
			case AbstractTextParams.DECORATION_STYLE_WAVY: {
				// Quadratic curve with half-period 2t and amplitude t (control points at ±2t give extrema at ±t).
				// For the final partial half-period, scale amplitude with length so the curve ends on the line.
				final double length = vertical ? y1 - y0 : x1 - x0;
				final double half = t * 2;
				final java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
				path.moveTo(x0, y0);
				double p = 0;
				int sign = 1;
				while (p < length) {
					final double h = Math.min(half, length - p);
					final double amp = 2 * t * (h / half);
					final double cp = p + h / 2, end = p + h;
					if (vertical) {
						path.quadTo(x0 + sign * amp, y0 + cp, x0, y0 + end);
					} else {
						path.quadTo(x0 + cp, y0 - sign * amp, x0 + end, y0);
					}
					p = end;
					sign = -sign;
				}
				gc.setLineJoin(GC.LineJoin.ROUND);
				gc.draw(path);
				break;
			}
			default:
				gc.draw(new Line2D.Double(x0, y0, x1, y1));
				break;
			}
		}
	}

	private final Drawable createTextSequenceDrawable(PageBox pageBox, Shape clip, AffineTransform transform,
			List<Object> drawingContents, int off, int len) {
		AbstractTextParams params = this.getTextParams();
		final LogicalLineEmission logicalLine = this.getLogicalLineEmission();
		final BidiSlice[] slices;
		if (logicalLine == null) {
			slices = null;
		} else {
			slices = new BidiSlice[len];
			for (int i = 0; i < len; ++i) {
				slices[i] = this.getBidiSlice(drawingContents.get(off + i));
			}
		}
		return new TextSequenceDrawable(pageBox, clip, transform, drawingContents, off, len, params, this.ascent,
				this.descent, logicalLine, this.getLogicalLineVisualText(), slices);
	}

	public final void pushGetTextSteps(final StringBuilder textBuff, final java.util.Deque<GetTextStep> worklist) {
		if (this.contents == null) {
			return;
		}
		// Text extraction must preserve document order, so build local appends (Text/
		// Control) and delegation to children (Inline/IAbsoluteBox) into the same sequence
		// of steps, then push them onto the worklist in **reverse order** (2026-07-20,
		// for the same reason as draw).
		final List<GetTextStep> localSteps = new ArrayList<>();
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> localSteps.add(w -> textBuff.append(text.getChars(), 0, text.getCharCount()));
			case Inline inline -> localSteps.add(IBox.getTextStep(inline.box, textBuff));
			case IAbsoluteBox absoluteBox -> localSteps.add(IBox.getTextStep(absoluteBox, textBuff));
			case Control control ->
				// Whitespace
				localSteps.add(w -> textBuff.append(control.getControlChar()));
			case net.zamasoft.foliojet.layout.text.LeaderQuad leader ->
				// Keep repeated dot sequences out of the logical text; insert only a single space.
				localSteps.add(w -> textBuff.append(' '));
			default -> throw new IllegalStateException();
			}
		}
		for (int i = localSteps.size() - 1; i >= 0; --i) {
			worklist.push(localSteps.get(i));
		}
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		final List<Object> drawingContents = this.getDrawingContents();
		if (drawingContents == null || drawingContents.isEmpty()) {
			return;
		}
		// Local drawing (text runs and decoration) and child drawing (inline and absolutely positioned boxes)
		// alternate within the same loop, so accumulate both in a local list in this order,
		// then push them onto the shared worklist in **reverse order** (2026-07-20:
		// converted to iteration for the same reason as IBox.pushDrawSteps). Executing local drawing
		// immediately here would put it ahead of child drawing that has not run yet,
		// breaking the drawing order.
		final List<DrawStep> localSteps = new ArrayList<>();
		int off = 0;
		int len = 0;
		double xx = x, yy = y;
		double tx = 0, ty = 0;

		boolean decoration = false;
		double dx = 0, dy = 0;
		final AbstractTextParams lineParams = this.getTextParams();
		final boolean vertical = lineParams.flow.isVertical();
		final boolean bottomToTop = vertical && lineParams.writingModeVariant != WritingModeVariant.NORMAL
				&& TypesettingMode.inlineProgression(lineParams.flow, lineParams.writingModeVariant,
						lineParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		final double inlineExtent = vertical ? this.getInnerHeight() : this.getInnerWidth();
		// Draw text and inline boxes
		for (int i = 0; i < drawingContents.size(); ++i) {
			switch (drawingContents.get(i)) {
			case Text text -> {
				// Text
				if (len == 0) {
					off = i;
					tx = xx;
					ty = yy;
				}
				if (!decoration) {
					dx = xx;
					dy = yy;
					decoration = true;
				}
				++len;
				if (vertical) {
					// Vertical writing
					yy += text.getAdvance();
				} else {
					// Horizontal writing
					xx += text.getAdvance();
				}
			}

			case Inline inline -> {
				// Inline
				if (lineParams.opacity != 0 && len > 0) {
					final int foff = off, flen = len;
					final double ftx = tx;
					final double fty = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, ty - y, yy - y)
							: ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, drawingContents, foff, flen), ftx, fty));
					len = 0;
				}
				if (decoration) {
					// Decoration
					if (this.decoration != null) {
						final double width = xx - dx;
						final double height = yy - dy;
						if ((vertical && height > 0) || (!vertical && width > 0)) {
							final double fdx = dx;
							final double fdy = bottomToTop
									? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, dy - y, yy - y)
									: dy;
							localSteps.add(w -> {
								Drawable drawable = new TextDecorationDrawable(pageBox, clip, transform, lineParams,
										this.decoration, this.ascent, this.descent, width, height);
								drawer.visitDrawable(drawable, fdx, fdy);
							});
						}
					}
					decoration = false;
				}
				final IInlineBox inlineBox = inline.box;
				double ascent;
				switch (inlineBox.getType()) {
				case INLINE: {
					// Normal inline
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
				}
					break;
				case BLOCK: {
					// Inline block
					final AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					ascent = (vertical ? inlineBox.getWidth() : inlineBox.getHeight()) - box.inlineDescent(lineParams);
				}
					break;
				case REPLACED: {
					// Image
					if (vertical) {
						// Vertical writing
						ascent = inlineBox.getWidth() / 2.0;
					} else {
						// Horizontal writing (images with a baseline, i.e., formulas, have it above the bottom edge)
						ascent = inlineBox.getHeight() - ((AbstractReplacedBox) inlineBox).getBaselineDescent();
					}
				}
					break;
				default:
					throw new IllegalStateException();
				}

				// Align to the baseline
				// Inline ascent is the distance from the baseline to the inner edge,
				// so account for borders and margins.
				double voffset = (ascent - this.ascent);
				if (vertical) {
					// Vertical writing (Japanese)
					final double drawX;
					if (lineParams.writingModeVariant == WritingModeVariant.SIDEWAYS_CCW) {
						drawX = xx + this.ascent - ascent - inline.verticalAlign;
					} else {
						voffset += (this.getWidth() - inlineBox.getWidth());
						drawX = xx + voffset + inline.verticalAlign;
					}
					final double inlineStart = yy - y;
					final double drawY = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, inlineStart,
									inlineStart + inlineBox.getHeight())
							: yy;
					localSteps.add(IBox.drawStep(inlineBox, pageBox, drawer, visitor, clip, transform, contextX,
							contextY, drawX, drawY));
					yy += inlineBox.getHeight();
				} else {
					// Horizontal writing
					final double drawX = xx, drawY = yy - voffset - inline.verticalAlign;
					localSteps.add(IBox.drawStep(inlineBox, pageBox, drawer, visitor, clip, transform, contextX,
							contextY, drawX, drawY));
					xx += inlineBox.getWidth();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// Absolute positioning
				if (lineParams.opacity != 0 && len > 0) {
					final int foff = off, flen = len;
					final double ftx = tx;
					final double fty = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, ty - y, yy - y)
							: ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, drawingContents, foff, flen), ftx, fty));
					len = 0;
				}
				double xxx, yyy;
				final AbsolutePos pos = absoluteBox.getAbsolutePos();
				if (pos.location.getLeftType() != LengthType.AUTO || pos.location.getRightType() != LengthType.AUTO) {
					xxx = contextX;
				} else {
					xxx = xx;
				}
				if (pos.location.getTopType() != LengthType.AUTO || pos.location.getBottomType() != LengthType.AUTO) {
					yyy = contextY;
				} else {
					yyy = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, yy - y, yy - y)
							: yy;
				}
				localSteps.add(IBox.drawStep(absoluteBox, pageBox, drawer, visitor, clip, transform, contextX,
						contextY, xxx, yyy));
			}

			case Control control -> {
				// Whitespace
				if (lineParams.opacity != 0 && len > 0) {
					final int foff = off, flen = len;
					final double ftx = tx;
					final double fty = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, ty - y, yy - y)
							: ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, drawingContents, foff, flen), ftx, fty));
					len = 0;
				}
				if (!decoration) {
					dx = xx;
					dy = yy;
					decoration = true;
				}
				if (vertical) {
					// Vertical writing
					yy += control.getAdvance();
				} else {
					// Horizontal writing
					xx += control.getAdvance();
				}
			}

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// leader() L2: Draw the repeated pattern (do not materialize it as a glyph sequence;
				// align its phase to a fixed grid with its origin at the line end).
				if (lineParams.opacity != 0 && len > 0) {
					final int foff = off, flen = len;
					final double ftx = tx;
					final double fty = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, ty - y, yy - y)
							: ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, drawingContents, foff, flen), ftx, fty));
					len = 0;
				}
				if (!decoration) {
					dx = xx;
					dy = yy;
					decoration = true;
				}
				if (lineParams.opacity != 0) {
					final double fx = xx;
					final double leaderStart = yy - y;
					final double fy = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, leaderStart,
									leaderStart + leader.getAdvance())
							: yy;
					localSteps.add(w -> drawer.visitDrawable(new LeaderDrawable(pageBox, clip, transform,
							this.getTextParams(), leader, this.ascent, this.descent), fx, fy));
				}
				if (vertical) {
					yy += leader.getAdvance();
				} else {
					xx += leader.getAdvance();
				}
			}

			default -> throw new IllegalStateException();
			}
		}
		if (lineParams.opacity != 0 && len > 0) {
			final int foff = off, flen = len;
			final double ftx = tx;
			final double fty = bottomToTop
					? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, ty - y, yy - y)
					: ty;
			localSteps.add(w -> drawer.visitDrawable(
					this.createTextSequenceDrawable(pageBox, clip, transform, drawingContents, foff, flen), ftx, fty));
			len = 0;
		}
		if (decoration && this.decoration != null) {
			final double width = xx - dx;
			final double height = yy - dy;
			if ((vertical && height > 0) || (!vertical && width > 0)) {
				final double fdx = dx;
				final double fdy = bottomToTop
						? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, dy - y, yy - y)
						: dy;
				localSteps.add(w -> {
					Drawable drawable = new TextDecorationDrawable(pageBox, clip, transform, lineParams,
							this.decoration, this.ascent, this.descent, width, height);
					drawer.visitDrawable(drawable, fdx, fdy);
				});
			}
		}
		// Push onto the shared worklist in reverse order to preserve the original execution order.
		for (int i = localSteps.size() - 1; i >= 0; --i) {
			worklist.push(localSteps.get(i));
		}
	}

	private void missingFontOutline(final PageBox pageBox, final Text text) {
		final String c = new String(text.getChars(), 0, text.getCharCount());
		final StringBuilder codes = new StringBuilder();
		for (int j = 0; j < c.length(); ++j) {
			codes.append("[").append(Integer.toHexString(c.charAt(j))).append("]");
		}
		pageBox.getUserAgent().message(MessageCodes.WARN_MISSING_FONT_OUTLINE, c + codes);
	}

	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			java.util.Deque<TextShapeStep> worklist) {
		final List<Object> drawingContents = this.getDrawingContents();
		if (drawingContents == null || drawingContents.isEmpty()) {
			return;
		}
		// Drawing order does not matter for appends to a clipping path, so text can be appended
		// immediately here. Only push child (inline) outlines onto
		// the worklist (2026-07-20: converted to iteration for the same reason as draw).
		final List<TextShapeStep> localSteps = new ArrayList<>();
		double xx = x, yy = y;

		final AbstractTextParams lineParams = this.getTextParams();
		final boolean sideways = lineParams.writingModeVariant != WritingModeVariant.NORMAL;
		final boolean vertical = lineParams.flow.isVertical();
		final boolean bottomToTop = vertical && lineParams.writingModeVariant != WritingModeVariant.NORMAL
				&& TypesettingMode.inlineProgression(lineParams.flow, lineParams.writingModeVariant,
						lineParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		final double inlineExtent = vertical ? this.getInnerHeight() : this.getInnerWidth();
		// Draw text and inline boxes
		for (int i = 0; i < drawingContents.size(); ++i) {
			switch (drawingContents.get(i)) {
			case Text text -> {
				// Text
				Font font = ((FontMetricsImpl) text.getFontMetrics()).getFont();
				if (sideways) {
					if (font instanceof ShapedFont) {
						final double drawY = bottomToTop
								? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, yy - y,
										yy - y + text.getAdvance())
								: yy;
						AffineTransform at = SidewaysGeometry.runTransform(lineParams.writingModeVariant, xx, drawY,
								this.ascent, this.descent, text.getAdvance());
						at.preConcatenate(transform);
						FontUtils.addTextPath(path, (ShapedFont)font, text, at);
					} else {
						if (TextShapeContext.warnIfMissing()) {
							this.missingFontOutline(pageBox, text);
						}
					}
					yy += text.getAdvance();
				} else if (vertical) {
					// Vertical writing
					if (font instanceof ShapedFont) {
						final double drawY = bottomToTop
								? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, yy - y,
										yy - y + text.getAdvance())
								: yy;
						AffineTransform at = AffineTransform.getTranslateInstance(xx + this.descent, drawY);
						at.preConcatenate(transform);
						FontUtils.addTextPath(path, (ShapedFont)font, text, at);
					}
					else {
						if (TextShapeContext.warnIfMissing()) {
							this.missingFontOutline(pageBox, text);
						}
					}
					yy += text.getAdvance();
				} else {
					// Horizontal writing
					if (font instanceof ShapedFont) {
						AffineTransform at = AffineTransform.getTranslateInstance(xx, yy + this.ascent);
						at.preConcatenate(transform);
						FontUtils.addTextPath(path, (ShapedFont)font, text, at);
					}
					else {
						if (TextShapeContext.warnIfMissing()) {
							this.missingFontOutline(pageBox, text);
						}
					}
					xx += text.getAdvance();
				}
			}

			case Inline inline -> {
				// Inline
				final IInlineBox inlineBox = inline.box;
				double ascent;
				switch (inlineBox.getType()) {
				case INLINE: {
					// Normal inline
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
				}
					break;
				case BLOCK: {
					// Inline block
					final AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					ascent = (vertical ? inlineBox.getWidth() : inlineBox.getHeight()) - box.inlineDescent(lineParams);
				}
					break;
				case REPLACED: {
					// Image
					if (vertical) {
						// Vertical writing
						ascent = inlineBox.getWidth() / 2.0;
					} else {
						// Horizontal writing (images with a baseline, i.e., formulas, have it above the bottom edge)
						ascent = inlineBox.getHeight() - ((AbstractReplacedBox) inlineBox).getBaselineDescent();
					}
				}
					break;
				default:
					throw new IllegalStateException();
				}

				// Align to the baseline
				// Inline ascent is the distance from the baseline to the inner edge,
				// so account for borders and margins.
				double voffset = (ascent - this.ascent);
				if (vertical) {
					// Vertical writing (Japanese)
					final double sx;
					if (lineParams.writingModeVariant == WritingModeVariant.SIDEWAYS_CCW) {
						sx = xx + this.ascent - ascent - inline.verticalAlign;
					} else {
						voffset += (this.getWidth() - inlineBox.getWidth());
						sx = xx + voffset + inline.verticalAlign;
					}
					final double inlineStart = yy - y;
					final double sy = bottomToTop
							? y + LayoutUtils.inlineToPhysical(lineParams, inlineExtent, inlineStart,
									inlineStart + inlineBox.getHeight())
							: yy;
					localSteps.add(IBox.textShapeStep(inlineBox, pageBox, path, transform, sx, sy));
					yy += inlineBox.getHeight();
				} else {
					// Horizontal writing
					final double sx = xx, sy = yy - voffset - inline.verticalAlign;
					localSteps.add(IBox.textShapeStep(inlineBox, pageBox, path, transform, sx, sy));
					xx += inlineBox.getWidth();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// Absolute positioning
				// ignore
			}

			case Control control -> {
				// Whitespace
				if (vertical) {
					// Vertical writing
					yy += control.getAdvance();
				} else {
					// Horizontal writing
					xx += control.getAdvance();
				}
			}

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// A leader does not affect glyph selection; only advance by its width.
				if (vertical) {
					yy += leader.getAdvance();
				} else {
					xx += leader.getAdvance();
				}
			}

			default -> throw new IllegalStateException();
			}
		}
		// Push onto the shared worklist in reverse order to preserve the original execution order.
		for (int i = localSteps.size() - 1; i >= 0; --i) {
			worklist.push(localSteps.get(i));
		}
	}

	public final double getAscent() {
		return this.ascent;
	}

	public final double getDescent() {
		return this.descent;
	}

	public void restyle(final GlyphHandler gh, final boolean widow) {
		if (this.contents == null) {
			return;
		}
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				// Text
				assert text.getGlyphCount() > 0;
				if (text instanceof TextImpl impl && impl.materializedHyphen) {
					// **Do not replay hyphenation hyphens materialized at line ends** (2026-08-31).
					// This path writes lines discarded at a page break back into the event sequence.
					// Its contract is to carry source-equivalent content, not layout decisions.
					// Including hyphens would leave them at positions where reflow no longer breaks the line:
					// this occurred 10 times in a 226-page book, with `Bu-reau` at a line start
					// and `orga-`/`niza-tions` on the continuation side too. The immediately following SoftHyphen (control)
					// carries the break opportunity, so it is preserved even without replaying the hyphen.
					break;
				}
				text.toGlyphs(gh);
			}

			case Inline content -> {
				// Inline
				switch (content.box.getType()) {
				case INLINE: {
					final InlineBox inlineBox = (InlineBox) content.box;
					inlineBox.restyle(gh, widow && i == 0);
				}
					break;

				case REPLACED: {
					final AbstractReplacedBox replacedBox = (AbstractReplacedBox) content.box;
					final InlineQuad quad = InlineQuad.createReplacedBoxQuad(replacedBox);
					gh.control(quad);
				}
					break;

				case BLOCK: {
					final InlineBlockBox inlineBox = (InlineBlockBox) content.box;
					final InlineQuad quad = InlineQuad.createInlineBlockBoxQuad(inlineBox);
					gh.control(quad);
				}
					break;

				default:
					throw new IllegalStateException();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				final InlineQuad quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
				gh.control(quad);
			}

			case Control control -> gh.control(control);

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader ->
				// Feed the quad again on rerun (drawLine reallocates the width).
				gh.control(leader);

			default -> throw new IllegalStateException();
			}
		}
	}

	public final Object getContent(int ix) {
		return this.contents.get(ix);
	}

	public final int getContentCount() {
		if (this.contents == null) {
			return 0;
		}
		return this.contents.size();
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		if (this.contents != null) {
			for (int i = 0; i < this.contents.size(); ++i) {
				buff.append(this.contents.get(i));
			}
		}
		return buff.toString();
	}
}
