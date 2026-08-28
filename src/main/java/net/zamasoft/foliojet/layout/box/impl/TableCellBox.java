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
	 * セル内容へ適用する論理ブロック軸の整列量です。明示された
	 * {@code align-content} は、HTML/CSS2由来の {@code vertical-align}
	 * より優先します。通常値では従来のセル整列をそのまま保ちます。
	 */
	private double contentAlignmentOffset() {
		return this.params.blockAlignContent == net.zamasoft.foliojet.layout.box.params.BoxAlignment.NORMAL
				? this.verticalAlign
				: this.blockContentAlignmentOffset();
	}

	@Override
	protected double blockAlignedX(final double x) {
		return this.params.flow.isVertical()
				? x + LayoutUtils.pageAxisSign(this.params.flow) * this.contentAlignmentOffset()
				: x;
	}

	@Override
	protected double blockAlignedY(final double y) {
		return this.params.flow.isVertical() ? y : y + this.contentAlignmentOffset();
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
					this.getWidth(), this.getHeight()).withBlendMode(this.params.blendMode);
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);
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

		if (this.params.overflow.clipsPaint()) {
			// クリッピング
			clip = this.clip(clip, x, y);
		}
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);
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
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);

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
		// ただし整列余白は「セル全体が1つの断片に収まる」前提でしか意味を
		// 持たない。確定セル高がrowspanや背の高い隣接セルのせいで内容より
		// ずっと大きいと、**余白だけで切断線を越えてしまい**、内容が1単位も
		// 前断片に残らない。前ページには境界だけ・文字は次ページ、という
		// 読み順の逆転になる(2026-07-27、不変条件7で検出。seed 130 では
		// 行2が[ ][ ][T12]と[T10][T11][ ]に割れた)。
		// そこで**先頭の不可分単位(先頭行)が前断片に残る範囲まで**しか
		// 余白を残さない。余白が足りている通常のセルには当たらない
		// (条件が成立するのは、余白のせいで前断片が内容ゼロになる場合だけ)。
		final double savedVerticalAlign = this.verticalAlign;
		if (this.verticalAlign > 0) {
			final double fragmentInner = pageLimit - this.frame.getFramePageStart(this.params.flow);
			// getCutPoint(0) は「0以上で最初に現れる切断可能位置」= 先頭の
			// 不可分単位の下端(純粋な問い合わせ。段組の均し
			// AbstractContainerBox でも同じ意味で使っている)
			final double firstUnitEnd = this.container.getCutPoint(0);
			if (LayoutUtils.compare(this.verticalAlign + firstUnitEnd, fragmentInner) > 0) {
				this.verticalAlign = Math.max(0, fragmentInner - firstUnitEnd);
			}
		}
		pageLimit -= this.verticalAlign;
		final SplitResult result;
		try {
			result = super.split(pageLimit, mode, flags);
		} catch (RuntimeException | Error e) {
			// 途中で失敗したら詰めた整列余白を戻す(縮んだままだと、
			// 失敗後にこのセルを描く経路で余白が失われる)
			this.verticalAlign = savedVerticalAlign;
			throw e;
		}
		// System.err.println("CELL A: pageLimit=" + pageLimit + "/mode=" + mode
		// + "/flags=" + flags + "/" + (nextBox == null) + "/"
		// + (nextBox == this));
		if (!(result instanceof SplitResult.Split(final IPageBreakableBox remainder))) {
			// 切断されなかった(丸ごと残る・丸ごと移動する)場合、セルは
			// 確定高のまま描かれるので整列余白を元に戻す
			this.verticalAlign = savedVerticalAlign;
			assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0;
			return result;
		}
		// **浮動体をここで移送し直さない**(2026-08-03)。
		//
		// 上の super.split() は AbstractContainerBox.split →
		// FlowContainer.splitPageAxis と降りていき、その最後で
		// splitFloatings(Existing(残余のcontainer), ...) まで済ませている。
		// ここでもう一度 splitFloatings を呼ぶと、次の二重の害がある:
		//
		// 1. **既に切断済みの浮動体をもう一度切断する。** 1回目で内容は
		//    残余断片へ移っているので、2回目は**中身が空の断片**を作る
		// 2. **移送先の台帳を上書きする。** FlowContainer.remainderWith は
		//    `container.floatings = moved` と代入するので、1回目に移した
		//    内容入りの断片が、2回目の空の断片で置き換わる
		//
		// 結果として、浮動体の中身が出力から**黙って消えていた**。再現は
		// files/fuzz-repro/nested-float-content-loss.html(細い箱・表・
		// 右寄せ・左寄せの4つが揃うと内側の浮動体の文字が消える)。
		// 原因の特定にはcodex・agyへの独立相談が効いた。
		return result;
	}
}
