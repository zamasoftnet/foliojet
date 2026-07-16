package net.zamasoft.foliojet.layout.box.params;

/**
 * 行のパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FirstLineParams.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FirstLineParams extends AbstractLineParams {

	public Background background = Background.NULL_BACKGROUND;

	public ParamsType getType() {
		return ParamsType.FIRST_LINE;
	}

	public String toString() {
		return super.toString() + "[background=" + this.background + "]";
	}
}
