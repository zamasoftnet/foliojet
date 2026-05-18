package net.zamasoft.foliojet.style.box.params;

/**
 * テーブル行グループのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowGroupPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableRowGroupPos extends AbstractBlockLevelPos {
	public byte rowGroupType = Types.ROW_GROUP_TYPE_BODY;

	public byte getType() {
		return TYPE_TABLE_ROW_GROUP;
	}

	public String toString() {
		return super.toString() + "[rowGroupType=" + this.rowGroupType + "]";
	}
}
