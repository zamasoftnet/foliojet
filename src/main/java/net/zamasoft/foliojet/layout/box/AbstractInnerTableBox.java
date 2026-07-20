package net.zamasoft.foliojet.layout.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.draw.Drawer;
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
	
	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d,
			Deque<TextShapeStep> worklist) {
		// TODO
	}

	/**
	 * 枠を描画します(2026-07-20、反復化——{@link AbstractContainerBox#frames}
	 * と同じ理由・同じ規約。テーブル内部系統(行・行グループ・列・列グループ)
	 * は{@link AbstractContainerBox}を継承しない独立の系統のため、この
	 * 共通の親クラスに入口メソッドを置く)。
	 */
	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		final Deque<FramesStep> worklist = new ArrayDeque<>();
		worklist.push(AbstractInnerTableBox.framesStep(this, pageBox, drawer, clip, transform, x, y));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * {@code box}の{@link #pushFramesSteps}を実行する1つの{@link FramesStep}を
	 * 作ります。
	 */
	public static FramesStep framesStep(final AbstractInnerTableBox box, final PageBox pageBox, final Drawer drawer,
			final Shape clip, final AffineTransform transform, final double x, final double y) {
		return worklist -> box.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	/**
	 * このボックス(とその子孫)の枠描画手順を{@code worklist}へ積みます。
	 * {@link IBox#pushDrawSteps}と同じ規約(元の走査順を保つため**逆順**で
	 * push)に従ってください。
	 */
	public abstract void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform,
			double x, double y, Deque<FramesStep> worklist);
}
