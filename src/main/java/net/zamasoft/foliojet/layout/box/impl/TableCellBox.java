package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.CellAlign;

import net.zamasoft.foliojet.layout.box.params.EmptyCellsMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;

import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * Table cell implementation.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCellBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TableCellBox extends AbstractContainerBox {

	protected final BlockParams params;
	private Drawer pendingDrawer = null;

	protected final TableCellPos pos;

	protected double verticalAlign = 0, pageSize = 0;

	protected boolean collapse;

	protected boolean forceDraw;

	public TableCellBox(final BlockParams params, final TableCellPos pos, final Container container) {
		this(params, pos, params.size, params.minSize, new AbsoluteRectFrame(params.frame), container);
	}

	public TableCellBox(final BlockParams params, final TableCellPos pos, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, Container container) {
		super(size, minSize, container);
		this.params = params;
		this.pos = pos;
		this.frame = frame;
	}

	public final BoxType getType() {
		return BoxType.TABLE_CELL;
	}

	public final Params getParams() {
		return this.params;
	}

	public final BlockParams getBlockParams() {
		return this.params;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableCellPos getTableCellPos() {
		return this.pos;
	}

	public final boolean isSpecifiedPageSize() {
		return false;
	}

	public void setPageAxis(double newSize) {
		if (newSize > this.pageSize) {
			this.pageSize = newSize;
		}
		super.setPageAxis(newSize);
	}

	public final void setWidth(double width) {
		assert !LayoutUtils.isNone(width);
		this.width = width - this.frame.getFrameWidth();
	}

	public final void setHeight(double height) {
		assert !LayoutUtils.isNone(height);
		this.height = height - this.frame.getFrameHeight();
	}

	public final void verticalAlign() {
		switch (this.pos.verticalAlign) {
		case CellAlign.START:
		case CellAlign.BASELINE:
			// Top alignment / baseline
			return;
		}
		double pageSize;
		if (this.params.flow.isVertical()) {
			pageSize = this.width;
		} else {
			pageSize = this.height;
		}
		double diff = Math.max(0, pageSize - this.pageSize);
		switch (this.pos.verticalAlign) {
		case CellAlign.END:
			// Bottom alignment
			this.verticalAlign = diff;
			break;
		case CellAlign.MIDDLE:
			// Center alignment
			this.verticalAlign = diff / 2.0;
			break;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Alignment offset along the logical block axis applied to cell content. An explicit
	 * {@code align-content} takes precedence over {@code vertical-align} from HTML/CSS2.
	 * The normal value preserves the existing cell alignment.
	 */
	private double contentAlignmentOffset() {
		return this.params.blockAlignContent == net.zamasoft.foliojet.layout.box.params.BoxAlignment.NORMAL
				? this.verticalAlign
				: this.blockContentAlignmentOffset();
	}

	@Override
	protected double blockAlignedX(final double x) {
		return this.params.flow.isVertical()
				? x + LayoutUtils.pageAxisSign(this.params.flow) * this.contentAlignmentOffset()
				: x;
	}

	@Override
	protected double blockAlignedY(final double y) {
		return this.params.flow.isVertical() ? y : y + this.contentAlignmentOffset();
	}

	/**
	 * Creates a scratch replica for table Pass B (row measurement) (E-6 increment 5b-1, 2026-07-24:
	 * a component of the measurement primitive in codex design §4.4, "replay the cell range at the
	 * final column width, obtain only dimensions, and discard"). Returns a fresh cell with the same
	 * initial layout state as this cell after prepareLayout and column-width application
	 * (setWidth/setHeight): frame, min/max page-direction size, collapsed-border flag, and inner sizes
	 * along both axes. Copies the frame defensively so replica layout does not touch this cell's state.
	 * Multi-column cells (non-FlowContainer) are unsupported and return null
	 * (the caller treats them as ineligible for Pass B).
	 *
	 * @return the replica cell, or null for a multi-column cell
	 */
	public final TableCellBox newMeasureReplica() {
		if (!this.canMeasureReplica()) {
			// Container replication for multi-column cells is unsupported (ineligible for Pass B).
			return null;
		}
		final AbsoluteRectFrame frameCopy = new AbsoluteRectFrame(this.frame.frame);
		frameCopy.margin = new AbsoluteInsets(this.frame.margin.top, this.frame.margin.right, this.frame.margin.bottom,
				this.frame.margin.left);
		frameCopy.padding.set(this.frame.padding);
		final TableCellBox replica = new TableCellBox(this.params, this.pos, this.size, this.minSize, frameCopy,
				new net.zamasoft.foliojet.layout.box.content.FlowContainer());
		replica.collapse = this.collapse;
		replica.minPageAxis = this.minPageAxis;
		replica.maxPageAxis = this.maxPageAxis;
		replica.width = this.width;
		replica.height = this.height;
		return replica;
	}

	/**
	 * Checks whether {@link #newMeasureReplica()} can create a replica (i.e., this is not a multi-column
	 * cell) without creating one (E-6 increment 5b-2, 2026-07-24). Table Pass C's per-table eligibility
	 * check, {@code RetainedTableBuilder.isRowSequentialBindEligible}, uses this in its pre-bind scan.
	 */
	public final boolean canMeasureReplica() {
		return this.container instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer;
	}

	public final void prepareLayout(double lineSize, TableBox tableBox, AbsoluteInsets spacing) {
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		this.frame.margin = spacing;

		TableParams tableParams = tableBox.getTableParams();
		this.collapse = tableParams.borderCollapse == TableParams.BORDER_COLLAPSE;
		RectFrame frame = this.frame.frame;
		if (this.collapse) {
			this.frame.frame = RectFrame.create(frame.margin, RectBorder.NONE_RECT_BORDER, frame.background,
					frame.padding);
		}

		if (this.params.flow.isVertical()) {
			switch (this.minSize.getWidthType()) {
			case ABSOLUTE:
				this.minPageAxis = this.minSize.getWidth();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.minPageAxis = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (params.maxSize.getWidthType()) {
			case ABSOLUTE:
				this.maxPageAxis = this.params.maxSize.getWidth();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.maxPageAxis = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			this.width = this.minPageAxis;
		} else {
			switch (this.minSize.getHeightType()) {
			case ABSOLUTE:
				this.minPageAxis = this.minSize.getHeight();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.minPageAxis = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (params.maxSize.getHeightType()) {
			case ABSOLUTE:
				this.maxPageAxis = this.params.maxSize.getHeight();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.maxPageAxis = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			this.height = this.minPageAxis;
		}
	}

	public final void baseline(double rowAscent) {
		if (this.pos.verticalAlign != CellAlign.BASELINE) {
			return;
		}
		double firstAscent = this.getFirstAscent();
		if (LayoutUtils.isNone(firstAscent)) {
			return;
		}
		double xascent = rowAscent - firstAscent;
		if (xascent > 0) {
			this.verticalAlign += xascent;
			if (this.params.flow.isVertical()) {
				this.width += xascent;
			} else {
				this.height += xascent;
			}
		}
	}

	public final boolean isContextBox() {
		return this.getTableCellPos().offset != null;
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, java.util.Deque<FramesStep> worklist) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.params.isStackingContext()) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
			this.pendingDrawer = newDrawer;
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);
		drawer.adoptTransform(this.params, transform);

		if (this.draw()) {
			Drawable drawable = new TableCellBoxDrawable(clip, pageBox, this.params.opacity, transform,
					this.frame.frame.background, this.frame.frame.border, this.frame.frame.padding, this.collapse, this.frame.margin,
					this.getWidth(), this.getHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);
		this.container.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0 || this.isContextBox() || !this.container.hasFloatings()) {
			return;
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		if (this.params.overflow.clipsPaint()) {
			// Clipping
			clip = this.clip(clip, x, y);
		}
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);
		// Like IBox.draw, floats is a self-contained entry point, so create a worklist
		// and drain it completely before returning (2026-07-20).
		final java.util.Deque<DrawStep> worklist = new java.util.ArrayDeque<>();
		this.container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		if (this.isContextBox()) {
			this.frames(pageBox, drawer, clip, transform, x, y);
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			if (this.pendingDrawer != null) {
				drawer = this.pendingDrawer;
				this.pendingDrawer = null;
			} else {
				final Drawer newDrawer = new Drawer(this.params, transform);
				drawer.visitDrawer(newDrawer);
				drawer = newDrawer;
			}
		}
		drawer.adoptTransform(this.params, transform);
		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);

		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		final boolean contextBox = this.isContextBox();
		if (contextBox) {
			contextX = x;
			contextY = y;
		}
		final Drawer fdrawer = drawer;
		final double fx = x, fy = y, fcontextX = contextX, fcontextY = contextY;
		final Shape flowsClip = clip;
		final Shape absolutesClip = contextBox ? clip : null;
		// To preserve the original execution order (floatings[contextBox only] → flows → absolutes → endStruct),
		// push onto the stack in reverse order.
		worklist.push(w -> pageBox.endStruct(fdrawer, this.params.element, structCount, fx, fy));
		this.container.pushDrawAbsolutes(pageBox, drawer, visitor, absolutesClip, transform, fcontextX, fcontextY, x,
				y, worklist);
		this.container.pushDrawFlows(pageBox, drawer, visitor, flowsClip, transform, fcontextX, fcontextY, x, y,
				worklist);
		if (contextBox) {
			this.container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, fcontextX, fcontextY, x, y,
					worklist);
		}
	}

	private final boolean draw() {
		if (!this.frame.isVisible()) {
			return false;
		}
		if (this.pos.emptyCells == EmptyCellsMode.SHOW) {
			return true;
		}
		if (this.collapse) {
			return true;
		}
		if (this.container.hasFlows()) {
			return true;
		}
		if (this.container.hasFloatings()) {
			return true;
		}
		if (this.forceDraw) {
			return true;
		}
		return false;
	}

	protected static class TableCellBoxDrawable extends BackgroundBorderDrawable {
		protected final boolean collapse;
		protected final AbsoluteInsets spacing;

		public TableCellBoxDrawable(Shape clip, PageBox pageBox, float opacity, AffineTransform transform,
				Background background, RectBorder border, Insets padding, boolean collapse, AbsoluteInsets spacing, double width,
				double height) {
			super(pageBox, clip, opacity, transform, background, border, padding, width, height);
			this.collapse = collapse;
			this.spacing = spacing;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			if (this.collapse) {
				this.background.draw(gc, x, y, this.width, this.height, this.border, this.padding, null);// TODO text clip
			} else {
				x += this.spacing.left;
				y += this.spacing.top;
				double width = this.width - this.spacing.getFrameWidth();
				double height = this.height - this.spacing.getFrameHeight();
				if (width >= 0 || height >= 0) {
					this.background.draw(gc, x, y, width, height, this.border, this.padding, null);// TODO text clip
					this.border.draw(gc, x, y, width, height);
				}
			}
		}
	}

	protected final AbstractContainerBox splitPage(Container container, double pageLimit, boolean columnSpanning) {
		final boolean vertical = this.params.flow.isVertical();
		// Fragment-state calculation extracted into pure logic in TableCutter (C4-T2).
		final net.zamasoft.foliojet.layout.fragment.TableCutter.CellFragmentState state = net.zamasoft.foliojet.layout.fragment.TableCutter
				.cellFragmentState(vertical, this.size, this.minSize, this.frame,
						vertical ? this.width : this.height, pageLimit);

		// A split fragment is a continuation (no anchor; not replayed as a fresh box. P0).
		final TableCellBox cell = new TableCellBox(this.params, this.pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
		cell.collapse = this.collapse;
		cell.forceDraw = this.draw();
		this.frame = state.prevFrame();
		if (vertical) {
			cell.height = this.height;
			this.width = pageLimit;
		} else {
			cell.width = this.width;
			this.height = pageLimit;
		}
		this.forceDraw = this.draw();
		return cell;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// A-3b physical alignment contract: The cut position passed to cell content is
		// "the row's physical split line - verticalAlign (the content-start offset computed
		// from the difference between the measured final cell height and content height)".
		// Continuation cells start with verticalAlign=0: the preceding fragment has already
		// consumed the original cell's leading space, and the remaining content is not realigned
		// (documented 2026-07-24; see the development record).
		// However, alignment space is meaningful only when the entire cell fits in one fragment.
		// When rowspan or a tall adjacent cell makes the final cell height much larger than the content,
		// **the space alone can exceed the cut line**, leaving no content unit
		// in the preceding fragment. Only borders remain on the previous page, with text on the next:
		// this reverses reading order (2026-07-27, detected by invariant 7; for seed 130,
		// row 2 split into [ ][ ][T12] and [T10][T11][ ]).
		// Therefore, retain space **only to the extent that the first indivisible unit (first line)
		// remains in the preceding fragment**. Ordinary cells with sufficient space are unaffected
		// (the condition holds only when the space would leave the preceding fragment with no content).
		final double savedVerticalAlign = this.verticalAlign;
		if (this.verticalAlign > 0) {
			final double fragmentInner = pageLimit - this.frame.getFramePageStart(this.params.flow);
			// getCutPoint(0) is the first available cut position at or beyond 0: the bottom of
			// the first indivisible unit (a pure query, used with the same meaning for
			// multi-column balancing in AbstractContainerBox).
			final double firstUnitEnd = this.container.getCutPoint(0);
			if (LayoutUtils.compare(this.verticalAlign + firstUnitEnd, fragmentInner) > 0) {
				this.verticalAlign = Math.max(0, fragmentInner - firstUnitEnd);
			}
		}
		pageLimit -= this.verticalAlign;
		final SplitResult result;
		try {
			result = super.split(pageLimit, mode, flags);
		} catch (RuntimeException | Error e) {
			// If an intermediate step fails, restore the reduced alignment space (leaving it reduced
			// would lose space on paths that draw this cell after the failure).
			this.verticalAlign = savedVerticalAlign;
			throw e;
		}
		if (!(result instanceof SplitResult.Split(final IPageBreakableBox remainder))) {
			// If no cut occurs (the whole cell stays or moves), restore the alignment space
			// because the cell is drawn at its final height.
			this.verticalAlign = savedVerticalAlign;
			assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0;
			return result;
		}
		// **Do not transfer floats again here** (2026-08-03).
		//
		// super.split() above descends through AbstractContainerBox.split →
		// FlowContainer.splitPageAxis and, at the end, has already completed
		// splitFloatings(Existing(remainder container), ...).
		// Calling splitFloatings again here has two harmful effects:
		//
		// 1. **It cuts already-cut floats again.** The first call has moved their content
		//    into remainder fragments, so the second creates **fragments with no content**.
		// 2. **It overwrites the destination registry.** FlowContainer.remainderWith assigns
		//    `container.floatings = moved`, so the content-bearing fragments from the first
		//    transfer are replaced with the empty fragments from the second.
		//
		// As a result, float content **silently disappeared** from output. Reproducer:
		// files/fuzz-repro/nested-float-content-loss.html (with narrow boxes, tables,
		// right alignment, and left alignment all present, text in the inner float disappears).
		// Independent consultations with codex and agy helped identify the cause.
		return result;
	}
}
