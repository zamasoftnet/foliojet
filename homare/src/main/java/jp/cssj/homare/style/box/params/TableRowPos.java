package jp.cssj.homare.style.box.params;

/**
 * テーブル行のパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableRowPos extends AbstractBlockLevelPos {
	public byte getType() {
		return TYPE_TABLE_ROW;
	}
}
