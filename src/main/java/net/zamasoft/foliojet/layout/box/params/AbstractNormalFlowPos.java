package net.zamasoft.foliojet.layout.box.params;

/**
 * Positioning parameters.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractNormalFlowPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractNormalFlowPos extends AbstractBlockLevelPos {
	/**
	 * How the box clears floats.
	 */
	public ClearMode clear = ClearMode.NONE;

	public String toString() {
		return super.toString() + "[clear=" + this.clear + "]";
	}
}
