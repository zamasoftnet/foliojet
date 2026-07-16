package net.zamasoft.foliojet.layout.box.params;

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
	public ClearMode clear = ClearMode.NONE;

	public String toString() {
		return super.toString() + "[clear=" + this.clear + "]";
	}
}
