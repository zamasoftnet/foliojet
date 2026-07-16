package net.zamasoft.foliojet.layout.box.params;

/**
 * テーブル行グループのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowGroupPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableRowGroupPos extends AbstractBlockLevelPos {
	public RowGroupType rowGroupType = RowGroupType.BODY;

	public PosType getType() {
		return PosType.TABLE_ROW_GROUP;
	}

	public String toString() {
		return super.toString() + "[rowGroupType=" + this.rowGroupType + "]";
	}
}
