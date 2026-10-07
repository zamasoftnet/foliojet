package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.fragment.BreakOpportunity;

import net.zamasoft.foliojet.layout.box.content.BreakToken;

import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.impl.FirstLineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.LineBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineAbsoluteQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.LayoutContext.Flow;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;

/**
 * Builds a text block.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: TextBuilder.java 1593 2019-12-03 07:02:17Z miyabe $
 */
public class TextBuilder {

	/**
	 * Returns the advance for one tab (tab-size, 2026-08-29): the distance from the position {@code lineAxis},
	 * measured from line start, to the next tab stop (an integer multiple of tab width). Does not advance if tab width
	 * is nonpositive (specification: 0 makes tabs zero-width).
	 */
	static double tabAdvance(final AbstractTextParams params, final double lineAxis) {
		double width = params.tabSize;
		if (params.tabSizeIsMultiple) {
			width *= params.getFontListMetrics().getSpaceAdvance();
		}
		if (width <= 0) {
			return 0;
		}
		return width - (lineAxis % width);
	}

	/** The innermost text parameters to which the control character belongs. */
	private AbstractTextParams currentTextParams() {
		if (this.textParamStack == null || this.textParamStack.isEmpty()) {
			return this.lineBox.getTextParams();
		}
		return ((InlineBox) this.textParamStack.get(this.textParamStack.size() - 1)).getTextParams();
	}

	/**
	 * A placed inline box.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: TextBuilder.java 1593 2019-12-03 07:02:17Z miyabe $
	 */
	protected static class Inline {
		public final InlineBox box;
		public double baseline;

		public Inline(InlineBox inline) {
			this.box = inline;
		}
	}

	private final BlockBuilder builder;
	private final byte paragraphDirection;

	/**
	 * State of the nearest ancestor's {@code line-clamp} (null if absent; 2026-08-29). Counts every {@link #addLine}
	 * and discards lines beyond N.
	 */
	private final net.zamasoft.foliojet.layout.builder.LineClampState lineClamp;

	/**
	 * Text block under construction.
	 */
	TextBlockBox textBlockBox;

	/**
	 * Stack of inline boxes under construction.
	 */
	private List<Inline> inlineStack = null;

	private List<InlineBox> textParamStack = null;

	/**
	 * Flags indicating the units at line start, the beginning, and the preceding break.
	 */
	private boolean lineHead, firstUnit, last;

	/** The first formatted line of this element has not yet been finalized. */
	private boolean firstFormattedLine;

	/**
	 * Breaks the line on the next inline or text addition.
	 */
	private boolean toLineFeed = false;

	/**
	 * Space collapsing and wrapping.
	 */
	private boolean collapseSpaces, wrap;

	/**
	 * Word splitting.
	 */
	private byte breakWord;

	private double textIndent, letterSpacing, minLineAxis, maxLineSize, maxPageSize, lastSpaceAdvance;

	private double pageAxis = 0;

	private double lineAxis = 0;

	private AbstractLineBox lineBox;

	private TextImpl text = null;

	private List<Element> textBuffer = new ArrayList<Element>();

	private double unitAdvance = 0;

	/**
	 * Last break opportunity that fit.
	 */
	private BreakOpportunity opportunity = BreakOpportunity.NONE;

	/**
	 * Selected breakpoint sequence for Knuth-Plass line breaking ({@code text-wrap-style: pretty}) (2026-07-23, M3c
	 * increment 3). Non-null only during optimized replay by {@link TotalFitSession}. Bound to this instance; not
	 * carried over if a page break between lines replaces it with a new TextBuilder (the remainder uses the legacy
	 * greedy algorithm). Always null on the default legacy path, leaving behavior unchanged.
	 */
	TotalFitProjection.Plan totalFitPlan = null;

	private final net.zamasoft.foliojet.layout.RetainedTextLimit retainedTextLimit;

	public TextBuilder(BlockBuilder builder, BreakToken breakToken) {
		this.builder = builder;
		this.retainedTextLimit = net.zamasoft.foliojet.layout.RetainedTextLimit.get(builder);
		this.lineClamp = net.zamasoft.foliojet.layout.builder.LineClampState.find(builder);
		final Flow flow = builder.getFlow();
		final BlockParams params = flow.box.getBlockParams();
		this.paragraphDirection = params.direction;
		this.textBlockBox = new TextBlockBox(params, breakToken);

		if (!breakToken.midFlow()) {
			this.textIndent = flow.box.getTextIndent();
		} else {
			this.textIndent = 0;
		}
		final AbstractLineBox lineBox;
		if (!breakToken.midFlow() && params.firstLineStyle != null) {
			lineBox = new FirstLineBox(params.firstLineStyle);
		} else {
			lineBox = new LineBox(params);
		}

		this.last = !breakToken.midLine();
		this.firstFormattedLine = !breakToken.midFlow();
		this.lineBox = lineBox;
		this.lineBox.setBidiBaseDirection(this.paragraphDirection);
		this.lineHead = this.firstUnit = true;
		this.lastSpaceAdvance = 0;
		this.changeTextState(params);
	}

