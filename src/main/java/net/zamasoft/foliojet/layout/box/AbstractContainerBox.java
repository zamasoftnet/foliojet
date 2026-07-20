package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.ColumnsContainer;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Length;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder;
import net.zamasoft.foliojet.layout.fragment.ColumnBalancer;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 通常のフローを含むことができるボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractContainerBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public abstract class AbstractContainerBox extends AbstractBox
		implements IPageBreakableBox, INonReplacedBox, IFramedBox {

	protected Dimension size, minSize;

	protected AbsoluteRectFrame frame;

	protected double width = 0;
	protected double height = 0;
	protected double minPageAxis = 0, maxPageAxis = Double.MAX_VALUE;
	protected double offsetX = 0, offsetY = 0;

	protected Container container;

	protected AbstractContainerBox(Dimension size, Dimension minSize, Container container) {
		assert size != null;
		this.size = size;
		this.minSize = minSize;
		this.container = container;
		container.setBox(this);
	}

	public final Container getContainer() {
		return this.container;
	}

	/**
	 * コンテナボックスのパラメータを返します。
	 * 
	 * @return
	 */
	public abstract BlockParams getBlockParams();

	/**
	 * ページ方向に拡張します。
	 * 
	 * @param newSize
	 */

	public void setPageAxis(final double newSize) {
		final BlockParams params = this.getBlockParams();
		if (params.flow.isVertical()) {
			// 縦書き
			if (newSize <= this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		} else {
			// 横書き
			if (newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
	}

	/**
	 * ページ方向サイズが明示されていればtrueを返します。
	 * 
	 * @return
	 */
	public abstract boolean isSpecifiedPageSize();

	/**
	 * 絶対配置の基準となるボックスではtrueを返します。
	 * 
	 * @return
	 */
	public abstract boolean isContextBox();

	/**
	 * 枠を描画します(2026-07-20、反復化——drawと同じ理由。深いネストでの
	 * StackOverflowErrorを避けるため、明示的な{@link Deque}をワークリストと
	 * して使う反復DFSに置き換えた)。
	 *
	 * @param pageBox
	 *            TODO
	 * @param drawer
	 * @param clip
	 * @param transform
	 *            TODO
	 * @param x
	 * @param y
	 */
	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		final java.util.Deque<FramesStep> worklist = new java.util.ArrayDeque<>();
		worklist.push(AbstractContainerBox.framesStep(this, pageBox, drawer, clip, transform, x, y));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * {@code box}の{@link #pushFramesSteps}を実行する1つの{@link FramesStep}を
	 * 作ります。
	 */
	public static FramesStep framesStep(final AbstractContainerBox box, final PageBox pageBox, final Drawer drawer,
			final Shape clip, final AffineTransform transform, final double x, final double y) {
		return worklist -> box.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	/**
	 * このボックス(とその子孫)の枠描画手順を{@code worklist}へ積みます。
	 * {@link IBox#pushDrawSteps}と同じ規約(元の走査順を保つため**逆順**で
	 * push)に従ってください。
	 */
	public abstract void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform,
			double x, double y, java.util.Deque<FramesStep> worklist);

	/**
	 * 行幅を返します。
	 * 
	 * @return
	 */
	public final double getLineSize() {
		final BlockParams params = this.getBlockParams();
		double lineSize = LayoutUtils.getMaxAdvance(this);
		final int columnCount = this.getColumnCount();
		if (columnCount >= 2) {
			// マルチカラム
			lineSize = (lineSize + params.columns.gap) / columnCount - params.columns.gap;
		}
		return lineSize;
	}

	public int getColumnCount() {
		return 1;
	}

	/**
	 * 行方向サイズが AUTO(内容依存)であれば true を返します(M2c)。
	 */
	public final boolean isAutoLineSize() {
		return this.size.getLineType(this.getBlockParams().flow) == LengthType.AUTO;
	}

	public final boolean canColumnBreak() {
		final int columnCount = this.getColumnCount();
		if (columnCount < 2) {
			return false;
		}
		if (this.isSpecifiedPageSize()) {
			return true;
		}
		return this.getActualColumnCount() < columnCount;
	}

	protected int getActualColumnCount() {
		if (this.container instanceof ColumnsContainer columns) {
			return columns.getColumnCount();
		}
		return 1;
	}

	public final boolean isFixedMulticolumn() {
		if (this.getColumnCount() <= 1) {
			return false;
		}
		BlockParams params = this.getBlockParams();
		if (params.flow.isVertical()) {
			if (this.size.getWidthType() == LengthType.AUTO) {
				return false;
			}
		} else {
			if (this.size.getHeightType() == LengthType.AUTO) {
				return false;
			}
		}
		return true;
	}

	public final void balance(final BlockBuilder builder) {
		final Container oldCont = this.container;

		final int acc = this.getActualColumnCount();
		final int columnCount = this.getColumnCount();
		final boolean vertical = this.getBlockParams().flow.isVertical();
		final double pageSize;
		if (acc >= 2) {
			// 既に複数段に分かれて構築済みの場合は元の単一スタックが失われて
			// いるため、旧来の均等割り(総量/段数の一回スナップ)を使う
			final double total = oldCont.getContentSize() + (vertical ? this.width : this.height) * (acc - 1);
			pageSize = oldCont.getCutPoint(total / columnCount);
		} else {
			// 単一スタックの切り下げ境界で実切断を模擬し、全段に収まる
			// 最小容量を探索する(M5-B)
			pageSize = ColumnBalancer.balance(oldCont::getCutPointBelow, oldCont.getContentSize(), columnCount);
		}
		if (vertical) {
			this.maxPageAxis = this.width = pageSize;
		} else {
			this.maxPageAxis = this.height = pageSize;
		}

		this.container = new FlowContainer();
		this.container.setBox(this);

		final ColumnBuilder columnBuilder = new ColumnBuilder(builder, this);
		// バランス時この multicol は閉じた部分木(直後が SAX ヘッド)なので、
		// 内容をソースから再構築できる(M6c)。ボックス再生と違い非破壊で、
		// 将来は容量プローブの反復にも使える。範囲不明・Opaque 含み・
		// 分割済み(アンカー無効)の場合はボックス再生へフォールバック
		final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = builder.getPageContext();
		final boolean replayed = root != null && root.isSegmentRestyle()
				&& net.zamasoft.foliojet.layout.SourceReplayer.replayChildren(
						root.getPageGenerator().getLayoutSource(), this.getSourceAnchor(), columnBuilder,
						root.getPageGenerator());
		if (!replayed) {
			oldCont.restyle(columnBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED, true);
		}
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getFirstAscent() {
		return this.container.getFirstAscent();
	}

	public final double getLastDescent() {
		return this.container.getLastDescent();
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final double getTextIndent() {
		final double textIndent;
		final BlockParams params = this.getBlockParams();
		switch (params.textIndent.getType()) {
		case ABSOLUTE:
			textIndent = params.textIndent.getLength();
			break;
		case RELATIVE:
			textIndent = params.textIndent.getLength() * this.getLineSize();
			break;
		case MIXED:
			textIndent = params.textIndent.getLength() + params.textIndent.getRatio() * this.getLineSize();
			break;
		default:
			throw new IllegalStateException();
		}
		return textIndent;
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	public final void addFlow(IFlowBox box, double pageAxis) {
		this.container.addFlow(box, pageAxis);
	}

	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.container.addAbsolute(box, staticX, staticY);
	}

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.container.addFloating(box, lineAxis, pageAxis);
	}

	/**
	 * このクラス自体に局所処理は無い(2026-07-20、finishLayout反復化。
	 * 局所処理を持つ具象サブクラスはこれをオーバーライドする)。
	 */
	public void finishLayoutSelf(IFramedBox containerBox) {
	}

	public void pushFinishLayoutChildren(final IFramedBox containerBox,
			final java.util.Deque<FinishLayoutStep> worklist) {
		final Container container = this.container;
		worklist.push(w -> container.pushFinishLayoutChildren(containerBox, w));
	}

	protected final Shape clip(Shape clip, double x, double y) {
		if (this.getBlockParams().overflow != OverflowMode.HIDDEN) {
			return clip;
		}
		Rectangle2D.Double newClip = new Rectangle2D.Double(
				x + this.frame.frame.border.getLeft().width + this.frame.margin.left,
				y + this.frame.frame.border.getTop().width + this.frame.margin.top,
				this.width + this.frame.padding.getFrameWidth(), this.height + this.frame.padding.getFrameHeight());
		if (clip == null) {
			return newClip;
		}
		return newClip.createIntersection((Rectangle2D) clip);
	}

	protected abstract AbstractContainerBox splitPage(Container container, double pageLimit, boolean columnSpanning);

	public Container newColumn(double pageLimit, final BreakMode mode, final byte flags) {
		// このpageLimitは内辺から始まる
		final Container newContainer = this.container.splitPageAxis(pageLimit, mode, flags);

		if (newContainer == null) {
			return null;
		}
		final ColumnsContainer columns;
		if (this.container instanceof ColumnsContainer cc) {
			columns = cc;
			if (newContainer == columns.getLastColumn()) {
				return null;
			}
		} else {
			if (newContainer == this.container) {
				return null;
			}
			this.container = columns = new ColumnsContainer((FlowContainer) this.container);
			columns.setBox(this);
		}
		if (this.getBlockParams().flow.isVertical()) {
			this.width = pageLimit;
		} else {
			this.height = pageLimit;
		}
		columns.newColumn();
		return newContainer;
	}

	public SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final BreakMode xmode = BreakMode.absorbColumn(mode, this.getColumnCount());
		// コンテナ側の三義的返値の解釈はここに集約(コンテナ内部の型付けは M4-A3b)
		final Container nextContainer = this.container.splitPageAxis(pageLimit, xmode, flags);
		if (nextContainer == null) {
			return SplitResult.KEEP;
		}
		if (nextContainer == this.container) {
			return SplitResult.MOVE;
		}
		return new SplitResult.Split(
				this.splitPage(nextContainer, pageLimit, mode instanceof BreakMode.ColumnBreakMode));
	}

	public final void pushGetTextSteps(StringBuilder textBuff, java.util.Deque<GetTextStep> worklist) {
		this.container.pushGetTextSteps(textBuff, worklist);
	}
	
	public final void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x,
			double y, java.util.Deque<TextShapeStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		transform = this.transform(transform, x, y);
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		this.container.pushTextShapeSteps(pageBox, path, transform, x, y, worklist);
	}

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		this.container.restyle(builder, shape, false);
	}
}
