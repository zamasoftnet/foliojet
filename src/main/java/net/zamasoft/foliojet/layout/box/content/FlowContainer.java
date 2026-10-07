package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.fragment.FlowCutter;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.Absolutes.Absolute;
import net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.Floatings.Floating;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.layout.util.DebugFlags;

public class FlowContainer implements Container {
	/**
	 * Normal-flow content.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: FlowContainer.java 1631 2022-05-15 05:43:49Z miyabe $
	 */
	protected static class Flow extends BoxHolder {
		public final IFlowBox box;
		public final double pageAxis;

		public Flow(int serial, IFlowBox box, double pageAxis) {
			super(serial);
			this.box = box;
			this.pageAxis = pageAxis;
		}

		public IBox getBox() {
			return this.box;
		}
	}

	/**
	 * The replay range of an absorbed closed subtree (C1c). Holds no box;
	 * triggers source replay in the serial merge order of the restyle traversal.
	 */
	private static class Replay extends BoxHolder {
		final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range;

		Replay(net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range) {
			super(range.serial());
			this.range = range;
		}

		public IBox getBox() {
			return null;
		}
	}

	protected AbstractContainerBox box;

	protected int serial = 0;

	/**
	 * Normal-flow content.
	 */
	protected List<Flow> flows = null;

	protected Floatings floatings = null;

