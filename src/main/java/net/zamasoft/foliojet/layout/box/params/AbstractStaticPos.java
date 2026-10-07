package net.zamasoft.foliojet.layout.box.params;

/**
 * Positioning parameters.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractStaticPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractStaticPos implements Pos {
	/**
	 * Relative positioning settings.
	 */
	public Offset offset = null;

	public String toString() {
		return super.toString() + "[offset=" + this.offset + "]";
	}
}
