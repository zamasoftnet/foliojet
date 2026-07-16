package net.zamasoft.foliojet.layout.box.params;

/**
 * 配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TablePos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TablePos implements Pos {
	public static final TablePos POS = new TablePos();

	private TablePos() {
		// private
	}

	public PosType getType() {
		return PosType.TABLE;
	}
}