	protected Absolutes absolutes = null;

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		final List<BoxHolder> children = new ArrayList<BoxHolder>();
		if (this.flows != null) {
			children.addAll(this.flows);
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				children.add(this.floatings.getFloating(i));
			}
		}
		children.sort(java.util.Comparator.comparingInt(child -> child.serial));
		for (final BoxHolder child : children) {
			action.accept(child.getBox());
		}
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				action.accept(this.absolutes.getAbsolute(i).box);
			}
		}
	}

	public FlowContainer() {
		// default
	}

	public final void setBox(AbstractContainerBox box) {
		this.box = box;
	}

	public final void addFlow(IFlowBox box, double pageAxis) {
		assert box != null;
		this.addFlow(++this.serial, box, pageAxis);
	}

	private final void addFlow(int serial, IFlowBox box, double pageAxis) {
		assert box != null;
		Flow flow = new Flow(serial, box, pageAxis);
		if (this.flows == null) {
			this.flows = new ArrayList<Flow>();
		}
		this.flows.add(flow);
		this.adopt(box);
	}

	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.addAbsolute(box, staticX, staticY, false);
	}

	@Override
	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY, boolean blockStartAnchored) {
		if (this.absolutes == null) {
			this.absolutes = new Absolutes();
		}
		this.absolutes.addAbsolute(box, staticX, staticY, blockStartAnchored);
		this.adopt(box);
	}

	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.addFloating(box, lineAxis, pageAxis, false);
	}

	/**
	 * Detaches footnotes finally attached at column ends before column balancing (container replay)
	 * (increment 6). Replay does not preserve attachments, so move them to the page host first.
	 */
	public final boolean removeFloating(final IFloatBox box) {
		return this.floatings != null && this.floatings.removeFloating(box);
	}

	/** Holds a float with a one-time transfer to the next fragment determined at placement. */
	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis, boolean moveToNext) {
		if (this.floatings == null) {
			this.floatings = new Floatings();
		}
		final Floating floating = new Floating(++this.serial, box, lineAxis, pageAxis, moveToNext);
		this.floatings.addFloating(floating);
		this.adopt(box);
	}

	/**
	 * Translates page coordinates held directly by this container by {@code dy}.
	 * Replaces normal flows with new entries preserving their serials and boxes; rebuilds floats
	 * preserving their serials, line-axis positions, and {@code moveToNext}.
	 * For absolutely positioned boxes, moves only physical static positions according to the page
	 * container's writing direction (see {@link Absolutes#shiftPageAxis(double, WritingMode, java.util.Set)}).
	 * All lists retain their order, and boxes in {@code keep} retain their original entries and coordinates.
	 *
	 * @param dy   the translation along the page axis
	 * @param keep the set of boxes to leave at their current positions
	 */
	public final void shiftPageAxis(final double dy, final java.util.Set<IBox> keep) {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow flow = this.flows.get(i);
				if (!keep.contains(flow.box)) {
					this.flows.set(i, new Flow(flow.serial, flow.box, flow.pageAxis + dy));
				}
			}
		}
		if (this.floatings != null) {
			this.floatings.shiftPageAxis(dy, keep);
		}
		if (this.absolutes != null) {
			this.absolutes.shiftPageAxis(dy, this.box.getBlockParams().flow, keep);
		}
	}

	/**
	 * Returns the maximum page-axis end of normal flows held directly by this container.
	 * Scans all entries because the last list entry is not necessarily last geometrically.
	 *
	 * @param flow the writing direction that determines this container's page axis
	 * @return 0 if there are no normal flows, otherwise their maximum end
	 */
	public final double maxNormalFlowPageEnd(final WritingMode flow) {
		double pageEnd = 0;
		if (this.flows != null) {
			for (final Flow child : this.flows) {
				pageEnd = Math.max(pageEnd, child.pageAxis + child.box.getPageExtent(flow));
			}
		}
		return pageEnd;
	}

	/**
	 * Returns the maximum page-axis end of floats registered as parallel notes in this container.
	 * Excludes normal floats and page floats.
	 *
	 * @param flow the writing direction that determines this container's page axis
	 * @return 0 if there are no placed parallel notes, otherwise their maximum end
	 */
	public final double maxPageMarginNotePageEnd(final WritingMode flow) {
		double pageEnd = 0;
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				if (floating.box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageMarginNotePos) {
					final double extent = Math.max(floating.box.getPageExtent(flow),
							floating.box.paintedPageExtent(flow));
					pageEnd = Math.max(pageEnd, floating.pageAxis + extent);
				}
			}
		}
		return pageEnd;
	}

	public boolean hasFlows() {
		return this.flows != null && !this.flows.isEmpty();
	}

	public final int getFlowCount() {
		return this.flows == null ? 0 : this.flows.size();
	}

	/**
	 * The maximum extent occupied along {@code flow}'s line axis by orthogonal descendants
	 * (boxes whose writing-mode axis differs from {@code flow}), measured from the content-area start
	 * (2026-10-05). Used to lay out shrink-to-fit boxes once and remeasure ({@code DocumentBuilder}).
	 * For orthogonal children, use border-box size plus non-auto margins (exclude auto margins,
	 * which may be resolved before sizing). For blocks in the same direction, add the frame and traverse
	 * their contents. Exclude text lines (the simulated measurement already measures them correctly).
	 */
	public final double orthogonalLineExtent(final WritingMode flow) {
		double max = 0;
		if (this.flows == null) {
			return max;
		}
		for (final Flow f : this.flows) {
			if (!(f.box instanceof AbstractContainerBox child)) {
				continue;
			}
			final AbsoluteRectFrame frame = child.getFrame();
			final net.zamasoft.foliojet.layout.box.params.Insets specified = frame.frame.margin;
			final boolean vertical = flow.isVertical();
			final boolean startAuto = (vertical ? specified.getTopType() : specified.getLeftType())
					== net.zamasoft.foliojet.layout.box.params.LengthType.AUTO;
			final boolean endAuto = (vertical ? specified.getBottomType() : specified.getRightType())
					== net.zamasoft.foliojet.layout.box.params.LengthType.AUTO;
			final double margins = (startAuto ? 0 : vertical ? frame.margin.top : frame.margin.left)
					+ (endAuto ? 0 : vertical ? frame.margin.bottom : frame.margin.right);
			final double border = frame.getBorderLineExtent(flow);
			if (child.getBlockParams().flow.isVertical() != vertical) {
				final double inner = vertical ? child.getHeight() - frame.getFrameHeight()
						: child.getWidth() - frame.getFrameWidth();
				max = Math.max(max, inner + border + margins);
			} else if (child.getContainer() instanceof FlowContainer inner) {
				final double nested = inner.orthogonalLineExtent(flow);
				if (nested > 0) {
					max = Math.max(max, nested + border + margins);
				}
			}
		}
		return max;
	}

	@Override
	public final boolean isFirstFlow(final IFlowBox box) {
		return this.flows != null && !this.flows.isEmpty() && this.flows.get(0).box == box;
	}

	/**
	 * Memoizes whether non-decorative content exists (2026-08-29).
	 *
	 * <p>
	 * Page splitting descends from parent to child one level at a time, asking this question at each level.
	 * Naively traversing the subtree makes cost quadratic in depth; a valid document 5000 levels deep
	 * took over 120 seconds per page and was misidentified as hung by the test harness's no-progress
	 * watchdog (measured: 18 seconds at depth 1000, 93% spent in this traversal).
	 * From the container where content is added, moved, or removed,
	 * {@link #invalidateNonDecorationContent()} invalidates <b>only ancestors</b>
	 * (boxes remember their container via {@code AbstractBox.getContentParent()}).
	 * Splitting at one level therefore never discards lower-level memos, giving O(1) per level.
	 * If a box cannot remember its container (an implementation other than AbstractBox),
	 * advance the global generation to err on the safe side.
	 * </p>
	 */
	private boolean nonDecorationCached;
	private boolean nonDecorationResult;
	private long nonDecorationVersion;

	/**
	 * The global generation for changes to boxes that cannot remember their container.
	 *
	 * <p>
	 * One generation covers the whole process, so multiple threads advance it during parallel conversions.
	 * {@code ++} on a {@code volatile long} has three steps: read, add, write. Concurrent increments
	 * can lose one update, leaving a memo with a matching stale generation alive
	 * (identified in the 2026-09-02 design review).
	 * Make it atomic as a prerequisite for laying out EPUB items in parallel.
	 * </p>
	 */
	private static final java.util.concurrent.atomic.AtomicLong STRUCTURE_VERSION = new java.util.concurrent.atomic.AtomicLong();

	/**
	 * How many times a text block whose first line was pushed below the cut by a fragmented float was moved to
	 * the next fragmentainer (2026-10-07, for tests).
	 */
	public static final java.util.concurrent.atomic.AtomicLong PUSHED_LINE_MOVES = new java.util.concurrent.atomic.AtomicLong();

	/** Invalidates this container's memo and its ancestors'. Stops at an already invalidated ancestor. */
	public final void invalidateNonDecorationContent() {
		FlowContainer c = this;
		while (c != null && c.nonDecorationCached) {
			c.nonDecorationCached = false;
			c = c.box == null ? null : c.box.getContentParent();
		}
	}

	/** Accepts a box as content of this container and invalidates ancestor memos. */
	private void adopt(final IBox box) {
		if (box instanceof net.zamasoft.foliojet.layout.box.AbstractBox abstractBox) {
			abstractBox.setContentParent(this);
		} else {
			STRUCTURE_VERSION.incrementAndGet();
		}
		this.invalidateNonDecorationContent();
	}

	@Override
	public final boolean hasNonDecorationContent() {
		final long version = STRUCTURE_VERSION.get();
		if (this.nonDecorationCached && this.nonDecorationVersion == version) {
			return this.nonDecorationResult;
		}
		final boolean result = this.computeNonDecorationContent();
		this.nonDecorationVersion = version;
		this.nonDecorationResult = result;
		this.nonDecorationCached = true;
		return result;
	}

	private boolean computeNonDecorationContent() {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				if (hasNonDecorationContent(this.flows.get(i).box)) {
					return true;
				}
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				if (hasNonDecorationContent(this.floatings.getFloating(i).box)) {
					return true;
				}
			}
		}
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				final IAbsoluteBox box = this.absolutes.getAbsolute(i).box;
				final net.zamasoft.foliojet.css.StructureElement element = box.getParams().element;
				final boolean generatedDecoration = element != null && element.elementKey() < 0
						&& ("before".equals(element.lName()) || "after".equals(element.lName()));
				if (!generatedDecoration && hasNonDecorationContent(box)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public final boolean hasNonDecorationContentExcludingFloatings(
			final java.util.Set<? extends IFloatBox> excluded) {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				if (hasNonDecorationContent(this.flows.get(i).box)) {
					return true;
				}
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final IFloatBox floating = this.floatings.getFloating(i).box;
				if (!excluded.contains(floating) && hasNonDecorationContent(floating)) {
					return true;
				}
			}
		}
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				final IAbsoluteBox box = this.absolutes.getAbsolute(i).box;
				final net.zamasoft.foliojet.css.StructureElement element = box.getParams().element;
				final boolean generatedDecoration = element != null && element.elementKey() < 0
						&& ("before".equals(element.lName()) || "after".equals(element.lName()));
				if (!generatedDecoration && hasNonDecorationContent(box)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasNonDecorationContent(final IBox box) {
		// An empty TextBlockBox returns paintsAnything()==true in the normal content-loss prevention check
		// because it may paint something later. However, a fragmented head with no lines
		// is definitively empty, so do not count it as actual content that consumed fixed height.
		// Inline wrappers on Yahoo! News take this form.
		if (box.getType() == BoxType.TEXT_BLOCK) {
			return LayoutUtils.compare(((TextBlockBox) box).getPageSize(), 0) > 0;
		}
		if (box.getType() != BoxType.BLOCK) {
			return box.paintsAnything();
		}
		// A block box's paintsAnything() is "visible frame || contents paint", and non-decorative
		// content implies that the contents paint. Thus
		// "paintsAnything && (frame || content within)" equals "frame || content within",
		// so there is no need to traverse the subtree twice (2026-08-29).
		final AbstractContainerBox containerBox = (AbstractContainerBox) box;
		return containerBox.getFrame().isVisible() || containerBox.getContainer().hasNonDecorationContent();
	}

	public final void migrateFlowsFrom(final int fromIndex, final Container dest, final double crossShift) {
		if (this.flows == null || fromIndex >= this.flows.size()) {
			return;
		}
		for (int i = fromIndex; i < this.flows.size(); ++i) {
			final Flow flow = this.flows.get(i);
			dest.addFlow(flow.box, flow.pageAxis - crossShift);
		}
		this.flows = fromIndex <= 0 ? null : new ArrayList<Flow>(this.flows.subList(0, fromIndex));
		this.invalidateNonDecorationContent();
	}

	public boolean hasFloatings() {
		return this.floatings != null && this.floatings.getCount() > 0;
	}

	public double getFirstAscent() {
		final Flow flow = this.getFirstFlow();
		if (flow == null) {
			return LayoutUtils.NONE;
		}

		double ascent;
		switch (flow.box.getType()) {
		case BLOCK: {
			AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			double firstAscent = containerBox.getFirstAscent();
			if (LayoutUtils.isNone(firstAscent)) {
				return firstAscent;
			}
			ascent = firstAscent;
		}
			break;

		case TEXT_BLOCK: {
			TextBlockBox textBox = (TextBlockBox) flow.box;
			double firstAscent = textBox.getFirstAscent();
			ascent = firstAscent;
		}
			break;

		case RESCUE:
		case REPLACED:
		case TABLE:
			ascent = flow.box.getHeight();
			break;
		default:
			throw new IllegalStateException(String.valueOf(flow.box.getType()));
		}

		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// Horizontal writing
			ascent += this.box.getFrame().getFrameTop();
			break;
		case WritingMode.RL:
			// Vertical writing (Mongolian)
			ascent += this.box.getFrame().getFrameLeft();
			break;
		case WritingMode.LR:
			// Vertical writing (Japanese)
			ascent += this.box.getFrame().getFrameRight();
			break;
		default:
			throw new IllegalStateException();
		}
		return ascent;
	}

	public double getLastDescent() {
		final Flow flow = this.getLastFlow();
		if (flow == null) {
			return LayoutUtils.NONE;
		}

		double descent;
		switch (flow.box.getType()) {
		case BLOCK: {
			final AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			final double lastDescent = containerBox.getLastDescent();
			if (LayoutUtils.isNone(lastDescent)) {
				return lastDescent;
			}
			descent = lastDescent;
		}
			break;

		case TEXT_BLOCK: {
			final TextBlockBox textBox = (TextBlockBox) flow.box;
			double lastDescent = textBox.getLastDescent();
			descent = lastDescent;
		}
			break;

		case RESCUE:
		case REPLACED:
		case TABLE:
			descent = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// Horizontal writing
			descent += this.box.getFrame().getFrameBottom();
			break;
		case WritingMode.RL:
			// Vertical writing (Japanese)
			descent += this.box.getFrame().getFrameLeft();
			break;
		case WritingMode.LR:
			// Vertical writing (Mongolian)
			descent += this.box.getFrame().getFrameRight();
			break;
		default:
			throw new IllegalStateException();
		}
		return descent;
	}

	public double getContentSize() {
		final Flow flow = this.getLastFlow();
		if (flow == null) {
			return 0;
		}
		return flow.pageAxis + flow.box.getPageExtent(this.box.getBlockParams().flow);
	}

	@Override
	public double getConsumedPageSizeForFragmentation() {
		final double contentSize = this.getContentSize();
		if (LayoutUtils.compare(contentSize, 0) <= 0 || this.flows == null || this.flows.isEmpty()) {
			return contentSize;
		}
		// With nested fixed-height wrappers, even if all inner-wrapper content moves to the next page,
		// the outer wrapper sees the height of the empty preceding-fragment box as contentSize.
		// This is not space actually consumed. If only a fragmented shell with no painted content
		// remains at start position 0, do not subtract it from the continuation height.
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = this.flows.get(i);
			if (LayoutUtils.compare(flow.pageAxis, 0) > 0
					|| !(flow.box instanceof net.zamasoft.foliojet.layout.box.AbstractBox abstractBox)
					|| !abstractBox.isFragmented()
					|| hasNonDecorationContent(flow.box)) {
				return contentSize;
			}
		}
		return 0;
	}

	@Override
	public double balancePageSizeFloor() {
		if (this.flows == null) {
			return 0;
		}
		// Same-axis reverse-progression children (RL⇄LR) are atomic under the pagination contract.
		// They cannot split internally at column boundaries, so column capacity must not be below their full extent.
		// Previously, the capacity search (getCutPointBelow) returned an internal boundary of a reverse-progression
		// child, and balance() fixed maxPageAxis below the child size. After rebuilding,
		// contentSize (the child's specified width) did not update the box width, leaving the parent cursor
		// narrow too. RL edge alignment then drew content outside the paper (2026-08-22,
		// sweep seeds 1871636/1106107).
		//
		// Orthogonal children (horizontal writing in vertical columns, or vertical writing in horizontal columns)
		// are also atomic, but the capacity search returns their line boundaries on a different axis as cut points.
		// Without a floor, vertical columns became narrower than their children, drawing content off-paper;
		// horizontal columns became shorter than their children, overlapping subsequent content. This also applies
		// when nested inside same-writing-direction children, so descend into them to search (2026-10-03, sweep seed
		// 11587843). Traverse with a worklist to avoid stack consumption in deep nesting.
		final WritingMode outer = this.box.getBlockParams().flow;
		double floor = 0;
		final Deque<FlowContainer> containers = new ArrayDeque<FlowContainer>();
		final Deque<Double> offsets = new ArrayDeque<Double>();
		containers.push(this);
		offsets.push(0.0);
		while (!containers.isEmpty()) {
			final FlowContainer container = containers.pop();
			final double offset = offsets.pop();
			if (container.flows == null) {
				continue;
			}
			for (int i = 0; i < container.flows.size(); ++i) {
				final Flow f = (Flow) container.flows.get(i);
				if (f.box.getType() != BoxType.BLOCK) {
					continue;
				}
				final FlowBlockBox block = (FlowBlockBox) f.box;
				if (net.zamasoft.foliojet.layout.fragment.PaginationContract.isChainAtomicBoundary(outer,
						block.getBlockParams().flow)) {
					floor = Math.max(floor, offset + f.pageAxis + f.box.getPageExtent(outer));
				} else if (block.getContainer() instanceof FlowContainer inner) {
					containers.push(inner);
					offsets.push(offset + f.pageAxis + block.getFrame().getFramePageStart(outer));
				}
			}
		}
		return floor;
	}

	public double paintedPageEnd() {
		if (this.absolutes != null) {
			// Absolutely positioned boxes may paint independently of their static positions. Since this is unpredictable,
			// assume they paint throughout the box (conservative).
			return this.box.getInnerPageExtent(this.box.getBlockParams().flow);
		}
		final WritingMode flow = this.box.getBlockParams().flow;
		double end = 0;
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				end = Math.max(end, paintedEndOf(f.pageAxis, f.box, flow));
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				end = Math.max(end, paintedEndOf(floating.pageAxis, floating.box, flow));
			}
		}
		return end;
	}

	public boolean paintsAnything() {
		if (this.absolutes != null) {
			// Absolutely positioned boxes may paint independently of their static positions. Since this is unpredictable,
			// assume they paint (conservative).
			return true;
		}
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				if (((Flow) this.flows.get(i)).box.paintsAnything()) {
					return true;
				}
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				if (this.floatings.getFloating(i).box.paintsAnything()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A child that paints nothing ({@code paintedPageExtent==0}) contributes <b>0 regardless
	 * of position</b>. Prevents a box that paints nothing but sits deep in the page
	 * from making {@link #paintedPageEnd()} nonzero.
	 */
	private static double paintedEndOf(final double pageAxis, final IBox box, final WritingMode flow) {
		final double extent = box.paintedPageExtent(flow);
		return LayoutUtils.compare(extent, 0) <= 0 ? 0 : pageAxis + extent;
	}

	public double getCutPoint(double pageAxis) {
		final WritingMode flow = this.box.getBlockParams().flow;
		if (this.hasFlows()) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				final double bottom = f.pageAxis + f.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
					if (f.box.getType() == BoxType.BLOCK) {
						final FlowBlockBox blockBox = (FlowBlockBox) f.box;
						if (blockBox.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
							pageAxis = bottom;
							break;
						}
						final AbsoluteRectFrame frame = blockBox.getFrame();
						pageAxis = f.pageAxis
								+ blockBox.getContainer()
										.getCutPoint(pageAxis - f.pageAxis - frame.getFramePageStart(flow))
								+ frame.getFramePageStart(flow) + frame.getFramePageEnd(flow);
					} else if (f.box.getType() == BoxType.TEXT_BLOCK) {
						pageAxis = f.pageAxis + ((TextBlockBox) f.box).getCutPoint(pageAxis - f.pageAxis);
					} else {
						pageAxis = bottom;
					}
					break;
				}
			}
		}
		if (this.hasFloatings()) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				final double bottom = floating.pageAxis + floating.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
					pageAxis = bottom;
					break;
				}
			}
		}
		return pageAxis;
	}

	/**
	 * Returns the maximum line-axis size of floats (M2c: for reading the used line size).
	 */
	public double floatingsLineExtent(final WritingMode flow) {
		double max = 0;
		if (this.hasFloatings()) {
			final boolean vertical = flow.isVertical();
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				max = Math.max(max, vertical ? floating.box.getHeight() : floating.box.getWidth());
			}
		}
		return max;
	}

	public double getCutPointBelow(final double pageAxis) {
		final WritingMode flow = this.box.getBlockParams().flow;
		double result = 0;
		if (this.hasFlows()) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				final double bottom = f.pageAxis + f.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) <= 0) {
					// A flow that fits entirely before the position
					result = bottom;
					continue;
				}
				// A flow spanning the proposed position: search for an internal boundary.
				if (f.box.getType() == BoxType.BLOCK) {
					final FlowBlockBox blockBox = (FlowBlockBox) f.box;
					if (blockBox.getBlockParams().pageBreakInside != PageBreakMode.AVOID) {
						final double frameStart = blockBox.getFrame().getFramePageStart(flow);
						final double inner = blockBox.getContainer()
								.getCutPointBelow(pageAxis - f.pageAxis - frameStart);
						if (LayoutUtils.compare(inner, 0) > 0) {
							result = Math.max(result, f.pageAxis + frameStart + inner);
						}
					}
					// If there is no internal boundary, cut before the block (the preceding result).
				} else if (f.box.getType() == BoxType.TEXT_BLOCK) {
					final double inner = ((TextBlockBox) f.box).getCutPointBelow(pageAxis - f.pageAxis);
					if (LayoutUtils.compare(inner, 0) > 0) {
						result = Math.max(result, f.pageAxis + inner);
					}
				}
				break;
			}
		}
		if (this.hasFloatings()) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				final double top = floating.pageAxis;
				final double bottom = top + floating.box.getPageExtent(flow);
				if (LayoutUtils.compare(top, result) < 0 && LayoutUtils.compare(bottom, result) > 0) {
					// Lower the cut position to before any float spanning it.
					result = top;
				}
			}
		}
		return result;
	}

	public void eachFlowBox(final java.util.function.Consumer<IFlowBox> consumer) {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				consumer.accept(((Flow) this.flows.get(i)).box);
			}
		}
	}

	@Override
	public void eachFloatingBox(final java.util.function.Consumer<IFloatBox> consumer) {
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				consumer.accept(this.floatings.getFloating(i).box);
			}
		}
	}

	@Override
	public void eachAbsoluteBox(final java.util.function.Consumer<IAbsoluteBox> consumer) {
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				consumer.accept(this.absolutes.getAbsolute(i).box);
			}
		}
	}

	protected Flow getFirstFlow() {
		if (this.flows == null || this.flows.isEmpty()) {
			return null;
		}
		return (Flow) this.flows.get(0);
	}

	protected Flow getLastFlow() {
		if (this.flows == null || this.flows.isEmpty()) {
			return null;
		}
		return (Flow) this.flows.get(this.flows.size() - 1);
	}

	/**
	 * One worklist frame for iterative {@code avoidBreakBefore}/{@code avoidBreakAfter}
	 * (2026-07-20, ARCHITECTURE.md invariant 6).
	 * Represents traversal of a {@code FlowContainer}'s {@code flows} from the end or start.
	 * {@code awaitingChild} means a child frame has just been pushed to descend into the internal
	 * container of the {@link FlowBlockBox} at the current {@code index}, and this frame awaits
	 * that child's resolution (popped back to this frame, meaning no true result was found).
	 */
	private static final class AvoidBreakFrame {
		final List<Flow> flows;
		int index;
		boolean awaitingChild;

		AvoidBreakFrame(List<Flow> flows, int index) {
			this.flows = flows;
			this.index = index;
		}
	}

	public boolean avoidBreakBefore() {
		return this.walkAvoidBreak(false);
	}

	public boolean avoidBreakAfter() {
		return this.walkAvoidBreak(true);
	}

	/**
	 * Implements {@code avoidBreakBefore}/{@code avoidBreakAfter}.
	 *
	 * <p>
	 * The old implementation used polymorphic mutual recursion:
	 * {@code FlowContainer.avoidBreak{Before,After}()} called
	 * {@code flow.box.avoidBreak{Before,After}()}, which, for a {@link FlowBlockBox},
	 * delegated to its internal container (normally another {@code FlowContainer}).
	 * This caused {@code StackOverflowError} in deeply nested documents
	 * (open ancestor chains crossing page breaks), confirmed on 2026-07-20 in
	 * {@code DeepNestingRestyleTest}. Before work began on iterative restyle, this separate
	 * recursion path was also found to violate invariant 6.
	 * </p>
	 *
	 * <p>
	 * This method replaces only descent into {@link FlowBlockBox} with an explicit
	 * {@link Deque} worklist (the same iterative DFS pattern as finishLayout).
	 * Other {@link IFlowBox} implementations
	 * ({@link TableBox}, {@link TextBlockBox}, {@link net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox})
	 * and other {@link Container} implementations ({@link ColumnsContainer}) are all
	 * confirmed nonrecursive leaves, so call them directly.
	 * </p>
	 */
	private boolean walkAvoidBreak(final boolean after) {
		if (this.flows == null || this.flows.isEmpty()) {
			return false;
		}
		final Deque<AvoidBreakFrame> stack = new ArrayDeque<AvoidBreakFrame>();
		stack.push(new AvoidBreakFrame(this.flows, after ? this.flows.size() - 1 : 0));
		while (!stack.isEmpty()) {
			final AvoidBreakFrame frame = stack.peek();
			if (frame.index < 0 || frame.index >= frame.flows.size()) {
				stack.pop();
				continue;
			}
			final Flow flow = frame.flows.get(frame.index);
			final IFlowBox box = flow.box;
			boolean result;
			if (frame.awaitingChild) {
				// Returned from descending into a child container. If the child had found true,
				// the method would already have returned true there, so this point
				// is reached only when the result is definitively false.
				frame.awaitingChild = false;
				result = false;
			} else if (box instanceof FlowBlockBox) {
				final FlowBlockBox flowBlockBox = (FlowBlockBox) box;
				final PageBreakMode mode = after ? flowBlockBox.getFlowPos().pageBreakAfter
						: flowBlockBox.getFlowPos().pageBreakBefore;
				if (mode == PageBreakMode.AVOID) {
					return true;
				}
				final Container inner = flowBlockBox.getContainer();
				if (inner instanceof FlowContainer) {
					final FlowContainer innerFlowContainer = (FlowContainer) inner;
					if (innerFlowContainer.flows != null && !innerFlowContainer.flows.isEmpty()) {
						// Descend into the child container. On return, the awaitingChild branch above
						// handles the continuation (height check and advancement to the next candidate).
						frame.awaitingChild = true;
						stack.push(new AvoidBreakFrame(innerFlowContainer.flows,
								after ? innerFlowContainer.flows.size() - 1 : 0));
						continue;
					}
					result = false;
				} else {
					// ColumnsContainer, etc.: leaves confirmed to be nonrecursive
					result = after ? inner.avoidBreakAfter() : inner.avoidBreakBefore();
				}
			} else {
				// TableBox/TextBlockBox/FlowReplacedBox: nonrecursive leaves
				result = after ? box.avoidBreakAfter() : box.avoidBreakBefore();
			}
			if (result) {
				return true;
			}
			if (box.getHeight() > 0) {
				frame.index = after ? -1 : frame.flows.size();
			} else {
				frame.index += after ? -1 : 1;
			}
		}
		return false;
	}

	public void pushFinishLayoutChildren(IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		if (this.box.isContextBox()) {
			containerBox = (IFramedBox) this.box;
		}
		final IFramedBox childContainerBox = containerBox;
		// To preserve traversal order (flows→floatings→absolutes, each from the start),
		// push onto the stack in reverse order (absolutes→floatings→flows, each from the end).
		if (this.absolutes != null) {
			for (int i = this.absolutes.getCount() - 1; i >= 0; --i) {
				final Absolute c = this.absolutes.getAbsolute(i);
				worklist.push(IBox.step(c.box, childContainerBox));
			}
		}
		if (this.floatings != null) {
			for (int i = this.floatings.getCount() - 1; i >= 0; --i) {
				final Floating c = this.floatings.getFloating(i);
				worklist.push(IBox.step(c.box, childContainerBox));
			}
		}
		if (this.flows != null) {
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flows.get(i);
				worklist.push(IBox.step(flow.box, childContainerBox));
			}
		}
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// Centralize logical-to-physical coordinate conversion in LayoutUtils.drawX/drawY
		// (2026-07-25, vertical-lr support; previously used handwritten RL-only formulas here).
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// Normal flows (push onto the stack in reverse order to preserve traversal order)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			final boolean rescued = isRescuedFrameOwner(c.box);
			if (!rescued && !(c.box.getType() == BoxType.BLOCK && ((FlowPos) c.box.getPos()).offset == null
					&& !c.box.getParams().isStackingContext())) {
				continue;
			}
			final double cx = LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis,
					c.pageAxis + c.box.getWidth(), 0);
			final double cy = LayoutUtils.drawY(flow, y, c.pageAxis, 0);
			if (rescued) {
				// 2026-07-25 (rescue splitting, increment 6): Block-derived fragments also have
				// their frames (backgrounds and borders) drawn in this frame pass.
				((net.zamasoft.foliojet.layout.rescue.VisualRescueBox) c.box).pushSourceFramesSteps(pageBox, drawer,
						clip, transform, cx, cy, worklist);
			} else {
				worklist.push(AbstractContainerBox.framesStep((AbstractBlockBox) c.box, pageBox, drawer, clip,
						transform, cx, cy));
			}
		}
	}

	/**
	 * Returns true for a rescue fragment whose frame (background and border)
	 * should be drawn in the frame pass (2026-07-25, increment 6).
	 *
	 * <p>
	 * Checks whether the original box's normal-flow position has {@code offset == null}
	 * (i.e., is not relatively positioned), exactly the same condition as non-rescue blocks.
	 * Fragments derived from text blocks lack {@code FlowPos}, so return false here.
	 * Fragments derived from replaced elements draw their own frames,
	 * so {@code pushSourceFramesSteps} pushes nothing for them.
	 * </p>
	 */
	private static boolean isRescuedFrameOwner(final IFlowBox box) {
		return box.getType() == BoxType.RESCUE && box.getPos() instanceof FlowPos flowPos && flowPos.offset == null;
	}

	public final void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// Centralize logical-to-physical coordinates in LayoutUtils.drawX/drawY (2026-07-25).
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// Normal flows (push onto the stack in reverse order to preserve traversal order)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.drawStep(c.box, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis, c.pageAxis + c.box.getWidth(), 0),
					LayoutUtils.drawY(flow, y, c.pageAxis, 0)));
		}
	}

	public final void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x,
			double y, Deque<TextShapeStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// Centralize logical-to-physical coordinates in LayoutUtils.drawX/drawY (2026-07-25).
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// Normal flows (push onto the stack in reverse order to preserve traversal order)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.textShapeStep(c.box, pageBox, path, transform,
					LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis, c.pageAxis + c.box.getWidth(), 0),
					LayoutUtils.drawY(flow, y, c.pageAxis, 0)));
		}
	}

	public final void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.floatings == null) {
			return;
		}
		this.floatings.pushDraw(this.box, pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
	}

	public final void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.absolutes == null) {
			return;
		}
		this.absolutes.pushDraw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				this.box.getInnerWidth(), worklist);
	}

	/**
	 * The result observed in one split attempt of the automatic page-break main loop
	 * (two-phase separation, increment 2, 2026-08-01). Replaces the old IFlowBox sentinels
	 * (null=Keep, original box identity=Move, other=Split remainder) with types.
	 * This is a Probe, not final placement: pulling (i&lt;lastOrphan) may convert Keep to Move,
	 * and pushback rewind may probe the same flow twice.
	 * Frame (chain continuation) is immediately terminal, so is not included in this type.
	 */
	private sealed interface ProbeOutcome {
		enum MoveReason {
			NORMAL,
			MONOLITHIC_AVOID,
			UNFULFILLABLE_AVOID
		}

		/** Keeps the entire box on this side (provisional). */
		record Keep() implements ProbeOutcome {
		}

		/** Sends the entire box to the next fragment. */
		record Move(MoveReason reason) implements ProbeOutcome {
		}

		/** Splits and sends the remainder to the next fragment (the mutated head stays on this side). */
		record Split(IFlowBox remainder) implements ProbeOutcome {
		}

		ProbeOutcome KEEP = new Keep();
		Move MOVE = new Move(MoveReason.NORMAL);
		Move MONOLITHIC_AVOID_MOVE = new Move(MoveReason.MONOLITHIC_AVOID);
		Move UNFULFILLABLE_AVOID_MOVE = new Move(MoveReason.UNFULFILLABLE_AVOID);
	}

	private static SplitResult splitFlow(final IPageBreakableBox box, final double pageLimit,
			final BreakMode mode, final byte flags, final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		if (plan != null && plan.columnLimit() != null && box instanceof FlowBlockBox block
				&& !(box instanceof net.zamasoft.foliojet.layout.box.RowSplitBox)) {
			return block.split(pageLimit, mode, flags, plan.withoutChain());
		}
		return box.split(pageLimit, mode, flags);
	}

	/**
	 * Page-axis splitting with a continuation plan (C1d-C). A single implementation:
	 * the old three-argument wrapper mapping to Plain was removed in increment 5;
	 * callers now unwrap Plain directly. Fragments of chain members selected by plan
	 * (always the last flow) propagate to the parent in a WithFrame return value.
	 */
	public net.zamasoft.foliojet.layout.fragment.ContainerCut splitPageAxis(double pageLimit, final BreakMode mode,
			final byte flags, net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		if (plan != null && plan.columnLimit() != null && plan.columnLimit().owner() == this.box) {
			pageLimit = plan.contentLimit(this.box, pageLimit);
			// ColumnsContainer delegates to the last column. Subtract once in that column; do not pass it to children.
			plan = plan.withColumnLimit(null);
		}
		final boolean vertical = this.box.getBlockParams().flow.isVertical();
		final double frameStart = this.box.getFrame().getFramePageStart(this.box.getBlockParams().flow);
		final double pageSize = this.box.getPageExtent(this.box.getBlockParams().flow);
		final double pageInnerSize = this.box.getInnerPageExtent(this.box.getBlockParams().flow);

		if (DebugFlags.FLOAT_TRACE) {
			final StringBuilder sb = new StringBuilder();
			if (this.flows != null) {
				for (final Object o : this.flows) {
					final Flow f = (Flow) o;
					sb.append(' ').append(f.box.getClass().getSimpleName()).append('@').append(f.pageAxis).append('+')
							.append(f.box.getPageExtent(this.box.getBlockParams().flow));
				}
			}
			System.err.println("[split-entry] el=" + (this.box.getParams() == null ? "-" : this.box.getParams().element)
					+ " pageLimit=" + pageLimit + " pageSize=" + pageSize + " inner=" + pageInnerSize + " frameStart="
					+ frameStart + " flags=" + flags + " floats=" + (this.floatings == null ? 0 : 1) + " flows=" + sb);
		}
		if (mode instanceof final ForceBreakMode force) {
			return this.splitForced(pageLimit, force, flags, plan);
		}

		final double prevPageSize = pageLimit;
		// Pre-loop decisions are pure functions in FlowCutter (M4-A2).
		//
		// Pass a size **including normal flow overflowing the box**, not just box geometry
		// (2026-10-02). Content in a box with an explicit page-axis size (width in vertical writing,
		// height in horizontal writing) continues outside the box with overflow:visible. If geometry alone
		// determines "cut line beyond inner bottom = keep on preceding page", overflowing content extends
		// beyond the paper. Normal flow under construction bypasses this check via FLAGS_LAST,
		// but a page-start float split as a closed box arrives with only FLAGS_FIRST
		// (sweep seed 11065158, OffPageFloatTest).
		//
		// Use the same overflow measure as the main loop below: computeFlowBottoms() (the larger of child geometry
		// and the child content end). Even if overflow here leads to the main loop, it will not split if the loop
		// uses a different measure. Exclude float overflow: KeepFloats handles it
		// (keep the owner and move only floats). Counting it via paintedPageEnd() broke heading break avoidance
		// in a document with floating images protruding from paragraphs (0110-clear/avoid-before-block).
		// If the box itself clips, its overflow is not painted, so exclude it
		// (splitting would expand the fragment to fill the page and reveal hidden content).
		final BlockParams params = this.box.getBlockParams();
		final boolean hasFlows = this.flows != null && !this.flows.isEmpty();
		final double[] flowBottoms = hasFlows ? this.computeFlowBottoms(params) : null;
		double overflowEnd = 0;
		if (hasFlows && !params.clipsOverflowPaint()) {
			for (final double bottom : flowBottoms) {
				overflowEnd = Math.max(overflowEnd, bottom);
			}
		}
		final FlowCutter.PreDecision pre = FlowCutter.preDecide(pageLimit, Math.max(pageSize, overflowEnd),
				Math.max(pageInnerSize, overflowEnd), frameStart, flags, hasFlows);
		// **Cannot choose "keep on this page" when the last flow is still open**
		// (2026-08-03). The last flow selected by plan is still being built
		// and remains open (a member of the continuation chain). Choosing
		// KeepFloats here (= keep the owner on this page and move only overflowing floats)
		// would remove that flow from the next page even though the document has not closed it.
		// The resumed flow stack would then be shallower than the continuation depth,
		// causing ContinuationInvariantViolationException.
		//
		// This happens when body text is exhausted but page floats or footnotes still require a page break
		// (remaining height is then 0). The second page break in
		// files/fuzz-repro/flowstack-depth-pagefloat-footnote.html exhibited this,
		// reproducing only with all four of vertical-lr + float:top + float:footnote +
		// float:left present.
		final boolean openTailSelected = plan != null && this.flows != null && !this.flows.isEmpty()
				&& plan.selects(((Flow) this.flows.get(this.flows.size() - 1)).box);
		if (openTailSelected && pre instanceof FlowCutter.PreDecision.KeepFloats(final double keepLimit)) {
			pageLimit = keepLimit;
		} else {
			if (!(pre instanceof FlowCutter.PreDecision.Proceed(final double adjustedPageLimit))) {
				return plain(switch (pre) {
				case FlowCutter.PreDecision.CutHead(final double atLimit) -> this.cutHead(atLimit, flags);
				case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this
						.splitFloatingsKeepingOwner(atLimit, flags);
				case FlowCutter.PreDecision.MoveAll moveAll -> this;
				case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this
						.splitFloatingsMovingOwner(atLimit, flags);
				case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
				case FlowCutter.PreDecision.Proceed proceed -> throw new IllegalStateException();
				});
			}
			pageLimit = adjustedPageLimit;
		}

		// Find the normal-flow box reaching the specified position.
		// (flowBottoms was computed by the pre-loop check; this point is reached only when flows exist.)
		int lastOrphan = FlowCutter.lastOrphan(flowBottoms, pageLimit);

		// Pure data for FlowCutter (measurements for avoid pushback and subsequent decisions)
		final FlowMeasurements flowMeasurements = this.measureFlows(params);
		final double[] flowPageStarts = flowMeasurements.pageStarts();
		final double[] flowPageExtents = flowMeasurements.pageExtents();
		final boolean[] avoidBefore = flowMeasurements.avoidBefore();
		final boolean[] avoidAfter = flowMeasurements.avoidAfter();
		final double[] flowPageEndFrames = flowMeasurements.pageEndFrames();
		final FloatMeasurements floatMeasurements = this.measureFloats(params);
		final double[] floatPageStarts = floatMeasurements.pageStarts();
		final double[] floatPageExtents = floatMeasurements.pageExtents();
		final boolean[] floatUncut = floatMeasurements.uncut();
		// Pass down whether a cuttable float here or in an enclosing container crosses the cut (2026-10-07)
		final byte innerFlags = FlowCutter.hasCrossingCuttableFloat(pageLimit, floatPageStarts, floatPageExtents,
				floatUncut) ? (byte) (flags | IPageBreakableBox.FLAGS_FLOAT_CROSSES) : flags;

		if (lastOrphan == this.flows.size()) {
			// When there is no flow at or beyond the cut line
			//
			// **Continue a still-open last flow even when there is no content to move**
			// (2026-08-03). The last flow selected by plan is still being built
			// and remains open (a member of the continuation chain).
			// Keeping it on the preceding page here would remove that flow from the next page
			// even though the document has not closed it. The resumed flow stack
			// would be shallower than the continuation depth,
			// causing ContinuationInvariantViolationException.
			//
			// This happens when body text is exhausted but page floats or footnotes
			// still require a page break. It occurred at
			// the second page break in files/fuzz-repro/flowstack-depth-pagefloat-footnote.html
			// with the combination vertical-lr + float:top +
			// float:footnote + float:left.
			if ((flags & IPageBreakableBox.FLAGS_LAST) == 0 && !openTailSelected) {
				if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0 || (flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
					return plain(this.cutTail(prevPageSize, flags));
				}
				final double contentHeight = flowPageStarts[this.flows.size() - 1]
						+ flowPageExtents[this.flows.size() - 1];
				if (LayoutUtils.compare(pageInnerSize, contentHeight) > 0) {
					// Split a box taller than its natural height.
					return plain(this.cutTail(prevPageSize, flags));
				}
				// Keep on the preceding page.
				return plain(this.splitFloatingsKeepingOwner(prevPageSize, flags));
			}
			lastOrphan = this.flows.size() - 1;
		}

		FlowContainer nextBox = null;
		boolean ignoreAvoid = false;
		int relaxInsideIndex = -1;
		double savePageLimit = pageLimit;
		// B5c-2 Step3 (automatic page-break main loop, retried 2026-07-22): Whether the chain member
		// selected by plan (always the last entry in this.flows) has been probed at least once.
		// Do not use the chain member's raw Keep/Move result here:
		// this impure call may be probed twice after pushback rewind,
		// and the value immediately after the switch was found to differ from final placement
		// (see the development log
		// for details). Instead, only at the final "nextBox + splitFloatings" return,
		// where the entire container's outcome is settled, use the facts established at that point
		// (whether nextBox contains only the chain member)
		// to decide whether to attach PlainWithChainStop. Limit this to cases isomorphic to force-branch:
		// partial continuations involving multiple flows cannot be expressed as a simple binary state.
		// Leave other cases as plain() and defer to the existing
		// container-identity comparison fallback.
		boolean sawChainMember = false;
		// A text block whose first line was pushed below the cut by a float that continues on the next page
		// (2026-10-07, see the TEXT_BLOCK case)
		boolean pushedBelowFloat = false;
		// Check from top to bottom.
		for (int i = lastOrphan; i < this.flows.size(); ++i) {
			Flow prevFlow = (Flow) this.flows.get(i);
			// Flag calculation is pure in FlowCutter (two-phase separation, increment 1, 2026-08-01).
			final FlowCutter.StepFlags step = FlowCutter.stepFlags(pageLimit, prevFlow.pageAxis, i, this.flows.size(),
					((AutoBreakMode) mode).box == this.box, innerFlags);
			final double splitLine = step.splitLine();
			final byte lflags = step.positionMask();
			final byte xflags = step.splitFlags();

			final boolean monolithicAvoid;
			final boolean unfulfillableAvoid;
			if (prevFlow.box.getType() == BoxType.BLOCK) {
				final BlockParams childParams = ((AbstractContainerBox) prevFlow.box).getBlockParams();
				monolithicAvoid = childParams.pageBreakInside == PageBreakMode.AVOID;
				unfulfillableAvoid = monolithicAvoid
						&& mode instanceof BreakMode.AutoBreakMode auto && auto.fragmentCapacity > 0
						&& LayoutUtils.compare(prevFlow.box.getPageExtent(this.box.getBlockParams().flow),
								auto.fragmentCapacity) > 0;
			} else {
				monolithicAvoid = false;
				unfulfillableAvoid = false;
			}
			final ProbeOutcome.Move moveOutcome = unfulfillableAvoid ? ProbeOutcome.UNFULFILLABLE_AVOID_MOVE
					: monolithicAvoid ? ProbeOutcome.MONOLITHIC_AVOID_MOVE : ProbeOutcome.MOVE;
			ProbeOutcome outcome;
			switch (prevFlow.box.getType()) {
			case TABLE:
			case TEXT_BLOCK: {
				// A line that did not fit beside a float is located below the float's end, which can lie past the
				// cut when the float spans fragmentainers (2026-10-07, fit sweep seed 11885076). At the start of a
				// fragmentainer the line cutter keeps a single line unconditionally and the rescue split below
				// treats it as one tall line, so the real line stayed off this page and only artifact copies
				// reached the following pages. When the first line starts at or after the cut and a cuttable float
				// of this or an enclosing container crosses the cut (FLAGS_FLOAT_CROSSES), move the text block on:
				// the float's head stays in this fragmentainer (progress), and the moved block is laid out again
				// against the float's continuation, so the line lands where the float actually ends.
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0 && (xflags & IPageBreakableBox.FLAGS_FLOAT_CROSSES) != 0
						&& prevFlow.box instanceof net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock) {
					final double firstLineStart = textBlock.getFirstLinePageStart();
					if (!LayoutUtils.isNone(firstLineStart) && LayoutUtils.compare(firstLineStart, 0) > 0
							&& LayoutUtils.compare(firstLineStart, splitLine) >= 0) {
						PUSHED_LINE_MOVES.incrementAndGet();
						pushedBelowFloat = true;
						outcome = ProbeOutcome.MOVE;
						break;
					}
				}
				// 2026-07-25 (rescue splitting, increment 6): An oversized line. As recommendation §1 states,
				// check its physical bottom **before** calling TextBlockBox.split().
				// LineCutter unconditionally returns KEEP when effectively only one line exists at the fragment start
				// (line splitting makes no progress), so the split result
				// cannot distinguish this non-progress case. Huge fonts, tall inline blocks,
				// inline tables, ruby units, and inline replaced elements are all
				// caught at this single point as one tall line;
				// do not create separate branches for them.
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						&& prevFlow.box instanceof net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock) {
					final double unbreakableEnd = textBlock.getUnbreakableLinePageEnd();
					if (!LayoutUtils.isNone(unbreakableEnd) && LayoutUtils.compare(splitLine, unbreakableEnd) < 0) {
						final IFlowBox rescued = this.rescueSplit(i, prevFlow, splitLine, prevPageSize);
						if (rescued != null) {
							// On success, rescueSplit has already replaced this.flows[i] with the head fragment;
							// treat the tail remainder like a Split remainder.
							outcome = new ProbeOutcome.Split(rescued);
							break;
						}
					}
				}
				IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
				outcome = switch (prevFlowBox.split(splitLine, mode, xflags)) {
				case SplitResult.Keep keep -> ProbeOutcome.KEEP;
				case SplitResult.Move move -> moveOutcome;
				case SplitResult.Split(final IPageBreakableBox remainder) -> new ProbeOutcome.Split(
						(IFlowBox) remainder);
				case SplitResult.Frame frame -> throw new IllegalStateException(
						"チェーン継続は表・テキストでは起きない");
				};
			}
				break;
			case BLOCK:
				BlockParams cParams = ((AbstractContainerBox) prevFlow.box).getBlockParams();
				// **An avoid constraint is impossible to honor if the box cannot fit even in a whole fragmentainer**
				// (2026-08-20, css-break). Moving it still requires an internal split,
				// leaving only a large blank area on the source page (measured with oversized bilingual
				// figures in w3c-jlreq: over 760 pt in a 756 pt type area).
				// Split internally in place if on the same axis; record a reason for Move on orthogonal flows.
				// If page breaks are prohibited and the box is not at the page start (§5.11), or the axes
				// differ (PaginationContract.splitsInPageAxis=false,
				// §5.10 rule 3), use the atomic REPLACED path without an internal page break.
				if ((cParams.pageBreakInside != PageBreakMode.AVOID || (xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						|| unfulfillableAvoid)
						&& net.zamasoft.foliojet.layout.fragment.PaginationContract.splitsInPageAxis(vertical,
								(AbstractContainerBox) prevFlow.box)) {
					if (plan != null && plan.selects(prevFlow.box)) {
						// C1d-C: Continue a chain member. Fragments propagate to the parent in the return value
						// as frames, not boxes
						// (chain children are always last, so no subsequent flows need transfer).
						if (i != this.flows.size() - 1) {
							throw new IllegalStateException("continuation frame child is not the open-tail flow");
						}
						sawChainMember = true;
						switch (((AbstractBlockBox) prevFlow.box).splitForContinuation(splitLine, mode, xflags,
								plan)) {
						case SplitResult.Keep keep -> outcome = ProbeOutcome.KEEP;
						case SplitResult.Move move -> outcome = moveOutcome;
						case SplitResult.Frame(
								final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f) -> {
							// With Existing, the result is always collectedNext itself
							// (a ledger is attached if anything moves), matching the old API's
							// return value (=nextBox).
							final FlowContainer collectedNext = new FlowContainer();
							this.splitFloatings(new FloatTransferTarget.Existing(collectedNext), prevPageSize, flags);
							return new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(collectedNext, f);
						}
						case SplitResult.Split(final IPageBreakableBox remainder) -> throw new IllegalStateException(
								"チェーンメンバーは Split を返さない");
						}
						break;
					}
					IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
					switch (splitFlow(prevFlowBox, splitLine, mode, xflags, plan)) {
					case SplitResult.Keep keep -> outcome = ProbeOutcome.KEEP;
					case SplitResult.Move move -> outcome = moveOutcome;
					case SplitResult.Split(final IPageBreakableBox remainder) -> outcome = new ProbeOutcome.Split(
							(IFlowBox) remainder);
					case SplitResult.Frame frame -> throw new IllegalStateException("継続化は plan の選択なしには起きない");
					}
					break;
				}
				if ((xflags & IPageBreakableBox.FLAGS_LAST) != 0) {
					// At the end, always move boxes with a page-break prohibition.
					outcome = moveOutcome;
					break;
				}
			case RESCUE:
				// 2026-07-25 (rescue splitting, increment 5): Continuation of a rescue fragment.
				// A fragment is an indivisible box representing the original box's remainder,
				// so use exactly the replaced-element decision (rescue again if first,
				// keep if it fits, otherwise move it whole to the next fragmentainer if in the middle).
			case REPLACED: {
				// Replaced box
				double prevFlowPageSize = prevFlow.box.getPageExtent(this.box.getBlockParams().flow);
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						|| LayoutUtils.compare(splitLine, prevFlowPageSize) >= 0) {
					// Keep it if at the page start or if it does not intersect the page bottom.
					if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
							&& LayoutUtils.compare(splitLine, prevFlowPageSize) < 0) {
						// 2026-07-25 (rescue splitting, increment 4/5): "Fragment start,
						// indivisible, still overflowing" is the sole non-progress point here
						// currently falling through to drawing with overflow (recommendation §1).
						// Capacity is based on the unadjusted cut line: the distance from this container's
						// start to the fragmentainer end. The container's own inner size
						// (pageInnerSize) cannot be the basis because with auto height
						// it grows with its content.
						final IFlowBox rescued = this.rescueSplit(i, prevFlow, splitLine, prevPageSize);
						if (rescued != null) {
							outcome = new ProbeOutcome.Split(rescued);
							break;
						}
					}
					outcome = ProbeOutcome.KEEP;
				} else {
					// Send to the next page.
					outcome = moveOutcome;
				}
			}
				break;
			default:
				throw new IllegalStateException(prevFlow.box.toString());
			}

			if (pushedBelowFloat) {
				nextBox = this.applyPartition(i, outcome);
				break;
			}
			if (outcome instanceof ProbeOutcome.Keep) {
				// Keep resolution rules are pure in FlowCutter (two-phase separation, increment 3).
				// TREAT_AS_MOVE, conversion to Move by pulling, is a prime example of a Probe not being final placement.
				switch (FlowCutter.resolveKeep(i, lastOrphan, xflags)) {
				case KEEP_ALL:
					return plain(null);
				case EXAMINE_NEXT:
					continue;
				case TREAT_AS_MOVE:
					outcome = ProbeOutcome.MOVE;
					break;
				}
			}
			if (outcome instanceof ProbeOutcome.Move move) {
				if (move.reason() == ProbeOutcome.MoveReason.UNFULFILLABLE_AVOID) {
					relaxInsideIndex = Math.max(relaxInsideIndex, i);
				}
				// When splitting is impossible. Resolution rules are pure in FlowCutter (two-phase separation,
				// increment 4); this code only applies the decision.
				final FlowCutter.MoveResolution resolution = FlowCutter.resolveMove(lflags, flags, i, lastOrphan,
						ignoreAvoid, relaxInsideIndex, prevPageSize, pageLimit, ((AutoBreakMode) mode).fragmentCapacity,
						flowPageStarts, flowPageExtents, avoidBefore, avoidAfter,
						flowPageEndFrames, floatPageStarts, floatPageExtents, floatUncut);
				if (DebugFlags.FLOAT_TRACE) {
					System.err.println("[move-resolution] " + resolution + " i=" + i + " lastOrphan=" + lastOrphan
							+ " pageLimit=" + pageLimit + " el=" + (this.box.getParams() == null ? "-" : this.box.getParams().element));
				}
				switch (resolution) {
				case FlowCutter.MoveResolution.Terminal(final FlowCutter.PreDecision action):
					// **A still-open last flow cannot be left behind on the preceding page**
					// (2026-08-21, sweep seed 46342 and 30 other cases). CutTail and KeepFloats
					// both decide to keep flows on this page and move only floats to the next
					// (cutTail merely transfers the float ledger to an empty nextBox
					// without moving any flows). The last flow selected by plan
					// is still under construction and open. If absent from the next page,
					// the resumed flow stack becomes shallower than the continuation depth,
					// causing ContinuationInvariantViolationException.
					// The same hole in preDecide was closed on 2026-08-03, but the main loop's
					// resolveMove→Terminal remained. If the last flow is open, move the owner
					// together with it to the next page and continue.
					if (openTailSelected && (action instanceof FlowCutter.PreDecision.CutTail
							|| action instanceof FlowCutter.PreDecision.KeepFloats)) {
						// Send the whole container to the next page (equivalent to MoveAll).
						// Cannot use splitFloatingsMovingOwner: on Remainder it returns an empty container
						// and drops the flows (measured).
						return plain(this);
					}
					return plain(switch (action) {
					case FlowCutter.PreDecision.CutHead(final double atLimit) -> this.cutHead(atLimit, flags);
					case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
					case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this
							.splitFloatingsKeepingOwner(atLimit, flags);
					case FlowCutter.PreDecision.MoveAll moveAll -> this;
					default -> throw new IllegalStateException(String.valueOf(action));
					});
				case FlowCutter.MoveResolution.RestartIgnoringAvoid(final int nextIndex):
					// Rerun ignoring break avoidance (rewind the cut line to the resume value).
					pageLimit = savePageLimit;
					i = nextIndex - 1; // Assumes the for loop's ++i.
					ignoreAvoid = true;
					continue;
				case FlowCutter.MoveResolution.RelaxInside(final int index, final int fallbackIndex): {
					// Preserve boundary avoid and geometrically split only the last monolithic box.
					// target.pageAxis includes the amount consumed by the preceding heading on the new fragmentainer,
					// so split using the space remaining after that heading,
					// not the capacity from the start.
					final Flow target = this.flows.get(index);
					final double available = savePageLimit - target.pageAxis;
					// **Do not rescue-split still-open boxes** (2026-09-16). Continuation-chain members
					// remain open as ancestors of the current break point and will receive more content.
					// If visually cut and replaced with a closed remainder, resume restores it via addRescueBound
					// as a closed box without rebuilding flowStack, causing a mismatch with the continuation's
					// open depth (invariant: flowStack depth ≠ continuation depth; the most frequent sweep defect,
					// STRICT 2,459/WILD 1,570 cases). Fall through to the existing path below in this case:
					// relax boundary avoid and split internally, creating a continuation frame.
					// For the same reason, do not rescue boxes open at the break point even if not selected by the plan
					// (2026-10-07, OpenBoxes; descent without a plan receives plan=null).
					final boolean selected = plan != null && plan.selects(target.box);
					final boolean open = !selected
							&& net.zamasoft.foliojet.layout.fragment.OpenBoxes.isOpen(target.box);
					final IFlowBox rescued = selected || open ? null
							: this.rescueSplit(index, target, available,
									((AutoBreakMode) mode).fragmentCapacity, false, true);
					if (open) {
						net.zamasoft.foliojet.layout.fragment.OpenBoxes.UNSELECTED_RESCUES_PREVENTED.incrementAndGet();
					}
					if (rescued != null) {
						nextBox = this.applyPartition(index, new ProbeOutcome.Split(rescued));
						break;
					}
					// If no useful fragment can be made, relax boundary avoid as before.
					pageLimit = savePageLimit;
					i = fallbackIndex - 1;
					ignoreAvoid = true;
					continue;
				}
				case FlowCutter.MoveResolution.Pushback(final int resumeIndex, final double newPageLimit):
					if (resumeIndex + 1 == 0 && (flags & IPageBreakableBox.FLAGS_FIRST) != 0
							&& LayoutUtils.compare(flowPageStarts[0], 0) <= 0
							&& !this.hasInFlowContentBefore(newPageLimit)) {
						// Progress (2026-09-19): If the pushback chain starts at the page start (container FIRST
						// and the first flow touches its start) and no normal-flow content candidate precedes the cut line,
						// there is no earlier page-break position. Following CSS break-before:avoid semantics,
						// ignore avoid and move this flow and those after it. Pushback would split inside the first block's
						// frame (1 pt), making its float advance by only 1 pt at a time too (strict sweep
						// seed 8471349: a float in an empty framed div followed by a ul with the UA default
						// page-break-before:avoid; a 207 pt float took 207 pages at 1 pt per page).
						// Do not count empty blocks with backgrounds as content candidates: the block remains, so the page
						// is not blank. Stop pushback of mere 1 pt fragments (0500-twopass-range/t4b-flex-middle-pushed).
						nextBox = this.applyPartition(i, outcome);
						break;
					}
					// When a page break between blocks is prohibited
					i = resumeIndex;
					pageLimit = newPageLimit;
					continue;
				case FlowCutter.MoveResolution.Partition partition:
					nextBox = this.applyPartition(i, outcome);
					break;
				}
			} else {
				nextBox = this.applyPartition(i, outcome);
			}
			break;
		}

		if (nextBox == null) {
			// Keep the block (never keep the last block). The decision is pure in FlowCutter.
			assert !((flags & IPageBreakableBox.FLAGS_LAST) != 0 && ((AutoBreakMode) mode).box != this.box);
			final double lastFlowBottom = flowPageStarts[this.flows.size() - 1]
					+ flowPageExtents[this.flows.size() - 1];
			final FlowCutter.PreDecision tailAction = FlowCutter.tailDecide(flags, lastOrphan, pageInnerSize, lastFlowBottom, prevPageSize);
			return plain(switch (tailAction) {
			case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatingsKeepingOwner(atLimit,
					flags);
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this
					.splitFloatingsMovingOwner(atLimit, flags);
			default -> throw new IllegalStateException();
			});
		}

		// Attach PlainWithChainStop only if nextBox contains exactly the chain member,
		// isomorphic to force-branch's plain whole-box move that identity cannot distinguish.
		// When multiple flows are involved (for example, pushback rewind made an intermediate sibling
		// the actual cut point), safely fall back to the existing
		// container-identity comparison.
		final boolean chainMemberAlone = sawChainMember && nextBox.flows != null && nextBox.flows.size() == 1;
		// With Existing, the result is always nextBox itself (a ledger is attached if anything moves),
		// matching the old API's return value (=nextBox).
		this.splitFloatings(new FloatTransferTarget.Existing(nextBox), prevPageSize, flags);
		final Container splitResult = nextBox;
		return chainMemberAlone
				? new net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(splitResult,
						net.zamasoft.foliojet.layout.fragment.ChainStopReason.MOVE)
				: plain(splitResult);
	}

	/**
	 * Splits for a forced page or column break (extracted from {@link #splitPageAxis} on 2026-10-05;
	 * the body was only moved). If this container does not own the break, split the last flow;
	 * if it does, aggregate only floats without moving flows.
	 */
	private net.zamasoft.foliojet.layout.fragment.ContainerCut splitForced(final double pageLimit,
			final ForceBreakMode force, final byte flags, final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		final FlowContainer nextBox = new FlowContainer();
		net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame chainFrame = null;
		net.zamasoft.foliojet.layout.fragment.ChainStopReason chainStopReason = null;
		boolean moved = false;
		final int index;
		if (this.box != force.box) {
			index = this.flows.size() - 1;
			byte lflags = (byte) 0xFF;
			if (index != 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			final Flow flow = this.flows.get(index);
			if (plan != null && plan.selects(flow.box)) {
				// C1d-C: Continue a chain member. Fragments propagate to the parent in the return value
				// as frames, not boxes.
				switch (((AbstractBlockBox) flow.box).splitForContinuation(pageLimit - flow.pageAxis, force,
						(byte) (lflags & flags), plan)) {
				case SplitResult.Frame(
						final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f) ->
					chainFrame = f;
				case SplitResult.Split(final IPageBreakableBox remainder) -> throw new IllegalStateException(
						"チェーンメンバーは Split を返さない");
				case SplitResult.Keep keep -> {
					// Continuation failed (chainFrame remains null). Keep the entire box
					// on this side; the final chainFrame==null branch naturally
					// falls back to PlainWithChainStop(nextBox).
					chainStopReason = net.zamasoft.foliojet.layout.fragment.ChainStopReason.KEEP;
				}
				case SplitResult.Move move -> {
					// Send the entire box to nextBox. As in the automatic page-break main loop
					// ({@link #applyPartition}), also remove it from this.flows;
					// otherwise, the same box remains on both the preceding and following pages.
					// (Perform removal after the splitFloatings call below,
					// which assumes the original size of this.flows.)
					nextBox.addFlow(flow.serial, flow.box, 0);
					moved = true;
					chainStopReason = net.zamasoft.foliojet.layout.fragment.ChainStopReason.MOVE;
				}
				}
			} else {
				final IPageBreakableBox flowBox = (IPageBreakableBox) flow.box;
				final SplitResult forceResult = splitFlow(flowBox, pageLimit - flow.pageAxis, force,
						(byte) (lflags & flags), plan);
				switch (forceResult) {
				case SplitResult.Split(final IPageBreakableBox remainder) -> nextBox.addFlow(flow.serial,
						(IFlowBox) remainder, 0);
				case SplitResult.Frame frame -> throw new IllegalStateException("継続化は plan の選択なしには起きない");
				case SplitResult.Keep keep -> {
					// Keep the entire box on this side (add nothing to nextBox).
				}
				case SplitResult.Move move -> {
					// As above: also remove it from this.flows (after the splitFloatings
					// call).
					nextBox.addFlow(flow.serial, flow.box, 0);
					moved = true;
				}
				}
			}
		} else {
			index = this.flows == null ? 0 : this.flows.size();
		}
		final FloatAggregate aggregate = this.aggregateFloatings(pageLimit, flags, index);
		if (moved) {
			// aggregateFloatings(pageLimit, flags, index) scans 0..index-1 assuming
			// this.flows.size()==index+1 at call time,
			// so remove after that call (removing first would
			// skew the FLAGS_LAST check).
			this.flows.remove(index);
			this.invalidateNonDecorationContent();
		}
		this.attachAggregate(nextBox, aggregate);
		assert nextBox != null;
		assert nextBox != this;
		if (chainFrame != null) {
			return new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(nextBox, chainFrame);
		}
		return chainStopReason != null
				? new net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(nextBox,
						chainStopReason)
				: plain(nextBox);
	}

	private static net.zamasoft.foliojet.layout.fragment.ContainerCut plain(final Container container) {
		return new net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain(container);
	}

	/**
	 * The sole commit point applying the main loop's conclusion as actual flow transfer
	 * (two-phase separation, increment 6, 2026-08-01).
	 *
	 * <p>
	 * Move: send subsequent flows <b>including</b> the current flow to the next fragment
	 * (structurally prevents recurrence of B3b-2: Move without removal from the source caused duplicate drawing).
	 * Split: keep the mutated head on this side and send the remainder plus subsequent flows.
	 * </p>
	 */
	private FlowContainer applyPartition(final int index, final ProbeOutcome outcome) {
		final FlowContainer nextBox = new FlowContainer();
		final int from;
		if (outcome instanceof ProbeOutcome.Split(final IFlowBox remainder)) {
			nextBox.addFlow(remainder, 0);
			from = index + 1;
		} else {
			assert outcome instanceof ProbeOutcome.Move : outcome;
			nextBox.flows = new ArrayList<Flow>();
			from = index;
		}
		for (int j = from; j < this.flows.size(); ++j) {
			nextBox.flows.add(this.flows.get(j));
			nextBox.adopt(this.flows.get(j).box);
		}
		for (int j = this.flows.size() - 1; j >= from; --j) {
			this.flows.remove(j);
		}
		this.invalidateNonDecorationContent();
		assert this.flows.size() == from : this.flows.size() + "/" + from;
		return nextBox;
	}

	/**
	 * The sole insertion point for visual rescue splitting in normal flow
	 * (added 2026-07-25). The specification and rationale for design decisions are centralized
	 * in the class documentation of {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}.
	 *
	 * <p>
	 * Called only at the <b>non-progress point</b> "fragment start, indivisible, still overflowing":
	 * the sole point currently falling through to drawing with overflow.
	 * Normal paths (fits, or fits after one deferral) never reach this method.
	 * </p>
	 *
	 * <p>
	 * Fragment transport <b>uses the existing remainder transport mechanism unchanged</b>:
	 * replace the relevant {@code this.flows} entry with the head fragment and return the tail
	 * fragment. The caller places it in the next fragmentainer's container through the same path as
	 * {@code SplitResult.Split(remainder)}. This directly implements recommendation §2:
	 * do not build all fragments in advance; create only one head and one tail at each page break.
	 * </p>
	 *
	 * @param index     the position in {@code this.flows} to replace with head
	 * @param prevFlow  the flow that reached a non-progress point
	 * @param available the available page-axis amount in this fragmentainer
	 * @param capacity  the fragmentainer's inner page-axis size (for detecting tiny fragments)
	 * @return the remainder fragment to send to the next fragmentainer, or {@code null} if no rescue applies
	 */
	private IFlowBox rescueSplit(final int index, final Flow prevFlow, final double available, final double capacity) {
		return this.rescueSplit(index, prevFlow, available, capacity, true, false);
	}

	/**
	 * Performs rescue splitting with an explicit normal fragment-start check
	 * and an exception allowed only for impossible-to-honor avoid constraints.
	 */
	private IFlowBox rescueSplit(final int index, final Flow prevFlow, final double available, final double capacity,
			final boolean atFragmentStart, final boolean relaxUnfulfillableAvoid) {
		final IFlowBox box = prevFlow.box;
		final WritingMode progression = this.box.getBlockParams().flow;
		final IFlowBox source;
		final double sourcePageExtent;
		final double offset;
		if (box instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox fragment) {
			// Continuation of an already rescued fragment. Represent the interval only with offset/sliceExtent
			// (do not create fragments of fragments).
			source = (IFlowBox) fragment.getSource();
			sourcePageExtent = fragment.getSourcePageExtent();
			offset = fragment.getOffset();
		} else {
			source = box;
			sourcePageExtent = box.getPageExtent(progression);
			offset = 0;
		}
		final net.zamasoft.foliojet.layout.rescue.RescueDecision decision = net.zamasoft.foliojet.layout.rescue.RescueStats
				.record(net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner.planInFragmentainer(
						box.getPos().getType(), atFragmentStart || relaxUnfulfillableAvoid, capacity, available,
						sourcePageExtent, offset));
		if (!(decision instanceof net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice)) {
			return null;
		}
		if (!net.zamasoft.foliojet.layout.rescue.RescuePolicy.isEnabled()) {
			// Test-only injection point (for comparison with previous behavior). Always enabled in production.
			return null;
		}
		if (!isRescueEnabled(box)) {
			return null;
		}
		if (slice.lastFragment()) {
			// The calling condition (still overflowing) excludes this case. Do not rescue, as a precaution.
			return null;
		}
		final double tailOffset = slice.nextOffset();
		final double tailExtent = sourcePageExtent - tailOffset;
		// Runtime progress check (recommendation §5: check strict increase of offset at runtime too,
		// and create no tail on failure). Duplicates the planner invariant, but the absence
		// of infinite loops is an absolute requirement, so enforce it at runtime too.
		if (!(tailOffset > offset) || !(tailExtent > 0)) {
			return null;
		}
		final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox head = new net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox(
				source, progression, sourcePageExtent, slice.offset(), slice.sliceExtent());
		final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox tail = new net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox(
				source, progression, sourcePageExtent, tailOffset, tailExtent);
		if (DebugFlags.RESCUE_PROBE) {
			System.err.println("[rescueProbe] box=" + box.getClass().getSimpleName() + " element="
					+ (box.getParams() == null ? "-" : String.valueOf(box.getParams().element)) + " offset=" + offset
					+ " sourcePageExtent=" + sourcePageExtent + " available=" + available + " capacity=" + capacity
					+ " atFragmentStart=" + atFragmentStart + " relaxUnfulfillableAvoid=" + relaxUnfulfillableAvoid
					+ " container=" + (this.box.getParams() == null ? "-" : String.valueOf(this.box.getParams().element)));
			new Throwable("[rescueProbe] call site").printStackTrace();
		}
		this.flows.set(index, new Flow(prevFlow.serial, head, prevFlow.pageAxis));
		this.adopt(head);
		net.zamasoft.foliojet.layout.rescue.RescueStats.recordEnabled();
		return tail;
	}

	/**
	 * The scope where rescue splitting is actually enabled. The specification and design rationale
	 * are centralized in the class documentation of
	 * {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}, not here.
	 *
	 * <p>
	 * <b>This is not a class enumeration.</b> By the time this check is reached,
	 * the engine has already classified the case as "fragment start, indivisible, still overflowing"
	 * (recommendation §4):
	 * </p>
	 *
	 * <ul>
	 * <li>{@code REPLACED} has no splitting entry point to begin with.</li>
	 * <li>{@code TEXT_BLOCK} reaches here only when its first line exceeds capacity,
	 * so line splitting cannot make progress (checked beforehand by the caller).</li>
	 * <li>{@code BLOCK} reaches here only when the engine <b>classifies it as atomic and lets it
	 * fall through to the REPLACED path</b>, for example because its writing direction differs
	 * from the main flow. Normal blocks in the same direction split recursively within their
	 * own containers and never reach this point.</li>
	 * <li>{@code RESCUE} is a continuation of an already rescued fragment.</li>
	 * </ul>
	 *
	 * <p>
	 * The fragmentainer type (page, column, or table cell) makes no difference.
	 * Within columns and table cells, both the decision ({@code prevPageSize} = that fragmentainer's
	 * capacity) and transport (return the remainder to the parent) use exactly the same path.
	 * </p>
	 *
	 * <p>
	 * <b>Not enabled</b>: {@code TABLE}, the path that geometrically cuts an entire table.
	 * See §4 of the {@code VisualRescuePlanner} class documentation for the reason.
	 * </p>
	 */
	private static boolean isRescueEnabled(final IFlowBox box) {
		return switch (box.getType()) {
		case REPLACED, BLOCK -> box.getPos().getType() == PosType.FLOW;
		case TEXT_BLOCK -> box.getPos().getType() == PosType.TEXT_BLOCK;
		case RESCUE -> true;
		default -> false;
		};
	}

	/**
	 * Flow measurements passed to splitPageAxis decisions (pure data for FlowCutter.avoidPushback/tailDecide).
	 */
	private record FlowMeasurements(double[] pageStarts, double[] pageExtents, boolean[] avoidBefore,
			boolean[] avoidAfter, double[] pageEndFrames) {
	}

	/**
	 * Float measurements passed to splitPageAxis decisions. All fields are null if there are no floats
	 * (preserving the old code's contract).
	 */
	private record FloatMeasurements(double[] pageStarts, double[] pageExtents, boolean[] uncut) {
	}

	/**
	 * Calculates each flow's content bottom (its reach along the page axis).
	 * Under this product's internal convention (vertical pageAxis always runs right-to-left;
	 * LR reverses at drawing time), separate RL/LR branches choose the frame edge.
	 */
	private double[] computeFlowBottoms(final BlockParams params) {
		final double[] flowBottoms = new double[this.flows.size()];
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			double lastBottom = flow.pageAxis;
			if (flow.box.getType() == BoxType.BLOCK) {
				final FlowBlockBox flowBlock = (FlowBlockBox) flow.box;
				switch (params.flow) {
				case WritingMode.TB: {
					// Horizontal writing
					lastBottom += Math.max(flowBlock.getInnerHeight(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameTop();
					break;
				}
				case WritingMode.RL: {
					// Vertical writing (Japanese)
					lastBottom += Math.max(flowBlock.getInnerWidth(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameRight();
					break;
				}
				case WritingMode.LR: {
					// Vertical writing (Mongolian)
					lastBottom += Math.max(flowBlock.getInnerWidth(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameLeft();
					break;
				}
				default:
					throw new IllegalStateException();
				}
			} else {
				lastBottom += flow.box.getPageExtent(params.flow);
			}
			flowBottoms[i] = lastBottom;
		}
		return flowBottoms;
	}

	/**
	 * Returns whether there is any <b>normal-flow content candidate</b> before the cut line
	 * (text, tables, replaced elements, grid/flex, or orthogonal-flow boxes: anything other than
	 * a FlowBlockBox with a plain, same-axis FlowContainer).
	 * Excludes backgrounds, frames, empty specified sizes, floats, and absolutely positioned boxes.
	 * Descends into same-axis nesting and checks each candidate's position; encountering a flow
	 * after the cut line does not end the search, since negative margins can bring later flows back
	 * before it. Orthogonal flows use a different coordinate system, so treat the whole box
	 * as a candidate without descending. Used to ensure progress during avoid pushback.
	 */
	private boolean hasInFlowContentBefore(final double limit) {
		if (this.flows == null) {
			return false;
		}
		final WritingMode flow = this.box.getBlockParams().flow;
		for (final Object o : this.flows) {
			final Flow f = (Flow) o;
			if (f.box instanceof FlowBlockBox block && block.getContainer() != null
					&& block.getContainer().getClass() == FlowContainer.class
					&& block.getBlockParams().flow.isVertical() == flow.isVertical()) {
				if (((FlowContainer) block.getContainer())
						.hasInFlowContentBefore(limit - f.pageAxis - block.getFrame().getFramePageStart(flow))) {
					return true;
				}
				continue;
			}
			if (LayoutUtils.compare(f.pageAxis, limit) < 0) {
				return true;
			}
		}
		return false;
	}

	private FlowMeasurements measureFlows(final BlockParams params) {
		final double[] flowPageStarts = new double[this.flows.size()];
		final double[] flowPageExtents = new double[this.flows.size()];
		final boolean[] avoidBefore = new boolean[this.flows.size()];
		final boolean[] avoidAfter = new boolean[this.flows.size()];
		final double[] flowPageEndFrames = new double[this.flows.size()];
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			flowPageStarts[i] = flow.pageAxis;
			flowPageExtents[i] = flow.box.getPageExtent(params.flow);
			avoidBefore[i] = flow.box.avoidBreakBefore();
			avoidAfter[i] = flow.box.avoidBreakAfter();
			flowPageEndFrames[i] = flow.box.getType() == BoxType.BLOCK
					? ((AbstractContainerBox) flow.box).getFrame().getFramePageEnd(params.flow)
					: 0;
		}
		return new FlowMeasurements(flowPageStarts, flowPageExtents, avoidBefore, avoidAfter, flowPageEndFrames);
	}

	private FloatMeasurements measureFloats(final BlockParams params) {
		if (this.floatings == null) {
			return new FloatMeasurements(null, null, null);
		}
		final int floatCount = this.floatings.getCount();
		final double[] floatPageStarts = new double[floatCount];
		final double[] floatPageExtents = new double[floatCount];
		final boolean[] floatUncut = new boolean[floatCount];
		for (int k = 0; k < floatCount; ++k) {
			final Floating floating = this.floatings.getFloating(k);
			floatPageStarts[k] = floating.pageAxis;
			floatPageExtents[k] = floating.box.getPageExtent(params.flow);
			// RESCUE fragments cannot split in the normal sense: they have no internal cut points
			// such as rows or row groups. Geometric rescue splitting is separate
			// from this avoid-pushback decision, so treat them
			// exactly like replaced elements (2026-07-25, increment 7).
			floatUncut[k] = floating.box.getType() == BoxType.REPLACED || floating.box.getType() == BoxType.RESCUE
					|| ((AbstractContainerBox) floating.box).getBlockParams().pageBreakInside == PageBreakMode.AVOID;
		}
		return new FloatMeasurements(floatPageStarts, floatPageExtents, floatUncut);
	}

	/**
	 * Paginates float boxes (directly held floats plus recursive aggregation from child flows)
	 * and returns a typed destination for the moved portion (2026-07-24, P2-4).
	 * Corresponds one-to-one with the authoritative branch table, the "public three-argument version"
	 * table in the development log.
	 *
	 * <table>
	 * <caption>Destination mapping</caption>
	 * <tr><td>No movement</td><td>{@link FloatTransferResult#KEEP_OWNER}
	 * (the caller continues using the target container)</td></tr>
	 * <tr><td>MoveAll and target=MOVE_OWNER</td>
	 * <td>{@link FloatTransferResult#MOVE_OWNER}</td></tr>
	 * <tr><td>MoveAll and target=KEEP and non-FIRST and innerPageExtent&lt;=0</td>
	 * <td>{@link FloatTransferResult#MOVE_OWNER} (<b>special case: move the entire empty container
	 * together with its floats</b>; the ledger stays attached to the owner)</td></tr>
	 * <tr><td>Otherwise, with movement</td><td>{@code Remainder} (attach the ledger to the target
	 * container for Existing, or to a new FlowContainer for KEEP/MOVE_OWNER)</td></tr>
	 * </table>
	 */
	public FloatTransferResult splitFloatings(final FloatTransferTarget target, final double pageLimit,
			final byte flags) {
		this.invalidateNonDecorationContent();
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 || target instanceof FloatTransferTarget.Existing;
		final int flowCount = this.flows == null ? 0 : this.flows.size();
		return switch (this.aggregateFloatings(pageLimit, flags, flowCount)) {
		case FloatAggregate.None none -> FloatTransferResult.KEEP_OWNER;
		case FloatAggregate.OwnerAll ownerAll -> {
			if (target instanceof FloatTransferTarget.MoveOwner) {
				yield FloatTransferResult.MOVE_OWNER;
			}
			if (target instanceof FloatTransferTarget.Keep && (flags & IPageBreakableBox.FLAGS_FIRST) == 0
					&& LayoutUtils.compare(this.box.getInnerPageExtent(this.box.getBlockParams().flow), 0) <= 0) {
				// Special case: move an entire empty container with its floats (branch table).
				yield FloatTransferResult.MOVE_OWNER;
			}
			final Floatings moved = this.floatings;
			this.floatings = null;
			yield remainderWith(target, moved);
		}
		case FloatAggregate.Detached(final Floatings moved) -> remainderWith(target, moved);
		};
	}

	/**
	 * Maps a typed call with target={@code KEEP} to the splitting path's existing Container
	 * contract (null=no movement / this=move the whole owner / new=remainder container) (P2-4).
	 * The consumer of this contract ({@code ContainerCut.Plain}) is outside P2's scope.
	 */
	private Container splitFloatingsKeepingOwner(final double pageLimit, final byte flags) {
		return switch (this.splitFloatings(FloatTransferTarget.KEEP, pageLimit, flags)) {
		case FloatTransferResult.KeepOwner keepOwner -> null;
		case FloatTransferResult.MoveOwner moveOwner -> this;
		case FloatTransferResult.Remainder(final FlowContainer container) -> container;
		};
	}

	/**
	 * Helper for a typed call with target={@code MOVE_OWNER}, where the whole owner moves
	 * to the next fragment (P2-4). The owner moves even if no floats move, so both KeepOwner
	 * and MoveOwner map to this (the same contract as passing {@code nextBox==this} to the old API).
	 */
	private Container splitFloatingsMovingOwner(final double pageLimit, final byte flags) {
		return switch (this.splitFloatings(FloatTransferTarget.MOVE_OWNER, pageLimit, flags)) {
		case FloatTransferResult.KeepOwner keepOwner -> this;
		case FloatTransferResult.MoveOwner moveOwner -> this;
		case FloatTransferResult.Remainder(final FlowContainer container) -> container;
		};
	}

	private static FloatTransferResult remainderWith(final FloatTransferTarget target, final Floatings moved) {
		final FlowContainer container = target instanceof FloatTransferTarget.Existing(final FlowContainer existing)
				? existing
				: new FlowContainer();
		container.floatings = moved;
		for (int i = 0; i < moved.getCount(); ++i) {
			container.adopt(moved.getFloating(i).box);
		}
		return new FloatTransferResult.Remainder(container);
	}

	public final java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags) {
		this.invalidateNonDecorationContent();
		final int flowCount = this.flows == null ? 0 : this.flows.size();
		return switch (this.aggregateFloatings(pageLimit, flags, flowCount)) {
		case FloatAggregate.None none -> java.util.Optional.empty();
		case FloatAggregate.OwnerAll ownerAll -> {
			// Detach and return this container's ledger (internal contract for child-flow recursion).
			final Floatings moved = this.floatings;
			this.floatings = null;
			yield java.util.Optional.of(moved);
		}
		case FloatAggregate.Detached(final Floatings moved) -> java.util.Optional.of(moved);
		};
	}

	/**
	 * The internal result of recursive aggregation (P2-4). Represents the local state
	 * {@code NONE/OWNER_ALL/DETACHED} from codex design §2.3 as types; not exposed externally.
	 */
	private sealed interface FloatAggregate {
		/** No floats move. */
		record None() implements FloatAggregate {
		}

		/**
		 * All directly held floats of the owner move; the ledger remains attached to the owner
		 * (deferred detach; the caller finalizes reassignment).
		 */
		record OwnerAll() implements FloatAggregate {
		}

		/**
		 * A moved ledger (the owner's own detached ledger, a direct split's remainder,
		 * or Floatings taken from a child).
		 */
		record Detached(Floatings floatings) implements FloatAggregate {
		}
	}

	private static final FloatAggregate AGGREGATE_NONE = new FloatAggregate.None();
	private static final FloatAggregate AGGREGATE_OWNER_ALL = new FloatAggregate.OwnerAll();

	/** Diagnostics: the number of held flows and directly held floats. */
	int flowCountForDebug() {
		return (this.flows == null ? 0 : this.flows.size()) * 100
				+ (this.floatings == null ? 0 : this.floatings.getCount());
	}

	/**
	 * Splits and aggregates directly held float boxes and those in child flows [0..index)
	 * (P2-4 replaced the old private three-argument sentinel state machine with types).
	 */
	private FloatAggregate aggregateFloatings(final double pageLimit, final byte flags, final int index) {
		// Final snapshot at entry (lesson from the addBound incident; codex design §2.5).
		// The old lflags LAST check read the current this.flows.size(),
		// whereas the loop bound index was a snapshot from call time.
		// this.flows does not mutate during this method (child recursion mutates only the child's own
		// container), so the entry snapshot always equals the current value,
		// making the snapshot equivalent. Caller mutation order also preserves this assumption
		// (see force-branch's comment: remove flows after the splitFloatings call).
		final int originalFlowCount = this.flows == null ? 0 : this.flows.size();
		assert index <= originalFlowCount;
		FloatAggregate state;
		if (this.floatings != null) {
			// Split directly held floats.
			state = switch (this.floatings.splitPageAxis(this.box, pageLimit, flags)) {
			case FloatSplitResult.KeepAll keepAll -> AGGREGATE_NONE;
			case FloatSplitResult.MoveAll moveAll -> AGGREGATE_OWNER_ALL;
			case FloatSplitResult.Partition(final Floatings remainder) -> new FloatAggregate.Detached(remainder);
			};
			if (this.floatings.getCount() == 0) {
				// A defensive check from the old implementation (unreachable now, because Partition
				// in plan-driven commit does not empty the source side).
				this.floatings = null;
			}
		} else {
			state = AGGREGATE_NONE;
		}
		for (int i = 0; i < index; ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			byte lflags = (byte) 0xFF;
			if (i != 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			if (i != originalFlowCount - 1) {
				lflags ^= IPageBreakableBox.FLAGS_LAST;
			}
			switch (flow.box.getType()) {
			case RESCUE:
				// 2026-07-25 (rescue splitting, increment 6): Rescue fragments hold no exclusion-area ledger
				// and do not descend into child containers. Increment 6 introduced source boxes
				// with their own containers (blocks with differing writing directions and text blocks),
				// but <b>doing nothing is correct</b>.
				//
				// Reason: A rescue fragment draws the entire original box shifted by the consumed amount
				// and clipped (recommendation §2). Floats inside the original box
				// are drawn as part of that box, only in the range visible
				// within each fragment's clip. Calling detachMovedFloatings here
				// to move them into the next fragment's ledger would
				// (a) remove floats from the original box, making them disappear in the head fragment,
				// and (b) place them at new positions in the next fragment, ignoring the original geometry.
				// This violates the core design: do not change layout calculations;
				// only translate and clip the same box.
				//
				// The amount the fragment occupies (= its exclusion-area height) is
				// sliceExtent, exactly the value returned by flow.box.getPageExtent(),
				// so no additional ledger is needed. The tail remainder
				// is placed normally in the next fragment, where the same rule
				// applies (recommendation §5).
				assert flow.box instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueBox : flow.box;
				break;
			case BLOCK:
				final AbstractContainerBox blockBox = (AbstractContainerBox) flow.box;
				double pageAxis = pageLimit - flow.pageAxis;
				pageAxis -= blockBox.getFrame().getFramePageStart(blockBox.getBlockParams().flow);
				// The child detaches and returns its own ledger (detachMovedFloatings recursion).
				final java.util.Optional<Floatings> childDetached = blockBox.getContainer()
						.detachMovedFloatings(pageAxis, (byte) (lflags & flags));
				if (childDetached.isEmpty()) {
					break;
				}
				final Floatings childFloatings = childDetached.get();
				switch (state) {
				case FloatAggregate.None none ->
					// Adopt the child's Floatings object as is (take over the whole container).
					state = new FloatAggregate.Detached(childFloatings);
				case FloatAggregate.OwnerAll ownerAll -> {
					// Detach from the owner only when adding a child float.
					final Floatings owned = this.floatings;
					this.floatings = null;
					for (int j = 0; j < childFloatings.getCount(); ++j) {
						owned.addFloating(childFloatings.getFloating(j));
					}
					state = new FloatAggregate.Detached(owned);
				}
				case FloatAggregate.Detached(final Floatings moved) -> {
					for (int j = 0; j < childFloatings.getCount(); ++j) {
						moved.addFloating(childFloatings.getFloating(j));
					}
				}
				}
				break;
			}
		}
		assert !(state instanceof FloatAggregate.Detached(final Floatings moved) && moved.getCount() == 0);
		return state;
	}

	private FlowContainer cutHead(double pageLimit, byte flags) {
		if (pageLimit < 0) {
			pageLimit = 0;
		}
		FlowContainer nextBox = new FlowContainer();
		if (this.flows != null) {
			nextBox.flows = this.flows;
			this.flows = null;
			for (int i = 0; i < nextBox.flows.size(); ++i) {
				nextBox.adopt(nextBox.flows.get(i).box);
			}
			this.invalidateNonDecorationContent();
		}
		// Flows have already been transferred to nextBox, so aggregate only directly held floats
		// (index=0; this.flows==null, so the LAST check is unaffected).
		this.attachAggregate(nextBox, this.aggregateFloatings(pageLimit, flags, 0));
		return nextBox;
	}

	private FlowContainer cutTail(double pageLimit, byte flags) {
		FlowContainer nextBox = new FlowContainer();
		int flowCount = this.flows == null ? 0 : this.flows.size();
		this.attachAggregate(nextBox, this.aggregateFloatings(pageLimit, flags, flowCount));
		return nextBox;
	}

	/**
	 * Attaches the aggregated moved ledger to {@code nextBox} (P2-4; replaces
	 * "assign nextBox.floatings, then set this.floatings=null based on identity comparison").
	 */
	private void attachAggregate(final FlowContainer nextBox, final FloatAggregate aggregate) {
		switch (aggregate) {
		case FloatAggregate.None none -> {
		}
		case FloatAggregate.OwnerAll ownerAll -> {
			nextBox.floatings = this.floatings;
			this.floatings = null;
		}
		case FloatAggregate.Detached(final Floatings moved) -> nextBox.floatings = moved;
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// Push onto the stack in reverse order to preserve traversal order.
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			// Normal flow
			Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.getTextStep(c.box, textBuff));
		}
	}

	/**
	 * Absorbs closed subtrees already processed by stampRanges from the container (C1c).
	 * Removes top-level closed plain blocks with recorded replay ranges from the flows
	 * (those that become replay-subtree during restyle: BLOCK, non-float, matching writing direction)
	 * and returns them as replay ranges with serials. resume passes these as the prefix to
	 * {@link #restyle(BlockBuilder, int, boolean, List)}, merges them with remaining items by serial,
	 * and replays them. Call after calculating the source-log watermark, since absorbed items
	 * are no longer visible to the watermark calculation that traverses the container.
	 */
	public final List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> extractReplayable(
			final java.util.Map<IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges,
			final boolean rootVertical, final int walkDepth) {
		if (this.flows == null || ranges.isEmpty()) {
			return List.of();
		}
		List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix = null;
		// When walkDepth >= 1, the last flow is an open continuation (a moved-open chain child
		// if depth>1, or open text if depth==1). Even if closed in the source log
		// (all events received), it must be pushed back onto flowStack, so do not absorb it.
		// Removing the last item would also shift the open check (lastFlow) to the preceding item.
		// This moves to recording time the condition previously protected through C1b
		// by the lastFlow check during walk taking effect before replay.
		int limit = walkDepth >= 1 ? this.flows.size() - 1 : this.flows.size();
		for (int i = 0; i < limit;) {
			final Flow flow = this.flows.get(i);
			if (flow.box.getType() != BoxType.BLOCK || flow.box.getPos().getType() == PosType.FLOAT
					|| ((AbstractContainerBox) flow.box).getBlockParams().flow.isVertical() != rootVertical) {
				// Tables, replaced elements, text, and mixed writing axes retain the existing path.
				++i;
				continue;
			}
			final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range = ranges.remove(flow.box);
			if (range == null) {
				++i;
				continue;
			}
			if (prefix == null) {
				prefix = new ArrayList<>();
			}
			prefix.add(new net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange(flow.serial, range.fromId(),
					range.toId()));
			this.flows.remove(i);
			this.invalidateNonDecorationContent();
			--limit;
		}
		if (prefix == null) {
			return List.of();
		}
		if (this.flows.isEmpty()) {
			this.flows = null;
		}
		return prefix;
	}

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes) {
		this.restyle(builder, shape, restyleAbsolutes, List.of());
	}

	/**
	 * Resumes while merging absorbed replay ranges (C1c) in serial order.
	 *
	 * <p>
	 * 2026-07-30 (legacy recursion removal, increment 4a): The worklist executor became the sole driver.
	 * Previously, an {@code isWorklistMode()} branch and the old recursive driver
	 * (for loop + {@code RECURSIVE_DESCENDER}) coexisted here; {@code RootBuilder} selected one for
	 * each continuation via the WorklistTailGate eligibility check. Increment 1 proved byte
	 * equivalence for MULTICOL native scope descent; increment 2 extended the gate to allow MULTICOL;
	 * increment 3 connected rootless COLUMN. With no remaining entry to legacy execution,
	 * the branch and driver were removed (codex consultation, design consultation).
	 * {@link #restyleItem} handles TEXT/BLOCK/TABLE/REPLACED semantics;
	 * only OpenChain descent uses the explicit stack (worklist).
	 * </p>
	 */
	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		this.restyleWorklist(builder, shape, restyleAbsolutes, prefix);
	}

	/**
	 * A worklist executor stack entry (introduced 2026-07-30, legacy recursion removal, increment 1).
	 * Previously only {@link RestyleFrame}, generalized to a sum type to represent
	 * MULTICOL native descent ({@link MulticolRestyleScope}) without recursion.
	 */
	private sealed interface WorklistStep permits RestyleFrame, MulticolRestyleScope {
	}

	/**
	 * One level of the worklist executor (added 2026-07-22, B6a1). A mutable class holding
	 * sorted {@code items}, the next index to process, and this level's {@code lastFlow},
	 * {@code shape}, and tracing {@code depth}.
	 */
	private static final class RestyleFrame implements WorklistStep {
		final List<BoxHolder> items;
		final Flow lastFlow;
		final net.zamasoft.foliojet.layout.fragment.OpenShape shape;
		final int depth;
		/**
		 * The item count at processing start (2026-07-30, increment 4a). Precisely preserves
		 * the old for loop's contract of fixing {@code int size} before the loop.
		 * Since {@code size} also reaches the next item's end-anchor check, rereading
		 * {@code items.size()} each time would change semantics for mid-processing mutations.
		 */
		final int size;
		int nextIndex = 0;

		RestyleFrame(List<BoxHolder> items, Flow lastFlow, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
				int depth) {
			this.items = items;
			this.lastFlow = lastFlow;
			this.shape = shape;
			this.depth = depth;
			this.size = items == null ? 0 : items.size();
		}
	}

	/**
	 * A scope representing the {@link ColumnsContainer#restyle} state machine without recursion
	 * (2026-07-30, increment 1). Holds the old-column snapshot after
	 * {@code ColumnsContainer.beginRestyleScope()}. The executor pushes columns one at a time
	 * as {@link RestyleFrame}s in ascending index order. Pushing LIFO while the parent frame
	 * remains paused preserves the old order: finish all MULTICOL depth-first, then return
	 * to the parent's subsequent item. Pop when all columns finish.
	 *
	 * <p>
	 * Only the last column receives the open tail ({@code inner}); earlier columns receive
	 * {@code CLOSED}, the same boundary as {@link ColumnsContainer#restyle}.
	 * Passing it to another column would lay out content inside someone else's still-open box.
	 * Do not call {@code endFlowBlock()} on the MULTICOL owner: {@code inner} is OpenChain/OpenText,
	 * never Closed, so omit it as legacy {@code FlowBlockBox.restyle()} does.
	 * </p>
	 */
	private static final class MulticolRestyleScope implements WorklistStep {
		final List<Container> snapshot;
		final net.zamasoft.foliojet.layout.fragment.OpenShape inner;
		int nextColumn = 0;

		MulticolRestyleScope(List<Container> snapshot, net.zamasoft.foliojet.layout.fragment.OpenShape inner) {
			this.snapshot = snapshot;
			this.inner = inner;
		}
	}

	/**
	 * The worklist executor that drives {@code OpenChain} with an explicit stack
	 * (added 2026-07-22, B6a1; directly implements the design in
	 * `設計相談
	 * -explicit-worklist-executor-codex.txt`).
	 * Became the <b>sole driver</b> in increment 4 on 2026-07-30; see the {@link #restyle}
	 * Javadoc for the period of coexistence with the old recursive driver.
	 * {@link #restyleItem} handles TEXT/BLOCK/TABLE/REPLACED semantics;
	 * {@link #descendWorklist} pushes {@code OpenChain} descent onto this {@code Deque}
	 * as {@link RestyleFrame} (plain flow) or {@link MulticolRestyleScope} (multi-column layout).
	 *
	 * <p>
	 * <b>Actual bug fixed on 2026-07-22</b>:
	 * {@code containerBox.restyle(builder, inner)} resolves polymorphically to the override
	 * {@code FlowBlockBox
	 * .restyle()}, not {@code
	 * AbstractContainerBox.restyle()}. It delegates to {@code this.container.restyle(...)}
	 * **after** calling {@code builder.startFlowBlock(this)}, and calls
	 * {@code builder.endFlowBlock()} only when {@code shape} is {@code Closed}
	 * (near `FlowBlockBox.java:628`). The first implementation skipped this
	 * {@code startFlowBlock} call and pushed the items of {@code containerBox
	 * .getContainer()} directly onto the deque. As a result, {@code flowStack} failed to reach
	 * the correct depth and caused `ContinuationInvariant
	 * ViolationException(flowStack.size() != continuation.depth())`
	 * (see `開発記録
	 * -bug-found-and-reverted.md`). By construction of `OpenShape.of()`,
	 * {@code inner} during {@code OpenChain} descent is always OpenChain or OpenText, never Closed.
	 * The corresponding {@code endFlowBlock} call is therefore unnecessary, as in legacy recursion.
	 * Adding only {@code startFlowBlock} to this push branch has the same effect as
	 * `FlowBlockBox.restyle()`.
	 * </p>
	 */
	private void restyleWorklist(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes, List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		final Deque<WorklistStep> stack = new ArrayDeque<>();
		this.pushWorklistFrame(stack, builder, shape, restyleAbsolutes, prefix);
		while (!stack.isEmpty()) {
			final WorklistStep step = stack.peek();
			if (step instanceof MulticolRestyleScope scope) {
				if (scope.nextColumn >= scope.snapshot.size()) {
					// All columns complete
					stack.pop();
					continue;
				}
				final int c = scope.nextColumn++;
				final FlowContainer column = (FlowContainer) scope.snapshot.get(c);
				// Only the last column receives the open tail; earlier ones receive CLOSED
				// (the same boundary as ColumnsContainer.restyle).
				final net.zamasoft.foliojet.layout.fragment.OpenShape columnShape = c == scope.snapshot.size() - 1
						? scope.inner
						: net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED;
				column.pushWorklistFrame(stack, builder, columnShape, false, List.of());
				continue;
			}
			final RestyleFrame frame = (RestyleFrame) step;
			if (frame.items == null || frame.nextIndex >= frame.size) {
				stack.pop();
				continue;
			}
			final int i = frame.nextIndex++;
			// restyleItem() does not reference any instance state of this
			// (it uses only parameters such as items/lastFlow/shape),
			// so the same call works regardless of which FlowContainer produced the frame.
			// The receiver can remain fixed as this.
			this.restyleItem(builder, frame.items, i, frame.size, frame.lastFlow, frame.shape, frame.depth,
					stack);
		}
	}

	private void pushWorklistFrame(Deque<WorklistStep> stack, BlockBuilder builder,
			net.zamasoft.foliojet.layout.fragment.OpenShape shape, boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		final CollectedItems collected = this.collectItems(builder, restyleAbsolutes, prefix);
		if (collected.items() != null) {
			Collections.sort(collected.items());
			moveOpenChainTailLast(collected.items(), collected.lastFlow(), shape);
			stack.push(new RestyleFrame(collected.items(), collected.lastFlow(), shape, shape.depth()));
		}
	}

	/**
	 * <b>Always process a box descended into while still open last</b> (added 2026-07-27).
	 *
	 * <p>
	 * {@link #collectItems} merges floats and flows into one list, which the caller sorts by serial.
	 * However, floats taken from children by {@code aggregateFloatings} retain <b>the child's serials</b>,
	 * mixing parent and child numbering, so {@code lastFlow} is not guaranteed to come last.
	 * </p>
	 *
	 * <p>
	 * When {@code lastFlow} is an open tail, {@code FlowBlockBox.restyle}
	 * <b>intentionally omits {@code endFlowBlock()}</b>, so any remaining items after it are laid out
	 * <b>inside someone else's still-open box</b>. Flows are reanchored by box identity and are unaffected,
	 * but <b>floats are positionally anchored to the currently open flow</b>
	 * ({@code BlockBuilder.commitFloatPlacement}). Wrong order alone therefore moves them into
	 * a different subtree. If {@code balance()} discards that subtree during source replay,
	 * the content silently disappears.
	 * </p>
	 *
	 * <p>
	 * <b>{@code OpenText} is excluded.</b> It is deferred in the same way as {@code toAddFloating}
	 * in live construction, so reordering is unnecessary; measurements showed that reordering
	 * failed {@code FloatPagebreakTest} and {@code ImageAfterAvoidTest}.
	 * </p>
	 *
	 * <p>
	 * Measured on 2026-07-27, discovered in a 200,000-document sweep (1 in 50,000 documents):
	 * all content of a float inside multi-column layout disappeared.
	 * </p>
	 */
	private static void moveOpenChainTailLast(final List<BoxHolder> items, final Flow lastFlow,
			final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (lastFlow == null || !(shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenChain)) {
			return;
		}
		// BoxHolder does not override equals, so indexOf compares identity.
		final int at = items.indexOf(lastFlow);
		if (at < 0 || at == items.size() - 1) {
			return;
		}
		items.remove(at);
		items.add(lastFlow);
	}

	/**
	 * The result of {@link #collectItems} (2026-07-22, B6a1 preparation).
	 * The raw, unsorted merge result; the caller sorts it.
	 */
	private record CollectedItems(List<BoxHolder> items, Flow lastFlow) {
	}

	/**
	 * Merges floatings, absolutes (if enabled), flows, and prefix into one {@code items} list.
	 * Extracted from {@code restyle()} on 2026-07-22 for B6a1 preparation: purely a function
	 * extraction with no behavior changes. Preserves the side effects of consuming and nulling
	 * {@code this
	 * .floatings}/{@code this.absolutes}/{@code this.flows}.
	 * The worklist executor (`restyleWorklist`) calls the same method when descending into
	 * a child {@code FlowContainer}, avoiding duplicate merge logic.
	 */
	private CollectedItems collectItems(BlockBuilder builder, boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		// Floats are anchored to their nearest block ancestor's container, so internal floats move
		// with a moved subtree and are not duplicated during source replay
		// (golden: float-in-moved). For subtrees with absolutely positioned boxes,
		// stampRanges' containsAbsolute gate (containsOpaque via Opaque recording before E-6 increment 4e)
		// correctly falls back for the whole subtree;
		// no per-hierarchy gate is needed.
		List<BoxHolder> items = null;
		if (this.floatings != null) {
			Floatings floatings = this.floatings;
			this.floatings = null;
			int size = floatings.getCount();
			if (size > 0) {
				if (items == null) {
					items = new ArrayList<BoxHolder>();
				}
				for (int i = 0; i < size; ++i) {
					items.add(floatings.getFloating(i));
				}
			}
		}

		if (restyleAbsolutes && this.absolutes != null) {
			Absolutes absolutes = this.absolutes;
			this.absolutes = null;
			int size = absolutes.getCount();
			for (int i = 0; i < size; ++i) {
				builder.addBound(absolutes.getAbsolute(i).box);
			}
		}

		Flow lastFlow = null;
		if (this.flows != null) {
			List<Flow> flows = this.flows;
			this.flows = null;
			this.invalidateNonDecorationContent();
			int size = flows.size();
			if (size > 0) {
				if (items == null) {
					items = new ArrayList<BoxHolder>();
				}
				for (int i = 0; i < size; ++i) {
					items.add(flows.get(i));
				}
				lastFlow = (Flow) flows.get(size - 1);
			}
		}

		if (!prefix.isEmpty()) {
			// C1c: Include absorbed closed subtrees in the serial-order merge.
			if (items == null) {
				items = new ArrayList<BoxHolder>();
			}
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range : prefix) {
				items.add(new Replay(range));
			}
		}
		return new CollectedItems(items, lastFlow);
	}

	/**
	 * Box/container class pairs already warned about for the compatibility fallback
	 * ({@link #descendWorklist}; 2026-07-30, increment 4b).
	 * Prevents repeated logs for the same pair; emits WARNING only on the first occurrence.
	 */
	private static final java.util.Set<String> WARNED_FALLBACK_PAIRS = java.util.concurrent.ConcurrentHashMap
			.newKeySet();

	/**
	 * Descends one level into {@code OpenChain} descendants (2026-07-30, increment 4b).
	 * Collapsed the {@code ChainDescender} interface and two lambda implementations into
	 * a concrete helper, since the worklist driver became the sole driver and the replacement
	 * point no longer served a purpose.
	 *
	 * <ul>
	 * <li>Plain flow ({@link FlowContainer} child): explicitly reproduces startFlowBlock,
	 * which legacy recursion ({@code FlowBlockBox.restyle()}) calls implicitly, and pushes a frame.
	 * Actual bug fixed on 2026-07-22: skipping it leaves {@code flowStack} too shallow and violates the invariant.</li>
	 * <li>MULTICOL ({@link ColumnsContainer} child): native scope descent (increment 1).
	 * startFlowBlock → beginRestyleScope → push scope.
	 * Do not call endFlowBlock, since inner is never Closed.</li>
	 * <li>Unknown combination: <b>compatibility fallback</b>. Increment a counter, warn on the first
	 * occurrence, and preserve the polymorphic semantics of {@code containerBox.restyle(builder, inner)}.
	 * Do not fail closed with an exception, because eliminating crashes is an absolute requirement.
	 * The proof that non-PLAIN/MULTICOL tails are structurally impossible is not strong enough
	 * for all entry points (codex consultation consult-codex-2026-07-30-increment4-removal-spec.txt §3).
	 * A future unknown FlowBlockBox subtype is classified as MULTICOL, but there is no type guarantee
	 * that its container is ColumnsContainer. Reentered restyle() unconditionally uses the worklist,
	 * so the old driver does not return.</li>
	 * </ul>
	 */
	private static void descendWorklist(Deque<WorklistStep> stack, BlockBuilder builder,
			net.zamasoft.foliojet.layout.box.AbstractContainerBox containerBox,
			net.zamasoft.foliojet.layout.fragment.OpenShape inner) {
		final Container childContainer = containerBox.getContainer();
		if (childContainer instanceof FlowContainer childFc && containerBox instanceof FlowBlockBox flowBox) {
			builder.startFlowBlock(flowBox);
			childFc.pushWorklistFrame(stack, builder, inner, false, List.of());
		} else if (childContainer instanceof ColumnsContainer columns && containerBox instanceof FlowBlockBox flowBox) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordMulticolNativeDescent();
			builder.startFlowBlock(flowBox);
			stack.push(new MulticolRestyleScope(columns.beginRestyleScope(), inner));
		} else {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordWorklistCompatFallback();
			final String pair = containerBox.getClass().getName() + "/"
					+ (childContainer == null ? "null" : childContainer.getClass().getName());
			if (WARNED_FALLBACK_PAIRS.add(pair)) {
				java.util.logging.Logger.getLogger(FlowContainer.class.getName())
						.warning("OpenChain descent fell back to polymorphic restyle for unknown box/container pair "
								+ pair + "; the worklist executor cannot represent this container as a frame "
								+ "(expected FlowContainer or ColumnsContainer under FlowBlockBox)");
			}
			containerBox.restyle(builder, inner);
		}
	}

	/**
	 * Shared dispatch for one entry in sorted {@code items} (2026-07-22).
	 * Extracted from the {@code restyle()} for-loop body for B6a1 preparation: purely a function
	 * extraction with no behavior changes (old {@code continue} statements were mechanically
	 * replaced with {@code return}). Prepares for the future worklist executor
	 * (see `開発記録`) to call this shared dispatch unchanged instead of duplicating it.
	 * Meets the codex design consultation requirement to avoid duplicate TEXT/BLOCK/TABLE/REPLACED
	 * semantics (rejected proposal: copy the entire switch into the new executor).
	 * {@link #descendWorklist} pushes {@code OpenChain} descent onto the explicit {@code stack}
	 * (increment 4b removed the replaceable descender mechanism; worklist is the sole driver).
	 */
	private void restyleItem(BlockBuilder builder, List<BoxHolder> items, int i, int size, Flow lastFlow,
			net.zamasoft.foliojet.layout.fragment.OpenShape shape, int depth, Deque<WorklistStep> stack) {
		// The two nested {} blocks below intentionally preserve the pre-extraction indentation
		// (two if/for levels), avoiding errors from reindenting many lines.
		{
			{
				BoxHolder holder = (BoxHolder) items.get(i);
				if (DebugFlags.RESUME_DETAIL) {
					// Provenance of each resumed item (2026-09-17, diagnostics). ResumeTrace wording is fixed by goldens,
					// so leave it unchanged and emit this to stderr under a separate switch.
					final IBox b = holder instanceof Replay ? null : holder.getBox();
					System.err.println("[resumeDetail] depth=" + depth + " i=" + i + "/" + size + " serial=" + holder.serial
							+ (holder instanceof Replay r ? " REPLAY range=" + r.range
									: " " + b.getType() + " " + b.getClass().getSimpleName() + " element="
											+ (b.getParams() == null ? "-" : String.valueOf(b.getParams().element)))
							+ " last=" + (lastFlow == holder) + " shape=" + shape + " builder="
							+ builder.getClass().getSimpleName() + " container="
							+ (this.box == null || this.box.getParams() == null ? "-"
									: String.valueOf(this.box.getParams().element)));
				}
				if (holder instanceof Replay replay) {
					// C1c: Replay the source of an absorbed closed subtree (unconditional, since replay eligibility
					// was established at the break point; op is unchanged).
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-subtree",
							"serial=" + holder.serial);
					builder.getPageContext().replaySubtree(replay.range, builder);
					return;
				}
				switch (holder.getBox().getType()) {
				case TEXT_BLOCK: {
					// Text block box
					final TextBlockBox textBlock = (TextBlockBox) holder.getBox();
					final boolean open = lastFlow == holder
							&& shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenText;
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth,
							open ? "restyle-text-open" : "restyle-text", "serial=" + holder.serial);
					if (open) {
						// M3b Phase 1: Via slice transport (record→replay inside restyle).
						// Measurements for typed TextTail in Phase 2/3.
						net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenTextHandoff();
					}
					textBlock.restyle(builder);
					if (!open) {
						builder.endTextBlock();
					}
				}
					break;
				case BLOCK: {
					if (holder.getBox().getPos().getType() != PosType.FLOAT) {
						AbstractContainerBox containerBox = (AbstractContainerBox) holder.getBox();
						if (containerBox.getBlockParams().flow.isVertical() != builder.getRootBox().getBlockParams().flow.isVertical()) {
							// When writing directions differ
							builder.addBound(containerBox);
						} else {
							// Block box
							// Anonymous box
							// Table caption
							if (lastFlow == holder
									&& shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenChain(
											final net.zamasoft.foliojet.layout.fragment.OpenShape inner)) {
								// Still-open ancestor chain
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-chain",
										"serial=" + holder.serial);
								net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordChainFiring();
								descendWorklist(stack, builder, containerBox, inner);
							} else if (!((builder instanceof net.zamasoft.foliojet.layout.builder.impl.RootBuilder
									|| builder instanceof net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder)
									&& builder.getPageContext() != null
									&& builder.getPageContext().replayFromSource(containerBox, builder))) {
								// Replay wholly moved closed subtrees from source (M6b segment-restyle).
								// If false, fall back to box replay.
								// The lastFlow && OpenText tail is also a closed box (the next level is Closed).
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-box",
										"serial=" + holder.serial);
								containerBox.restyle(builder,
										net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
							} else {
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-subtree",
										"serial=" + holder.serial);
							}
						}
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
					}
				}
					break;

				case TABLE: {
					// Table
					TableBox tableBox = (TableBox) holder.getBox();
					// Table set T-c (2026-07-30, user-approved update to the G-1 decision):
					// Rebuild tables stamped at the break point (stampRanges TABLE root range)
					// by source replay. Like replayFromSource in the BLOCK branch,
					// fail closed: no range, missing range data, or a non-Root/Column context
					// uses box-restyle=addBound as before. This is the table-recipe consumer
					// missing in G-1; the TABLE_REPLAYS counter detects
					// that replay actually occurs.
					if ((builder instanceof net.zamasoft.foliojet.layout.builder.impl.RootBuilder
							|| builder instanceof net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder)
							&& builder.getPageContext() != null
							&& builder.getPageContext().replayFromSource(tableBox, builder)) {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-table",
								"serial=" + holder.serial);
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-table",
								"serial=" + holder.serial);
						builder.addBound(tableBox);
					}
				}
					break;
				case RESCUE: {
					// 2026-07-25 (rescue splitting, increment 5): Remainder of a rescue fragment.
					// Dispatch explicitly to its dedicated entry point instead of impersonating another BoxType
					// (recommendation §5: passing it to normal addBound() fails on a Params cast).
					if (holder.getBox().getPos().getType() == PosType.FLOAT) {
						// Increment 7: Rerun normal float placement for float remainders.
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float-rescue",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
						break;
					}
					final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox rescueBox = (net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox) holder
							.getBox();
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-rescue",
							"serial=" + holder.serial);
					builder.addRescueBound(rescueBox);
					break;
				}
				case REPLACED: {
					// Replaced box
					AbstractReplacedBox replacedBox = (AbstractReplacedBox) holder.getBox();
					if (replacedBox.getPos().getType() != PosType.FLOAT) {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-replaced",
								"serial=" + holder.serial);
						builder.addBound(replacedBox);
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float-replaced",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
					}
					break;
				}
				default:
					throw new IllegalStateException(holder.getBox().toString());
				}
			}
		}
	}

	public String toString() {
		return super.toString() + "/flowCount=" + (this.flows == null ? 0 : this.flows.size());
	}
}
