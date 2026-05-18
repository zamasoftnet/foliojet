package net.zamasoft.foliojet.style.box.params;

/**
 * 行のパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FirstLineParams.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FirstLineParams extends AbstractLineParams {

	public Background background = Background.NULL_BACKGROUND;

	public byte getType() {
		return TYPE_FIRST_LINE;
	}

	public String toString() {
		return super.toString() + "[background=" + this.background + "]";
	}
}
