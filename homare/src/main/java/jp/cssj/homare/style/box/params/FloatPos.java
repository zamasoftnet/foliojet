package jp.cssj.homare.style.box.params;

/**
 * 浮動体の配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FloatPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FloatPos extends AbstractNormalFlowPos implements Pos {
	public byte floating = Types.FLOATING_START;

	public byte getType() {
		return TYPE_FLOAT;
	}

	public String toString() {
		return super.toString() + "[floating=" + this.floating + "]";
	}
}
