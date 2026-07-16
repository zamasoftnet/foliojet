package net.zamasoft.foliojet.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IFramedBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.style.box.impl.TableRowBox.Cell;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.InnerTableParams;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.style.draw.DebugDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
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
		if (this.tableParams.flow.isVertical()) {
			// 縦書き
			x += this.pageSize;
			for (int i = 0; i < this.rows.size(); ++i) {
				TableRowBox row = (TableRowBox) this.rows.get(i);
				x -= row.getPageSize();
				row.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		} else {
			// 横書き
			for (int i = 0; i < this.rows.size(); ++i) {
				TableRowBox row = (TableRowBox) this.rows.get(i);
				row.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
				y += row.getPageSize();
			}
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
		if (this.tableParams.flow.isVertical()) {
			// 縦書き
			x += this.pageSize;
			for (int i = 0; i < this.rows.size(); ++i) {
				TableRowBox row = (TableRowBox) this.rows.get(i);
				x -= row.getPageSize();
				row.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		} else {
			// 横書き
			for (int i = 0; i < this.rows.size(); ++i) {
				TableRowBox row = (TableRowBox) this.rows.get(i);
				row.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
				y += row.getPageSize();
			}
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

	public final IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, final byte flags) {
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
			return nextRowGroup;
		}

		if (StyleUtils.compare(pageLimit, 0) < 0) {
			// 切断線より下にある場合
			return null;
		}
		if (StyleUtils.compare(pageLimit, this.getPageSize()) >= 0) {
			// 移動なし
			return null;
		}
		InnerTableParams con = this.params;
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0
				&& (con.pageBreakInside == Types.PAGE_BREAK_AVOID || StyleUtils.compare(pageLimit, 0) < 0)) {
			// 全部移動
			return this;
		}

		// 空の場合
		if (this.rows == null || this.rows.isEmpty()) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return null;
			}
			return this;
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
			if (i < this.rows.size() - 1 && StyleUtils.compare(pageLimit, prevRowSize) > 0) {
				// 切断線がかかっている行まですすむ
				pageLimit -= prevRowSize;
				continue;
			}
			byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
			if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// ページの先頭の場合
				if (i > 0) {
					// ２つめ以降の行
					xflags ^= IPageBreakableBox.FLAGS_FIRST;
					boolean first = false;
					// 連結したセルが先頭にあるかチェック
					TableRowBox topRow = (TableRowBox) this.rows.get(0);
					for (int j = 0; j < prevRow.getCellCount(); ++j) {
						Cell prevCell = prevRow.getCell(j);
						if (j >= topRow.getCellCount()) {
							continue;
						}
						Cell topCell = topRow.getCell(j);
						if (prevCell.getCellBox().getParams() == topCell.getCellBox().getParams()) {
							first = true;
							break;
						}
					}
					if (first) {
						xflags |= IPageBreakableBox.FLAGS_FIRST_ROW;
					}
				} else {
					// 最初の行
					xflags |= IPageBreakableBox.FLAGS_FIRST_ROW;
				}
			}
			final TableRowBox nextRow = (TableRowBox) prevRow.splitPageAxis(pageLimit, mode, xflags);
			// System.err.println("TRG C: xflags=" + xflags + "/row=" + i
			// + "/pageLimit=" + pageLimit + "/pass="
			// + (nextRow == prevRow) + "/leave=" + (nextRow == null));
			if (nextRow == null) {
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
			if (nextRow == prevRow) {
				if (i == 0) {
					// 先頭の場合は全体を移動
					assert ((xflags & IPageBreakableBox.FLAGS_FIRST) == 0);
					return this;
				}
				TableRowBox beforeRow = (TableRowBox) this.rows.get(i - 1);
				if (!ignoreBreakAvoid) {
					// 改ページ禁止処理
					// System.err.println("TRG :" + i);

					// 行間の改ページ禁止
					boolean breakAvoid = beforeRow.getTableRowPos().pageBreakAfter == Types.PAGE_BREAK_AVOID
							|| prevRow.getTableRowPos().pageBreakBefore == Types.PAGE_BREAK_AVOID;
					// ページ先頭の1-2行目で連結されたセルがある場合は適用しない
					if (!breakAvoid && (i != 1 || (flags & IPageBreakableBox.FLAGS_FIRST) == 0)) {
						// 連結されたセルによる改ページ禁止
						for (int j = 0; j < beforeRow.getCellCount(); ++j) {
							final Cell cell = beforeRow.getCell(j);
							final BlockParams cellParams = cell.getCellBox().getBlockParams();
							if (cellParams.pageBreakInside == Types.PAGE_BREAK_AUTO && cellParams.flow.isVertical() == this.tableParams.flow.isVertical()) {
								continue;
							}
							if (cell.getNextExtendedCell() != null) {
								breakAvoid = true;
								break;
							}
						}
					} else if (i == 1 && (flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
						for (int j = 0; j < beforeRow.getCellCount(); ++j) {
							final Cell cell = beforeRow.getCell(j);
							if (cell.getNextExtendedCell() == null) {
								continue;
							}
							final BlockParams cellParams = cell.getCellBox().getBlockParams();
							if (cellParams.flow.isVertical() == this.tableParams.flow.isVertical()) {
								breakAvoid = false;
								continue;
							}
							// 書字方向が違えば必ず改ページしない
							breakAvoid = true;
							break;
						}
					}
					if (breakAvoid) {
						// 行の改ページ禁止
						if (!ignoreBreakAvoid && (xflags & IPageBreakableBox.FLAGS_FIRST_ROW) != 0) {
							// ページ先頭行の場合は改ページ禁止を無視してやりなおす
							ignoreBreakAvoid = true;
							pageLimit = savePageLimit;
							i = -1;
							continue;
						}
						// 一つ戻って前の行を末尾で切る
						pageLimit = beforeRow.getPageSize() - StyleUtils.THRESHOLD * 2;
						i -= 2;
						continue;
					}
				}

				// 書字方向が違えば必ず改ページしない
				boolean breakAvoid = false;
				for (int j = 0; j < prevRow.getCellCount(); ++j) {
					final Cell cell = prevRow.getCell(j);
					final BlockParams cellParams = cell.getCellBox().getBlockParams();
					if (cellParams.flow.isVertical() == this.tableParams.flow.isVertical()) {
						continue;
					}
					breakAvoid = true;
					break;
				}
				if (breakAvoid) {
					return null;
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
			nextRowGroup.addTableRow(nextRow);
			++i;
			break;
		}
		// System.err.println("E:" + this.getHeight()+"/"+remove + "/" +
		// this.rows.size());
		if (nextRowGroup == null) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return null;
			}
			return this;
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
		return nextRowGroup;
	}

	private TableRowGroupBox splitTableRowGroup() {
		final TableRowGroupBox nextRowGroup = new TableRowGroupBox(this.params, this.pos);
		nextRowGroup.setTableParams(this.tableParams);
		nextRowGroup.lineSize = this.lineSize;
		return nextRowGroup;
	}
}
