package net.zamasoft.foliojet.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJLastHeadingValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJLastHeadingValue implements ExtValue {
	private final int level;

	public CSSJLastHeadingValue(int level) {
		this.level = level;
	}

	public short getValueType() {
		return TYPE_CSSJ_LAST_HEADING;
	}

	public int getLevel() {
		return this.level;
	}
}