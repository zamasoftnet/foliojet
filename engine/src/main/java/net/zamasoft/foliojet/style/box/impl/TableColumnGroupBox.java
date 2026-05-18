package net.zamasoft.foliojet.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.params.InnerTableParams;
import net.zamasoft.foliojet.style.box.params.TableColumnPos;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;

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

	public final byte getType() {
		return IBox.TYPE_TABLE_COLUMN_GROUP;
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

	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		if (this.columns == null) {
			return;
		}
		if (StyleUtils.isVertical(this.tableParams.flow)) {
			for (int i = 0; i < this.columns.size(); ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				column.frames(pageBox, drawer, clip, transform, x, y);
				y += column.getLineSize();
			}
		} else {
			for (int i = 0; i < this.columns.size(); ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				column.frames(pageBox, drawer, clip, transform, x, y);
				x += column.getLineSize();
			}
		}
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		super.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
		if (this.columns == null) {
			return;
		}
		if (StyleUtils.isVertical(this.tableParams.flow)) {
			for (int i = 0; i < this.columns.size(); ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				column.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
				y += column.getLineSize();
			}
		} else {
			for (int i = 0; i < this.columns.size(); ++i) {
				TableColumnBox column = (TableColumnBox) this.columns.get(i);
				column.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
				x += column.getLineSize();
			}
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
