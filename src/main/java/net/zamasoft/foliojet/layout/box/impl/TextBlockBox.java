package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.TextShapeSink;
import net.zamasoft.foliojet.layout.fragment.LineCutter;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

import net.zamasoft.foliojet.layout.box.content.BreakToken;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.css.impl.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TextBlockPos;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BuilderGlyphHandler;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;

/**
 * A box that can contain only text.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TextBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TextBlockBox extends AbstractBox implements IPageBreakableBox, IFlowBox {
	/**
	 * Outlines the box's outer edges with a light purple border.
	 */

	/**
	 * A placed line.
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: TextBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
	 */
	protected static class Line {
		public final AbstractLineBox box;
		public final double pageAxis;

		public Line(AbstractLineBox line, double pageAxis) {
			this.box = line;
			this.pageAxis = pageAxis;
		}

		public double getPageEnd() {
			return this.pageAxis + this.box.getAscent() + this.box.getDescent();
		}

		public String toString() {
			return this.box.toString();
		}
	}

	protected final BlockParams params;

	/**
	 * List of lines in the text block.
	 */
	protected final List<Line> lines = new ArrayList<Line>();

	protected double lineSize = 0;

	/**
	 * Continuation state of this text block.
	 */
	protected final BreakToken breakToken;

	public TextBlockBox(final BlockParams params, final BreakToken breakToken) {
		this.params = params;
		this.breakToken = breakToken;
	}

	public final BoxType getType() {
		return BoxType.TEXT_BLOCK;
	}

	/**
	 * Returns this text block's continuation token (M6b).
	 */
	public final BreakToken getBreakToken() {
		return this.breakToken;
	}

	public final Params getParams() {
		return this.params;
	}

	public final BlockParams getBlockParams() {
		return this.params;
	}

	public final Pos getPos() {
		return TextBlockPos.POS;
	}

	public final double getFirstAscent() {
		double ascent = 0;
		if (this.lines != null && !this.lines.isEmpty()) {
			Line line = (Line) this.lines.get(0);
			ascent += line.box.getAscent();
		}
		return ascent;
	}

	public final double getLastDescent() {
		double descent = 0;
		if (this.lines != null && !this.lines.isEmpty()) {
			final Line line = (Line) this.lines.get(this.lines.size() - 1);
			descent += line.box.getDescent();
		}
		return descent;
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		// A split-remainder carrier, or a block that discarded only an unmatched INLINE_END
		// during recovery, has no lines yet or no longer has them. Its geometric size is 0;
		// leave the conservative painting check to PAINTS_UNKNOWN in paintedPageExtent().
		if (this.lines.isEmpty()) {
			return 0;
		}
		Line line = (Line) this.lines.get(this.lines.size() - 1);
		return line.getPageEnd();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>A text block with no lines is "unmeasurable"</b>: the tail fragment of a split paragraph is
	 * waiting for content not yet replayed from source, so it must not be declared to paint nothing.
	 * Doing so makes blank-page suppression wrongly conclude that the entire fragment can be discarded,
	 * <b>losing content</b>. Returns {@link net.zamasoft.foliojet.layout.util.LayoutUtils#PAINTS_UNKNOWN}
	 * to always choose the conservative result (= there is content to paint). {@link #getPageSize()},
	 * which returns the geometric size, returns 0 when empty; painting presence is handled separately.
	 * </p>
	 */
	@Override
	public double paintedPageExtent(final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		if (this.lines.isEmpty()) {
			return LayoutUtils.PAINTS_UNKNOWN;
		}
		return this.getPageExtent(flow);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Text is painted <b>only within lines</b>. If the page-direction height occupied by lines is 0,
	 * there is nowhere to place glyph bounds (ink) or underlines, so nothing is painted
	 * ({@link #getPageSize()} is the end of the last line, i.e., the sum of all line heights).
	 * </p>
	 *
	 * <p>
	 * <b>With no lines, this also answers "paints"</b>, because {@link #paintedPageExtent} returns
	 * {@link LayoutUtils#PAINTS_UNKNOWN}. The tail fragment of a split paragraph is a container that
	 * will receive content through source replay; discarding it as empty <b>loses content</b>
	 * (see that method's Javadoc).
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		return LayoutUtils.compare(this.paintedPageExtent(this.params.flow), 0) > 0;
	}

	public final double getWidth() {
		if (this.params.flow.isVertical()) {
			// Vertical writing
			return this.getPageSize();
		} else {
			// Horizontal writing
			return this.lineSize;
		}
	}

	public final double getHeight() {
		if (this.params.flow.isVertical()) {
			// Vertical writing
			return this.lineSize;
		} else {
			// Horizontal writing
			return this.getPageSize();
		}
	}

	public final double getInnerWidth() {
		return this.getWidth();
	}

	public final double getInnerHeight() {
		return this.getHeight();
	}

	/**
	 * Enumerates line boxes (read-only; exposed for footnote F4 call traversal).
	 *
	 * @param action operation to apply to each line
	 */
	public final void forEachLine(final java.util.function.Consumer<net.zamasoft.foliojet.layout.box.AbstractLineBox> action) {
		for (int i = 0; i < this.lines.size(); ++i) {
			action.accept(this.lines.get(i).box);
		}
	}

	public final void addLine(AbstractLineBox lineBox, double pageAxis) {
		// Adding a line may change the answer to "has content" (see the note in FlowContainer).
		if (this.getContentParent() != null) {
			this.getContentParent().invalidateNonDecorationContent();
		}
		assert !LayoutUtils.isNone(pageAxis);
		this.lines.add(new Line(lineBox, pageAxis));
		// This extension has little meaning outside IE compatibility mode.
		// (T2/H1: Exclude line-end compression/hanging from logical width, i.e., use the effective width.)
		this.lineSize = Math.max(lineBox.getLineSize() - lineBox.getEndHangAdvance(), this.lineSize);
	}

	/**
	 * Whether this block contains only an outside marker emitted separately just before a table.
	 */
	public final boolean overlaysFollowingBlock() {
		return this.lines.size() == 1 && this.lines.get(0).box.containsOnlyOverlayOutsideMarker();
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
	}

	public final void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// Push in reverse order (last line first) to preserve the original traversal order (first line first).
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			worklist.push(IBox.step(line.box, containerBox));
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// Push in reverse order (last line first) to preserve the original traversal order (first line first).
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			worklist.push(IBox.getTextStep(line.box, textBuff));
		}
	}

	/**
	 * Returns the physical bottom edge of the sole line when no progress is possible at line boundaries
	 * (= no line-splitting cut point exists). Returns {@link LayoutUtils#NONE} if progress is possible
	 * (added 2026-07-25, rescue splitting increment 6; design consultation §1).
	 *
	 * <p>
	 * Rescue splitting of an oversized line checks this value <b>before calling</b>
	 * {@link #split(double, byte)}. At the fragment start ({@code FLAGS_FIRST}), {@link LineCutter}
	 * <b>unconditionally</b> returns {@code KEEP} if there is effectively only one line, so it is drawn
	 * with overflow even when capacity is exceeded. Thus, the cut result cannot distinguish this lack
	 * of progress. Huge fonts, tall inline blocks, inline tables, ruby units, and inline replaced elements
	 * all converge here as one tall line (without separate branches).
	 * </p>
	 *
	 * <p>
	 * <b>Do not apply rescue splitting when there are multiple lines</b>. Line splitting actually makes
	 * progress (keeping the first line and sending the rest to the next fragment), so this is not a
	 * no-progress point. Geometrically cutting the entire paragraph here clearly degrades output:
	 * every page shows a band of every line (observed with {@code files/unittest/2010-LIMIT/line.html}).
	 * A paragraph whose first line alone is extremely tall still overflows as before.
	 * </p>
	 *
	 * @return the bottom edge of the sole line if no progress is possible; {@code NONE} otherwise
	 */
	public final double getUnbreakableLinePageEnd() {
		if (this.lines.isEmpty()) {
			return LayoutUtils.NONE;
		}
		final double[] lineStarts = new double[this.lines.size()];
		final double[] lineEnds = new double[this.lines.size()];
		this.measureLines(lineStarts, lineEnds);
		if (!LineCutter.singleEffectiveLine(lineStarts, lineEnds)) {
			return LayoutUtils.NONE;
		}
		return lineEnds[0];
	}

	/**
	 * Returns where the first line starts, from the top of this box ({@code NONE} without lines). It is above
	 * zero only when the line was pushed down past floats while it was located.
	 */
	public final double getFirstLinePageStart() {
		if (this.lines.isEmpty()) {
			return LayoutUtils.NONE;
		}
		return ((Line) this.lines.get(0)).pageAxis;
	}

	/** Collects each line's top and bottom edges (distances from this box's top edge). */
	private void measureLines(final double[] lineStarts, final double[] lineEnds) {
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			lineStarts[i] = line.pageAxis;
			lineEnds[i] = line.getPageEnd();
		}
	}

	public final double getCutPoint(double pageAxis) {
		if (this.lines.isEmpty()) {
			return pageAxis;
		}
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			final double bottom = line.pageAxis + line.box.getPageExtent(this.getBlockParams().flow);
			if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
				pageAxis = bottom;
				break;
			}
		}

		return pageAxis;
	}

	/**
	 * Returns the line boundary immediately before the proposed position (M5-B). Rounds down rather
	 * than up as getCutPoint does; returns 0 if no line boundary precedes the proposed position.
	 *
	 * @param pageAxis proposed position
	 * @return the immediately preceding line boundary (0 if none)
	 */
	public final double getCutPointBelow(final double pageAxis) {
		double result = 0;
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			final double bottom = line.pageAxis + line.box.getPageExtent(this.getBlockParams().flow);
			if (LayoutUtils.compare(bottom, pageAxis) > 0) {
				break;
			}
			result = bottom;
		}
		return result;
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);
		visitor.visitBox(transform, this, drawer, x, y);

		// Push in reverse order (last line first) to preserve the original traversal order (first line first).
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			// Draw (logical → physical conversion is centralized in LayoutUtils.drawX/drawY).
			worklist.push(IBox.drawStep(lineBox, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(this.params.flow, x, this.getPageSize(), line.pageAxis, line.getPageEnd(), 0),
					LayoutUtils.drawY(this.params.flow, y, line.pageAxis, 0)));
		}
	}

	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist) {
		// Push in reverse order (last line first) to preserve the original traversal order (first line first).
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			worklist.push(IBox.textShapeStep(lineBox, pageBox, sink, transform,
					LayoutUtils.drawX(this.params.flow, x, this.getPageSize(), line.pageAxis, line.getPageEnd(), 0),
					LayoutUtils.drawY(this.params.flow, y, line.pageAxis, 0)));
		}
	}

	/**
	 * Cuts in the page direction at a line boundary (the typed protocol of pillar 2c).
	 * {@link LineCutter} decides the cut; for Split, mutates this box to retain only the lines
	 * for the previous page.
	 *
	 * @param pageLimit distance from the box's outer edge to the cut line
	 * @param flags     bitwise OR of IPageBreakableBox.FLAGS_*
	 * @return the cut result
	 */
	public final SplitResult split(final double pageLimit, final byte flags) {
		assert (!this.lines.isEmpty());
		// FLAGS_LAST applies to actual elements, not virtual text blocks.

		final double pageSize = this.getPageExtent(this.params.flow);
		final double[] lineStarts = new double[this.lines.size()];
		final double[] lineEnds = new double[this.lines.size()];
		this.measureLines(lineStarts, lineEnds);
		final LineCutter.Decision decision = LineCutter.decide(pageLimit, pageSize, this.params.lineHeight,
				this.params.orphans, this.params.widows, (flags & IPageBreakableBox.FLAGS_FIRST) != 0,
				(flags & IPageBreakableBox.FLAGS_AVOID_PROBE) != 0, lineStarts, lineEnds);
		switch (decision) {
		case LineCutter.Decision.Keep keep:
			return SplitResult.KEEP;
		case LineCutter.Decision.Move move:
			return SplitResult.MOVE;
		case LineCutter.Decision.CutAfter(final int lastLine): {
			// Move the cut line and subsequent lines (widows) to the next page's fragment.
			final int firstWidow = lastLine + 1;
			final double top = ((Line) this.lines.get(firstWidow)).pageAxis;
			// Resume position = the trailing character end of the preceding fragment (through the cut line) (M6b v3).
			// The remainder's initial firstCharOffset may shift due to rounding when Text is split
			// during line breaking, so derive it from the retained portion's end.
			final int resumeOffset = ((Line) this.lines.get(lastLine)).box.lastCharEnd();
			final BreakToken token = ((Line) this.lines.get(lastLine)).box.isLast()
					? new BreakToken.MidFlow(resumeOffset)
					: new BreakToken.MidLine(resumeOffset);
			final TextBlockBox nextTextBlock = new TextBlockBox(this.params, token);
			for (int i = firstWidow; i < this.lines.size(); ++i) {
				final Line line = (Line) this.lines.get(i);
				nextTextBlock.addLine(line.box, line.pageAxis - top);
			}
			while (this.lines.size() > firstWidow) {
				this.lines.remove(this.lines.size() - 1);
			}
			assert !this.lines.isEmpty();
			assert !nextTextBlock.lines.isEmpty();
			// M3b Phase 2/3a: Finalize handoff content at the break. Capture the remainder's
			// normalized event sequence here and discard the lines. The carrier transports
			// only slice+breakToken (live cutting, immutable transport,
			// reconstruction on resume; grok decision in docs/consult-p3-resplit-grok.txt.
			// No path reads the remainder's lines or dimensions before resume).
			final AbstractLineBox firstLine = ((Line) this.lines.get(0)).box;
			final List<AbstractLineBox> bidiPrefixLines = new ArrayList<>();
			for (int i = 0; i <= lastLine; ++i) {
				bidiPrefixLines.add(((Line) this.lines.get(i)).box);
			}
			final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix bidiPrefix =
					firstLine.getBidiReplayPrefix().append(bidiPrefixLines);
			nextTextBlock.slice = nextTextBlock.recordSlice(bidiPrefix);
			nextTextBlock.lines.clear();
			return new SplitResult.Split(nextTextBlock);
		}
		}
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert !(mode instanceof BreakMode.ForceBreakMode);
		return this.split(pageLimit, flags);
	}

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		if (this.lines != null) {
			for (final Line line : this.lines) {
				action.accept(line.box);
			}
		}
	}

	/**
	 * Normalized event sequence of the remainder captured at the break (M3b Phase 2).
	 * Non-null only for split fragments (carriers).
	 */
	private net.zamasoft.foliojet.layout.fragment.TextReplaySlice slice;

	public final void restyle(final BlockBuilder builder) {
		// An empty block without a slice was finalized by recovering from and discarding
		// only an unmatched INLINE_END. It has neither source nor lines to replay.
		// Split remainders have a slice even with no lines, so they are not absorbed here.
		if (this.slice == null && this.lines.isEmpty()) {
			return;
		}
		assert this.slice != null || !this.lines.isEmpty();
		builder.setBreakToken(this.breakToken);
		// M3b Phase 1/2: The carrier is a slice. Split fragments were captured at the break;
		// capture all others (all restyle paths) here. Capture → replay produces
		// structurally identical call sequences, so behavior is unchanged.
		final net.zamasoft.foliojet.layout.fragment.TextReplaySlice slice = this.slice != null ? this.slice
				: this.recordSlice();
		slice.replay(new BuilderGlyphHandler(builder));
	}

	/**
	 * Captures the normalized event sequence of the remaining lines (M3b Phase 1 / C3).
	 * Captured at the output of the WordHyphenator equivalent (unitizer), this is the same sequence
	 * that restyle delivers to BuilderGlyphHandler.
	 */
	private net.zamasoft.foliojet.layout.fragment.TextReplaySlice recordSlice() {
		final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix bidiPrefix = this.lines.isEmpty()
				? net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix.EMPTY
				: ((Line) this.lines.get(0)).box.getBidiReplayPrefix();
		return this.recordSlice(bidiPrefix);
	}

	private net.zamasoft.foliojet.layout.fragment.TextReplaySlice recordSlice(
			final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix bidiPrefix) {
		return net.zamasoft.foliojet.layout.fragment.TextReplaySlice.record(gh -> {
			final FilterGlyphHandler textUnitizer = new CSSJTextUnitizer(this.params);
			textUnitizer.setGlyphHandler(gh);
			for (int i = 0; i < this.lines.size(); ++i) {
				final Line line = (Line) this.lines.get(i);
				line.box.restyle(textUnitizer, i == 0);
			}
			textUnitizer.close();
		}, bidiPrefix);
	}

	public final boolean avoidBreakAfter() {
		return false;
	}

	public final boolean avoidBreakBefore() {
		return false;
	}

	public String toString() {
		return super.toString() + "/lineCount=" + this.lines.size();
	}
}
