package net.zamasoft.foliojet.layout.box.params;

/**
 * Table parameters.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCellPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableCellPos extends AbstractBlockLevelPos {
	public int colspan = 1;

	public int rowspan = 1;

	public EmptyCellsMode emptyCells = EmptyCellsMode.HIDE;

	public CellAlign verticalAlign = CellAlign.BASELINE;

	/**
	 * Whether the author explicitly declares {@code page-break-inside: auto} (or {@code break-inside: auto}) on
	 * this cell (2026-08-27). Boundaries between rows crossed by a rowspan behave as avoid (the specification in
	 * manual 4550), but cells with explicit auto can opt out. After removing the UA default avoid for cells,
	 * computed values alone could no longer distinguish default auto from explicit auto, so carry whether the
	 * declaration is present.
	 */
	public boolean breakInsideDeclaredAuto = false;

	public PosType getType() {
		return PosType.TABLE_CELL;
	}

	public String toString() {
		return super.toString() + "[colspan=" + this.colspan + ",rowspan=" + this.rowspan + ",emptyCells="
				+ this.emptyCells + ",verticalAlign=" + this.verticalAlign + "]";
	}
}
