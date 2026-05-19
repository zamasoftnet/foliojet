package net.zamasoft.foliojet.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJPageContentValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJPageContentValue implements ExtValue {
	private final byte[] pages;

	public CSSJPageContentValue(byte[] pages) {
		this.pages = pages;
	}

	public short getValueType() {
		return TYPE_CSSJ_PAGE_CONTENT;
	}

	public byte[] getPages() {
		return this.pages;
	}
}