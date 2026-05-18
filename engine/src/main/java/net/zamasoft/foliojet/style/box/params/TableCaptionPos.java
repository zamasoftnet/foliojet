package net.zamasoft.foliojet.style.box.params;

/**
 * テーブルキャプションボックスのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCaptionPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableCaptionPos extends FlowPos {
	public byte captionSide = Types.CAPTION_SIDE_BEFORE;

	public byte getType() {
		return TYPE_TABLE_CAPTION;
	}

	public String toString() {
		return super.toString() + "[captionSide=" + this.captionSide + "]";
	}
}
