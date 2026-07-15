package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJPageContentValue implements Value {
	private final byte[] pages;

	public CSSJPageContentValue(byte[] pages) {
		this.pages = pages;
	}

	public byte[] getPages() {
		return this.pages;
	}
}