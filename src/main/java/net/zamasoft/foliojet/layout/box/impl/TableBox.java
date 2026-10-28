package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.TextShapeSink;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TablePos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.part.TableCollapsedBorders;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * Table implementation.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TableBox extends AbstractBox implements IPageBreakableBox, IFlowBox, INonReplacedBox {

	protected final TableParams params;

	protected AbstractBlockBox block;

	protected AbsoluteRectFrame frame;

	protected TableColumnGroupBox columnGroupBox = null;

	protected TableRowGroupBox headerGroupBox = null;

	/**
	 * Whether this table is a continuation fragment (the second half created by splitTableBox)
	 * (tagged PDF defect ② fix, 2026-07-30). Headers displayed in continuation fragments
	 * are repetitions of the same element, not continuations. Used by {@link #isRepeatedGroup}.
	 */
	private boolean tableContinuation = false;

	protected List<TableRowGroupBox> bodyGroups = null;

	protected TableRowGroupBox footerGroupBox = null;

	protected TableCollapsedBorders borders;

	protected double width = 0;

	protected double height = 0;

	protected double offsetX = 0;

	protected double offsetY = 0;

	public TableBox(final TableParams params, final AbstractBlockBox block) {
		this(params, new AbsoluteRectFrame(params.frame), block);
	}

	protected TableBox(final TableParams params, final AbsoluteRectFrame frame, final AbstractBlockBox block) {
		this.params = params;
		this.block = block;
		this.frame = frame;
	}

	public final BoxType getType() {
		return BoxType.TABLE;
	}

	public final Pos getPos() {
		return TablePos.POS;
	}

	public final Params getParams() {
		return this.params;
	}

	public final TableParams getTableParams() {
		return this.params;
	}

	public final AbstractBlockBox getBlockBox() {
		return this.block;
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final void setSize(double width, double height) {
		this.width = width;
		this.height = height;
	}

	public final void calculateFrame(double lineSize) {
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.params.frame.margin, lineSize);
		if (this.params.borderCollapse == TableParams.BORDER_SEPARATE) {
			// Separated border model
			//
			// ■ Calculate padding
			//
			LayoutUtils.computePaddings(this.frame.padding, this.params.frame.padding, lineSize);
			this.frame.padding.top = params.borderSpacingV / 2.0;
			this.frame.padding.right = params.borderSpacingH / 2.0;
			this.frame.padding.bottom = params.borderSpacingV / 2.0;
			this.frame.padding.left = params.borderSpacingH / 2.0;
		} else {
			this.frame.frame = RectFrame.create(params.frame.margin, RectBorder.NONE_RECT_BORDER,
					params.frame.background, params.frame.padding);
			return;
		}
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
	}

	public final void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// Push onto the stack in reverse order to preserve the original traversal order (header → body(0..n) → footer).
		if (this.footerGroupBox != null) {
			worklist.push(IBox.step(this.footerGroupBox, containerBox));
		}
		for (int i = this.getTableBodyCount() - 1; i >= 0; --i) {
			worklist.push(IBox.step(this.getTableBody(i), containerBox));
		}
		if (this.headerGroupBox != null) {
			worklist.push(IBox.step(this.headerGroupBox, containerBox));
		}
	}

	public final void setCollapsedBorders(TableCollapsedBorders borders) {
		assert borders != null;
		this.borders = borders;
	}

	public final TableCollapsedBorders getCollapsedBorders() {
		return this.borders;
	}

	public final void setTableColumnGroup(TableColumnGroupBox columnGroup) {
		this.columnGroupBox = columnGroup;
	}

	public final void setTableHeader(TableRowGroupBox headerGroup) {
		this.headerGroupBox = headerGroup;
		if (this.params.flow.isVertical()) {
			this.width += headerGroup.getWidth();
			if (headerGroup.getHeight() > this.height) {
				this.height = headerGroup.getHeight();
			}
		} else {
			this.height += headerGroup.getHeight();
			if (headerGroup.getWidth() > this.width) {
				this.width = headerGroup.getWidth();
			}
		}
	}

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		if (this.headerGroupBox != null) {
			action.accept(this.headerGroupBox);
		}
		for (int i = 0; i < this.getTableBodyCount(); ++i) {
			action.accept(this.getTableBody(i));
		}
		if (this.footerGroupBox != null) {
			action.accept(this.footerGroupBox);
		}
	}

	public final TableRowGroupBox getTableHeader() {
		return this.headerGroupBox;
	}

	public final void setTableFooter(TableRowGroupBox footerGroup) {
		this.footerGroupBox = footerGroup;
		if (this.params.flow.isVertical()) {
			this.width += footerGroup.getWidth();
			if (footerGroup.getHeight() > this.height) {
				this.height = footerGroup.getHeight();
			}
		} else {
			this.height += footerGroup.getHeight();
			if (footerGroup.getWidth() > this.width) {
				this.width = footerGroup.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableFooter() {
		return this.footerGroupBox;
	}

	public final void addTableBody(TableRowGroupBox rowGroupBox) {
		if (this.bodyGroups == null) {
			this.bodyGroups = new ArrayList<TableRowGroupBox>();
		}
		this.bodyGroups.add(rowGroupBox);
		if (this.params.flow.isVertical()) {
			this.width += rowGroupBox.getWidth();
			if (rowGroupBox.getHeight() > this.height) {
				this.height = rowGroupBox.getHeight();
			}
		} else {
			this.height += rowGroupBox.getHeight();
			if (rowGroupBox.getWidth() > this.width) {
				this.width = rowGroupBox.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableBody(int i) {
		return (TableRowGroupBox) this.bodyGroups.get(i);
	}

	public final int getTableBodyCount() {
		return this.bodyGroups == null ? 0 : this.bodyGroups.size();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Tables do not belong to the {@link AbstractContainerBox} family, so without an override they return
	 * the conservative {@link IBox} default ({@code true}). However, an empty table with no rows, columns,
	 * backgrounds, or borders actually paints nothing. In vertical writing, a trailing empty table with
	 * an explicit width was moved to the next page solely because of its width; that blank page was
	 * misclassified as potentially painting content and retained (fuzz seed 5141).
	 * </p>
	 *
	 * <p>
	 * HTML anonymous table box generation can create anonymous rows and cells even for an empty
	 * {@code display:table}. Thus, the presence of rows alone is insufficient: {@code paintsAnything()}
	 * also traverses column, row, and cell backgrounds, borders, and content. For collapsed borders,
	 * checks whether any borders are actually visible.
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		if (this.params.opacity == 0) {
			return false;
		}
		if (this.params.frame.background.isVisible()) {
			return true;
		}
		if (this.params.borderCollapse == TableParams.BORDER_SEPARATE) {
			if (this.frame.frame.border.isVisible()) {
				return true;
			}
		} else if (this.borders != null && this.borders.paintsAnything()) {
			return true;
		}
		if (this.columnGroupBox != null && this.columnGroupBox.paintsAnything()) {
			return true;
		}
		if (this.headerGroupBox != null && this.headerGroupBox.paintsAnything()) {
			return true;
		}
		if (this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				if (this.bodyGroups.get(i).paintsAnything()) {
					return true;
				}
			}
		}
		return this.footerGroupBox != null && this.footerGroupBox.paintsAnything();
	}

	private void drawBorders(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x, double y,
			double xx, double yy) {
		switch (this.params.borderCollapse) {
		case TableParams.BORDER_SEPARATE: {
			if (!this.frame.frame.border.isVisible()) {
				break;
			}
			// Separated borders
			Drawable drawable = new BorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.frame.frame.border,
					this.width + this.frame.padding.getFrameWidth() + this.frame.frame.border.getFrameWidth(),
					this.height + this.frame.padding.getFrameHeight() + this.frame.frame.border.getFrameHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, x + this.frame.margin.left, y + this.frame.margin.top);
		}
			break;

		case TableParams.BORDER_COLLAPSE: {
			// Collapsed borders
			Drawable drawable = CollapsedBordersDrawable.of(pageBox, clip, this.params.opacity, transform,
					this.borders, this.params.flow).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, xx, yy);
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x) : "Undefined x";
		assert !LayoutUtils.isNone(y) : "Undefined y";
		x += this.offsetX;
		y += this.offsetY;

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}
		double xx = x + this.frame.getFrameLeft();
		double yy = y + this.frame.getFrameTop();

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			final Drawer newDrawer = new Drawer(params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		if (this.params.frame.background.isVisible()) {
			Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.frame.background, this.params.frame.border, this.params.frame.padding,
					this.getWidth() - this.frame.getFrameWidth(), this.getHeight() - this.frame.getFrameHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, xx, yy);
		}

		// The frames/floats passes still use recursion (a separate task; see RELIABILITY-PLAN.md).
		// Table nesting depth is bounded independently of the row/cell count, so synchronous
		// calls are acceptable here. Make only the content (draw) pass iterative:
		// first calculate child drawing coordinates without side effects, then push onto
		// the worklist in **reverse order** to preserve the original traversal order.
		final List<IBox> contentBoxes = new ArrayList<>();
		final List<Double> contentXs = new ArrayList<>();
		final List<Double> contentYs = new ArrayList<>();

		// Merged the two structurally identical vertical/horizontal branches (2026-07-25, vertical-lr support).
		// Track only logical positions (start/end in the page direction), and delegate physical-coordinate
		// conversion to LayoutUtils.drawX/drawY. Previously, vertical writing used handwritten
		// RL-only formulas (subtracting the cursor from the right edge); this duplication
		// caused vertical-lr cases to be missed.
		final WritingMode flow = this.params.flow;
		final double tableExtent = flow.isVertical() ? this.width : this.height;
		// Arrange groups that consume space in the page direction in document order (header → body → footer).
		final List<TableRowGroupBox> groups = new ArrayList<>();
		if (this.headerGroupBox != null) {
			groups.add(this.headerGroupBox);
		}
		if (this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				groups.add((TableRowGroupBox) this.bodyGroups.get(i));
			}
		}
		if (this.footerGroupBox != null) {
			groups.add(this.footerGroupBox);
		}
		final int groupCount = groups.size();
		final double[] groupXs = new double[groupCount];
		final double[] groupYs = new double[groupCount];
		{
			double pageStart = 0;
			for (int i = 0; i < groupCount; ++i) {
				final TableRowGroupBox group = groups.get(i);
				final double pageEnd = pageStart + (flow.isVertical() ? group.getWidth() : group.getHeight());
				groupXs[i] = LayoutUtils.drawX(flow, xx, tableExtent, pageStart, pageEnd, 0);
				groupYs[i] = LayoutUtils.drawY(flow, yy, pageStart, 0);
				pageStart = pageEnd;
			}
		}
		// Column groups span the entire table, so their page-direction interval is [0, tableExtent].
		final double columnGroupX = LayoutUtils.drawX(flow, xx, tableExtent, 0, tableExtent, 0);
		final double columnGroupY = LayoutUtils.drawY(flow, yy, 0, 0);

		// Inner borders/backgrounds
		if (this.columnGroupBox != null) {
			this.columnGroupBox.frames(pageBox, drawer, clip, transform, columnGroupX, columnGroupY);
		}
		for (int i = 0; i < groupCount; ++i) {
			groups.get(i).frames(pageBox, drawer, clip, transform, groupXs[i], groupYs[i]);
		}

		this.drawBorders(pageBox, drawer, clip, transform, x, y, xx, yy);

		// Floating boxes (excluding column groups)
		for (int i = 0; i < groupCount; ++i) {
			final TableRowGroupBox group = groups.get(i);
			final boolean repetition = this.isRepeatedGroup(group);
			if (repetition) {
				pageBox.pushStructRepetition();
			}
			group.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, groupXs[i], groupYs[i]);
			if (repetition) {
				pageBox.popStructRepetition();
			}
		}

		// Content
		if (this.columnGroupBox != null) {
			contentBoxes.add(this.columnGroupBox);
			contentXs.add(columnGroupX);
			contentYs.add(columnGroupY);
		}
		for (int i = 0; i < groupCount; ++i) {
			contentBoxes.add(groups.get(i));
			contentXs.add(groupXs[i]);
			contentYs.add(groupYs[i]);
		}
		final Drawer fdrawer = drawer;
		final double fx = x, fy = y;
		worklist.push(w -> pageBox.endStruct(fdrawer, this.params.element, structCount, fx, fy));
		for (int i = contentBoxes.size() - 1; i >= 0; --i) {
			final IBox content = contentBoxes.get(i);
			final boolean repetition = this.isRepeatedGroup(content);
			if (repetition) {
				// LIFO makes the execution order push → drawStep → pop.
				worklist.push(w -> pageBox.popStructRepetition());
			}
			worklist.push(IBox.drawStep(content, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					contentXs.get(i), contentYs.get(i)));
			if (repetition) {
				worklist.push(w -> pageBox.pushStructRepetition());
			}
		}
	}

	/**
	 * Returns whether displaying this group repeats the same element (tagged PDF defect ② fix, 2026-07-30).
	 * This applies to headers in continuation fragments and footers in fragments with subsequent fragments
	 * (the original header is in the first fragment; the original footer is in the last).
	 * While drawing repetitions, bypass the cross-page registry and declare a StructElem per page as before.
	 * Merging them as continuations would duplicate the same content once per page in a single element.
	 */
	private boolean isRepeatedGroup(final IBox box) {
		return (box == this.headerGroupBox && this.tableContinuation)
				|| (box == this.footerGroupBox && this.isFragmented());
	}

	public final void pushGetTextSteps(final StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// To preserve the original traversal order (header → body(0..n) → footer),
		// push onto the stack in reverse order.
		if (this.footerGroupBox != null) {
			worklist.push(IBox.getTextStep(this.footerGroupBox, textBuff));
		}
		if (this.bodyGroups != null) {
			for (int i = this.bodyGroups.size() - 1; i >= 0; --i) {
				TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
				worklist.push(IBox.getTextStep(rowGroup, textBuff));
			}
		}
		if (this.headerGroupBox != null) {
			worklist.push(IBox.getTextStep(this.headerGroupBox, textBuff));
		}
	}
	
	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double d,
			Deque<TextShapeStep> worklist) {
		// TODO
	}

	protected static class BorderDrawable extends AbstractDrawable {
		protected final RectBorder border;
		protected final double width, height;

		public BorderDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform, RectBorder border,
				double width, double height) {
			super(pageBox, clip, opacity, transform);
			this.border = border;
			this.width = width;
			this.height = height;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			this.border.draw(gc, x, y, this.width, this.height);
		}

		@Override
		public String describe() {
			return String.format(java.util.Locale.ROOT, "TableBorder[w=%.2f h=%.2f]", this.width, this.height);
		}
	}

	protected static class CollapsedBordersDrawable extends AbstractDrawable {
		protected final TableCollapsedBorders borders;
		protected final boolean vertical;

		public CollapsedBordersDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				TableCollapsedBorders borders, boolean vertical) {
			super(pageBox, clip, opacity, transform);
			this.borders = borders;
			this.vertical = vertical;
		}

		/** The drawable for the borders of a table in {@code flow}. */
		static CollapsedBordersDrawable of(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				TableCollapsedBorders borders, net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
			return flow == net.zamasoft.foliojet.layout.box.params.WritingMode.LR
					? new LeftToRightCollapsedBordersDrawable(pageBox, clip, opacity, transform, borders)
					: new CollapsedBordersDrawable(pageBox, clip, opacity, transform, borders, flow.isVertical());
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			BorderRenderer.INSTANCE.drawTableCollapseBorders(gc, this.borders, x, y, this.vertical);

		}

		/**
		 * Serializes border content (for display-list golden).
		 * Lists each grid border other than NONE as H column,index / V row,index = [style,width,color].
		 */
		public String describe() {
			final StringBuilder sb = new StringBuilder("CollapsedBorders[");
			final int rows = this.borders.getRowCount();
			final int cols = this.borders.getColumnCount();
			sb.append(cols).append('x').append(rows).append(this.vertical ? " vertical" : "");
			for (int col = 0; col < cols; ++col) {
				for (int i = 0; i <= rows; ++i) {
					final net.zamasoft.foliojet.layout.box.params.Border b = this.borders.getHBorder(col, i);
					if (b != null && !b.isNull()) {
						sb.append(" H").append(col).append(',').append(i).append('=').append(b);
					}
				}
			}
			for (int row = 0; row < rows; ++row) {
				for (int i = 0; i <= cols; ++i) {
					final net.zamasoft.foliojet.layout.box.params.Border b = this.borders.getVBorder(row, i);
					if (b != null && !b.isNull()) {
						sb.append(" V").append(row).append(',').append(i).append('=').append(b);
					}
				}
			}
			return sb.append(']').toString();
		}
	}

	/**
	 * Collapsed borders of a table in vertical-lr (2026-10-28, mirror seed 45250016). The drawing lays the rows out
	 * from the right as vertical-rl does: mirrored across the table, the first row is on the left.
	 */
	protected static final class LeftToRightCollapsedBordersDrawable extends CollapsedBordersDrawable {
		LeftToRightCollapsedBordersDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				TableCollapsedBorders borders) {
			super(pageBox, clip, opacity, transform, borders, true);
		}

		@Override
		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			double extent = 0;
			for (int row = 0; row < this.borders.getRowCount(); ++row) {
				extent += this.borders.getRowSize(row);
			}
			try (final var state = gc.begin()) {
				gc.transform(new AffineTransform(-1, 0, 0, 1, 2 * x + extent, 0));
				BorderRenderer.INSTANCE.drawTableCollapseBorders(gc, this.borders, x, y, true);
			}
		}
	}

	/**
	 * Common exit for decisions that move the whole table (2026-08-23). Disables source replay on Move:
	 * the table's lexical source range may contain content finalized outside the table by foster parenting,
	 * so replay after MOVE duplicates finalized content (see {@code AbstractBox.invalidateSourceReplay}).
	 * The box-restyle fallback carries the constructed boxes.
	 */
	private SplitResult keepOrMoveWholeTable(final byte flags) {
		final SplitResult result = net.zamasoft.foliojet.layout.fragment.TableCutter.keepOrMoveAll(flags);
		if (result instanceof SplitResult.Move) {
			this.invalidateSourceReplay();
		}
		return result;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		// assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;

		final boolean vertical = this.params.flow.isVertical();
		int origBodyRowCount = 0;
		if (this.borders != null && this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				origBodyRowCount += this.getTableBody(i).getTableRowCount();
			}
		}
		if (mode instanceof BreakMode.ForceBreakMode) {
			// Forced page break between rows
			TableForceBreakMode force = (TableForceBreakMode) mode;
			TableBox nextTable = this.splitTableBox();
			int rowGroup = force.rowGroup;
			int row = force.row;
			if (row != -1) {
				assert force.box.getType() == BoxType.TABLE_ROW || force.box.getType() == BoxType.TABLE_ROW_GROUP;
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) this.bodyGroups.get(rowGroup);
				TableRowGroupBox newRowGroupBox = net.zamasoft.foliojet.layout.fragment.TableCutter
						.requireSplitRemainder(rowGroupBox.split(pageLimit, mode, (byte) 0), TableRowGroupBox.class,
								"TableRowGroupBox.split for a forced break between rows");
				nextTable.addTableBody(newRowGroupBox);
				if (vertical) {
					this.width -= newRowGroupBox.getPageSize();
				} else {
					this.height -= newRowGroupBox.getPageSize();
				}
			}
			for (int j = rowGroup + 1; j < this.bodyGroups.size(); ++j) {
				nextTable.addTableBody((TableRowGroupBox) this.bodyGroups.get(j));
			}
			for (int j = this.bodyGroups.size() - 1; j > rowGroup; --j) {
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) this.bodyGroups.remove(j);
				if (vertical) {
					this.width -= rowGroupBox.getPageSize();
				} else {
					this.height -= rowGroupBox.getPageSize();
				}
			}
			if (this.borders != null) {
				// Collapsed borders
				nextTable.borders = this.borders.splitPageAxis(this, nextTable, origBodyRowCount);
			}
			return new SplitResult.Split(nextTable);
		}

		if (this.bodyGroups == null || this.bodyGroups.isEmpty()) {
			return this.keepOrMoveWholeTable(flags);
		}

		// Subtract the heights of the unbreakable parts at the top and bottom (headers, footers, etc.).
		// (Decision extracted into pure logic in TableCutter.)
		final double headerSize = this.headerGroupBox != null ? this.headerGroupBox.getPageSize() : -1;
		final double footerSize = this.footerGroupBox != null ? this.footerGroupBox.getPageSize() : -1;
		if (vertical) {
			pageLimit = net.zamasoft.foliojet.layout.fragment.TableCutter.reserveNonBreakable(pageLimit,
					this.getWidth(), this.frame.getFramePageStart(this.params.flow),
					this.frame.getFramePageEnd(this.params.flow), this.frame.getMarginPageEnd(this.params.flow),
					headerSize, footerSize);
		} else {
			pageLimit = net.zamasoft.foliojet.layout.fragment.TableCutter.reserveNonBreakable(pageLimit,
					this.getHeight(), this.frame.getFrameTop(), this.frame.getFrameBottom(), this.frame.margin.bottom,
					headerSize, footerSize);
		}

		// The table header and footer do not fit.
		if (LayoutUtils.compare(pageLimit, 0) <= 0) {
			return this.keepOrMoveWholeTable(flags);
		}

		TableBox nextTable = null;
		int i;
		boolean ignoreBreakAvoid = false;
		double savePageLimit = pageLimit;
		for (i = 0; i < this.bodyGroups.size(); ++i) {
			final TableRowGroupBox prevRowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
			double prevRowGroupSize = prevRowGroup.getPageSize();
			if (i < this.bodyGroups.size() - 1 && LayoutUtils.compare(pageLimit, prevRowGroupSize) > 0) {
				pageLimit -= prevRowGroupSize;
				continue;
			}
			byte lflags = (byte) (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT);
			if (i > 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			final SplitResult groupResult = prevRowGroup.split(pageLimit, mode, (byte) (lflags & flags));
			assert nextTable == null || !(groupResult instanceof SplitResult.Keep);
			if (groupResult instanceof SplitResult.Keep) {
				if (!ignoreBreakAvoid && i == 0 && (flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
					// At the start of a page, retry while ignoring page-break avoidance.
					ignoreBreakAvoid = true;
					pageLimit = savePageLimit;
					i = -1;
					continue;
				}
				pageLimit -= prevRowGroupSize;
				continue;
			}
			if (groupResult instanceof SplitResult.Move) {
				if (i == 0) {
					// Move everything (source replay disabled; see keepOrMoveWholeTable).
					assert (flags & IPageBreakableBox.FLAGS_FIRST) == 0;
					this.invalidateSourceReplay();
					return SplitResult.MOVE;
				}
				if (!ignoreBreakAvoid) {
					final TableRowGroupBox beforeGroup = (TableRowGroupBox) this.bodyGroups.get(i - 1);
					// Decision extracted into pure logic in TableCutter.
					if (net.zamasoft.foliojet.layout.fragment.TableCutter.groupBreakAvoid(
							beforeGroup.getTableRowGroupPos().pageBreakAfter,
							prevRowGroup.getTableRowGroupPos().pageBreakBefore,
							beforeGroup.getTableRowCount() > 0
									? beforeGroup.getTableRow(beforeGroup.getTableRowCount() - 1)
											.getTableRowPos().pageBreakAfter
									: PageBreakMode.AUTO,
							prevRowGroup.getTableRowCount() > 0
									? prevRowGroup.getTableRow(0).getTableRowPos().pageBreakBefore
									: PageBreakMode.AUTO)) {
						// Row-group page-break avoidance
						// Go back one group and cut the previous row group at its end.
						pageLimit = beforeGroup.getPageSize() - LayoutUtils.THRESHOLD * 2;
						i -= 2;
						continue;
					}
				}
				nextTable = this.splitTableBox();
				break;
			}
			nextTable = this.splitTableBox();
			prevRowGroupSize -= prevRowGroup.getPageSize();
			if (vertical) {
				this.width -= prevRowGroupSize;
			} else {
				this.height -= prevRowGroupSize;
			}
			nextTable.addTableBody(net.zamasoft.foliojet.layout.fragment.TableCutter.requireSplitRemainder(groupResult,
					TableRowGroupBox.class, "TableRowGroupBox.split at the table cut line"));
			++i;
			break;
		}

		if (nextTable == null) {
			return this.keepOrMoveWholeTable(flags);
		}

		int remove = 0;
		for (int j = i; j < this.bodyGroups.size(); ++j) {
			final TableRowGroupBox prevRowGroup = (TableRowGroupBox) this.bodyGroups.get(j);
			if (vertical) {
				this.width -= prevRowGroup.getPageSize();
			} else {
				this.height -= prevRowGroup.getPageSize();
			}
			nextTable.addTableBody(prevRowGroup);
			++remove;
		}
		for (int j = 0; j < remove; ++j) {
			this.bodyGroups.remove(this.bodyGroups.size() - 1);
		}
		if (this.columnGroupBox != null) {
			// Columns
			if (vertical) {
				nextTable.columnGroupBox = (TableColumnGroupBox) this.columnGroupBox.splitPageAxis(this.width,
						nextTable.width);
			} else {
				nextTable.columnGroupBox = (TableColumnGroupBox) this.columnGroupBox.splitPageAxis(this.height,
						nextTable.height);
			}
		}
		if (this.borders != null) {
			// Collapsed borders
			nextTable.borders = this.borders.splitPageAxis(this, nextTable, origBodyRowCount);
		}
		return new SplitResult.Split(nextTable);
	}

	public final TableBox splitTableBox() {
		net.zamasoft.foliojet.layout.builder.impl.TableBuildStats.TABLE_FRAGMENTS.incrementAndGet();
		// Table set T-b (2026-07-30): The preceding fragment (this) keeps its anchor, but its range
		// also includes the remainder held by the continuation fragment (row-cutting progress).
		// Replaying the preceding fragment's source rebuilds the entire table and rolls back split progress.
		// Mark it as fragmented, as in an actual split of AbstractBlockBox,
		// and exclude it from stampRanges/replay with isSourceReplayable()=false.
		this.markFragmented();
		final boolean vertical = this.params.flow.isVertical();
		// Frame-cut decisions extracted into pure logic in TableCutter (C4-T2).
		final net.zamasoft.foliojet.layout.fragment.TableCutter.TableFragmentFrames frames = net.zamasoft.foliojet.layout.fragment.TableCutter
				.tableFragmentFrames(this.params.flow, this.headerGroupBox != null, this.footerGroupBox != null,
						this.frame);
		// A split fragment is a continuation (no anchor; not replayed as a fresh box. P0).
		TableBox nextTable = new TableBox(this.params, frames.nextFrame(), this.block);
		// Tagged PDF defect ② (2026-07-30): Headers in continuation fragments are repetitions
		// (see isRepeatedGroup).
		nextTable.tableContinuation = true;
		if (vertical) {
			nextTable.height = this.height;
		} else {
			nextTable.width = this.width;
		}

		if (this.headerGroupBox != null) {
			nextTable.setTableHeader(this.headerGroupBox);
		}
		if (this.footerGroupBox != null) {
			nextTable.setTableFooter(this.footerGroupBox);
		}
		this.frame = frames.prevFrame();
		return nextTable;
	}

	public final boolean avoidBreakBefore() {
		return false;
	}

	public final boolean avoidBreakAfter() {
		return false;
	}
}
