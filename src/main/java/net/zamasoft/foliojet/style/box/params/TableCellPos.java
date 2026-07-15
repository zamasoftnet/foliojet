package net.zamasoft.foliojet.style.box.params;

/**
 * テーブルのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCellPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableCellPos extends AbstractBlockLevelPos {
	public int colspan = 1;

	public int rowspan = 1;

	public byte emptyCells = Types.EMPTY_CELLS_HIDE;

	public byte verticalAlign = Types.VERTICAL_ALIGN_BASELINE;

	public PosType getType() {
		return PosType.TABLE_CELL;
	}

	public String toString() {
		return super.toString() + "[colspan=" + this.colspan + ",rowspan=" + this.rowspan + ",emptyCells="
				+ this.emptyCells + ",verticalAlign=" + this.verticalAlign + "]";
	}
}
