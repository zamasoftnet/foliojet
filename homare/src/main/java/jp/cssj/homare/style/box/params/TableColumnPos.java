package jp.cssj.homare.style.box.params;

/**
 * テーブルカラムまたはカラムグループのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableColumnPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableColumnPos implements Pos {
	public int span = 1;

	public byte getType() {
		return TYPE_TABLE_COLUMN;
	}

	public String toString() {
		return super.toString() + "[span=" + this.span + "]";
	}
}
