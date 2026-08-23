package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/** CopperPDFの割注指定。CSSに標準プロパティがないため独自拡張。 */
public enum CSSJWarichuValue implements Value {
	NONE("none"), AUTO("auto");

	private final String text;

	private CSSJWarichuValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
