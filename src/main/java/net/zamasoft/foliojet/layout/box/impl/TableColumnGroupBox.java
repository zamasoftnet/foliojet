package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Table column implementation.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableColumnGroupBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableColumnGroupBox extends TableColumnBox {
	private List<TableColumnBox> columns = null;

	public TableColumnGroupBox(final InnerTableParams params, final TableColumnPos pos) {
		super(params, pos);
	}

	public final BoxType getType() {
		return BoxType.TABLE_COLUMN_GROUP;
	}

	public final void addTableColumn(TableColumnBox column) {
		if (this.columns == null) {
			this.columns = new ArrayList<TableColumnBox>();
		}
		this.columns.add(column);
	}

	public final TableColumnBox getTableColumn(int i) {
		return (TableColumnBox) this.columns.get(i);
	}

	public final int getTableColumnCount() {
		return this.columns == null ? 0 : this.columns.size();
	}

	/** Whether the column group itself or any child column has a visible background. */
	@Override
	public boolean paintsAnything() {
		if (super.paintsAnything()) {
			return true;
		}
		for (int i = 0; i < this.getTableColumnCount(); ++i) {
			if (this.getTableColumn(i).paintsAnything()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Traverses leaf columns (and childless column groups) in document order.
	 * A view of {@link #eachColumn} that selects only leaves.
	 *
	 * @param consumer operation to apply to each column
	 */
	public final void forEachColumn(java.util.function.Consumer<TableColumnBox> consumer) {
		this.eachColumn((column, col, span) -> {
			if (column.getType() != BoxType.TABLE_COLUMN_GROUP
					|| ((TableColumnGroupBox) column).getTableColumnCount() == 0) {
				consumer.accept(column);
			}
		});
	}

	/**
	 * Visitor for column traversal. Visits column groups with children in group → children order.
	 * col is the leaf column position; span is colPos.span for leaves, or the number of immediate
	 * children for groups (the convention of the former manual-stack traversal).
	 */
	public interface ColumnVisitor {
		void visit(TableColumnBox column, int col, int span);
	}

	/** Traversal frame for {@link #eachColumn} (the group and the next child to visit). */
	private static final class ColumnWalkFrame {
		final TableColumnGroupBox group;
		int next = 0;

		ColumnWalkFrame(final TableColumnGroupBox group) {
			this.group = group;
		}
	}

	/**
	 * Traverses columns and column groups with column positions. Replaces the seven manual-stack
	 * RECURSE traversals across the two table builders. Does not recurse (design invariant 6:
	 * A-5 made it iterative with an explicit stack, 2026-07-30. Visit order and col counting
	 * match the former recursive version).
	 */
	public final void eachColumn(final ColumnVisitor visitor) {
		final java.util.ArrayDeque<ColumnWalkFrame> stack = new java.util.ArrayDeque<>();
		stack.push(new ColumnWalkFrame(this));
		int col = 0;
		while (!stack.isEmpty()) {
			final ColumnWalkFrame frame = stack.peek();
			if (frame.next >= frame.group.getTableColumnCount()) {
				stack.pop();
				continue;
			}
			final TableColumnBox column = frame.group.getTableColumn(frame.next++);
			if (column.getType() == BoxType.TABLE_COLUMN_GROUP
					&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
				visitor.visit(column, col, ((TableColumnGroupBox) column).getTableColumnCount());
				stack.push(new ColumnWalkFrame((TableColumnGroupBox) column));
			} else {
				final int span = column.getTableColumnPos().span;
				visitor.visit(column, col, span);
				col += span;
			}
		}
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		if (this.columns == null) {
			return;
		}
		// First calculate column drawing coordinates without side effects, then push in
		// **reverse order** to preserve the original traversal order.
		final int n = this.columns.size();
		final double[] xs = new double[n];
		final double[] ys = new double[n];
		if (this.tableParams.flow.isVertical()) {
			for (int i = 0; i < n; ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				xs[i] = x;
				ys[i] = y;
				y += column.getLineSize();
			}
		} else {
			for (int i = 0; i < n; ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				xs[i] = x;
				ys[i] = y;
				x += column.getLineSize();
			}
		}
		for (int i = n - 1; i >= 0; --i) {
			TableColumnBox column = (TableColumnBox) this.columns.get(i);
			worklist.push(AbstractInnerTableBox.framesStep(column, pageBox, drawer, clip, transform, xs[i], ys[i]));
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
		if (this.columns == null) {
			return;
		}
		// First calculate column drawing coordinates without side effects, then push in
		// **reverse order** to preserve the original traversal order.
		final int n = this.columns.size();
		final double[] drawXs = new double[n];
		final double[] drawYs = new double[n];
		if (this.tableParams.flow.isVertical()) {
			for (int i = 0; i < n; ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				drawXs[i] = x;
				drawYs[i] = y;
				y += column.getLineSize();
			}
		} else {
			for (int i = 0; i < n; ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				drawXs[i] = x;
				drawYs[i] = y;
				x += column.getLineSize();
			}
		}
		for (int i = n - 1; i >= 0; --i) {
			TableColumnBox column = (TableColumnBox) this.columns.get(i);
			worklist.push(IBox.drawStep(column, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					drawXs[i], drawYs[i]));
		}
	}

	public final TableColumnBox splitPageAxis(double prevPageSize, double nextPageSize) {
		this.pageSize = prevPageSize;
		TableColumnGroupBox columnGroup = new TableColumnGroupBox(this.params, this.pos);
		columnGroup.setTableParams(this.tableParams);
		columnGroup.lineSize = this.lineSize;
		columnGroup.pageSize = nextPageSize;
		if (this.columns != null) {
			for (int i = 0; i < this.columns.size(); ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				columnGroup.addTableColumn(column.splitPageAxis(prevPageSize, nextPageSize));
			}
		}
		return columnGroup;
	}
}
