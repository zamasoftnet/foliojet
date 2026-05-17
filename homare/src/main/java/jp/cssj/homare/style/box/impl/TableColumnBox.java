package jp.cssj.homare.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import jp.cssj.homare.style.box.AbstractInnerTableBox;
import jp.cssj.homare.style.box.IBox;
import jp.cssj.homare.style.box.IFramedBox;
import jp.cssj.homare.style.box.params.InnerTableParams;
import jp.cssj.homare.style.box.params.Pos;
import jp.cssj.homare.style.box.params.TableColumnPos;
import jp.cssj.homare.style.draw.BackgroundBorderDrawable;
import jp.cssj.homare.style.draw.Drawable;
import jp.cssj.homare.style.draw.Drawer;
import jp.cssj.homare.style.visitor.Visitor;

/**
 * テーブル列の実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableColumnBox.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class TableColumnBox extends AbstractInnerTableBox {
	protected final TableColumnPos pos;

	public TableColumnBox(final InnerTableParams params, final TableColumnPos pos) {
		super(params);
		this.pos = pos;
	}

	public byte getType() {
		return IBox.TYPE_TABLE_COLUMN;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableColumnPos getTableColumnPos() {
		return this.pos;
	}

	public final void setLineSize(double lineSize) {
		this.lineSize = lineSize;
	}

	public final void setPageSize(double pageSize) {
		this.pageSize = pageSize;
	}

	public final void finishLayout(IFramedBox containerBox) {
		// ignore
	}

	public void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x, double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (!this.params.background.isVisible()) {
			return;
		}
		Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
				this.params.background, this.params.border, null, this.getWidth(), this.getHeight());
		drawer.visitDrawable(drawable, x, y);
	}

	public final void getText(final StringBuffer textBuff) {
		// ignore
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, double contextX,
			double contextY, double x, double y) {
		// ignore
	}

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		visitor.visitBox(transform, this, x, y);
	}

	public TableColumnBox splitPageAxis(double prevPageSize, double nextPageSize) {
		this.pageSize = prevPageSize;
		final TableColumnBox column = new TableColumnBox(this.params, this.pos);
		column.setTableParams(this.tableParams);
		column.lineSize = this.lineSize;
		column.pageSize = nextPageSize;
		return column;
	}
}
