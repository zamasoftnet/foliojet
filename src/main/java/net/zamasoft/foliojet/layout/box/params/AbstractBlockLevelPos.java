package net.zamasoft.foliojet.layout.box.params;

/**
 * 配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractBlockLevelPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractBlockLevelPos extends AbstractStaticPos {
	/**
	 * 直前の改ページ方法です。
	 */
	public PageBreakMode pageBreakBefore = PageBreakMode.AUTO;

	/**
	 * 直後の改ページ方法です。
	 */
	public PageBreakMode pageBreakAfter = PageBreakMode.AUTO;

	public String toString() {
		return super.toString() + "[pageBreakBefore=" + this.pageBreakBefore + ",pageBreakAfter=" + this.pageBreakAfter
				+ "]";
	}
}
