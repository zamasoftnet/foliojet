package net.zamasoft.foliojet.style.box.params;

/**
 * 配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: LinePos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class LinePos implements Pos {
	public static final LinePos POS = new LinePos();

	private LinePos() {
		// private
	}

	public byte getType() {
		return TYPE_LINE;
	}
}
