package net.zamasoft.foliojet.ua;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;

public class DocumentContext {
	private URI baseURI;

	private String encoding = "ISO-8859-1";

	private CompatibleMode compatibleMode = CompatibleMode.NORMAL;

	/**
	 * インラインSVGへ持ち込む著者CSSのSVG向け部分集合です(2026-08-07)。
	 * 収集・注入・var()解決の仕組みは{@link net.zamasoft.foliojet.css.SVGAuthorCss}参照。
	 */
	private final net.zamasoft.foliojet.css.SVGAuthorCss svgAuthorCss = new net.zamasoft.foliojet.css.SVGAuthorCss();

	public net.zamasoft.foliojet.css.SVGAuthorCss getSVGAuthorCss() {
		return this.svgAuthorCss;
	}

	public void setBaseURI(URI baseURI) {
		this.baseURI = baseURI;
	}

	public URI getBaseURI() {
		return this.baseURI;
	}

	public CompatibleMode getCompatibleMode() {
		return this.compatibleMode;
	}

	public void setCompatibleMode(CompatibleMode compatibleMode) {
		this.compatibleMode = compatibleMode;
	}

	public String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) throws UnsupportedEncodingException {
		encoding = encoding.trim();
		try {
			if (!Charset.isSupported(encoding)) {
				throw new UnsupportedEncodingException(encoding);
			}
		} catch (Exception e) {
			throw new UnsupportedEncodingException(encoding);
		}
		this.encoding = encoding;
	}
}
