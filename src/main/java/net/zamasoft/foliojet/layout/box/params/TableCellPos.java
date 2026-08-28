package net.zamasoft.foliojet.layout.box.params;

/**
 * テーブルのパラメータです。
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
	 * 著者が明示的に{@code page-break-inside: auto}(または{@code break-inside:
	 * auto})を宣言したセルか(2026-08-27)。rowspanが跨ぐ行間はavoid相当
	 * (説明書4550の仕様)だが、明示autoのセルはそこからオプトアウトできる。
	 * UA既定のセルavoid撤去に伴い、計算値だけでは「既定のauto」と
	 * 「明示のauto」を区別できなくなったため宣言有無を運ぶ。
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
