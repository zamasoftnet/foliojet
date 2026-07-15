package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJFirstHeadingValue implements Value {
	private final int level;

	public CSSJFirstHeadingValue(int level) {
		this.level = level;
	}

	public int getLevel() {
		return this.level;
	}
}