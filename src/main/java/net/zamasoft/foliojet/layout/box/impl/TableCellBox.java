package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.CellAlign;

import net.zamasoft.foliojet.layout.box.params.EmptyCellsMode;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;

import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * テーブルセルの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCellBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TableCellBox extends AbstractContainerBox {
	private static final boolean DEBUG = false;

	protected final BlockParams params;

	protected final TableCellPos pos;

	protected double verticalAlign = 0, pageSize = 0;

	protected boolean collapse;

	protected boolean forceDraw;

	public TableCellBox(final BlockParams params, final TableCellPos pos, final Container container) {
		this(params, pos, params.size, params.minSize, new AbsoluteRectFrame(params.frame), container);
	}

	public TableCellBox(final BlockParams params, final TableCellPos pos, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, Container container) {
		super(size, minSize, container);
		this.params = params;
		this.pos = pos;
		this.frame = frame;
	}

	public final BoxType getType() {
		return BoxType.TABLE_CELL;
	}

	public final Params getParams() {
		return this.params;
	}

	public final BlockParams getBlockParams() {
		return this.params;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableCellPos getTableCellPos() {
		return this.pos;
	}

	public final boolean isSpecifiedPageSize() {
		return false;
	}

	public void setPageAxis(double newSize) {
		if (newSize > this.pageSize) {
			this.pageSize = newSize;
		}
		super.setPageAxis(newSize);
	}

	public final void setWidth(double width) {
		assert !LayoutUtils.isNone(width);
		this.width = width - this.frame.getFrameWidth();
	}

	public final void setHeight(double height) {
		//System.out.println("setInnerHeight:"+this.height+"/"+height);
		assert !LayoutUtils.isNone(height);
		this.height = height - this.frame.getFrameHeight();
	}

	public final void verticalAlign() {
		switch (this.pos.verticalAlign) {
		case CellAlign.START:
		case CellAlign.BASELINE:
			// 上寄せ・ベースライン
			return;
		}
		double pageSize;
		if (this.params.flow.isVertical()) {
			pageSize = this.width;
		} else {
			pageSize = this.height;
		}
		double diff = Math.max(0, pageSize - this.pageSize);
		switch (this.pos.verticalAlign) {
		case CellAlign.END:
			// 下寄せ
			this.verticalAlign = diff;
			break;
		case CellAlign.MIDDLE:
			// 中央寄せ
			this.verticalAlign = diff / 2.0;
			break;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * 表Pass B(行計測)用のscratch複製を作ります(E-6増分5b-1、2026-07-24——
	 * codex設計§4.4「確定列幅でセルrangeを再生し寸法だけ取得して破棄」の
	 * 計測プリミティブの部品)。prepareLayout・列幅適用
	 * (setWidth/setHeight)済みの自分と同じレイアウト初期状態
	 * (フレーム・min/maxページ方向寸法・つぶし境界フラグ・両軸の内寸)を
	 * 持つ新品を返す。フレームは防御コピー(複製側のレイアウトが
	 * 自分の状態へ触れない)。段組セル(非FlowContainer)は未対応でnull
	 * (呼び出し側がPass B対象外として扱う)。
	 *
	 * @return 複製セル。段組セルはnull
	 */
	public final TableCellBox newMeasureReplica() {
		if (!this.canMeasureReplica()) {
			// 段組セルのコンテナ複製は未対応(Pass B対象外)
			return null;
		}
		final AbsoluteRectFrame frameCopy = new AbsoluteRectFrame(this.frame.frame);
		frameCopy.margin = new AbsoluteInsets(this.frame.margin.top, this.frame.margin.right, this.frame.margin.bottom,
				this.frame.margin.left);
		frameCopy.padding.set(this.frame.padding);
		final TableCellBox replica = new TableCellBox(this.params, this.pos, this.size, this.minSize, frameCopy,
				new net.zamasoft.foliojet.layout.box.content.FlowContainer());
		replica.collapse = this.collapse;
		replica.minPageAxis = this.minPageAxis;
		replica.maxPageAxis = this.maxPageAxis;
		replica.width = this.width;
		replica.height = this.height;
		return replica;
	}

	/**
	 * {@link #newMeasureReplica()}が複製を作れるか(=段組セルでないか)を
	 * 複製を作らずに判定します(E-6増分5b-2、2026-07-24——表Pass Cの
	 * 表単位適格判定{@code RetainedTableBuilder.isRowSequentialBindEligible}
	 * がbind前スキャンで使う)。
	 */
	public final boolean canMeasureReplica() {
		return this.container instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer;
	}

	public final void prepareLayout(double lineSize, TableBox tableBox, AbsoluteInsets spacing) {
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		this.frame.margin = spacing;

		TableParams tableParams = tableBox.getTableParams();
		this.collapse = tableParams.borderCollapse == TableParams.BORDER_COLLAPSE;
		RectFrame frame = this.frame.frame;
		if (this.collapse) {
			this.frame.frame = RectFrame.create(frame.margin, RectBorder.NONE_RECT_BORDER, frame.background,
					frame.padding);
		}

		if (this.params.flow.isVertical()) {
			switch (this.minSize.getWidthType()) {
			case ABSOLUTE:
				this.minPageAxis = this.minSize.getWidth();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.minPageAxis = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (params.maxSize.getWidthType()) {
			case ABSOLUTE:
				this.maxPageAxis = this.params.maxSize.getWidth();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.maxPageAxis = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			this.width = this.minPageAxis;
		} else {
			switch (this.minSize.getHeightType()) {
			case ABSOLUTE:
				this.minPageAxis = this.minSize.getHeight();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.minPageAxis = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (params.maxSize.getHeightType()) {
			case ABSOLUTE:
				this.maxPageAxis = this.params.maxSize.getHeight();
				break;
			case RELATIVE:
			case MIXED:
			case AUTO:
				this.maxPageAxis = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			this.height = this.minPageAxis;
		}
	}

	public final void baseline(double rowAscent) {
		// System.err.println("baseline: " + rowAscent);
		if (this.pos.verticalAlign != CellAlign.BASELINE) {
			return;
		}
		double firstAscent = this.getFirstAscent();
		if (LayoutUtils.isNone(firstAscent)) {
			return;
		}
		double xascent = rowAscent - firstAscent;
		if (xascent > 0) {
			this.verticalAlign += xascent;
			if (this.params.flow.isVertical()) {
				this.width += xascent;
			} else {
				this.height += xascent;
			}
		}
	}

	public final boolean isContextBox() {
		return this.getTableCellPos().offset != null;
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, java.util.Deque<FramesStep> worklist) {
		if (this.params.opacity == 0) {
			return;
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		if (this.draw()) {
			Drawable drawable = new TableCellBoxDrawable(clip, pageBox, this.params.opacity, transform,
					this.frame.frame.background, this.frame.frame.border, this.frame.frame.padding, this.collapse, this.frame.margin,
					this.getWidth(), this.getHeight());
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		if (this.params.flow.isVertical()) {
			x -= this.verticalAlign;
		} else {
			y += this.verticalAlign;
		}
		this.container.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0 || this.isContextBox() || !this.container.hasFloatings()) {
			return;
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		if (this.params.overflow == OverflowMode.HIDDEN) {
			// クリッピング
			clip = this.clip(clip, x, y);
		}
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		if (this.params.flow.isVertical()) {
			x -= this.verticalAlign;
		} else {
			y += this.verticalAlign;
		}
		// floatsはIBox.drawと同じく完結した1つの入口点なので、自前の
		// ワークリストを作って最後まで消化してから返る(2026-07-20)
		final java.util.Deque<DrawStep> worklist = new java.util.ArrayDeque<>();
		this.container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(0, 1, 1));
			drawer.visitDrawable(drawable, x, y);
		}
		if (this.isContextBox()) {
			this.frames(pageBox, drawer, clip, transform, x, y);
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}
		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		if (this.params.flow.isVertical()) {
			x -= this.verticalAlign;
		} else {
			y += this.verticalAlign;
		}

		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		final boolean contextBox = this.isContextBox();
		if (contextBox) {
			contextX = x;
			contextY = y;
		}
		final Drawer fdrawer = drawer;
		final double fx = x, fy = y, fcontextX = contextX, fcontextY = contextY;
		final Shape flowsClip = clip;
		final Shape absolutesClip = contextBox ? clip : null;
		// 元の実行順(floatings[contextBoxのみ]→flows→absolutes→endStruct)を
		// 保つため、スタックへは逆順でpushする
		worklist.push(w -> pageBox.endStruct(fdrawer, this.params.element, structCount, fx, fy));
		this.container.pushDrawAbsolutes(pageBox, drawer, visitor, absolutesClip, transform, fcontextX, fcontextY, x,
				y, worklist);
		this.container.pushDrawFlows(pageBox, drawer, visitor, flowsClip, transform, fcontextX, fcontextY, x, y,
				worklist);
		if (contextBox) {
			this.container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, fcontextX, fcontextY, x, y,
					worklist);
		}
	}

	private final boolean draw() {
		if (DEBUG) {
			return true;
		}
		if (!this.frame.isVisible()) {
			return false;
		}
		if (this.pos.emptyCells == EmptyCellsMode.SHOW) {
			return true;
		}
		if (this.collapse) {
			return true;
		}
		if (this.container.hasFlows()) {
			return true;
		}
		if (this.container.hasFloatings()) {
			return true;
		}
		if (this.forceDraw) {
			return true;
		}
		return false;
	}

	protected static class TableCellBoxDrawable extends BackgroundBorderDrawable {
		protected final boolean collapse;
		protected final AbsoluteInsets spacing;

		public TableCellBoxDrawable(Shape clip, PageBox pageBox, float opacity, AffineTransform transform,
				Background background, RectBorder border, Insets padding, boolean collapse, AbsoluteInsets spacing, double width,
				double height) {
			super(pageBox, clip, opacity, transform, background, border, padding, width, height);
			this.collapse = collapse;
			this.spacing = spacing;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			if (this.collapse) {
				this.background.draw(gc, x, y, this.width, this.height, this.border, this.padding, null);// TODO text clip
			} else {
				x += this.spacing.left;
				y += this.spacing.top;
				double width = this.width - this.spacing.getFrameWidth();
				double height = this.height - this.spacing.getFrameHeight();
				if (width >= 0 || height >= 0) {
					this.background.draw(gc, x, y, width, height, this.border, this.padding, null);// TODO text clip
					this.border.draw(gc, x, y, width, height);
				}
			}
		}
	}

	protected final AbstractContainerBox splitPage(Container container, double pageLimit, boolean columnSpanning) {
		final boolean vertical = this.params.flow.isVertical();
		// 断片状態の計算は TableCutter に純化(C4-T2)
		final net.zamasoft.foliojet.layout.fragment.TableCutter.CellFragmentState state = net.zamasoft.foliojet.layout.fragment.TableCutter
				.cellFragmentState(vertical, this.size, this.minSize, this.frame,
						vertical ? this.width : this.height, pageLimit);

		// 分割断片は継続物(アンカーなし — 新品として再生されない。P0)
		final TableCellBox cell = new TableCellBox(this.params, this.pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
		cell.collapse = this.collapse;
		cell.forceDraw = this.draw();
		this.frame = state.prevFrame();
		if (vertical) {
			cell.height = this.height;
			this.width = pageLimit;
		} else {
			cell.width = this.width;
			this.height = pageLimit;
		}
		this.forceDraw = this.draw();
		return cell;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// A-3bのアラインメント物理契約: セル内容へ渡す切断位置は
		// 「行の物理分割線 - verticalAlign(実測の確定セル高と内容高の
		// 差から計算される内容開始オフセット)」。継続セルは
		// verticalAlign=0から始まる——元セルの先頭側余白は前断片で
		// 消費済みであり、残余内容を再アラインしない(2026-07-24文書化、
		// docs/history/2026-07-23-a3b-goal-narrowed.md参照)。
		pageLimit -= this.verticalAlign;
		final SplitResult result = super.split(pageLimit, mode, flags);
		// System.err.println("CELL A: pageLimit=" + pageLimit + "/mode=" + mode
		// + "/flags=" + flags + "/" + (nextBox == null) + "/"
		// + (nextBox == this));
		if (!(result instanceof SplitResult.Split(final IPageBreakableBox remainder))) {
			assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0;
			return result;
		}
		final TableCellBox nextBox = (TableCellBox) remainder;
		if (this.container.hasFloatings()) {
			pageLimit -= this.frame.getFramePageStart(this.params.flow);
			// P2-4: 型付きAPIへ移行。セルとその残余セルのcontainerは常に
			// 同型なので、残余側が段組(非FlowContainer)なのは自分も段組の
			// 場合だけ——そのときはColumnsContainer実装がtargetを見ずに
			// KEEP_OWNERを返すため、フォールバックのKEEPは実際には使われない
			final net.zamasoft.foliojet.layout.box.content.FloatTransferTarget target = nextBox.container instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer flowContainer
					? new net.zamasoft.foliojet.layout.box.content.FloatTransferTarget.Existing(flowContainer)
					: net.zamasoft.foliojet.layout.box.content.FloatTransferTarget.KEEP;
			this.container.splitFloatings(target, pageLimit, flags);
		}
		return result;
	}
}
