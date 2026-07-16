package net.zamasoft.foliojet.layout.box.params;

/**
 * 配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TextBlockPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextBlockPos implements Pos {
	public static final TextBlockPos POS = new TextBlockPos();

	private TextBlockPos() {
		// private
	}

	public PosType getType() {
		return PosType.TEXT_BLOCK;
	}
}
