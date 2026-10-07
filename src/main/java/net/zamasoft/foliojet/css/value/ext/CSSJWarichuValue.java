package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/** CopperPDF's warichu setting. A proprietary extension because CSS has no standard property. */
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
