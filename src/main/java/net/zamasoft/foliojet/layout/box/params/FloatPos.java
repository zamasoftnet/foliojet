package net.zamasoft.foliojet.layout.box.params;

/**
 * 浮動体の配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FloatPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FloatPos extends AbstractNormalFlowPos implements Pos {
	public FloatSide floating = FloatSide.START;

	public PosType getType() {
		return PosType.FLOAT;
	}

	public String toString() {
		return super.toString() + "[floating=" + this.floating + "]";
	}
}
