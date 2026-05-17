package jp.cssj.homare.style.box.params;

/**
 * 配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractStaticPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractStaticPos implements Pos {
	/**
	 * 相対配置指定です。
	 */
	public Offset offset = null;

	public String toString() {
		return super.toString() + "[offset=" + this.offset + "]";
	}
}
