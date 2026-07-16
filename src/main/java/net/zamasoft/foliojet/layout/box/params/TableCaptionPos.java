package net.zamasoft.foliojet.layout.box.params;

/**
 * テーブルキャプションボックスのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCaptionPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableCaptionPos extends FlowPos {
	public CaptionSideMode captionSide = CaptionSideMode.BEFORE;

	public PosType getType() {
		return PosType.TABLE_CAPTION;
	}

	public String toString() {
		return super.toString() + "[captionSide=" + this.captionSide + "]";
	}
}
