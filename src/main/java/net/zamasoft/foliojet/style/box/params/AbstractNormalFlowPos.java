package net.zamasoft.foliojet.style.box.params;

/**
 * 配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractNormalFlowPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractNormalFlowPos extends AbstractBlockLevelPos {
	/**
	 * ボックスのクリア方法です。
	 */
	public byte clear = Types.CLEAR_NONE;

	public String toString() {
		return super.toString() + "[clear=" + this.clear + "]";
	}
}