	/**
	 * Switches text parameters.
	 *
	 * @param params
	 */
	private void changeTextState(AbstractTextParams params) {
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
			this.collapseSpaces = false;
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			this.collapseSpaces = true;
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NORMAL:
			this.collapseSpaces = true;
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			this.collapseSpaces = true;
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			this.collapseSpaces = false;
			this.wrap = true;
			break;
		default:
			throw new IllegalStateException();
		}
		if (this.wrap) {
			this.breakWord = params.wordWrap;
		} else {
			this.breakWord = AbstractTextParams.WORD_WRAP_NORMAL;
		}
		this.letterSpacing = LayoutUtils.computeLength(params.letterSpacing, this.builder.getFlowBox().getLineSize());
		// Japanese spacing compression A2/A3/T1b: follow effective flags and trim policy across inline
		// boundaries. Keep pair state; pairs across a boundary use the current element's values.
		// Vertical writing uses the same mechanism (gap is xadvance on the logical inline axis; A3).
		this.autospace.setFlags(params.textAutospace);
		this.autospace.setTrimOff(params.textSpacingTrimOff);
		// Japanese spacing compression H1: hang line-end punctuation (hanging-punctuation: allow-end).
		this.hangingEnd = params.hangingPunctuationEnd;

	}

	/** Whether this line contains text (Text, visible Control, or leader). */
	private boolean lineHasText = false;

	/** Whether this line contains an image or inline block (atomic inline). */
	private boolean lineHasAtomic = false;

	/**
	 * Adds a strut using the block's font and line-height (CSS 2.1 §10.8) to textless lines containing only images or
	 * inline blocks (2026-09-02).
	 *
	 * <p>
	 * Only for standards-mode documents (with DOCTYPE); omit in quirks mode, as browsers do. Leave text-bearing lines
	 * alone, since their text already contributes the block font height (applying this to all lines changed 77 tests
	 * because the text-line height formula differs from the strut formula; this restriction leaves every text line
	 * unchanged). In Acid2's image-height-test, an image in a {@code font: 20em} line appeared at the line top because
	 * the strut was missing.
	 * </p>
	 */
	private void addStrutIfTextless(final AbstractLineBox line) {
		if (this.lineHasText || !this.lineHasAtomic) {
			return;
		}
		final AbstractTextParams params = line.getTextParams();
		final double lineHeight = line.getLineParams().lineHeight;
		if (params == null || !params.strictLineBox || LayoutUtils.isNone(lineHeight) || params.fontStyle == null) {
			// Omit the strut in quirks mode (HTML without DOCTYPE), as browsers do.
			return;
		}
		final AbstractContainerBox flowBox = this.builder.getFlowBox();
		if (flowBox instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox) {
			// Lines inside list-marker boxes (only a dot image) are not body-text lines.
			return;
		}
		final double[] strut = strutAscentDescent(params, lineHeight);
		line.addAscentDescent(strut[0], strut[1]);
	}

	/**
	 * Computes strut ascent/descent from the block font and line-height (shared by {@link #addStrutIfTextless} and
	 * {@link #getVirtualClosedPageAxis}).
	 */
	private static double[] strutAscentDescent(final AbstractTextParams params, final double lineHeight) {
		double ascent, descent;
		if (params.isVerticalTypesetting()) {
			// Vertical writing: glyph bounds extend half the size on each side of the center line.
			ascent = descent = params.fontStyle.getSize() / 2.0;
		} else {
			// CSS 2.1 strut metrics come from the first available font. Do not use the maximum over
			// the entire list (getMaxAscent): an incomplete list during parallel font-index warm-up can
			// change the value (imageTest's msn.htm differed once in four runs).
			final net.zamasoft.pdfg2d.gc.font.FontListMetrics flm = params.getFontListMetrics();
			final net.zamasoft.pdfg2d.gc.font.FontMetrics[] metrics = flm.metrics();
			if (metrics != null && metrics.length > 0) {
				ascent = metrics[0].getAscent();
				descent = metrics[0].getDescent();
			} else {
				ascent = flm.getMaxAscent();
				descent = flm.getMaxDescent();
			}
		}
		final double textHeight = ascent + descent;
		if (!LayoutUtils.isNone(lineHeight) && lineHeight != textHeight) {
			final double half = (lineHeight - textHeight) / 2.0;
			ascent += half;
			descent += half;
		}
		return new double[] { ascent, descent };
	}

	/** Measurement lines do not mutate input boxes, such as by compressing atomic inlines. */
	private boolean measuringLine;

	/**
	 * Virtual position after closing a line with preceding content alone. Does not modify body lines, runs, or
	 * inlines. Applies the same startInline/addElement as finalization to measurement boxes and maximizes
	 * ascent/descent separately. Borrows only glyph metrics from a word awaiting hyphenation, without finalizing or
	 * consuming it.
	 */
	double getVirtualClosedPageAxis(final java.util.function.Consumer<GlyphHandler> pending) {
		final TextBuilder measure = new TextBuilder(this.builder, BreakToken.NONE);
		measure.measuringLine = true;
		measure.lineBox = this.lineBox instanceof FirstLineBox
				? new FirstLineBox((net.zamasoft.foliojet.layout.box.params.FirstLineParams) this.lineBox.getLineParams())
				: new LineBox((BlockParams) this.lineBox.getLineParams());
		measure.lineBox.addAscentDescent(this.lineBox.getAscent(), this.lineBox.getDescent());
		measure.lineHasText = this.lineHasText;
		measure.lineHasAtomic = this.lineHasAtomic;
		measure.pageAxis = this.pageAxis;
		measure.lineAxis = this.lineAxis;
		measure.textIndent = this.textIndent;
		measure.maxLineSize = this.maxLineSize;
		measure.maxPageSize = this.maxPageSize;
		measure.minLineAxis = this.minLineAxis;
		measure.firstUnit = this.firstUnit;
		measure.last = this.last;
		measure.firstFormattedLine = this.firstFormattedLine;
		measure.lineHead = this.lineHead;
		measure.lastSpaceAdvance = this.lastSpaceAdvance;
		measure.opportunity = this.opportunity;
		measure.unitAdvance = this.unitAdvance;
		measure.fontStyle = this.fontStyle == null ? this.builder.getOpenRunFontStyle() : this.fontStyle;
		measure.fontMetrics = this.fontMetrics == null ? this.builder.getOpenRunFontMetrics() : this.fontMetrics;
		measure.textParamStack = this.textParamStack == null ? null : new ArrayList<>(this.textParamStack);
		measure.changeTextState(this.currentTextParams());
		if (this.inlineStack != null) {
			measure.inlineStack = new ArrayList<>();
			for (final Inline inline : this.inlineStack) {
				final Inline copy = new Inline(copyInlineForMeasurement(inline.box));
				copy.baseline = inline.baseline;
				measure.inlineStack.add(copy);
			}
		}
		final List<Element> buffer = new ArrayList<>(this.textBuffer);
		if (this.text != null) {
			measure.text = measureTextSlice(this.text, 0, this.text.getGlyphCount());
			buffer.set(buffer.indexOf(this.text), measure.text);
		}
		measure.textBuffer = buffer;
		measure.autospace.copyFrom(this.autospace, this.text, measure.text);
		pending.accept(new GlyphHandler() {
			public void startTextRun(final int offset, final FontStyle style, final FontMetrics metrics) {
				measure.startTextRun(style, metrics);
			}
			public void glyph(final int offset, final char[] chars, final int off, final byte len, final int gid) {
				measure.glyph(offset, chars, off, len, gid);
			}
			public void endTextRun() {
				if (measure.text != null) measure.endTextRun();
			}
			public void control(final TextControl control) {
				measure.control(measure.copyPendingControl(control));
			}
			public void flush() {
				// Record only ordinary inter-character break candidates. Do not add lines to the body or parent builder.
				if (!measure.wrap || buffer.isEmpty()) return;
				if (measure.firstUnit && measure.lineAxis > 0) {
					measure.locateLine();
					measure.firstUnit = false;
				}
				if (measure.opportunity.elementCount() == 0
						|| LayoutUtils.compare(measure.lineAxis - measure.lastSpaceAdvance,
								measure.maxLineSize - measure.textIndent) <= 0) {
					measure.opportunity = measure.captureOpportunity();
				}
			}
			public void close() { }
		});
		final double trailingSpace = measure.lastSpaceAdvance;
		// Even if final delivery reveals overflow, do not finalize the body line. Virtually close the preceding line
		// using only existing break candidates (the absolute position does not become a new candidate).
		int from = 0;
		boolean locate = measure.firstUnit;
		final double overflow = measure.lineAxis - trailingSpace - (measure.maxLineSize - measure.textIndent);
		if (!measure.firstUnit && measure.opportunity.elementCount() > 0 && LayoutUtils.compare(overflow, 0) > 0
				&& !measure.tryJlreqLineShrink(overflow, false)) {
			from = measure.opportunity.elementCount();
			final int glyphs = measure.opportunity.glyphCount();
			if (glyphs > 0 && buffer.get(from - 1) instanceof Text run && glyphs < run.getGlyphCount()) {
				buffer.set(from - 1, measureTextSlice(run, 0, glyphs));
				buffer.add(from, measureTextSlice(run, glyphs, run.getGlyphCount()));
			}
			for (int i = 0; i < from; ++i) {
				measure.measureElement(buffer.get(i));
			}
			measure.addStrutIfTextless(measure.lineBox);
			measure.pageAxis += measure.lineBox.getPageSize();
			measure.lineBox = new LineBox(this.textBlockBox.getBlockParams());
			measure.lineHasText = measure.lineHasAtomic = false;
			final List<Inline> inlines = measure.inlineStack;
			measure.inlineStack = null;
			if (inlines != null) {
				for (final Inline inline : inlines) {
					final InlineBox continued = inline.box.splitLine(true);
					continued.fixLineAxis(this.builder.getFlowBox());
					measure.startInline(continued);
				}
			}
			if (measure.collapseSpaces) {
				while (from < buffer.size() && buffer.get(from) instanceof WhiteSpace) {
					++from;
				}
			}
			measure.lineAxis = 0;
			for (int i = from; i < buffer.size(); ++i) {
				measure.lineAxis += buffer.get(i).getAdvance();
			}
			locate = true;
		}
		for (int i = from; i < buffer.size(); ++i) {
			measure.measureElement(buffer.get(i));
		}
		measure.addStrutIfTextless(measure.lineBox);
		if (measure.lineBox.getPageSize() == 0) {
			return measure.pageAxis;
		}
		if (locate) {
			// nowrap/pre has not yet passed through locateLine. Read exclusions with the same search.
			measure.textBuffer = buffer.subList(from, buffer.size());
			measure.locateLine();
		}
		return measure.pageAxis + measure.lineBox.getPageSize();
	}

	/** Copies mutable undelivered controls for measurement, since downstream processing sets their widths. */
	private TextControl copyPendingControl(final TextControl control) {
		if (control instanceof InlineQuad quad) {
			final WritingMode flow = this.currentTextParams().flow;
			final InlineQuad copy;
			switch (quad.getType()) {
			case InlineQuad.INLINE_START:
			case InlineQuad.INLINE_END: {
				final InlineBox box = copyInlineForMeasurement((InlineBox) quad.getBox());
				final boolean start = quad.getType() == InlineQuad.INLINE_START;
				copy = start ? InlineQuad.createInlineBoxStartQuad(box) : InlineQuad.createInlineBoxEndQuad(box);
				final AbstractTextParams params = box.getTextParams();
				final boolean reverse = params.flow.isVertical() && params.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant, params.direction)
								== TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
				copy.advance = start ? (reverse ? box.getFrame().getFrameBottom() : box.getFrame().getFrameLineStart(flow))
						: (reverse ? box.getFrame().getFrameTop() : box.getFrame().getFrameLineEnd(flow));
				break;
			}
			case InlineQuad.INLINE_REPLACED:
				copy = InlineQuad.createReplacedBoxQuad(((InlineReplacedQuad) quad).box);
				copy.advance = quad.getBox().getLineExtent(flow);
				break;
			case InlineQuad.INLINE_BLOCK:
				copy = InlineQuad.createInlineBlockBoxQuad(((InlineQuad.InlineBlockQuad) quad).box);
				copy.advance = quad.getBox().getLineExtent(flow);
				break;
			case InlineQuad.INLINE_ABSOLUTE:
				copy = InlineQuad.createInlineAbsoluteBoxQuad(((InlineAbsoluteQuad) quad).box);
				break;
			default:
				throw new IllegalStateException();
			}
			return copy;
		}
		if (control instanceof WhiteSpace space) {
			final var metrics = this.currentTextParams().getFontListMetrics();
			final WhiteSpace copy = new WhiteSpace(metrics, space.getCharOffset());
			copy.setWordSpacing(space.getAdvance() - metrics.getSpaceAdvance());
			return copy;
		}
		if (control instanceof Tab tab) {
			return new Tab(this.currentTextParams().getFontListMetrics(), tab.getCharOffset());
		}
		return control;
	}

	private static TextImpl measureTextSlice(final Text run, final int from, final int to) {
		final TextImpl copy = new TextImpl(run.getCharOffset(), run.getFontStyle(), run.getFontMetrics());
		copy.setLetterSpacing(run.getLetterSpacing());
		int charOffset = 0;
		for (int i = 0; i < to; ++i) {
			final byte length = run.getClusterLengths()[i];
			if (i >= from) {
				copy.appendGlyph(run.getChars(), charOffset, length, run.getGlyphIds()[i]);
				if (run.xAdvances() != null) {
					copy.addXAdvance(i - from, run.xAdvances().get(i));
				}
			}
			charOffset += length;
		}
		return copy;
	}

	private static InlineBox copyInlineForMeasurement(final InlineBox box) {
		final InlineBox copy = new InlineBox(box.getInlineParams(), box.getInlinePos());
		copy.getFrame().frame = box.getFrame().frame;
		copy.getFrame().margin.set(box.getFrame().margin);
		copy.getFrame().padding.set(box.getFrame().padding);
		// addAscentDescent adds the frame, so first subtract that frame once from the original outer size.
		final var frame = box.getFrame();
		if (box.getTextParams().flow.isVertical()) {
			final boolean sideways = box.getTextParams().writingModeVariant == WritingModeVariant.SIDEWAYS_CCW;
			copy.addAscentDescent(box.getAscent() - (sideways ? frame.getFrameLeft() : frame.getFrameRight()),
					box.getDescent() - (sideways ? frame.getFrameRight() : frame.getFrameLeft()));
		} else {
			copy.addAscentDescent(box.getAscent() - frame.getFrameTop(), box.getDescent() - frame.getFrameBottom());
		}
		return copy;
	}

	private void measureElement(final Element element) {
		if (element instanceof InlineQuad quad) {
			switch (quad.getType()) {
			case InlineQuad.INLINE_START:
				this.startInline(copyInlineForMeasurement((InlineBox) quad.getBox()));
				break;
			case InlineQuad.INLINE_END:
				this.endInline();
				break;
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_REPLACED:
				this.startInline((IInlineBox) quad.getBox());
				this.endInline();
				break;
			case InlineQuad.INLINE_ABSOLUTE:
				break;
			default:
				throw new IllegalStateException();
			}
		} else if (element instanceof Control control && control.getControlChar() == '\n') {
			// Do not create a new line after a processed br. Pass preserved spaces, tabs, and leaders to addElement.
		} else {
			this.addElement(element);
		}
	}

	/**
	 * Adds a line to the text block.
	 */
	private void addLine(AbstractLineBox lineBox) {
		this.addStrutIfTextless(lineBox);
		this.lineHasText = false;
		this.lineHasAtomic = false;
		if (this.lineClamp != null) {
			if (this.lineClamp.exhausted()) {
				// line-clamp (2026-08-29): discard lines beyond N (contribute neither height nor
				// drawing). Only now is content after line N confirmed,
				// so apply the ellipsis held for line N.
				this.lineClamp.truncatePending();
				return;
			}
			if (this.lineClamp.countLine()) {
				// Line N: if nothing follows, leave it unchanged (no ellipsis),
				// so store only the truncation method. Freeze values at line closure.
				final double avail = this.maxLineSize, lineStart = this.minLineAxis;
				this.lineClamp.setPending(() -> this.applyLineClampEllipsis(lineBox, lineStart, avail));
			}
		}
		this.textBlockBox.addLine(lineBox, this.pageAxis);
		this.builder.noteBidiLine(this.textBlockBox, lineBox);
		final double pageAdvance = lineBox.getAscent() + lineBox.getDescent();
		this.pageAxis += pageAdvance;
		assert !LayoutUtils.isNone(this.pageAxis);
		if (pageAdvance > 0) {
			this.builder.poLastMargin = this.builder.neLastMargin = 0;
		}
	}

	/**
	 * Recomputes pair adjustment at an intra-run split boundary (autospace gap minus punctuation compression) (T1a: to
	 * undo an applied adjustment when splitting moves the boundary across lines). The adjustment is pure and can be
	 * recomputed without recording. Approximate autospace flags with current values; inaccurate only when an inline
	 * switch combines with a split inside an old run (recorded in consultations).
	 */
	private double boundaryAdjustment(final TextImpl head, final TextImpl tail) {
		final char[] headChars = head.getChars();
		final int prevCp = Character.codePointBefore(headChars, head.getCharCount());
		final int cp = Character.codePointAt(tail.getChars(), 0);
		final double fontSize = head.getFontStyle().getSize();
		final int prevGid = head.getGlyphIds()[head.getGlyphCount() - 1];
		final int gid = tail.getGlyphIds()[0];
		// Same check as AutospaceTracker.gapBefore (Japanese/Latin spacing also after proportional punctuation, 2026-09-14).
		final boolean proportionalPunctuation = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
				.proportionalPunctuation(prevCp, head.getFontMetrics(), prevGid, fontSize,
						head.getFontStyle().getDirection());
		final double gap = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.gapEm(prevCp, cp,
				this.autospace.getFlags(), proportionalPunctuation) * fontSize;
		double trim = 0;
		if (!this.autospace.isTrimOff() && head.getFontMetrics().getKerning(prevGid, gid) == 0) {
			trim = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.cappedPairTrim(prevCp,
					head.getFontMetrics(), prevGid, fontSize, head.getFontStyle(), cp, tail.getFontMetrics(),
					gid, tail.getFontStyle().getSize(), tail.getFontStyle());
		}
		return gap - trim;
	}

	/**
	 * Next page-direction position to try when a line does not fit.
	 *
	 * <p>
	 * For rectangular floats, jump to the bottom as before (width is unchanged until then). For floats with {@code
	 * shape-outside}, move down by the line height and search again: the band widens in the lower half of a circle, so
	 * jumping to its bottom prevents wrapping around it (2026-08-29). Every step advances at least 1 pt and is capped
	 * at the float bottom, so the retry loop always terminates.
	 * </p>
	 */
	private static double nextSearchPage(final FloatExclusion exclusion, final double pageStart,
			final double lineHeight) {
		final double pageEnd = exclusion.pageSpan().end();
		if (exclusion.shape() == null) {
			return pageEnd;
		}
		return Math.min(pageEnd, pageStart + Math.max(lineHeight, 1));
	}

	/**
	 * Whether the first line can fit in the current fragment while avoiding page floats. Without page floats, always
	 * returns true and delegates to existing line placement.
	 */
	static boolean hasFirstLineBandInFragment(final BlockBuilder builder, final double fragmentLimit) {
		if (builder.pageFloatExclusionsForLineLayout().isEmpty()) {
			return true;
		}
		final BlockParams params = builder.getFlowBox().getBlockParams();
		final double lineHeight = !builder.breakToken.midFlow() && params.firstLineStyle != null
				? params.firstLineStyle.lineHeight
				: params.lineHeight;
		if (LayoutUtils.compare(lineHeight, 0) <= 0) {
			return true;
		}
		double pageStart = builder.pageAxis;
		final double lineStart0 = builder.lineAxis;
		final double lineEnd0 = lineStart0 + builder.getFlowBox().getLineSize();
		for (;;) {
			final ExclusionSpace.LineScan found = builder.scanLineBandForLineLayout(pageStart, lineHeight,
					lineStart0, lineEnd0);
			double maxPageSize = fragmentLimit - pageStart;
			if (found.maxPageSizeSet()) {
				maxPageSize = Math.min(maxPageSize, found.maxPageSize());
			}
			if (LayoutUtils.compare(found.lineEnd() - found.lineStart(), lineHeight) >= 0) {
				return LayoutUtils.compare(maxPageSize, lineHeight) >= 0;
			}
			if (found.startExclusion() == null && found.endExclusion() == null) {
				// If the containing block itself is smaller than one line height, leave it to the existing
				// overflow handling instead of repeatedly breaking pages because of page floats.
				return true;
			}
			if (found.endExclusion() == null) {
				pageStart = nextSearchPage(found.startExclusion(), pageStart, lineHeight);
			} else if (found.startExclusion() == null) {
				pageStart = nextSearchPage(found.endExclusion(), pageStart, lineHeight);
			} else {
				pageStart = Math.min(nextSearchPage(found.startExclusion(), pageStart, lineHeight),
						nextSearchPage(found.endExclusion(), pageStart, lineHeight));
			}
		}
	}

	/**
	 * Adjusts the position of the line currently under construction.
	 */
	private void locateLine() {
		double pageStart = this.builder.pageAxis + this.pageAxis;
		double lineStart = this.builder.lineAxis;
		this.maxPageSize = Double.MAX_VALUE;

		this.maxLineSize = this.builder.getFlowBox().getLineSize();
		if (this.builder.hasLineExclusions()) {
			final double lineHeight = this.lineBox.getLineParams().lineHeight;
			final double lineEnd0 = this.builder.lineAxis + this.maxLineSize;
			// Scan ordinary floats and page floats as separate immutable snapshots
			// on each iteration.
			for (;;) {
				// Left and right edges of the space available for a line.
				final ExclusionSpace.LineScan found = this.builder.scanLineBandForLineLayout(pageStart, lineHeight,
						this.builder.lineAxis, lineEnd0);
				lineStart = found.lineStart();
				if (found.maxPageSizeSet()) {
					// Existing code updates this.maxPageSize only in this branch;
					// the previous outer for(;;) iteration's value can remain
					// (2026-07-23: actual state carry-over between iterations, as in BlockBuilder.addBound;
					// always update only conditionally).
					this.maxPageSize = found.maxPageSize();
				}
				this.maxLineSize = found.lineEnd() - found.lineStart();
				if (LayoutUtils.compare(this.maxLineSize, this.lineAxis) >= 0) {
					// Enough width is available.
					break;
				}
				// If not enough, move down once and search again.
				if (found.startExclusion() == null && found.endExclusion() == null) {
					break;
				}
				if (found.endExclusion() == null) {
					pageStart = nextSearchPage(found.startExclusion(), pageStart, lineHeight);
				} else if (found.startExclusion() == null) {
					pageStart = nextSearchPage(found.endExclusion(), pageStart, lineHeight);
				} else {
					double startEnd = nextSearchPage(found.startExclusion(), pageStart, lineHeight);
					double endEnd = nextSearchPage(found.endExclusion(), pageStart, lineHeight);
					if (startEnd > endEnd) {
						pageStart = endEnd;
					} else {
						pageStart = startEnd;
					}
				}
			}
		}

		assert LayoutUtils.compare(pageStart - this.builder.pageAxis, this.pageAxis) >= 0;
		this.pageAxis = pageStart - this.builder.pageAxis;
		assert !LayoutUtils.isNone(this.pageAxis);
		this.minLineAxis = lineStart - this.builder.lineAxis;

		// Flush-start punctuation (Japanese spacing compression S1/JLREQ cl-01). In horizontal and vertical writing,
		// move the leading half-em space of a fullwidth-equivalent opening bracket outside the line. Per CSS Text 4,
		// apply flush-start only for trim-start; normal/space-all retain the leading half-em space,
		// the other JLREQ option.
		final AbstractLineParams lineParams = this.lineBox.getLineParams();
		// space-first keeps full width only on the block's first line and after forced line breaks;
		// use flush-start only after automatic wrapping. Existing this.last is true
		// for a forced preceding break or the first line, and false for automatic wrapping.
		final boolean trimStart = lineParams.textSpacingTrimStart
				|| (lineParams.textSpacingSpaceFirst && !this.last);
		for (int i = 0; i < this.textBuffer.size(); ++i) {
			Element e = (Element) this.textBuffer.get(i);
			if (e.getAdvance() == 0) {
				continue;
			}
			if (e instanceof Text) {
				final Text text = (Text) e;
				final double fontSize = text.getFontStyle().getSize();
				final boolean wide = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver
						.isWide(text.getFontMetrics(), text.getGlyphIds()[0], fontSize,
								text.getFontStyle().getDirection());
				final int firstCodePoint = Character.codePointAt(text.getChars(), 0);
				double headIndent = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver
						.lineHeadIndent(firstCodePoint, wide, trimStart) * fontSize;
				if (lineParams.hangingPunctuationFirst && this.firstFormattedLine) {
					headIndent += net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.firstHang(
							firstCodePoint, wide, text.getFontMetrics().getAdvance(text.getGlyphIds()[0]), fontSize,
							trimStart);
				}
				if (headIndent != 0) {
					// Apply flush-start even on the block's first line, preserving the author's
					// text-indent as the reference position.
					this.textIndent += headIndent;
				}
			}
			break;
		}
	}

	/**
	 * Returns the current text box.
	 *
	 * @return
	 */
	private AbstractTextBox getTextBox() {
		if (this.inlineStack == null || this.inlineStack.isEmpty()) {
			return this.lineBox;
		}
		Inline inline = (Inline) this.inlineStack.get(this.inlineStack.size() - 1);
		return inline.box;
	}

	private void startInline(IInlineBox box) {
		AbstractTextBox textBox = this.getTextBox();
		textBox.addInline(box);

		double baseline;
		if (this.inlineStack == null) {
			this.inlineStack = new ArrayList<Inline>();
			baseline = 0;
		} else if (this.inlineStack.isEmpty()) {
			baseline = 0;
		} else {
			Inline parentInline = (Inline) this.inlineStack.get(this.inlineStack.size() - 1);
			baseline = parentInline.baseline;
		}

		switch (box.getType()) {
		case INLINE: {
			InlineBox inlineBox = (InlineBox) box;
			InlineParams params = inlineBox.getInlineParams();
			InlinePos pos = box.getInlinePos();
			FontListMetrics flm = params.getFontListMetrics();
			double ascent = flm.getMaxAscent();
			double descent = flm.getMaxDescent();
			inlineBox.addAscentDescent(ascent, descent);

			AbstractTextParams textParams = textBox.getTextParams();
			double start = textParams.flow.isVertical() && textParams.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(textParams.flow, textParams.writingModeVariant,
							textParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP
						? inlineBox.getFrame().getFrameBottom()
						: inlineBox.getFrame().getFrameLineStart(textParams.flow);
			this.lineBox.addAdvance(start);
			inlineBox.addAdvance(start);
			Inline inline = new Inline(inlineBox);
			this.inlineStack.add(inline);

			// Set the baseline.
			double verticalAlign = pos.verticalAlign.getVerticalAlign(textBox, this.lineBox, ascent, descent,
					pos.lineHeight, baseline);
			inline.baseline = baseline + verticalAlign;

			if (inlineBox.getFrame().getFrameWidth() > 0) {
				// Apply line-height.
				double lineHeight = pos.lineHeight;
				lineHeight = Math.max(this.lineBox.getLineParams().lineHeight, lineHeight);
				double textHeight = ascent + descent;
				if (lineHeight != textHeight) {
					lineHeight = (lineHeight - textHeight) / 2.0;
					ascent = (ascent + lineHeight);
					descent = (descent + lineHeight);
				}
				ascent = ascent + verticalAlign + baseline;
				descent = descent - verticalAlign - baseline;
				this.lineBox.addAscentDescent(ascent, descent);
			}
		}
			break;

		case REPLACED:
		case BLOCK: {
			final IInlineBox inlineBox = box;
			final AbstractLineParams lineParams = this.lineBox.getLineParams();
			final double advance = inlineBox.getLineExtent(lineParams.flow);
			textBox.addAdvance(advance);
			if (this.lineBox != textBox) {
				this.lineBox.addAdvance(advance);
			}
			this.inlineStack.add(null);

			final InlinePos pos = box.getInlinePos();
			if (pos.verticalAlign instanceof net.zamasoft.foliojet.layout.box.content.CSSVerticalAlignPolicy va
					&& va.getVerticalAlignType() == net.zamasoft.foliojet.layout.box.content.CSSVerticalAlignPolicy.BASELINE
					&& !(box instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox)
					&& !(box instanceof net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox)) {
				// Strut candidates (addStrutIfTextless). Baseline-aligned boxes remain above the baseline
				// even if line ascent/descent grows later, so their positions stay unchanged.
				// Top/bottom/middle alignment uses the line height at that moment, so a later strut
				// would shift them; exclude them. Also exclude list-marker boxes.
				this.lineHasAtomic = true;
			}
			double descent, ascent;
			if (box.getType() == BoxType.BLOCK) {
				// Baseline for inline blocks and tables.
				final AbstractContainerBox inlineBlockBox = (AbstractContainerBox) box;
				final BlockParams params = inlineBlockBox.getBlockParams();
				if (!this.measuringLine && params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL
						&& lineParams.flow.isVertical() && !params.flow.isVertical()
						&& inlineBlockBox instanceof net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox stf) {
					// **Fit tate-chu-yoko (all) into a 1 em cell** (css-writing-modes-4
					// §9.1.3, 2026-08-11). Now that layout at natural width is complete, replace the width
					// with 1 em and apply a horizontal affine transform to the content. This also makes
					// the apparent width used by the line (= ascent/descent below) 1 em.
					final java.awt.geom.GeneralPath ink = new java.awt.geom.GeneralPath();
					final RootBuilder root = this.builder.getPageContext();
					if (root != null) {
						stf.textShapeQuiet(root.getCurrentPageBox(), ink, new java.awt.geom.AffineTransform(), 0, 0);
					}
					stf.compressTextCombine(params.fontStyle.getSize(), ink.getCurrentPoint() == null ? null
							: ink.getBounds2D());
				}
				final boolean verticalLine = lineParams.flow.isVertical();
				descent = inlineBlockBox.inlineDescent(lineParams);
				ascent = (verticalLine ? inlineBox.getWidth() : inlineBox.getHeight()) - descent;
			} else {
				// Image baseline.
				switch (lineParams.flow) {
				case WritingMode.TB:
					// Horizontal writing (images with a baseline, i.e. formulas, have it above the bottom;
					// same value as AbstractTextBox.verticalAlign and drawing).
					descent = ((net.zamasoft.foliojet.layout.box.AbstractReplacedBox) box).getBaselineDescent();
					ascent = box.getHeight() - descent;
					break;
				case WritingMode.LR:
				case WritingMode.RL:
					// Vertical writing.
					ascent = box.getWidth();
					descent = ascent = ascent / 2.0;
					break;
				default:
					throw new IllegalStateException();
				}
			}

			final double verticalAlign = pos.verticalAlign.getVerticalAlign(textBox, this.lineBox, ascent, descent,
					pos.lineHeight, baseline);

			if (box.getType() == BoxType.BLOCK) {
				// Apply line-height.
				double lineHeight = pos.lineHeight;
				lineHeight = Math.max(this.lineBox.getLineParams().lineHeight, lineHeight);
				double textHeight = ascent + descent;
				if (lineHeight > textHeight) {
					lineHeight = (lineHeight - textHeight) / 2.0;
					ascent = (ascent + lineHeight);
					descent = (descent + lineHeight);
				}
			}

			ascent = ascent + verticalAlign + baseline;
			descent = descent - verticalAlign - baseline;

			this.lineBox.addAscentDescent(ascent, descent);
		}
			break;

		default:
			throw new IllegalStateException();
		}
	}

	private void endInline() {
		// Silently discard INLINE_END without a start (2026-08-17; same reason as
		// INLINE_END in control()).
		if (this.inlineStack == null || this.inlineStack.isEmpty()) {
			return;
		}
		Inline inline = (Inline) this.inlineStack.remove(this.inlineStack.size() - 1);
		if (inline != null) {
			InlineBox inlineBox = inline.box;
			inlineBox.closeInline();

			AbstractLineParams params = this.lineBox.getLineParams();
			final double end = params.flow.isVertical() && params.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
							params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP
						? inlineBox.getFrame().getFrameTop()
						: inlineBox.getFrame().getFrameLineEnd(params.flow);
			final double advance = inlineBox.getLineExtent(params.flow) + end;
			inlineBox.addAdvance(end);
			this.lineBox.addAdvance(end);
			AbstractTextBox textBox = this.getTextBox();
			if (this.lineBox != textBox) {
				textBox.addAdvance(advance);
			}
		}
	}

	private void addRetainedText(final long payloadBytes) {
		if (!this.measuringLine && this.retainedTextLimit != null) {
			this.retainedTextLimit.add(payloadBytes);
		}
	}

	private void addElement(Element e) {
		final AbstractTextBox textBox = this.getTextBox();

		final double advance = e.getAdvance();
		double ascent;
		double descent;
		if (e instanceof Text) {
			final Text text = (Text) e;
			this.addRetainedText(2L * text.getCharCount());
			textBox.addText(text);
			ascent = text.getAscent();
			descent = text.getDescent();
			assert !LayoutUtils.isNone(ascent + descent);
			this.lineHasText = true;
		} else if (e instanceof Control) {
			final Control control = (Control) e;
			// Control has no getCharCount; it represents the single UTF-16 character from getControlChar().
			this.addRetainedText(2L);
			textBox.addControl(control);
			if ((control.getControlChar() == ' ' || control.getControlChar() == SoftHyphen.CHAR)
					&& control.getAdvance() == 0) {
				return;
			}
			ascent = control.getAscent();
			descent = control.getDescent();
			assert !LayoutUtils.isNone(ascent + descent);
			this.lineHasText = true;
		} else if (e instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
			// leader() L1: contribute to line height using pattern dimensions.
			textBox.addLeader(leader);
			ascent = leader.runs[0].getAscent();
			descent = leader.runs[0].getDescent();
			assert !LayoutUtils.isNone(ascent + descent);
			this.lineHasText = true;
		} else {
			throw new IllegalStateException();
		}
		textBox.addAdvance(advance);
		if (this.lineBox != textBox) {
			this.lineBox.addAdvance(advance);

			final AbstractTextBox parentText;
			final double baseline;
			if (this.inlineStack.size() >= 2) {
				final Inline parentInline = (Inline) this.inlineStack.get(this.inlineStack.size() - 2);
				baseline = parentInline.baseline;
				parentText = parentInline.box;
			} else {
				baseline = 0;
				parentText = this.lineBox;
			}
			final InlineBox inlineBox = (InlineBox) textBox;
			final InlinePos pos = inlineBox.getInlinePos();

			final double verticalAlign = pos.verticalAlign.getVerticalAlign(parentText, this.lineBox, ascent, descent,
					pos.lineHeight, baseline);
			double lineHeight = pos.lineHeight;
			// Apply the line's line-height.
			{
				final double textHeight = this.lineBox.getLineParams().lineHeight;
				if (!LayoutUtils.isNone(textHeight)) {
					lineHeight = Math.max(textHeight, lineHeight);
				}
			}
			assert !LayoutUtils.isNone(lineHeight);
			final double textHeight = ascent + descent;
			// Apply line-height.
			if (lineHeight != textHeight) {
				lineHeight = (lineHeight - textHeight) / 2.0;
				ascent = (ascent + lineHeight);
				descent = (descent + lineHeight);
				ascent = ascent + verticalAlign + baseline;
				descent = descent - verticalAlign - baseline;
			}
			assert !LayoutUtils.isNone(ascent + descent);
		} else {
			double lineHeight = this.lineBox.getLineParams().lineHeight;
			// Apply line-height.
			final double textHeight = ascent + descent;
			if (lineHeight != textHeight) {
				lineHeight = (lineHeight - textHeight) / 2.0;
				ascent = (ascent + lineHeight);
				descent = (descent + lineHeight);
			}
			assert !LayoutUtils.isNone(ascent + descent);
		}
		this.lineBox.addAscentDescent(ascent, descent);
	}

	double getActualPageAxis() {
		return this.pageAxis + this.lineBox.getPageSize();
	}

	/**
	 * Narrows the available width of the line under construction to absolute coordinate {@code newAbsLineEnd} for
	 * same-line placement of line-end floats ({@code BlockBuilder.tryFloatOnCurrentLine}, 2026-08-08). Returns {@code
	 * false} without changing state if the line has not been positioned (before locateLine) or existing content
	 * (textIndent + finalized and pending advances) does not fit the new width.
	 */
	boolean narrowCurrentLine(final double newAbsLineEnd) {
		if (this.lineBox == null || this.firstUnit) {
			return false;
		}
		final double newMaxLineSize = newAbsLineEnd - (this.builder.lineAxis + this.minLineAxis);
		if (LayoutUtils.compare(newMaxLineSize, this.textIndent + this.lineAxis) < 0) {
			return false;
		}
		if (newMaxLineSize < this.maxLineSize) {
			this.maxLineSize = newMaxLineSize;
		}
		return true;
	}

	double getPageAxis() {
		return this.pageAxis;
	}

	/**
	 * Extent that actually advances normal flow. A dedicated line for an outside marker overlaid at a table's start
	 * remains separate in reading order while sharing the following table's page position.
	 */
	double getFlowPageAdvance() {
		return this.textBlockBox.overlaysFollowingBlock() ? 0 : this.pageAxis;
	}

	double getLineAxis() {
		return this.lineAxis;
	}

	/**
	 * Starts a new line.
	 *
	 * @param last
	 */
	private boolean newLine(boolean last) {
		// Japanese spacing compression A2: an actual line break severs the pair (no gap across lines).
		this.autospace.reset();
		// Japanese spacing compression T2/H1: line-end compression/hanging extent for this line (set before align).
		if (this.pendingEndHang != 0) {
			this.lineBox.setEndHangAdvance(this.pendingEndHang);
			this.pendingEndHang = 0;
		}
		boolean lineAdded = false;
		if (this.drawLine(last, !last)) {
			this.firstFormattedLine = false;
			final AbstractLineBox lineBox = this.lineBox;
			final LineBox newLineBox = lineBox.splitLine(this.textBlockBox.getBlockParams());
			newLineBox.setBidiBaseDirection(this.paragraphDirection);

			// StringBuilder text = new StringBuilder();
			// lineBox.getText(text);

			// A line rebuilt after a page break can retain hyphens materialized during its previous
			// layout (2026-08-31). Hyphens are meaningful only at line end,
			// so remove them before alignment. See AbstractTextBox#removeStrayHyphens for details.
			final double strayHyphen = lineBox.removeStrayHyphens();
			if (strayHyphen != 0) {
				lineBox.addAdvance(-strayHyphen);
			}
			lineBox.align(this.textIndent, this.minLineAxis, this.maxLineSize, last);
			this.applyTextOverflow(lineBox);
			if (this.inlineStack != null && !this.inlineStack.isEmpty()) {
				final AbstractTextParams lineParams = this.lineBox.getTextParams();
				this.lineBox = newLineBox;
				// TODO Avoid regenerating inlineStack.
				List<Inline> inlineStack = this.inlineStack;
				this.inlineStack = null;

				for (int i = 0; i < inlineStack.size(); ++i) {
					Inline inline = (Inline) inlineStack.get(i);
					InlineBox oldInline = inline.box;
					InlineBox newInline = oldInline.splitLine(true);
					newInline.fixLineAxis(this.builder.getFlowBox());
					this.startInline(newInline);
				}
				for (int i = inlineStack.size() - 1; i >= 1; --i) {
					Inline inline = (Inline) inlineStack.get(i);
					Inline parent = (Inline) inlineStack.get(i - 1);
					parent.box.addAdvance(inline.box.getLineExtent(lineParams.flow));
				}
			} else {
				this.lineBox = newLineBox;
			}
			this.addLine(lineBox);
			if (last) {
				this.builder.resolveBidiParagraph(this.textBlockBox.getBlockParams());
			}
			lineAdded = true;
		}

		this.last = last;
		this.textIndent = 0;
		this.lineHead = this.firstUnit = true;
		this.lastSpaceAdvance = 0;
		if (last) {
			return lineAdded;
		}
		// Wrapping.
		if (!this.collapseSpaces) {
			return lineAdded;
		}
		// Collapse leading spaces.
		for (int i = 0; i < this.textBuffer.size(); ++i) {
			Element e = (Element) this.textBuffer.get(i);
			if (e instanceof WhiteSpace) {
				WhiteSpace whiteSpace = (WhiteSpace) e;
				this.lineAxis -= whiteSpace.getAdvance();
				whiteSpace.collapse();
				continue;
			}
			this.lineHead = false;
			break;
		}
		return lineAdded;
	}

	/**
	 * {@code text-overflow: ellipsis} (css-overflow-3 §4, 2026-08-29).
	 *
	 * <p>
	 * When the block's {@code overflow} is not visible and finalized line content exceeds the available width (a
	 * single nowrap line or an unbreakable long word), clip the line end at available width minus ellipsis width and
	 * draw an ellipsis there ({@link AbstractLineBox#setEllipsis}). Clipping avoids relayout, preserving line content
	 * and glyphs (clipped characters remain in PDF text extraction). Use the first font in the block's font-family
	 * containing "…" (U+2026), or "..." if none. Start-side ellipsis for right-to-left horizontal writing (direction:
	 * rtl) is unsupported (no action). This does not truncate by line count (as line-clamp does).
	 * </p>
	 */
	private void applyTextOverflow(final AbstractLineBox lineBox) {
		final BlockParams bp = this.textBlockBox.getBlockParams();
		if (bp.textOverflow != BlockParams.TEXT_OVERFLOW_ELLIPSIS || !bp.overflow.clipsPaint()
				|| bp.direction != AbstractTextParams.DIRECTION_LTR || bp.fontManager == null
				|| bp.fontStyle == null) {
			return;
		}
		final double avail = this.maxLineSize;
		// Same effective line-width definition as align() (excludes hanging line-end extent, includes indent).
		final double used = lineBox.getLineSize() - lineBox.getEndHangAdvance() + this.textIndent;
		if (LayoutUtils.compare(used, avail) <= 0) {
			return;
		}
		final TextImpl ellipsis = ellipsisText(bp);
		if (ellipsis == null) {
			return;
		}
		final double clipExtent = this.minLineAxis + avail - ellipsis.getAdvance();
		lineBox.setEllipsis(ellipsis, Math.max(0, clipExtent));
	}

	/**
	 * Truncates line N of {@code line-clamp} with an ellipsis (2026-08-29). Uses the same mechanism as {@link
	 * #applyTextOverflow} (clipping + extra drawing), but the line need not fill the width: the cut position is the
	 * lesser of content end (including alignment offset) and available width minus ellipsis width. For short content,
	 * the ellipsis follows immediately; for long content, the final glyphs are clipped and replaced by the ellipsis
	 * (does not reshape the final word as Chrome does). rtl is unsupported (no action).
	 */
	private void applyLineClampEllipsis(final AbstractLineBox lineBox, final double lineStart, final double avail) {
		final BlockParams bp = this.textBlockBox.getBlockParams();
		if (bp.direction != AbstractTextParams.DIRECTION_LTR || bp.fontManager == null || bp.fontStyle == null) {
			return;
		}
		final TextImpl ellipsis = ellipsisText(bp);
		if (ellipsis == null) {
			return;
		}
		final double contentEnd = lineBox.getLineAlign() + lineBox.getLineSize() - lineBox.getEndHangAdvance();
		final double boxEnd = lineStart + avail - ellipsis.getAdvance();
		lineBox.setEllipsis(ellipsis, Math.max(0, Math.min(contentEnd, boxEnd)));
	}

	/** Ellipsis glyph sequence (U+2026, or "..." if unavailable). Null if it cannot be created. */
	private static TextImpl ellipsisText(final BlockParams bp) {
		final FontListMetrics flm = bp.fontManager.getFontListMetrics(bp.fontStyle);
		String s = "…";
		FontMetrics fm = ellipsisFont(flm, 0x2026);
		if (fm == null) {
			s = "...";
			fm = ellipsisFont(flm, '.');
		}
		if (fm == null) {
			return null;
		}
		final net.zamasoft.pdfg2d.font.Font font = fm instanceof net.zamasoft.pdfg2d.font.FontMetricsImpl impl
				? impl.getFont()
				: fm.getFontSource().createFont();
		final TextImpl text = new TextImpl(-1, bp.fontStyle, fm);
		for (int i = 0; i < s.length(); ++i) {
			final char c = s.charAt(i);
			text.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c));
		}
		text.pack();
		return text;
	}

	private static FontMetrics ellipsisFont(final FontListMetrics flm, final int c) {
		for (int i = 0; i < flm.getLength(); ++i) {
			final FontMetrics fm = flm.getFontMetrics(i);
			final net.zamasoft.pdfg2d.font.FontSource source = fm.getFontSource();
			// Exclude the missing-glyph fallback font, which claims it can display anything.
			if (source instanceof net.zamasoft.pdfg2d.pdf.font.cid.missing.MissingCIDFontSource
					|| !source.canDisplay(c)) {
				continue;
			}
			return fm;
		}
		return null;
	}

	/**
	 * Generates a line.
	 *
	 * @param last {@code true} to align as the last line and consume the whole buffer
	 * @param materializeBreakHyphen {@code true} to materialize the soft hyphen at the confirmed break position
	 * @return true if content was added to the line
	 */
	private boolean drawLine(final boolean last, final boolean materializeBreakHyphen) {
		if (this.firstUnit) {
			this.locateLine();
			this.firstUnit = false;
		}
		final int count;
		if (last) {
			count = this.textBuffer.size();
		} else {
			count = this.opportunity.elementCount();
		}
		// TODO Make assert count > 0 hold here.
		assert count > 0 || last;

		boolean content;
		if (count > 0) {
			this.allocateLeaders(count, last);
			TextImpl trimEndCandidate = null;
			for (int i = 0; i < count; ++i) {
				Element e = (Element) this.textBuffer.get(i);
				if (e instanceof Text) {
					final TextImpl text = (TextImpl) e;
					if (i == count - 1) {
						if (last || this.opportunity.glyphCount() == 0 || this.opportunity.glyphCount() == text.getGlyphCount()) {
							// Last line or
							// not ending in text.
							// Only one unit's width is available.
							text.pack();
							if (this.text == text) {
								this.text = null;
							}
						} else {
							// Split at a breakable position.
							e = text.split(this.opportunity.glyphCount());
							TextImpl prevText = (TextImpl) e;
							// Undo kerning at the split and calculate the position
							// (T1a: font-layer kern is GPOS only; punctuation compression/autospace
							// adjustments are undone below).
							this.lineAxis += this.fontMetrics.getKerning(prevText.glyphIds[prevText.glyphCount - 1],
									text.glyphIds[0]);
							// Undo pair adjustment at the split boundary (equivalent to the old split's
							// kern restoration). The adjustment is on xadvance[0] of the current glyph,
							// the first glyph of the tail after splitting.
							final double edgeAdjustment = this.boundaryAdjustment(prevText, text);
							if (edgeAdjustment != 0) {
								text.addXAdvance(0, -edgeAdjustment);
								this.lineAxis -= edgeAdjustment;
							}
							this.textBuffer.add(i, e);
						}
					}
					this.addElement(e);
					trimEndCandidate = (TextImpl) e;
				} else if (e instanceof TextControl) {
					final TextControl quad = (TextControl) e;
					this.placeControl(quad, materializeBreakHyphen && i == count - 1);
					// Collapsed trailing spaces and zero-width boundaries do not prevent
					// the preceding punctuation from being at line end. If any other inline element
					// or leader follows, the punctuation is not at line end.
					if (!(e instanceof Control) && e.getAdvance() != 0) {
						trimEndCandidate = null;
					}
				} else {
					throw new IllegalStateException();
				}
				this.lineAxis -= e.getAdvance();
			}

			if (trimEndCandidate != null) {
				double endHang = this.lineBox.getEndHangAdvance();
				if (this.lineBox.getLineParams().textSpacingTrimEnd) {
					endHang = Math.max(endHang, this.endTrim(trimEndCandidate));
				}
				if (this.lineBox.getLineParams().hangingPunctuationForceEnd) {
					endHang = Math.max(endHang, this.forceEndHang(trimEndCandidate));
				}
				this.lineBox.setEndHangAdvance(endHang);
			}

			double lastSpaceAdvance = 0;
			for (int i = count - 1; i >= 0; --i) {
				Element e = (Element) this.textBuffer.get(i);
				if (e instanceof Control) {
					Control c = (Control) e;
					lastSpaceAdvance += c.getAdvance();
					continue;
				}
				if (e.getAdvance() <= 0) {
					continue;
				}
				break;
			}
			this.lineBox.addAdvance(-lastSpaceAdvance);
			int remainder = this.textBuffer.size() - count;
			for (int i = 0; i < remainder; ++i) {
				this.textBuffer.set(i, this.textBuffer.get(count + i));
			}
			for (int i = 0; i < count; ++i) {
				this.textBuffer.remove(this.textBuffer.size() - 1);
			}
			// Replay after the progress guard may deliver only INLINE_END without
			// a corresponding start. Check whether content actually entered the line,
			// not whether an element was consumed. Calling align() on an empty line
			// violates AbstractLineBox's invariant.
			content = this.lineBox.getContentCount() > 0;
		} else {
			content = false;
		}

		this.opportunity = this.captureOpportunity();
		this.builder.checkFloatings();
		return content;
	}

	/**
	 * Distributes remaining width among leaders in the selected line range (leader() L1;
	 * consult-codex-2026-07-31-leader.txt Q2).
	 *
	 * <p>
	 * Always reset all leaders to minimum width before distribution (so previous allocations do not leak when TwoPass
	 * recording/replay redrives the same instance). If the line splits within text (= full line), remaining width is
	 * ≈0 by definition, so keep minimum widths. Include collapsed trailing spaces in remaining width (they are removed
	 * before align). Leaders consume the remainder before justify, so inter-character justification on leader lines
	 * naturally becomes ≈0.
	 * </p>
	 */
	private void allocateLeaders(final int count, final boolean last) {
		List<net.zamasoft.foliojet.layout.text.LeaderQuad> leaders = null;
		double natural = 0;
		for (int i = 0; i < count; ++i) {
			final Element e = (Element) this.textBuffer.get(i);
			if (e instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
				leader.advance = leader.minAdvance;
				leader.endOffset = 0;
				if (leaders == null) {
					leaders = new ArrayList<>();
				}
				leaders.add(leader);
			}
			natural += e.getAdvance();
		}
		if (leaders == null) {
			return;
		}
		if (!last && count > 0 && this.textBuffer.get(count - 1) instanceof Text tail
				&& this.opportunity.glyphCount() > 0 && this.opportunity.glyphCount() != tail.getGlyphCount()) {
			// Splitting a line within text means it is full. Remaining width is ≈0, so keep minimum widths.
			return;
		}
		// Do not count trailing spaces (collapsed before align) in line width.
		double trailing = 0;
		for (int i = count - 1; i >= 0; --i) {
			final Element e = (Element) this.textBuffer.get(i);
			if (e instanceof Control control) {
				trailing += control.getAdvance();
				continue;
			}
			if (e.getAdvance() <= 0) {
				continue;
			}
			break;
		}
		final double extra = (this.maxLineSize - this.textIndent) - (natural - trailing);
		if (extra > 0) {
			final double share = extra / leaders.size();
			for (final net.zamasoft.foliojet.layout.text.LeaderQuad leader : leaders) {
				leader.advance += share;
				this.lineAxis += share;
			}
		}
		// Origin for line-end phase alignment: distance from each leader's end to the end of line content.
		double after = -trailing;
		for (int i = count - 1; i >= 0; --i) {
			final Element e = (Element) this.textBuffer.get(i);
			if (e instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
				leader.endOffset = Math.max(0, after);
			}
			after += e.getAdvance();
		}
	}

	/**
	 * Captures the current buffer state as a break opportunity. If the buffer ends in a soft hyphen, retains it for
	 * materialization at the cut (including when carried over at the remainder's end).
	 */
	private BreakOpportunity captureOpportunity() {
		final SoftHyphen hyphen = !this.textBuffer.isEmpty()
				&& this.textBuffer.get(this.textBuffer.size() - 1) instanceof SoftHyphen sh ? sh : null;
		return new BreakOpportunity(this.textBuffer.size(), this.text != null ? this.text.getGlyphCount() : 0, hyphen);
	}

	FontStyle fontStyle;
	FontMetrics fontMetrics;

	/** Japanese spacing compression A2: text-autospace pair tracking. */
	private final net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker autospace = new net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker();

	/** Japanese spacing compression H1: flag enabling hanging-punctuation: allow-end. */
	private boolean hangingEnd;

	/**
	 * Japanese spacing compression T2/H1: line-end compression/hanging extent for the line completed by the next
	 * newLine.
	 */
	private double pendingEndHang;

	/** JLREQ 3.8.3 compression point. Only hang moves the effective line end outward without changing drawing width. */
	private static final class JlreqShrinkPoint {
		final TextImpl text;
		final int glyphIndex;
		final double capacity;
		final boolean hang;

		JlreqShrinkPoint(final TextImpl text, final int glyphIndex, final double capacity, final boolean hang) {
			this.text = text;
			this.glyphIndex = glyphIndex;
			this.capacity = capacity;
			this.hang = hang;
		}
	}

	private static final class JlreqGlyph {
		final TextImpl text;
		final int glyphIndex;
		final int codePoint;
		final int gid;
		final double fontSize;

		JlreqGlyph(final TextImpl text, final int glyphIndex, final int codePoint) {
			this.text = text;
			this.glyphIndex = glyphIndex;
			this.codePoint = codePoint;
			this.gid = text.getGlyphIds()[glyphIndex];
			this.fontSize = text.getFontStyle().getSize();
		}
	}

	/**
	 * Compresses the current break candidate in the six JLREQ 3.8.3 stages (Latin word spaces → line-end punctuation →
	 * line-end middle dots → internal middle dots → brackets/commas → Japanese/Latin spacing). If the full capacity
	 * cannot make it fit, changes nothing and falls back to the preceding candidate as before.
	 */
	private boolean tryJlreqLineShrink(final double overflow) {
		return this.tryJlreqLineShrink(overflow, true);
	}

	/** Virtual closure for a static position checks only whether compression can fit; it does not alter advances. */
	@SuppressWarnings("unchecked")
	private boolean tryJlreqLineShrink(final double overflow, final boolean apply) {
		if (!(overflow > 0)) {
			return true;
		}
		final List<JlreqShrinkPoint>[] stages = new List[8];
		for (int i = 1; i < stages.length; ++i) {
			stages[i] = new ArrayList<>();
		}

		JlreqGlyph prev = null;
		JlreqGlyph beforeSpace = null;
		WhiteSpace pendingSpace = null;
		JlreqGlyph tail = null;
		for (final Element element : this.textBuffer) {
			if (element instanceof TextImpl text) {
				final char[] chars = text.getChars();
				final byte[] clusterLengths = text.getClusterLengths();
				int charIndex = 0;
				for (int glyphIndex = 0; glyphIndex < text.getGlyphCount(); ++glyphIndex) {
					final int cp = Character.codePointAt(chars, charIndex);
					final JlreqGlyph current = new JlreqGlyph(text, glyphIndex, cp);
					if (pendingSpace != null) {
						if (beforeSpace != null && isWestern(beforeSpace.codePoint) && isWestern(cp)) {
							final double min = Math.min(beforeSpace.fontSize, current.fontSize) / 4.0;
							addJlreqShrinkPoint(stages[1], current, Math.max(0, pendingSpace.getAdvance() - min));
						}
						pendingSpace = null;
						beforeSpace = null;
						prev = null;
					}
					if (prev != null) {
						addJlreqBoundaryShrinkPoints(stages, prev, current, !this.autospace.isTrimOff());
					}
					prev = tail = current;
					charIndex += clusterLengths[glyphIndex];
				}
				continue;
			}
			if (element instanceof WhiteSpace whiteSpace) {
				beforeSpace = prev;
				pendingSpace = whiteSpace;
				prev = null;
				continue;
			}
			if (element instanceof SoftHyphen) {
				continue;
			}
			if (element instanceof InlineQuad inline
					&& (inline.getType() == InlineQuad.INLINE_START || inline.getType() == InlineQuad.INLINE_END)
					&& inline.getAdvance() == 0) {
				continue;
			}
			prev = null;
			pendingSpace = null;
			beforeSpace = null;
			// After an atomic inline (formula, image, inline-block), the preceding character is no longer at line end
			// (2026-10-04, publishing report). Without clearing it, the trailing half of "。" was counted as compressible
			// at line end, placing a box that did not fit on the same line past the type area's right edge. Starts/ends
			// of framed inline elements and absolute-position placeholders only attach to text, so keep the line-end character.
			if (!(element instanceof InlineQuad quad && (quad.getType() == InlineQuad.INLINE_START
					|| quad.getType() == InlineQuad.INLINE_END || quad.getType() == InlineQuad.INLINE_ABSOLUTE))) {
				tail = null;
			}
		}

		if (tail != null) {
			addJlreqLineEndShrinkPoints(stages, tail);
		}

		double total = 0;
		for (int stage = 1; stage < stages.length; ++stage) {
			for (final JlreqShrinkPoint point : stages[stage]) {
				total += point.capacity;
			}
		}
		if (LayoutUtils.compare(total, overflow) < 0) {
			return false;
		}
		if (!apply) {
			return true;
		}

		double remainder = overflow;
		double physical = 0;
		double hang = 0;
		for (int stage = 1; stage < stages.length && remainder > 0.0001; ++stage) {
			double capacity = 0;
			for (final JlreqShrinkPoint point : stages[stage]) {
				capacity += point.capacity;
			}
			if (capacity <= 0) {
				continue;
			}
			final double used = Math.min(remainder, capacity);
			for (final JlreqShrinkPoint point : stages[stage]) {
				final double amount = used * point.capacity / capacity;
				if (point.hang) {
					hang += amount;
				} else if (amount != 0) {
					point.text.addXAdvance(point.glyphIndex, -amount);
					physical += amount;
				}
			}
			remainder -= used;
		}
		this.lineAxis -= physical;
		this.pendingEndHang = hang;
		return true;
	}

	/**
	 * Applies control elements arranged on the line (break hyphens, inline starts/ends, replaced elements, absolute
	 * positioning, control characters, and leaders) to the line under construction (extracted from {@link #drawLine}
	 * on 2026-10-05; the body was merely moved).
	 *
	 * @param quad             control element
	 * @param breakHyphenHere  true for the last element of a line at a confirmed break (materializes the break hyphen)
	 */
	private void placeControl(final TextControl quad, final boolean breakHyphenHere) {
		if (breakHyphenHere && quad == this.opportunity.hyphen()) {
			// The line broke at a soft-hyphen opportunity, so materialize the hyphen.
			//
			// A block end closed by a page split reaches here with last=true, so
			// !last alone cannot detect it. Pass whether the break is confirmed to materialize it.
			final TextImpl hyphen = this.opportunity.hyphen().getText();
			if (hyphen.getGlyphCount() > 0) {
				// hyphenate-character:"" breaks without displaying a character.
				this.addElement(hyphen);
			}
		} else if (quad instanceof InlineQuad) {
			// Inline box.
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				// Inline start.
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				this.startInline(inlineStartQuad.box);
			}
				break;

			case InlineQuad.INLINE_END: {
				// Inline end.
				this.endInline();
			}
				break;

			case InlineQuad.INLINE_REPLACED: {
				// Replaced box.
				final InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
				this.startInline((IInlineBox) inlineReplacedQuad.box);
				this.endInline();
			}
				break;

			case InlineQuad.INLINE_BLOCK: {
				// Block box.
				this.startInline((IInlineBox) inlineQuad.getBox());
				this.endInline();
			}
				break;

			case InlineQuad.INLINE_ABSOLUTE: {
				// Absolutely positioned box.
				final InlineAbsoluteQuad inlineAbsoluteQuad = (InlineAbsoluteQuad) inlineQuad;
				this.getTextBox().addAbsolute(inlineAbsoluteQuad.box);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		} else if (quad instanceof Control) {
			final Control control = (Control) quad;
			this.addElement(control);
		} else if (quad instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leaderQuad) {
			// leader() L1: store in the line with the allocated width.
			this.addElement(leaderQuad);
		} else {
			throw new IllegalStateException();
		}
	}

	private static void addJlreqShrinkPoint(final List<JlreqShrinkPoint> points, final JlreqGlyph glyph,
			final double capacity) {
		if (capacity > 0.0001) {
			points.add(new JlreqShrinkPoint(glyph.text, glyph.glyphIndex, capacity, false));
		}
	}

	private static void addJlreqBoundaryShrinkPoints(final List<JlreqShrinkPoint>[] stages,
			final JlreqGlyph prev, final JlreqGlyph current, final boolean punctuationTrim) {
		final net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass pc = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass
				.of(prev.codePoint);
		final net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass cc = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass
				.of(current.codePoint);

		// Boundary compression is on the current glyph's xadvance (negative = compression). The half-em
		// already removed by JLREQ 3.1 consecutive-punctuation compression (pairTrim) must be deducted
		// from stages 4 and 5. Otherwise, the ） in "）。" compresses from half-em to solid, and 。
		// intrudes into ）'s glyph bounds (2026-09-11, user report "（乙36）。").
		final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xa = current.text.xAdvances();
		final double existing = xa == null ? 0 : xa.get(current.glyphIndex);
		double applied = Math.max(0, -existing);

		// Half-em and quarter-em spaces belong to fullwidth (1 em) punctuation glyphs. Proportional punctuation
		// (e.g. "、" at 0.5 em in IPA P Gothic, "【" at 0.5 em in GenEi M Gothic) lacks them, so
		// use the same isWide check as pairTrim to set capacity to 0. Counting them overlaps glyph bounds
		// (2026-09-11: randomized tests with different fonts gave "・（" a −0.25 em advance in IPAPGothic
		// and a 0.2 em overlap for "文【" in GenEi M Gothic).
		final boolean prevWide = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(
				prev.text.getFontMetrics(), prev.gid, prev.fontSize, prev.text.getFontStyle().getDirection());
		final boolean currentWide = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(
				current.text.getFontMetrics(), current.gid, current.fontSize, current.text.getFontStyle().getDirection());

		// Stage 5: half-em space before cl-01 and after cl-02/cl-07. Do not compress after cl-06.
		double punctuation = 0;
		if (punctuationTrim) {
			if (cc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.OPENING && currentWide) {
				punctuation += current.fontSize / 2.0;
			}
			if ((pc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.CLOSING
					|| net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isComma(prev.codePoint)) && prevWide) {
				punctuation += prev.fontSize / 2.0;
			}
			final double consumed = Math.min(punctuation, applied);
			punctuation -= consumed;
			applied -= consumed;
		}

		// Stage 4: compress quarter-em spaces before and after cl-05 to solid.
		double middleDot = 0;
		if (punctuationTrim) {
			if (pc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.MIDDLE_DOT && prevWide) {
				middleDot += prev.fontSize / 4.0;
			}
			if (cc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.MIDDLE_DOT && currentWide) {
				middleDot += current.fontSize / 4.0;
			}
			middleDot = Math.max(0, middleDot - applied);
		}

		// Stage 6: compress text-autospace quarter-em gaps to a minimum eighth-em. Quarter-em gaps after
		// proportional punctuation (AutospaceTracker.gapBefore, 2026-09-14) compress at the same stage.
		final boolean japaneseLatin = isJapaneseLatinBoundary(prev.codePoint, current.codePoint)
				|| !prevWide && isWestern(current.codePoint)
						&& net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(prev.codePoint)
								== net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.PUNCTUATION;
		double autospace = 0;
		if (japaneseLatin && existing > 0) {
			final double ideographSize = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
					.ideographFirst(prev.codePoint) ? prev.fontSize : current.fontSize;
			autospace = Math.min(existing, ideographSize / 8.0);
		}
		if (middleDot == 0 && punctuation == 0 && autospace == 0) {
			return;
		}
		// Same pen-to-pen distance as drawText. Do not subtract already applied compression (existing) twice.
		final FontMetrics prevMetrics = prev.text.getFontMetrics();
		final double kerning = prev.text == current.text ? prevMetrics.getKerning(prev.gid, current.gid) : 0;
		final double penDistance = prevMetrics.getAdvance(prev.gid) + prev.text.getLetterSpacing() - kerning + existing;
		final double gap = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.inkGap(
				prevMetrics, prev.gid, prev.fontSize, prev.text.getFontStyle(), current.text.getFontMetrics(),
				current.gid, current.fontSize, current.text.getFontStyle(), penDistance);
		double remaining = Double.isNaN(gap) ? Double.POSITIVE_INFINITY : Math.max(0, gap);
		// Stages sharing a boundary share the budget in their actual consumption order, 4 → 5 → 6.
		middleDot = Math.min(middleDot, remaining);
		remaining = Math.max(0, remaining - middleDot);
		punctuation = Math.min(punctuation, remaining);
		remaining = Math.max(0, remaining - punctuation);
		autospace = Math.min(autospace, remaining);
		addJlreqShrinkPoint(stages[4], current, middleDot);
		addJlreqShrinkPoint(stages[5], current, punctuation);
		addJlreqShrinkPoint(stages[6], current, autospace);
	}

	private void addJlreqLineEndShrinkPoints(final List<JlreqShrinkPoint>[] stages, final JlreqGlyph tail) {
		final net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass cls = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass
				.of(tail.codePoint);
		final boolean wide = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(
				tail.text.getFontMetrics(), tail.gid, tail.fontSize, tail.text.getFontStyle().getDirection());
		final double advance = tail.text.getFontMetrics().getAdvance(tail.gid);
		final boolean force = this.lineBox.getLineParams().hangingPunctuationForceEnd
				&& cls == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.PUNCTUATION;
		if (force) {
			stages[2].add(new JlreqShrinkPoint(null, -1, advance, true));
			return;
		}
		double trim = 0;
		if (!this.autospace.isTrimOff()) {
			trim = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.endTrim(tail.codePoint,
					wide, tail.fontSize);
			if (trim > 0) {
				final int stage = cls == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.MIDDLE_DOT
						? 3 : 2;
				stages[stage].add(new JlreqShrinkPoint(null, -1, trim, true));
			}
		}
		if (wide && this.hangingEnd
				&& cls == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.PUNCTUATION
				&& advance > trim) {
			// allow-end is extra rescue after exhausting all six JLREQ stages.
			stages[7].add(new JlreqShrinkPoint(null, -1, advance - trim, true));
		}
	}

	private static boolean isWestern(final int codePoint) {
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind kind = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
				.of(codePoint);
		return kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
				|| kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC;
	}

	private static boolean isJapaneseLatinBoundary(final int prev, final int current) {
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind pk = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
				.of(prev);
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind ck = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
				.of(current);
		return pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH && isWestern(current)
				|| ck == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						&& isWestern(prev);
	}

	/** Unconditional line-end compression for trim-both/auto. */
	private double endTrim(final TextImpl text) {
		if (text.getGlyphCount() <= 0) {
			return 0;
		}
		final int cp = Character.codePointBefore(text.getChars(), text.getCharCount());
		final int gid = text.getGlyphIds()[text.getGlyphCount() - 1];
		final double fontSize = text.getFontStyle().getSize();
		return net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.endTrim(cp,
				net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(text.getFontMetrics(), gid,
						fontSize, text.getFontStyle().getDirection()),
				fontSize);
	}

	/** Unconditional hanging extent for hanging-punctuation: force-end. */
	private double forceEndHang(final TextImpl text) {
		if (text.getGlyphCount() <= 0) {
			return 0;
		}
		final int cp = Character.codePointBefore(text.getChars(), text.getCharCount());
		final int gid = text.getGlyphIds()[text.getGlyphCount() - 1];
		return net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.forceEndHang(cp,
				text.getFontMetrics().getAdvance(gid));
	}

	public void startTextRun(FontStyle fontStyle, FontMetrics fontMetrics) {
		assert this.text == null;
		// assert fontStyle != null;
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// **If the font was not inherited, restore it from the open run**
		// (2026-08-17). When the progress guard abandons an automatic page break,
		// a TextBuilder rebuilt midway can receive characters without receiving startTextRun.
		// These are the same values BlockBuilder.glyph uses when lazily creating TextBuilder,
		// so supplying them here does not change layout.
		// **Do not fail**: let livelock degrade layout and complete conversion
		// (ARCHITECTURE §5.13).
		if (this.fontStyle == null || this.fontMetrics == null) {
			final FontStyle openStyle = this.builder.getOpenRunFontStyle();
			final FontMetrics openMetrics = this.builder.getOpenRunFontMetrics();
			if (openStyle != null && openMetrics != null) {
				this.fontStyle = openStyle;
				this.fontMetrics = openMetrics;
			} else {
				// No restoration source either. Cannot measure the character, so discard it
				// (content in this range has already been lost by abandoning the livelock).
				return;
			}
		}
		// if (this.breakWord && this.unitAdvance > 0) {
		// if (this.firstUnit) {
		// this.locateLine();
		// this.firstUnit = false;
		// }
		// this.flush();
		// }
		// Japanese spacing compression A2/T1a: adjust the boundary with the preceding cluster: autospace gap
		// (positive) and punctuation compression (negative, same run only, as in the original font-layer kern).
		final double fontSize = this.fontStyle == null ? 0 : this.fontStyle.getSize();
		double autospaceGap = this.autospace.gapBefore(ch, coff, fontSize);
		double punctuationTrim = this.autospace.trimBefore(ch, coff, gid, this.text, this.fontMetrics, fontSize,
				this.fontStyle);
		if (!this.measuringLine && this.breakWord == AbstractTextParams.WORD_WRAP_BREAK_WORD && this.unitAdvance > 0) {
			if (this.firstUnit) {
				this.locateLine();
				this.firstUnit = false;
			}
			// Predict wrapping (formula defined by GlyphMeasureStep; preserve the existing addition order).
			double lineAxis = this.unitAdvance + this.letterSpacing + autospaceGap - punctuationTrim;
			if (this.text == null) {
				lineAxis += this.fontMetrics.getAdvance(gid);
			} else {
				lineAxis += this.text.glyphAdvance(gid);
			}
			final double maxLineAxis = this.maxLineSize - this.textIndent;
			if (LayoutUtils.compare(lineAxis, maxLineAxis) > 0) {
				this.flush();
				// If flush actually split the line, the tracker has already reset;
				// apply no adjustment to a pair across lines (recompute).
				autospaceGap = this.autospace.gapBefore(ch, coff, fontSize);
				punctuationTrim = this.autospace.trimBefore(ch, coff, gid, this.text, this.fontMetrics, fontSize,
						this.fontStyle);
			}
		}

		if (this.text == null) {
			assert this.fontStyle != null;
			assert this.fontMetrics != null;
			this.text = new TextImpl(charOffset, this.fontStyle, this.fontMetrics);
			this.text.setLetterSpacing(this.letterSpacing);
			this.textBuffer.add(this.text);
		}

		// Use the sole definition of the CSS width formula (GlyphMeasureStep). Add to line accounting
		// in the existing two steps, baseAndSpacing → adjustment, preserving floating-point order.
		final net.zamasoft.foliojet.layout.text.GlyphMeasureStep step = new net.zamasoft.foliojet.layout.text.GlyphMeasureStep(
				this.text.appendGlyph(ch, coff, clen, gid), this.letterSpacing, autospaceGap, punctuationTrim);
		final double advance = step.baseAndSpacing();
		this.unitAdvance += advance;
		this.lineAxis += advance;
		final double adjustment = step.adjustment();
		if (adjustment != 0) {
			// Embed in the current glyph's xadvance (= space before that glyph, same convention as CIDKeyedFont/
			// ruby distribute) and add to line accounting (A2/T1a).
			this.text.addXAdvance(this.text.getGlyphCount() - 1, adjustment);
			this.unitAdvance += adjustment;
			this.lineAxis += adjustment;
		}
		this.autospace.glyphAdded(this.text, fontSize, ch, coff, clen, gid);
		this.lastSpaceAdvance = 0;
		this.lineHead = false;

		if (LayoutUtils.compare(this.text.getAscent() + this.text.getDescent(), this.maxPageSize) > 0) {
			// Force wrapping when the line-height limit is exceeded.
			this.maxLineSize = 0;
		}

		if (this.text.getGlyphCount() > 10000) {
			// Prevent excessively long runs.
			this.endTextRun();
			this.startTextRun(this.fontStyle, this.fontMetrics);
		}
	}

	public void endTextRun() {
		assert this.text.getGlyphCount() > 0;
		this.text.pack();
		this.text = null;
	}

	public void control(TextControl quad) {
		assert this.text == null;
		// Japanese spacing compression A2: controls (spaces, line breaks, replaced elements, etc.) sever pairs
		// (no autospace for pairs with explicit whitespace). Zero-width inline starts/ends, however,
		// are merely boundaries and preserve pairs (Japanese/Latin boundaries across spans also receive
		// autospace, as specified).
		if (!(quad instanceof InlineQuad inlineQuad
				&& (inlineQuad.getType() == InlineQuad.INLINE_START
						|| inlineQuad.getType() == InlineQuad.INLINE_END)
				&& inlineQuad.getAdvance() == 0)) {
			this.autospace.reset();
		}
		if (quad instanceof Control) {
			// Control code.
			Control control = (Control) quad;
			switch (control.getControlChar()) {
			case SoftHyphen.CHAR:
				break;

			case '\n':
				// Line-break character.
				this.toLineFeed = true;
				break;

			case '\t':
				// Tab character. Width uses tab-size (css-text-3, 2026-08-29): a multiplier scales
				// the current font's single-space advance; a length is used directly.
				// Tab stops are integer multiples of tab width from line start (fixed at 24 pt until 2026-08-29).
				Tab tab = (Tab) control;
				tab.advance = tabAdvance(this.currentTextParams(), this.lineAxis);
				break;

			case '\u0020':
				// Space.
				if (!this.collapseSpaces) {
					break;
				}
				WhiteSpace whiteSpace = (WhiteSpace) control;
				if (this.lineHead) {
					// Collapse leading space.
					whiteSpace.collapse();
				} else {
					// Collapse trailing space.
					this.lastSpaceAdvance = whiteSpace.getAdvance();
				}
				break;

			default:
				throw new IllegalStateException();
			}
		} else if (quad instanceof net.zamasoft.foliojet.layout.text.LeaderQuad) {
			// leader() L1: participate in line-break decisions at minimum width (one pattern period).
			// Allocate widths at the start of drawLine.
			this.lineHead = false;
		} else {
			AbstractTextParams params;
			if (this.textParamStack == null || this.textParamStack.isEmpty()) {
				params = this.lineBox.getTextParams();
			} else {
				final InlineBox box = (InlineBox) this.textParamStack.get(this.textParamStack.size() - 1);
				params = box.getTextParams();
			}

			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				params = inlineStartQuad.box.getTextParams();
				this.changeTextState(params);
				if (this.textParamStack == null) {
					this.textParamStack = new ArrayList<InlineBox>();
				}
				assert this.textParamStack.isEmpty() || ((IBox) this.textParamStack.get(this.textParamStack.size() - 1))
						.getParams().element != params.element : params.element;
				this.textParamStack.add(inlineStartQuad.box);
				if (inlineStartQuad.getAdvance() != 0) {
					this.lastSpaceAdvance = 0;
				}
			}
				break;

			case InlineQuad.INLINE_END: {
				final InlineEndQuad inlineEndQuad = (InlineEndQuad) inlineQuad;
				// **INLINE_END can arrive without a start** (2026-08-17).
				// When the progress guard abandons an automatic page break, content is placed
				// with overflow in place, and a builder rebuilt midway may receive only the end
				// without seeing the matching INLINE_START. The guard's contract
				// (ARCHITECTURE §5.13) is to <b>degrade without failing conversion</b>,
				// so do not fail here. Observed: the w3c-jlreq glossary table
				// caused the entire conversion to fail with NullPointerException.
				if (this.textParamStack != null && !this.textParamStack.isEmpty()) {
					this.textParamStack.remove(this.textParamStack.size() - 1);
				}
				if (this.textParamStack == null || this.textParamStack.isEmpty()) {
					params = this.lineBox.getTextParams();
				} else {
					final InlineBox box = (InlineBox) this.textParamStack.get(this.textParamStack.size() - 1);
					params = box.getTextParams();
				}

				this.changeTextState(params);
				if (inlineEndQuad.getAdvance() != 0) {
					this.lastSpaceAdvance = 0;
				}
			}
				break;

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
				final double lineHeight = inlineQuad.getBox().getPageExtent(params.flow);
				if (LayoutUtils.compare(lineHeight, this.maxPageSize) > 0) {
					// Force wrapping when the line-height limit is exceeded.
					this.maxLineSize = 0;
				}
				this.lineHead = false;
				break;

			case InlineQuad.INLINE_ABSOLUTE:
				break;

			default:
				throw new IllegalStateException();
			}
		}
		this.unitAdvance += quad.getAdvance();
		this.lineAxis += quad.getAdvance();
		this.textBuffer.add(quad);
	}

	/**
	 * Returns true when a line break occurs.
	 *
	 * @return
	 */
	public boolean flush() {
		if (this.totalFitPlan != null) {
			// M3c: during optimized replay, break lines only at flushes selected by K-P.
			return this.plannedFlush();
		}
		this.unitAdvance = 0;
		if (this.textBuffer.isEmpty()) {
			return false;
		}
		if (this.lineAxis > 0) {
			if (this.firstUnit) {
				this.locateLine();
				this.firstUnit = false;
			}
			if (this.opportunity.elementCount() > 0) {
				double lineAxis = this.lineAxis - this.lastSpaceAdvance;
				double maxLineAxis = this.maxLineSize - this.textIndent;
				if (LayoutUtils.compare(lineAxis, maxLineAxis) > 0) {
					// JLREQ 3.8.3: if the current candidate fits after compression in priority order,
					// keep the whole buffer on this line. Otherwise, leave it unchanged and use the existing candidate.
					if (this.tryJlreqLineShrink(lineAxis - maxLineAxis)) {
						this.opportunity = this.captureOpportunity();
					}
					// Wrapping within a text block.
					final boolean ret = this.newLine(false);
					return ret;
				}
			}
		}
		if (this.toLineFeed) {
			// Line-break character.
			final boolean ret = this.newLine(true);
			this.toLineFeed = false;
			return ret;
		}
		if (!this.firstUnit && this.textBuffer.get(this.textBuffer.size() - 1) instanceof SoftHyphen) {
			// Allow a soft-hyphen break only if materializing the hyphen does not overflow the line.
			// However, allow it if the preceding portion alone already overflows (no other break point).
			final SoftHyphen softHyphen = (SoftHyphen) this.textBuffer.get(this.textBuffer.size() - 1);
			final double lineAxis = this.lineAxis - this.lastSpaceAdvance;
			final double maxLineAxis = this.maxLineSize - this.textIndent;
			if (LayoutUtils.compare(lineAxis, maxLineAxis) <= 0
					&& LayoutUtils.compare(lineAxis + softHyphen.getText().getAdvance(), maxLineAxis) > 0) {
				return false;
			}
		}
		//if (this.wrap) {
			this.opportunity = this.captureOpportunity();
		//}
		return false;
	}

	/**
	 * Flushes according to the K-P selected plan (M3c; only when {@link #totalFitPlan} is non-null). K-P has already
	 * checked overflow and soft-hyphen fit, so skip those checks and finalize the whole buffer as one line at a
	 * selected flush (consume-once prevents another break on reentry through {@code while(flush())}). The existing
	 * {@link #newLine} family handles physical generation: turning the buffer, already processed for kinsoku
	 * (line-breaking rules), into a line, materializing hyphens, regenerating inlines, and justification.
	 */
	private boolean plannedFlush() {
		this.unitAdvance = 0;
		if (this.textBuffer.isEmpty()) {
			return false;
		}
		if (!this.totalFitPlan.takeBreakAtCursor()) {
			return false;
		}
		if (this.toLineFeed) {
			// Treat an explicit line break (forced breakpoint) as a last line, as in legacy.
			final boolean ret = this.newLine(true);
			this.toLineFeed = false;
			return ret;
		}
		this.opportunity = this.captureOpportunity();
		return this.newLine(false);
	}

	void finish(final boolean fragmentBreak) {
		// End of the text block.
		// assert this.textParamStack == null || this.textParamStack.isEmpty();
		// fragmentBreak=true means the caller has confirmed that the type area is full
		// and text continues in a later fragment, not that the body text has ended.
		if (!this.drawLine(true, fragmentBreak)) {
			// A TextBuilder that only discarded unmatched INLINE_END for recovery
			// may finish without any lines.
			if (fragmentBreak) {
				this.builder.previewBidiParagraph(this.textBlockBox.getBlockParams());
			} else {
				this.builder.resolveBidiParagraph(this.textBlockBox.getBlockParams());
			}
			return;
		}
		this.lineBox.align(this.textIndent, this.minLineAxis, this.maxLineSize, true);
		// Apply text-overflow to the block's final line too (a nowrap line passes only here).
		this.applyTextOverflow(this.lineBox);
		this.addLine(this.lineBox);
		if (fragmentBreak) {
			this.builder.previewBidiParagraph(this.textBlockBox.getBlockParams());
		} else {
			this.builder.resolveBidiParagraph(this.textBlockBox.getBlockParams());
		}
	}
}
