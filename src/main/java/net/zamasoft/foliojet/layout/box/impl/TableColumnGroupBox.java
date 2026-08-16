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
 * テーブル列の実装です。
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

	/** 列グループ自身または子列に表示される背景があるか。 */
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
	 * 葉の列(および子を持たない列グループ)を文書順に走査します。
	 * {@link #eachColumn}の葉だけを拾うビューです。
	 *
	 * @param consumer 各列に適用する処理
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
	 * 列走査の訪問者です。列グループ(子あり)にはグループ→子の順で
	 * 訪問し、col は葉のカラム位置、span は葉なら colPos.span、
	 * グループなら直下の子の数(旧来の手動スタック走査の規約)。
	 */
	public interface ColumnVisitor {
		void visit(TableColumnBox column, int col, int span);
	}

	/** {@link #eachColumn}の走査フレーム(グループと、次に見る子)。 */
	private static final class ColumnWalkFrame {
		final TableColumnGroupBox group;
		int next = 0;

		ColumnWalkFrame(final TableColumnGroupBox group) {
			this.group = group;
		}
	}

	/**
	 * 列と列グループをカラム位置付きで走査します。両表ビルダーに7箇所
	 * あった手動スタックの RECURSE 走査の置き換えです。再帰しない
	 * (設計不変条件6——A-5で明示スタックへ反復化、2026-07-30。
	 * 訪問順・colの数え方は旧再帰版と同一)。
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
		// 列の描画座標を先に(副作用なく)計算してから、元の走査順を保つため
		// **逆順**でpushする
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
		// 列の描画座標を先に(副作用なく)計算してから、元の走査順を保つため
		// **逆順**でpushする
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
