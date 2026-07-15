package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum CSSJTitleValue implements Value {
	CSSJ_TITLE_VALUE;

	private CSSJTitleValue() {
		// singleton
	}

	public String toString() {
		return "-cssj-title";
	}
}