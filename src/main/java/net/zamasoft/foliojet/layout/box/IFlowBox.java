package net.zamasoft.foliojet.layout.box;

/**
 * A box placed in normal flow.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: IFlowBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IFlowBox extends IBox {
	/**
	 * Returns true if a page break immediately before this box is prohibited.
	 *
	 * @return
	 */
	public boolean avoidBreakBefore();

	/**
	 * Returns true if a page break immediately after this box is prohibited.
	 *
	 * @return
	 */
	public boolean avoidBreakAfter();
}
