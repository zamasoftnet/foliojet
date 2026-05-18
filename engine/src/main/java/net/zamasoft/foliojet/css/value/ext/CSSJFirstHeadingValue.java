package net.zamasoft.foliojet.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJHeadingValue.java 1034 2013-10-23 05:51:57Z miyabe $
 */
public class CSSJFirstHeadingValue implements ExtValue {
	private final int level;

	public CSSJFirstHeadingValue(int level) {
		this.level = level;
	}

	public short getValueType() {
		return TYPE_CSSJ_FIRST_HEADING;
	}

	public int getLevel() {
		return this.level;
	}
}