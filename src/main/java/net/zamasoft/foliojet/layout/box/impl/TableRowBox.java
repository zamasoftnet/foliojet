package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * テーブル行の実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowBox.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class TableRowBox extends AbstractInnerTableBox implements IPageBreakableBox {
	private static final boolean DEBUG = false;

	protected final TableRowPos pos;

	public static interface Cell {
		public boolean isSource();

		public TableCellBox getCellBox();

		public Cell getSource();

		public void setNextExtendedCell(ExtendedCell extended);

		public ExtendedCell getNextExtendedCell();

		public TableRowBox getTableRow();
	}

	public static interface ExtendedCell extends Cell {
		public void setSourceCell(Cell source);
	}

	protected static abstract class AbstractCell implements Cell {
		protected ExtendedCell extended;
		protected final TableRowBox row;

		protected AbstractCell(TableRowBox row) {
			this.row = row;
		}

		public ExtendedCell getNextExtendedCell() {
			return this.extended;
		}

		public void setNextExtendedCell(ExtendedCell extended) {
			this.extended = extended;
		}

		public TableRowBox getTableRow() {
			return this.row;
		}
	}

	protected static class SourceCellImpl extends AbstractCell {
		protected final TableCellBox cell;

		public SourceCellImpl(TableCellBox cell, TableRowBox row) {
			super(row);
			this.cell = cell;
		}

		public boolean isSource() {
			return true;
		}

		public Cell getSource() {
			return this;
		}

		public TableCellBox getCellBox() {
			return this.cell;
		}
	}

	protected static class ExtendedCellImpl extends AbstractCell implements ExtendedCell {
		protected Cell source;

		public ExtendedCellImpl(TableRowBox row) {
			super(row);
		}

		public boolean isSource() {
			return false;
		}

		public Cell getSource() {
			return this.source;
		}

		public TableCellBox getCellBox() {
			return this.source.getCellBox();
		}

		public void setSourceCell(Cell source) {
			this.source = source;
		}
	}

	protected final List<Cell> cells = new ArrayList<Cell>();

	public TableRowBox(final InnerTableParams params, final TableRowPos pos) {
		super(params);
		this.pos = pos;
	}

	public final BoxType getType() {
		return BoxType.TABLE_ROW;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableRowPos getTableRowPos() {
		return this.pos;
	}

	public final void setLineSize(double lineSize) {
		this.lineSize = lineSize;
	}

	public final void setPageSize(double pageSize) {
		this.pageSize = pageSize;
	}

	public final Cell addTableSourceCell(TableCellBox cellBox) {
		Cell source = new SourceCellImpl(cellBox, this);
		this.cells.add(source);
		return source;
	}

	public final ExtendedCell addTableExtendedCell(Cell cell) {
		ExtendedCell extended = new ExtendedCellImpl(this);
		extended.setSourceCell(cell.getSource());
		cell.setNextExtendedCell(extended);
		this.cells.add(extended);
		return extended;
	}

	public final Cell getCell(int i) {
		return (Cell) this.cells.get(i);
	}

	public final int getCellCount() {
		return this.cells.size();
	}

	/** 表示される行背景または元セルを持つか。rowspan の延長セルは重複して見ない。 */
	@Override
	public boolean paintsAnything() {
		if (this.params.opacity == 0) {
			return false;
		}
		if (this.params.background.isVisible()) {
			return true;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			final Cell cell = this.cells.get(i);
			if (cell.isSource() && cell.getCellBox().paintsAnything()) {
				return true;
			}
		}
		return false;
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
	}

	public final void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		if (this.cells == null) {
			return;
		}
		// 元の走査順(先頭セルから)を保つため、スタックへは逆順(末尾セルから)でpushする
		for (int i = this.cells.size() - 1; i >= 0; --i) {
			Cell cell = (Cell) this.cells.get(i);
			if (cell.isSource()) {
				worklist.push(IBox.step(cell.getCellBox(), containerBox));
			}
		}
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.params.background.isVisible()) {
			Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.background, this.params.border, null, this.getWidth(), this.getHeight());
			drawer.visitDrawable(drawable, x, y);
		}
		if (this.cells == null) {
			return;
		}
		// セルの描画対象と座標を先に(副作用なく)計算してから、元の走査順を
		// 保つため**逆順**でpushする
		final int n = this.cells.size();
		final TableCellBox[] sourceCells = new TableCellBox[n];
		final double[] xs = new double[n];
		final double[] ys = new double[n];
		int count = 0;
		if (this.tableParams.flow.isVertical()) {
			// 縦書き
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					sourceCells[count] = cellBox;
					// 連結セルは行のページ寸法を超える。向きの扱いは
					// LayoutUtils.drawX に任せる(従来はRL専用式を手書きして
					// おり、vertical-lr で連結セルだけ表の外へずれていた。
					// 通常セルは幅==行のページ寸法なので誤りが相殺され、
					// rowspan セルでだけ現れる。2026-07-25、独立レビューで発見)
					xs[count] = LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, 0, cellBox.getWidth(), 0);
					ys[count] = y;
					++count;
				}
				y += cellBox.getHeight();
			}
		} else {
			// 横書き
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					sourceCells[count] = cellBox;
					xs[count] = x;
					ys[count] = y;
					++count;
				}
				x += cellBox.getWidth();
			}
		}
		for (int i = count - 1; i >= 0; --i) {
			worklist.push(
					AbstractContainerBox.framesStep(sourceCells[i], pageBox, drawer, clip, transform, xs[i], ys[i]));
		}
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.cells == null) {
			return;
		}
		if (this.tableParams.flow.isVertical()) {
			for (int i = 0; i < this.cells.size(); ++i) {
				// 縦書き
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					cellBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY,
							LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, 0, cellBox.getWidth(), 0), y);

				}
				y += cellBox.getHeight();
			}
		} else {
			// 横書き
			for (int i = 0; i < this.cells.size(); ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					cellBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);

				}
				x += cellBox.getWidth();
			}
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}
		if (DEBUG) {
			drawer.visitDrawable(new Drawable() {
				public void draw(GC gc, double x, double y) throws GraphicsException {
					try (final var gcState = gc.begin()) {
						gc.setStrokePaint(RGBColor.create(127, 127, 255));
						double width = 0;
						for (int i = 0; i < TableRowBox.this.cells.size(); ++i) {
							Cell cell = (Cell) TableRowBox.this.cells.get(i);
							width += cell.getCellBox().getWidth();
						}
						gc.draw(new Rectangle2D.Double(x, y, width, getHeight()));
					}
				}
			}, x, y);
		}
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}
		if (this.cells == null) {
			return;
		}
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
		// セルの描画対象と座標を先に(副作用なく)計算してから、元の走査順を
		// 保つため**逆順**でpushする
		final int n = this.cells.size();
		final TableCellBox[] sourceCells = new TableCellBox[n];
		final double[] drawXs = new double[n];
		final double[] drawYs = new double[n];
		int sourceCount = 0;
		if (this.tableParams.flow.isVertical()) {
			// 縦書き
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource()) {
					sourceCells[sourceCount] = cellBox;
					drawXs[sourceCount] = LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, 0, cellBox.getWidth(), 0);
					drawYs[sourceCount] = y;
					++sourceCount;
				}
				y += cellBox.getHeight();
			}
		} else {
			// 横書き
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource()) {
					sourceCells[sourceCount] = cellBox;
					drawXs[sourceCount] = x;
					drawYs[sourceCount] = y;
					++sourceCount;
				}
				x += cellBox.getWidth();
			}
		}
		final Drawer fdrawer = drawer;
		final double fx = x, fy = y;
		worklist.push(w -> pageBox.endStruct(fdrawer, this.params.element, structCount, fx, fy));
		for (int i = sourceCount - 1; i >= 0; --i) {
			worklist.push(IBox.drawStep(sourceCells[i], pageBox, drawer, visitor, clip, transform, contextX,
					contextY, drawXs[i], drawYs[i]));
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		if (this.cells == null) {
			return;
		}
		// 元の走査順を保つため、スタックへは逆順でpushする
		for (int i = this.cells.size() - 1; i >= 0; --i) {
			Cell cell = (Cell) this.cells.get(i);
			worklist.push(IBox.getTextStep(cell.getCellBox(), textBuff));
		}
	}

	/**
	 * セルの切断位置です(C4-T3)。連結セル(rowspan)は連結元の行から
	 * 当行までのページ寸を加算する — 切断線はセル上端基準になる。
	 *
	 * <p>
	 * A-3bのアラインメント物理契約(2026-07-24文書化、
	 * `docs/history/2026-07-23-a3b-goal-narrowed.md`が正本): 行内の
	 * 全セルは**同一の物理分割線**で切られ(後発セルで初めて分割が
	 * 決まった場合は処理済みセルへ遡って強制分割)、各セルへは
	 * この上端基準位置から更に{@code verticalAlign}(実測の
	 * 確定セル高と内容高の差)を引いた内容座標が渡される
	 * ({@code TableCellBox.split}参照)。この表専用分割機構は
	 * FragmentRecipe系の共通継続IRへは統合しない(意図的な隔離領域
	 * ——共通IRは並列に開く複数セルを構造的に表現できないため。
	 * 設計相談で確定、再開条件は上記文書参照)。
	 * </p>
	 */
	private static double cellCutPageAxis(final Cell cell, final double pageLimit) {
		double cutPageAxis = pageLimit;
		if (!cell.isSource()) {
			final Cell sCell = cell.getSource();
			cutPageAxis += sCell.getTableRow().getPageSize();
			for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell
					.getNextExtendedCell()) {
				if (xcell == cell) {
					break;
				}
				cutPageAxis += xcell.getTableRow().getPageSize();
			}
		}
		return cutPageAxis;
	}

	/**
	 * FLAGS_SPLIT付きセル分割の契約(必ず{@code Split}を返し、残余は
	 * {@code TableCellBox})を明示検査して残余セルを返します。従来は
	 * uncheckedなcastで、契約違反が{@code ClassCastException}として
	 * 現れていた(2026-07-24アーキレビューE-1: 契約違反時の例外の種類を
	 * 明確化するのみで、正常経路のロジックは不変)。
	 */
	private static TableCellBox forcedCellRemainder(final TableCellBox cellBox, final double cutPageAxis,
			final BreakMode mode, final byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) != 0;
		return net.zamasoft.foliojet.layout.fragment.TableCutter.requireSplitRemainder(
				cellBox.split(cutPageAxis, mode, flags), TableCellBox.class, "TableCellBox.split with FLAGS_SPLIT");
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;

		final boolean vertical = this.tableParams.flow.isVertical();
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) == 0) {
			// 前置判定は TableCutter に純化(C4-T3)
			final double[] cellPageExtents = new double[this.cells.size()];
			final boolean[] cellFlowMatch = new boolean[this.cells.size()];
			final boolean[] cellInsideAvoid = new boolean[this.cells.size()];
			final boolean[] cellCollapsedAtStart = new boolean[this.cells.size()];
			for (int i = 0; i < this.cells.size(); ++i) {
				final TableCellBox cellBox = ((Cell) this.cells.get(i)).getCellBox();
				final BlockParams cellParams = cellBox.getBlockParams();
				cellPageExtents[i] = cellBox.getPageExtent(this.tableParams.flow);
				cellFlowMatch[i] = cellParams.flow.isVertical() == vertical;
				cellInsideAvoid[i] = cellParams.pageBreakInside == PageBreakMode.AVOID;
				cellCollapsedAtStart[i] = cellBox.getFrame().getFramePageStart(this.tableParams.flow) <= 0
						&& LayoutUtils.compare(cellBox.getInnerPageExtent(this.tableParams.flow), 0) <= 0;
			}
			final SplitResult pre = net.zamasoft.foliojet.layout.fragment.TableCutter.rowPreDecide(
					(flags & IPageBreakableBox.FLAGS_FIRST) != 0,
					(flags & IPageBreakableBox.FLAGS_FIRST_ROW) != 0, pageLimit, this.getPageSize(),
					this.params.pageBreakInside == PageBreakMode.AVOID, cellPageExtents, cellFlowMatch,
					cellInsideAvoid, cellCollapsedAtStart);
			if (pre != null) {
				return pre;
			}
		}
		// System.err.println("TR B/flags=" + flags + "/" + pageLimit);
		byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
		final double pageWindow = this.pageSize - pageLimit;
		TableRowBox nextRowBox = null;
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
			// 必ず切断する
			// 分割断片は継続物(アンカーなし — 新品として再生されない。P0)
			nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
			nextRowBox.setTableParams(this.tableParams);
			this.pageSize = pageLimit;
			nextRowBox.pageSize = pageWindow;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			TableCellBox prevCellBox = cell.getCellBox();
			TableCellBox nextCellBox;
			final double cutPageAxis = cellCutPageAxis(cell, pageLimit);
			// System.err.println(prevCellBox.getInnerHeight());
			final SplitResult cellResult = prevCellBox.split(cutPageAxis, mode, xflags);
			// System.err.println("TR C: " + i + "/" + xflags + "/pass="
			// + (nextCellBox == prevCellBox) + "/leave="
			// + (nextCellBox == null) + "/" + mode + "/" + cutPageAxis);
			if (cellResult instanceof SplitResult.Split(final IPageBreakableBox cellRemainder)) {
				if (!(cellRemainder instanceof TableCellBox typedRemainder)) {
					throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
							"TableCellBox.split remainder must be a TableCellBox but was " + cellRemainder);
				}
				nextCellBox = typedRemainder;
			} else {
				if (nextRowBox == null) {
					continue;
				}
				// 他に分割されたセルがある場合、強制切断する
				byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
				nextCellBox = forcedCellRemainder(prevCellBox, cutPageAxis, mode, xxflags);
			}
			// System.err.println("TR C: " + i + "/prevHeight="
			// + prevCellBox.getInnerHeight() + "/" + cutPageAxis);
			if (nextRowBox == null) {
				nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
				nextRowBox.setTableParams(this.tableParams);
				this.pageSize = pageLimit;
				nextRowBox.pageSize = pageWindow;
				// System.err.println("ROW Split:"+pageLimit+"/"+pageWindow);
				for (int j = 0; j < i; ++j) {
					Cell cell2 = (Cell) this.cells.get(j);
					TableCellBox prevCell2 = cell2.getCellBox();
					final double cutPageAxis2 = cellCutPageAxis(cell2, pageLimit);
					byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
					TableCellBox nextCell2 = forcedCellRemainder(prevCell2, cutPageAxis2, mode, xxflags);
					// System.err.println("TR D: " + j + "/cell splitted ="
					// + prevCell2.getInnerHeight() + "/"
					// + nextCell2.getInnerHeight());
					if (vertical) {
						prevCell2.setWidth(cutPageAxis2);
					} else {
						prevCell2.setHeight(cutPageAxis2);
					}
					this.restyleCell(nextCell2);
					Cell source = nextRowBox.addTableSourceCell(nextCell2);
					ExtendedCell xcell = cell2.getNextExtendedCell();
					double span = 1;
					if (xcell != null) {
						source.setNextExtendedCell(xcell);
						do {
							++span;
							xcell.setSourceCell(source);
							xcell = xcell.getNextExtendedCell();
						} while (xcell != null);
					}
					nextRowBox.pageSize = Math.max(nextRowBox.pageSize,
							nextCell2.getPageExtent(this.tableParams.flow) / span);
				}
			}
			if (vertical) {
				prevCellBox.setWidth(cutPageAxis);
			} else {
				prevCellBox.setHeight(cutPageAxis);
			}
			this.restyleCell(nextCellBox);
			Cell source = nextRowBox.addTableSourceCell(nextCellBox);
			ExtendedCell xcell = cell.getNextExtendedCell();
			double span = 1;
			if (xcell != null) {
				source.setNextExtendedCell(xcell);
				do {
					++span;
					xcell.setSourceCell(source);
					xcell = xcell.getNextExtendedCell();
				} while (xcell != null);
			}
			nextRowBox.pageSize = Math.max(nextRowBox.pageSize, nextCellBox.getPageExtent(this.tableParams.flow) / span);
		}

		// System.err.println("TR nextRowBox: pass=" + (nextRowBox ==
		// null)+"/"+flags);
		if (nextRowBox == null) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return SplitResult.KEEP;
			}
			// 現在の行を持ち越す
			return SplitResult.MOVE;
		}

		// 分割の後処理
		// System.err.println("ROW break:" + "/" + mode + "/" +
		// nextRowBox.height);
		for (int i = 0; i < nextRowBox.cells.size(); ++i) {
			Cell cell = (Cell) nextRowBox.cells.get(i);
			double rowSize = nextRowBox.pageSize;
			for (ExtendedCell xcell = cell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				rowSize += xcell.getTableRow().getPageSize();
			}
			TableCellBox nextCell = cell.getCellBox();
			// System.err.println("TR RowHeight:" + i + "/" + rowHeight);
			if (vertical) {
				nextCell.setWidth(rowSize);
			} else {
				nextCell.setHeight(rowSize);
			}
		}
		return new SplitResult.Split(nextRowBox);
	}

	public final void cutRowspanCells() {
		net.zamasoft.foliojet.layout.builder.impl.TableBuildStats.ROWSPAN_CUTS.incrementAndGet();
		// 連結されたセルを強制切断する
		final boolean vertical = this.tableParams.flow.isVertical();
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			if (cell.isSource()) {
				continue;
			}
			TableCellBox prevCell = cell.getCellBox();
			// 切断面は行の上辺、即ちセルの高さから現在行の高さを引いたもの
			Cell sCell = cell.getSource();
			double cutPageAxis = sCell.getTableRow().getPageSize();
			for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				if (xcell == cell) {
					break;
				}
				cutPageAxis += xcell.getTableRow().getPageSize();
			}
			// System.err.println(cutPageAxis);
			TableCellBox nextCell = forcedCellRemainder(prevCell, cutPageAxis, BreakMode.DEFAULT_BREAK_MODE,
					IPageBreakableBox.FLAGS_SPLIT);
			if (vertical) {
				prevCell.setWidth(cutPageAxis);
			} else {
				prevCell.setHeight(cutPageAxis);
			}
			this.restyleCell(nextCell);
			// addTableSourceCellの代わりに直接更新する
			Cell source = new SourceCellImpl(nextCell, this);
			this.cells.set(i, source);
			ExtendedCell xcell = cell.getNextExtendedCell();
			int span = 1;
			if (xcell != null) {
				source.setNextExtendedCell(xcell);
				do {
					++span;
					xcell.setSourceCell(source);
					xcell = xcell.getNextExtendedCell();
				} while (xcell != null);
			}
			// 行の高さをセルの高さを行数で割ったもので更新
			this.pageSize = Math.max(this.pageSize, nextCell.getPageExtent(this.tableParams.flow) / span);
			// System.err.println("TR cutRowSpanCells: " + i + "/"
			// + prevCell.getHeight());
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			double rowSize = this.pageSize;
			for (ExtendedCell xcell = cell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				rowSize += xcell.getTableRow().getPageSize();
			}
			TableCellBox nextCell = cell.getCellBox();
			if (vertical) {
				nextCell.setWidth(rowSize);
			} else {
				nextCell.setHeight(rowSize);
			}
		}
	}

	private final void restyleCell(TableCellBox nextCell) {
		// 再レイアウトにFIXEDボックスは関与しないのでpageContextBuilderはnullでよい
		final BlockBuilder cellBindBuilder = new BlockBuilder(null, nextCell);
		nextCell.restyle(cellBindBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
		cellBindBuilder.close();
	}
}
