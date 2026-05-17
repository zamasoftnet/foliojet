package jp.cssj.homare.style.box;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import jp.cssj.homare.style.box.impl.PageBox;
import jp.cssj.homare.style.box.params.InnerTableParams;
import jp.cssj.homare.style.box.params.Params;
import jp.cssj.homare.style.box.params.TableParams;
import jp.cssj.homare.style.util.StyleUtils;

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
		return StyleUtils.isVertical(this.tableParams.flow) ? this.pageSize : this.lineSize;
	}

	public final double getHeight() {
		return StyleUtils.isVertical(this.tableParams.flow) ? this.lineSize : this.pageSize;
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
