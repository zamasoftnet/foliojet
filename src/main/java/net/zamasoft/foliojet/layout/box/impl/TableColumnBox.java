package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

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

	public BoxType getType() {
		return BoxType.TABLE_COLUMN;
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

	public final void finishLayoutSelf(IFramedBox containerBox) {
		// ignore
	}

	public final void pushFinishLayoutChildren(IFramedBox containerBox, Deque<FinishLayoutStep> worklist) {
		// ignore(リーフ)
	}

	public void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
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

	public final void pushGetTextSteps(final StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// ignore
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, double contextX,
			double contextY, double x, double y) {
		// ignore
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist) {
		visitor.visitBox(transform, this, drawer, x, y);
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
