package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TablePos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.part.TableCollapsedBorders;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * テーブルの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TableBox extends AbstractBox implements IPageBreakableBox, IFlowBox, INonReplacedBox {
	private static final boolean DEBUG = false;

	protected final TableParams params;

	protected final AbstractBlockBox block;

	protected AbsoluteRectFrame frame;

	protected TableColumnGroupBox columnGroupBox = null;

	protected TableRowGroupBox headerGroupBox = null;

	/**
	 * この表が分割の継続断片(splitTableBoxで作られた後ろ半分)かどうかです
	 * (タグ付きPDF欠陥②の修正、2026-07-30)。継続断片に表示されるヘッダは
	 * 「同じ要素の反復表示」であって継続ではない——{@link #isRepeatedGroup}
	 * が使う。
	 */
	private boolean tableContinuation = false;

	protected List<TableRowGroupBox> bodyGroups = null;

	protected TableRowGroupBox footerGroupBox = null;

	protected TableCollapsedBorders borders;

	protected double width = 0;

	protected double height = 0;

	protected double offsetX = 0;

	protected double offsetY = 0;

	public TableBox(final TableParams params, final AbstractBlockBox block) {
		this(params, new AbsoluteRectFrame(params.frame), block);
	}

	protected TableBox(final TableParams params, final AbsoluteRectFrame frame, final AbstractBlockBox block) {
		this.params = params;
		this.block = block;
		this.frame = frame;
	}

	public final BoxType getType() {
		return BoxType.TABLE;
	}

	public final Pos getPos() {
		return TablePos.POS;
	}

	public final Params getParams() {
		return this.params;
	}

	public final TableParams getTableParams() {
		return this.params;
	}

	public final AbstractBlockBox getBlockBox() {
		return this.block;
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final void setSize(double width, double height) {
		this.width = width;
		this.height = height;
	}

	public final void calculateFrame(double lineSize) {
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.params.frame.margin, lineSize);
		if (this.params.borderCollapse == TableParams.BORDER_SEPARATE) {
			// 分離境界モデル
			//
			// ■ パディングの計算
			//
			LayoutUtils.computePaddings(this.frame.padding, this.params.frame.padding, lineSize);
			this.frame.padding.top = params.borderSpacingV / 2.0;
			this.frame.padding.right = params.borderSpacingH / 2.0;
			this.frame.padding.bottom = params.borderSpacingV / 2.0;
			this.frame.padding.left = params.borderSpacingH / 2.0;
		} else {
			this.frame.frame = RectFrame.create(params.frame.margin, RectBorder.NONE_RECT_BORDER,
					params.frame.background, params.frame.padding);
			return;
		}
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
	}

	public final void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// 元の走査順(header→body(0..n)→footer)を保つため、スタックへは逆順でpushする
		if (this.footerGroupBox != null) {
			worklist.push(IBox.step(this.footerGroupBox, containerBox));
		}
		for (int i = this.getTableBodyCount() - 1; i >= 0; --i) {
			worklist.push(IBox.step(this.getTableBody(i), containerBox));
		}
		if (this.headerGroupBox != null) {
			worklist.push(IBox.step(this.headerGroupBox, containerBox));
		}
	}

	public final void setCollapsedBorders(TableCollapsedBorders borders) {
		assert borders != null;
		this.borders = borders;
	}

	public final TableCollapsedBorders getCollapsedBorders() {
		return this.borders;
	}

	public final void setTableColumnGroup(TableColumnGroupBox columnGroup) {
		this.columnGroupBox = columnGroup;
	}

	public final void setTableHeader(TableRowGroupBox headerGroup) {
		this.headerGroupBox = headerGroup;
		if (this.params.flow.isVertical()) {
			this.width += headerGroup.getWidth();
			if (headerGroup.getHeight() > this.height) {
				this.height = headerGroup.getHeight();
			}
		} else {
			this.height += headerGroup.getHeight();
			if (headerGroup.getWidth() > this.width) {
				this.width = headerGroup.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableHeader() {
		return this.headerGroupBox;
	}

	public final void setTableFooter(TableRowGroupBox footerGroup) {
		this.footerGroupBox = footerGroup;
		if (this.params.flow.isVertical()) {
			this.width += footerGroup.getWidth();
			if (footerGroup.getHeight() > this.height) {
				this.height = footerGroup.getHeight();
			}
		} else {
			this.height += footerGroup.getHeight();
			if (footerGroup.getWidth() > this.width) {
				this.width = footerGroup.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableFooter() {
		return this.footerGroupBox;
	}

	public final void addTableBody(TableRowGroupBox rowGroupBox) {
		if (this.bodyGroups == null) {
			this.bodyGroups = new ArrayList<TableRowGroupBox>();
		}
		this.bodyGroups.add(rowGroupBox);
		if (this.params.flow.isVertical()) {
			this.width += rowGroupBox.getWidth();
			if (rowGroupBox.getHeight() > this.height) {
				this.height = rowGroupBox.getHeight();
			}
		} else {
			this.height += rowGroupBox.getHeight();
			if (rowGroupBox.getWidth() > this.width) {
				this.width = rowGroupBox.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableBody(int i) {
		return (TableRowGroupBox) this.bodyGroups.get(i);
	}

	public final int getTableBodyCount() {
		return this.bodyGroups == null ? 0 : this.bodyGroups.size();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 表は {@link AbstractContainerBox} 系ではないため、何もしなければ
	 * {@link IBox} の安全側の既定値 ({@code true}) を返します。しかし、行・列・
	 * 背景・罫線を一つも持たない空表は実際には何も描きません。縦組みで末尾の
	 * 空表に明示幅があると、幅だけを理由に次ページへ送られ、その空ページが
	 * 「描く可能性あり」と誤認されて残っていました (fuzz seed 5141)。
	 * </p>
	 *
	 * <p>
	 * HTML の匿名表ボックス生成は、空の {@code display:table} にも匿名の行・
	 * セルを作り得ます。そのため「行がある」だけでは判定せず、列・行・セルの
	 * 背景・罫線・内容まで {@code paintsAnything()} でたどります。つぶし境界は
	 * 実際に見える境界があるかを調べます。
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		if (this.params.opacity == 0) {
			return false;
		}
		if (this.params.frame.background.isVisible()) {
			return true;
		}
		if (this.params.borderCollapse == TableParams.BORDER_SEPARATE) {
			if (this.frame.frame.border.isVisible()) {
				return true;
			}
		} else if (this.borders != null && this.borders.paintsAnything()) {
			return true;
		}
		if (this.columnGroupBox != null && this.columnGroupBox.paintsAnything()) {
			return true;
		}
		if (this.headerGroupBox != null && this.headerGroupBox.paintsAnything()) {
			return true;
		}
		if (this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				if (this.bodyGroups.get(i).paintsAnything()) {
					return true;
				}
			}
		}
		return this.footerGroupBox != null && this.footerGroupBox.paintsAnything();
	}

	private void drawBorders(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x, double y,
			double xx, double yy) {
		switch (this.params.borderCollapse) {
		case TableParams.BORDER_SEPARATE: {
			if (!this.frame.frame.border.isVisible()) {
				break;
			}
			// 分離境界
			Drawable drawable = new BorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.frame.frame.border,
					this.width + this.frame.padding.getFrameWidth() + this.frame.frame.border.getFrameWidth(),
					this.height + this.frame.padding.getFrameHeight() + this.frame.frame.border.getFrameHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, x + this.frame.margin.left, y + this.frame.margin.top);
		}
			break;

		case TableParams.BORDER_COLLAPSE: {
			// つぶし境界
			Drawable drawable = new CollapsedBordersDrawable(pageBox, clip, this.params.opacity, transform,
					this.borders, this.params.flow.isVertical()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, xx, yy);
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x) : "Undefined x";
		assert !LayoutUtils.isNone(y) : "Undefined y";
		x += this.offsetX;
		y += this.offsetY;

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}
		double xx = x + this.frame.getFrameLeft();
		double yy = y + this.frame.getFrameTop();

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		if (this.params.frame.background.isVisible()) {
			Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.frame.background, this.params.frame.border, this.params.frame.padding,
					this.getWidth() - this.frame.getFrameWidth(), this.getHeight() - this.frame.getFrameHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, xx, yy);
		}

		// frames/floatsの各パスはまだ再帰実装のまま(別課題、RELIABILITY-PLAN.md
		// 参照)。テーブルのネスト段数は行/セル数に依存しない有界な段数
		// なので、ここでは同期的に呼んでよい。内容(draw)パスだけを
		// 反復化する——子の描画座標を先に(副作用なく)計算してから、
		// 元の走査順を保つため**逆順**でworklistへpushする。
		final List<IBox> contentBoxes = new ArrayList<>();
		final List<Double> contentXs = new ArrayList<>();
		final List<Double> contentYs = new ArrayList<>();

		// 縦横で同一の構造だった2つの分岐を統合(2026-07-25、vertical-lr対応)。
		// 論理位置(ページ方向の始端/終端)だけを数え、物理座標への変換は
		// LayoutUtils.drawX/drawY に任せる。従来は縦書き側でRL専用式
		// (カーソルを右端から減算)を手書きしており、これが重複していたことが
		// vertical-lrの取りこぼしを生んでいた。
		final WritingMode flow = this.params.flow;
		final double tableExtent = flow.isVertical() ? this.width : this.height;
		// ページ方向を消費するグループを、文書順(header→body→footer)に並べる
		final List<TableRowGroupBox> groups = new ArrayList<>();
		if (this.headerGroupBox != null) {
			groups.add(this.headerGroupBox);
		}
		if (this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				groups.add((TableRowGroupBox) this.bodyGroups.get(i));
			}
		}
		if (this.footerGroupBox != null) {
			groups.add(this.footerGroupBox);
		}
		final int groupCount = groups.size();
		final double[] groupXs = new double[groupCount];
		final double[] groupYs = new double[groupCount];
		{
			double pageStart = 0;
			for (int i = 0; i < groupCount; ++i) {
				final TableRowGroupBox group = groups.get(i);
				final double pageEnd = pageStart + (flow.isVertical() ? group.getWidth() : group.getHeight());
				groupXs[i] = LayoutUtils.drawX(flow, xx, tableExtent, pageStart, pageEnd, 0);
				groupYs[i] = LayoutUtils.drawY(flow, yy, pageStart, 0);
				pageStart = pageEnd;
			}
		}
		// 列グループは表全体に渡るので、ページ方向の区間は [0, tableExtent]
		final double columnGroupX = LayoutUtils.drawX(flow, xx, tableExtent, 0, tableExtent, 0);
		final double columnGroupY = LayoutUtils.drawY(flow, yy, 0, 0);

		// 内部の境界/背景
		if (this.columnGroupBox != null) {
			this.columnGroupBox.frames(pageBox, drawer, clip, transform, columnGroupX, columnGroupY);
		}
		for (int i = 0; i < groupCount; ++i) {
			groups.get(i).frames(pageBox, drawer, clip, transform, groupXs[i], groupYs[i]);
		}

		this.drawBorders(pageBox, drawer, clip, transform, x, y, xx, yy);

		// 浮動ボックス(列グループは対象外)
		for (int i = 0; i < groupCount; ++i) {
			final TableRowGroupBox group = groups.get(i);
			final boolean repetition = this.isRepeatedGroup(group);
			if (repetition) {
				pageBox.pushStructRepetition();
			}
			group.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, groupXs[i], groupYs[i]);
			if (repetition) {
				pageBox.popStructRepetition();
			}
		}

		// 内容
		if (this.columnGroupBox != null) {
			contentBoxes.add(this.columnGroupBox);
			contentXs.add(columnGroupX);
			contentYs.add(columnGroupY);
		}
		for (int i = 0; i < groupCount; ++i) {
			contentBoxes.add(groups.get(i));
			contentXs.add(groupXs[i]);
			contentYs.add(groupYs[i]);
		}
		final Drawer fdrawer = drawer;
		final double fx = x, fy = y;
		if (DEBUG) {
			worklist.push(w -> {
				Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(1, 0, 1));
				fdrawer.visitDrawable(drawable, fx, fy);
			});
		}
		worklist.push(w -> pageBox.endStruct(fdrawer, this.params.element, structCount, fx, fy));
		for (int i = contentBoxes.size() - 1; i >= 0; --i) {
			final IBox content = contentBoxes.get(i);
			final boolean repetition = this.isRepeatedGroup(content);
			if (repetition) {
				// LIFOなので実行順は push→drawStep→pop になる
				worklist.push(w -> pageBox.popStructRepetition());
			}
			worklist.push(IBox.drawStep(content, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					contentXs.get(i), contentYs.get(i)));
			if (repetition) {
				worklist.push(w -> pageBox.pushStructRepetition());
			}
		}
	}

	/**
	 * このグループの表示が「同じ要素の反復」かを返します(タグ付きPDF
	 * 欠陥②の修正、2026-07-30)。継続断片のヘッダと、後続断片を持つ断片の
	 * フッタが該当する(ヘッダの原本は先頭断片、フッタの原本は最終断片)。
	 * 反復の描画中はページ横断レジストリを迂回し、従来どおりページごとの
	 * StructElemを宣言する——継続として併合すると同じ内容がページ数ぶん
	 * 1つの要素に重複してしまう。
	 */
	private boolean isRepeatedGroup(final IBox box) {
		return (box == this.headerGroupBox && this.tableContinuation)
				|| (box == this.footerGroupBox && this.isFragmented());
	}

	public final void pushGetTextSteps(final StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// 元の走査順(header→body(0..n)→footer)を保つため、スタックへは
		// 逆順でpushする
		if (this.footerGroupBox != null) {
			worklist.push(IBox.getTextStep(this.footerGroupBox, textBuff));
		}
		if (this.bodyGroups != null) {
			for (int i = this.bodyGroups.size() - 1; i >= 0; --i) {
				TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
				worklist.push(IBox.getTextStep(rowGroup, textBuff));
			}
		}
		if (this.headerGroupBox != null) {
			worklist.push(IBox.getTextStep(this.headerGroupBox, textBuff));
		}
	}
	
	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d,
			Deque<TextShapeStep> worklist) {
		// TODO
	}

	protected static class BorderDrawable extends AbstractDrawable {
		protected final RectBorder border;
		protected final double width, height;

		public BorderDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform, RectBorder border,
				double width, double height) {
			super(pageBox, clip, opacity, transform);
			this.border = border;
			this.width = width;
			this.height = height;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			this.border.draw(gc, x, y, this.width, this.height);
		}

		@Override
		public String describe() {
			return String.format(java.util.Locale.ROOT, "TableBorder[w=%.2f h=%.2f]", this.width, this.height);
		}
	}

	protected static class CollapsedBordersDrawable extends AbstractDrawable {
		protected final TableCollapsedBorders borders;
		protected final boolean vertical;

		public CollapsedBordersDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				TableCollapsedBorders borders, boolean vertical) {
			super(pageBox, clip, opacity, transform);
			this.borders = borders;
			this.vertical = vertical;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			BorderRenderer.INSTANCE.drawTableCollapseBorders(gc, this.borders, x, y, this.vertical);

		}

		/**
		 * 境界内容を序列化します(display-list golden 用)。NONE 以外の
		 * 各グリッド境界を H列,添字 / V行,添字 = [style,width,color] で列挙。
		 */
		public String describe() {
			final StringBuilder sb = new StringBuilder("CollapsedBorders[");
			final int rows = this.borders.getRowCount();
			final int cols = this.borders.getColumnCount();
			sb.append(cols).append('x').append(rows).append(this.vertical ? " vertical" : "");
			for (int col = 0; col < cols; ++col) {
				for (int i = 0; i <= rows; ++i) {
					final net.zamasoft.foliojet.layout.box.params.Border b = this.borders.getHBorder(col, i);
					if (b != null && !b.isNull()) {
						sb.append(" H").append(col).append(',').append(i).append('=').append(b);
					}
				}
			}
			for (int row = 0; row < rows; ++row) {
				for (int i = 0; i <= cols; ++i) {
					final net.zamasoft.foliojet.layout.box.params.Border b = this.borders.getVBorder(row, i);
					if (b != null && !b.isNull()) {
						sb.append(" V").append(row).append(',').append(i).append('=').append(b);
					}
				}
			}
			return sb.append(']').toString();
		}
	}

	/**
	 * 表全体を運ぶ判定の共通出口です(2026-08-23)。Moveのときソース再生を
	 * 無効化する——表の字句的ソース区間はfoster parentingで表外へ確定した
	 * 内容を含みうるため、MOVE後の再生は確定済み内容を複製する
	 * ({@code AbstractBox.invalidateSourceReplay}参照)。構築済みの箱は
	 * box-restyleフォールバックが運ぶ。
	 */
	private SplitResult keepOrMoveWholeTable(final byte flags) {
		final SplitResult result = net.zamasoft.foliojet.layout.fragment.TableCutter.keepOrMoveAll(flags);
		if (result instanceof SplitResult.Move) {
			this.invalidateSourceReplay();
		}
		return result;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		// assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// System.err.println("TABLE A: flags=" + flags + "/pageLimit=" +
		// pageLimit
		// + "/mode=" + mode + "/height=" + this.getHeight() + "/bodies="
		// + (this.bodyGroups == null ? 0 : this.bodyGroups.size()) + "/"
		// + this.getParams().element);

		final boolean vertical = this.params.flow.isVertical();
		int origBodyRowCount = 0;
		if (this.borders != null && this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				origBodyRowCount += this.getTableBody(i).getTableRowCount();
			}
		}
		if (mode instanceof BreakMode.ForceBreakMode) {
			// 行間強制改ページ
			TableForceBreakMode force = (TableForceBreakMode) mode;
			TableBox nextTable = this.splitTableBox();
			int rowGroup = force.rowGroup;
			int row = force.row;
			if (row != -1) {
				assert force.box.getType() == BoxType.TABLE_ROW || force.box.getType() == BoxType.TABLE_ROW_GROUP;
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) this.bodyGroups.get(rowGroup);
				TableRowGroupBox newRowGroupBox = net.zamasoft.foliojet.layout.fragment.TableCutter
						.requireSplitRemainder(rowGroupBox.split(pageLimit, mode, (byte) 0), TableRowGroupBox.class,
								"TableRowGroupBox.split for a forced break between rows");
				nextTable.addTableBody(newRowGroupBox);
				if (vertical) {
					this.width -= newRowGroupBox.getPageSize();
				} else {
					this.height -= newRowGroupBox.getPageSize();
				}
			}
			for (int j = rowGroup + 1; j < this.bodyGroups.size(); ++j) {
				nextTable.addTableBody((TableRowGroupBox) this.bodyGroups.get(j));
			}
			for (int j = this.bodyGroups.size() - 1; j > rowGroup; --j) {
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) this.bodyGroups.remove(j);
				if (vertical) {
					this.width -= rowGroupBox.getPageSize();
				} else {
					this.height -= rowGroupBox.getPageSize();
				}
			}
			if (this.borders != null) {
				// つぶし境界
				nextTable.borders = this.borders.splitPageAxis(this, nextTable, origBodyRowCount);
			}
			return new SplitResult.Split(nextTable);
		}

		if (this.bodyGroups == null || this.bodyGroups.isEmpty()) {
			return this.keepOrMoveWholeTable(flags);
		}

		// 上下の改ページしない部分(ヘッダ・フッタ等)の高さを差し引く
		// (判定は TableCutter に純化)
		final double headerSize = this.headerGroupBox != null ? this.headerGroupBox.getPageSize() : -1;
		final double footerSize = this.footerGroupBox != null ? this.footerGroupBox.getPageSize() : -1;
		if (vertical) {
			pageLimit = net.zamasoft.foliojet.layout.fragment.TableCutter.reserveNonBreakable(pageLimit,
					this.getWidth(), this.frame.getFrameRight(), this.frame.getFrameLeft(), this.frame.margin.left,
					headerSize, footerSize);
		} else {
			pageLimit = net.zamasoft.foliojet.layout.fragment.TableCutter.reserveNonBreakable(pageLimit,
					this.getHeight(), this.frame.getFrameTop(), this.frame.getFrameBottom(), this.frame.margin.bottom,
					headerSize, footerSize);
		}

		// テーブルのヘッダとフッタがおさまらない
		if (LayoutUtils.compare(pageLimit, 0) <= 0) {
			return this.keepOrMoveWholeTable(flags);
		}

		TableBox nextTable = null;
		int i;
		boolean ignoreBreakAvoid = false;
		double savePageLimit = pageLimit;
		// System.err.println("C: " +pageLimit + "/" +
		// this.getHeight());
		for (i = 0; i < this.bodyGroups.size(); ++i) {
			final TableRowGroupBox prevRowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
			double prevRowGroupSize = prevRowGroup.getPageSize();
			if (i < this.bodyGroups.size() - 1 && LayoutUtils.compare(pageLimit, prevRowGroupSize) > 0) {
				pageLimit -= prevRowGroupSize;
				continue;
			}
			byte lflags = (byte) (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT);
			if (i > 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			final SplitResult groupResult = prevRowGroup.split(pageLimit, mode, (byte) (lflags & flags));
			assert nextTable == null || !(groupResult instanceof SplitResult.Keep);
			// System.err.println("TABLE D:
			// "+lflags+"/"+flags+"/"+index+"/"+(nextTable
			// != null)+"/"+(nextRowGroup != null));
			if (groupResult instanceof SplitResult.Keep) {
				if (!ignoreBreakAvoid && i == 0 && (flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
					// ページ先頭の場合は改ページ禁止を無視してやりなおす
					ignoreBreakAvoid = true;
					pageLimit = savePageLimit;
					i = -1;
					continue;
				}
				pageLimit -= prevRowGroupSize;
				continue;
			}
			if (groupResult instanceof SplitResult.Move) {
				if (i == 0) {
					// 全部移動(ソース再生は無効化——keepOrMoveWholeTable参照)
					assert (flags & IPageBreakableBox.FLAGS_FIRST) == 0;
					this.invalidateSourceReplay();
					return SplitResult.MOVE;
				}
				if (!ignoreBreakAvoid) {
					final TableRowGroupBox beforeGroup = (TableRowGroupBox) this.bodyGroups.get(i - 1);
					// 判定は TableCutter に純化
					if (net.zamasoft.foliojet.layout.fragment.TableCutter.groupBreakAvoid(
							beforeGroup.getTableRowGroupPos().pageBreakAfter,
							prevRowGroup.getTableRowGroupPos().pageBreakBefore,
							beforeGroup.getTableRowCount() > 0
									? beforeGroup.getTableRow(beforeGroup.getTableRowCount() - 1)
											.getTableRowPos().pageBreakAfter
									: PageBreakMode.AUTO,
							prevRowGroup.getTableRowCount() > 0
									? prevRowGroup.getTableRow(0).getTableRowPos().pageBreakBefore
									: PageBreakMode.AUTO)) {
						// 行グループの改ページ禁止
						// 一つ戻って前の行グループを末尾で切る
						pageLimit = beforeGroup.getPageSize() - LayoutUtils.THRESHOLD * 2;
						i -= 2;
						continue;
					}
				}
				nextTable = this.splitTableBox();
				break;
			}
			nextTable = this.splitTableBox();
			prevRowGroupSize -= prevRowGroup.getPageSize();
			if (vertical) {
				this.width -= prevRowGroupSize;
			} else {
				this.height -= prevRowGroupSize;
			}
			nextTable.addTableBody(net.zamasoft.foliojet.layout.fragment.TableCutter.requireSplitRemainder(groupResult,
					TableRowGroupBox.class, "TableRowGroupBox.split at the table cut line"));
			++i;
			break;
		}

		if (nextTable == null) {
			return this.keepOrMoveWholeTable(flags);
		}

		int remove = 0;
		for (int j = i; j < this.bodyGroups.size(); ++j) {
			final TableRowGroupBox prevRowGroup = (TableRowGroupBox) this.bodyGroups.get(j);
			if (vertical) {
				this.width -= prevRowGroup.getPageSize();
			} else {
				this.height -= prevRowGroup.getPageSize();
			}
			nextTable.addTableBody(prevRowGroup);
			++remove;
		}
		for (int j = 0; j < remove; ++j) {
			this.bodyGroups.remove(this.bodyGroups.size() - 1);
		}
		if (this.columnGroupBox != null) {
			// カラム
			if (vertical) {
				nextTable.columnGroupBox = (TableColumnGroupBox) this.columnGroupBox.splitPageAxis(this.width,
						nextTable.width);
			} else {
				nextTable.columnGroupBox = (TableColumnGroupBox) this.columnGroupBox.splitPageAxis(this.height,
						nextTable.height);
			}
		}
		if (this.borders != null) {
			// つぶし境界
			nextTable.borders = this.borders.splitPageAxis(this, nextTable, origBodyRowCount);
		}
		return new SplitResult.Split(nextTable);
	}

	public final TableBox splitTableBox() {
		net.zamasoft.foliojet.layout.builder.impl.TableBuildStats.TABLE_FRAGMENTS.incrementAndGet();
		// 表セット T-b(2026-07-30): 前断片(this)はアンカーを保持し続けるが、
		// その範囲は継続断片が持っている残り(row切断の進捗)も含む——
		// 前断片をソース再生すると表全体が再構築されて分割の進捗が巻き戻る。
		// AbstractBlockBoxの実分割と同じく断片化の印を付け、
		// isSourceReplayable()=falseでstampRanges/再生の対象から外す
		this.markFragmented();
		final boolean vertical = this.params.flow.isVertical();
		// フレームの切断判定は TableCutter に純化(C4-T2)
		final net.zamasoft.foliojet.layout.fragment.TableCutter.TableFragmentFrames frames = net.zamasoft.foliojet.layout.fragment.TableCutter
				.tableFragmentFrames(vertical, this.headerGroupBox != null, this.footerGroupBox != null, this.frame);
		// 分割断片は継続物(アンカーなし — 新品として再生されない。P0)
		TableBox nextTable = new TableBox(this.params, frames.nextFrame(), this.block);
		// タグ付きPDF欠陥②(2026-07-30): 継続断片のヘッダは「反復表示」
		// (isRepeatedGroup参照)
		nextTable.tableContinuation = true;
		if (vertical) {
			nextTable.height = this.height;
		} else {
			nextTable.width = this.width;
		}

		if (this.headerGroupBox != null) {
			nextTable.setTableHeader(this.headerGroupBox);
		}
		if (this.footerGroupBox != null) {
			nextTable.setTableFooter(this.footerGroupBox);
		}
		this.frame = frames.prevFrame();
		return nextTable;
	}

	public final boolean avoidBreakBefore() {
		return false;
	}

	public final boolean avoidBreakAfter() {
		return false;
	}
}
