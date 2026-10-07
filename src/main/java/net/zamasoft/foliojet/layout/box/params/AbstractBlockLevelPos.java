package net.zamasoft.foliojet.layout.box.params;

/**
 * Positioning parameters.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractBlockLevelPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractBlockLevelPos extends AbstractStaticPos {
	/**
	 * Page-break mode immediately before the box.
	 */
	public PageBreakMode pageBreakBefore = PageBreakMode.AUTO;

	/**
	 * Page-break mode immediately after the box.
	 */
	public PageBreakMode pageBreakAfter = PageBreakMode.AUTO;

	/**
	 * Used value of the page name (named pages N1b; null = unnamed).
	 * Resolved from the nearest non-auto ancestor; boundary decisions are in N2.
	 */
	public String pageName = null;

	public String toString() {
		return super.toString() + "[pageBreakBefore=" + this.pageBreakBefore + ",pageBreakAfter=" + this.pageBreakAfter
				+ "]";
	}
}
