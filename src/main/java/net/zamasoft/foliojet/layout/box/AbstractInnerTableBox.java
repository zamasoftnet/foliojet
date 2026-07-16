package net.zamasoft.foliojet.layout.box;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

public abstract class AbstractInnerTableBox extends AbstractBox implements INonReplacedBox {
	protected final InnerTableParams params;
	protected TableParams tableParams;
	protected double lineSize, pageSize;

	public AbstractInnerTableBox(final InnerTableParams params) {
		this.params = params;
	}

	public final Params getParams() {
		return this.params;
	}

	public final InnerTableParams getInnerTableParams() {
		return this.params;
	}

	public final void setTableParams(TableParams tableParams) {
		this.tableParams = tableParams;
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		return this.pageSize;
	}

	public final double getWidth() {
		return this.tableParams.flow.isVertical() ? this.pageSize : this.lineSize;
	}

	public final double getHeight() {
		return this.tableParams.flow.isVertical() ? this.lineSize : this.pageSize;
	}

	public final double getInnerWidth() {
		return this.getWidth();
	}

	public final double getInnerHeight() {
		return this.getHeight();
	}
	
	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d) {
		// TODO
	}
}
