package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox.Cell;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;

import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * テーブル行グループの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowGroupBox.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class TableRowGroupBox extends AbstractInnerTableBox implements IPageBreakableBox {
	private static final boolean DEBUG = false;

	protected final TableRowGroupPos pos;

	protected List<TableRowBox> rows = null;

	public TableRowGroupBox(final InnerTableParams params, final TableRowGroupPos pos) {
		super(params);
		this.pos = pos;
	}

	public final BoxType getType() {
		return BoxType.TABLE_ROW_GROUP;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableRowGroupPos getTableRowGroupPos() {
		return this.pos;
	}

	public final void addTableRow(TableRowBox row) {
		assert row != null;
		if (this.rows == null) {
			this.rows = new ArrayList<TableRowBox>();
		}
		this.rows.add(row);
		this.pageSize += row.getPageSize();
		if (row.getLineSize() > this.lineSize) {
			this.lineSize = row.getLineSize();
		}
	}

	public final int getTableRowCount() {
		if (this.rows == null) {
			return 0;
		}
		return this.rows.size();
	}

	public final TableRowBox getTableRow(int i) {
		if (this.rows == null) {
			throw new ArrayIndexOutOfBoundsException(i);
		}
		return (TableRowBox) this.rows.get(i);
	}

	public final void finishLayout(IFramedBox containerBox) {
		for (int j = 0; j < this.getTableRowCount(); ++j) {
			TableRowBox rowBox = this.getTableRow(j);
			rowBox.finishLayout(containerBox);
		}
	}

	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.params.background.isVisible()) {
			Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.background, this.params.border, null, this.getWidth(), this.getHeight());
			drawer.visitDrawable(drawable, x, y);
		}
		if (this.rows == null) {
			return;
		}
		if (this.tableParams.flow.isVertical()) {
			// 縦書き
			x += this.pageSize;
			for (int i = 0; i < this.rows.size(); ++i) {
				TableRowBox row = (TableRowBox) this.rows.get(i);
				x -= row.getPageSize();
				row.frames(pageBox, drawer, clip, transform, x, y);
			}
		} else {
			// 横書き
			for (int i = 0; i < this.rows.size(); ++i) {
				TableRowBox row = (TableRowBox) this.rows.get(i);
				row.frames(pageBox, drawer, clip, transform, x, y);
				y += row.getPageSize();
			}
		}
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.rows == null) {
			return;
		}
		double pageStart = 0;
		for (int i = 0; i < this.rows.size(); ++i) {
			final TableRowBox row = (TableRowBox) this.rows.get(i);
			final double pageEnd = pageStart + row.getPageSize();
			row.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, pageEnd, 0),
					LayoutUtils.drawY(this.tableParams.flow, y, pageStart, 0));
			pageStart = pageEnd;
		}
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(.7f, .7f, 1));
			drawer.visitDrawable(drawable, x, y);
		}
		if (this.rows == null) {
			return;
		}
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
		double pageStart = 0;
		for (int i = 0; i < this.rows.size(); ++i) {
			final TableRowBox row = (TableRowBox) this.rows.get(i);
			final double pageEnd = pageStart + row.getPageSize();
			row.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, pageEnd, 0),
					LayoutUtils.drawY(this.tableParams.flow, y, pageStart, 0));
			pageStart = pageEnd;
		}
		pageBox.endStruct(drawer, this.params.element, structCount, x, y);
	}

	public final void getText(StringBuilder textBuff) {
		if (this.rows == null) {
			return;
		}
		for (int i = 0; i < this.rows.size(); ++i) {
			// 通常のフロー
			IBox box = (IBox) this.rows.get(i);
			box.getText(textBuff);
		}
	}

	public final SplitResult split(double pageLimit, BreakMode mode, final byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// System.err.println("TRG A:" + pageLimit + "/" + mode
		// + "/" + flags+"/"+this.getHeight() + "/"
		// + (this.rows == null ? 0 : this.rows.size()));
		if (mode instanceof BreakMode.ForceBreakMode) {
			// 強制改ページ
			TableForceBreakMode force = (TableForceBreakMode) mode;
			TableRowGroupBox nextRowGroup = this.splitTableRowGroup();
			int row = force.row;
			if (row != -1) {
				for (int j = row + 1; j < this.rows.size(); ++j) {
					TableRowBox rowBox = (TableRowBox) this.rows.get(j);
					this.pageSize -= rowBox.getPageSize();
					nextRowGroup.addTableRow(rowBox);
				}
				for (int j = this.rows.size() - 1; j > row; --j) {
					this.rows.remove(j);
				}
			}
			return new SplitResult.Split(nextRowGroup);
		}

		if (LayoutUtils.compare(pageLimit, 0) < 0) {
			// 切断線より下にある場合
			return SplitResult.KEEP;
		}
		if (LayoutUtils.compare(pageLimit, this.getPageSize()) >= 0) {
			// 移動なし
			return SplitResult.KEEP;
		}
		InnerTableParams con = this.params;
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0
				&& (con.pageBreakInside == PageBreakMode.AVOID || LayoutUtils.compare(pageLimit, 0) < 0)) {
			// 全部移動
			return SplitResult.MOVE;
		}

		// 空の場合
		if (this.rows == null || this.rows.isEmpty()) {
			return net.zamasoft.foliojet.layout.fragment.TableCutter.keepOrMoveAll(flags);
		}

		// はみ出した行を移動
		// System.err.println("B: flags=" + flags + "/" + this.rows.size() + "/"
		// + pageLimit);
		TableRowGroupBox nextRowGroup = null;
		int i;
		boolean ignoreBreakAvoid = false;
		final double savePageLimit = pageLimit;
		// 上から下にチェック
		for (i = 0; i < this.rows.size(); ++i) {
			final TableRowBox prevRow = (TableRowBox) this.rows.get(i);
			double prevRowSize = prevRow.getPageSize();
			if (i < this.rows.size() - 1 && LayoutUtils.compare(pageLimit, prevRowSize) > 0) {
				// 切断線がかかっている行まですすむ
				pageLimit -= prevRowSize;
				continue;
			}
			byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
			{
				// ページ先頭での行フラグ(判定は TableCutter に純化)。
				// 連結したセルが先頭行にあるかチェック
				boolean linkedToTop = false;
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0 && i > 0) {
					final TableRowBox topRow = (TableRowBox) this.rows.get(0);
					for (int j = 0; j < prevRow.getCellCount() && j < topRow.getCellCount(); ++j) {
						if (prevRow.getCell(j).getCellBox().getParams() == topRow.getCell(j).getCellBox()
								.getParams()) {
							linkedToTop = true;
							break;
						}
					}
				}
				xflags = net.zamasoft.foliojet.layout.fragment.TableCutter.firstRowFlags(xflags, i, linkedToTop);
			}
			final SplitResult rowResult = prevRow.split(pageLimit, mode, xflags);
			// System.err.println("TRG C: xflags=" + xflags + "/row=" + i
			// + "/pageLimit=" + pageLimit + "/pass="
			// + (nextRow == prevRow) + "/leave=" + (nextRow == null));
			if (rowResult instanceof SplitResult.Keep) {
				if (!ignoreBreakAvoid && i == 0 && (flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
					// ページ先頭の場合は改ページ禁止を無視してやりなおす
					ignoreBreakAvoid = true;
					pageLimit = savePageLimit;
					i = -1;
					continue;
				}
				pageLimit -= prevRowSize;
				continue;
			}
			// 一度分割されたら、以降は持ち越し
			if (rowResult instanceof SplitResult.Move) {
				if (i == 0) {
					// 先頭の場合は全体を移動
					assert ((xflags & IPageBreakableBox.FLAGS_FIRST) == 0);
					return SplitResult.MOVE;
				}
				TableRowBox beforeRow = (TableRowBox) this.rows.get(i - 1);
				if (!ignoreBreakAvoid) {
					// 行間の改ページ禁止(判定は TableCutter に純化)
					final boolean tableVertical = this.tableParams.flow.isVertical();
					final boolean[] cuttable = new boolean[beforeRow.getCellCount()];
					final boolean[] extended = new boolean[beforeRow.getCellCount()];
					final boolean[] flowMatch = new boolean[beforeRow.getCellCount()];
					for (int j = 0; j < beforeRow.getCellCount(); ++j) {
						final Cell cell = beforeRow.getCell(j);
						final BlockParams cellParams = cell.getCellBox().getBlockParams();
						flowMatch[j] = cellParams.flow.isVertical() == tableVertical;
						cuttable[j] = cellParams.pageBreakInside == PageBreakMode.AUTO && flowMatch[j];
						extended[j] = cell.getNextExtendedCell() != null;
					}
					if (net.zamasoft.foliojet.layout.fragment.TableCutter.rowBreakAvoid(i,
							(flags & IPageBreakableBox.FLAGS_FIRST) != 0, beforeRow.getTableRowPos().pageBreakAfter,
							prevRow.getTableRowPos().pageBreakBefore, cuttable, extended, flowMatch)) {
						// 行の改ページ禁止
						if ((xflags & IPageBreakableBox.FLAGS_FIRST_ROW) != 0) {
							// ページ先頭行の場合は改ページ禁止を無視してやりなおす
							ignoreBreakAvoid = true;
							pageLimit = savePageLimit;
							i = -1;
							continue;
						}
						// 一つ戻って前の行を末尾で切る
						pageLimit = beforeRow.getPageSize() - LayoutUtils.THRESHOLD * 2;
						i -= 2;
						continue;
					}
				}

				// 書字方向が違えば必ず改ページしない(判定は TableCutter に純化)
				{
					final boolean tableVertical = this.tableParams.flow.isVertical();
					final boolean[] flowMatch = new boolean[prevRow.getCellCount()];
					for (int j = 0; j < prevRow.getCellCount(); ++j) {
						flowMatch[j] = prevRow.getCell(j).getCellBox().getBlockParams().flow
								.isVertical() == tableVertical;
					}
					if (net.zamasoft.foliojet.layout.fragment.TableCutter.mixedFlowKeep(flowMatch)) {
						return SplitResult.KEEP;
					}
				}

				// 持ち越す際に縦に連結されたセルを分割する
				prevRow.cutRowspanCells();
				nextRowGroup = this.splitTableRowGroup();
				break;
			}
			nextRowGroup = this.splitTableRowGroup();
			prevRowSize -= prevRow.getPageSize();
			this.pageSize -= prevRowSize;
			// System.err.println("D:" + prevRow.getHeight() +"/"+
			// nextRow.getHeight()+"/"+(nextRow == prevRow));
			nextRowGroup.addTableRow((TableRowBox) ((SplitResult.Split) rowResult).remainder());
			++i;
			break;
		}
		// System.err.println("E:" + this.getHeight()+"/"+remove + "/" +
		// this.rows.size());
		if (nextRowGroup == null) {
			return net.zamasoft.foliojet.layout.fragment.TableCutter.keepOrMoveAll(flags);
		}

		int remove = 0;
		for (int j = i; j < this.rows.size(); ++j) {
			TableRowBox prevRow = (TableRowBox) this.rows.get(j);
			this.pageSize -= prevRow.getPageSize();
			nextRowGroup.addTableRow(prevRow);
			++remove;
		}
		for (int j = 0; j < remove; ++j) {
			this.rows.remove(this.rows.size() - 1);
		}
		return new SplitResult.Split(nextRowGroup);
	}

	private TableRowGroupBox splitTableRowGroup() {
		// 分割断片は継続物(アンカーなし — 新品として再生されない。P0)
		final TableRowGroupBox nextRowGroup = new TableRowGroupBox(this.params, this.pos);
		nextRowGroup.setTableParams(this.tableParams);
		nextRowGroup.lineSize = this.lineSize;
		return nextRowGroup;
	}
}
