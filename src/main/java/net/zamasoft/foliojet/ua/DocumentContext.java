package net.zamasoft.foliojet.ua;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.pdfg2d.gc.image.Image;

public class DocumentContext {
	private URI baseURI;

	private String encoding = "ISO-8859-1";

	private CompatibleMode compatibleMode = CompatibleMode.NORMAL;

	/**
	 * The root element's style (2026-10-06). Parentless page and margin box styles use it to look up
	 * custom properties declared on the root element.
	 */
	private net.zamasoft.foliojet.css.CSSStyle rootStyle;

	public net.zamasoft.foliojet.css.CSSStyle getRootStyle() {
		return this.rootStyle;
	}

	public void setRootStyle(final net.zamasoft.foliojet.css.CSSStyle rootStyle) {
		this.rootStyle = rootStyle;
	}

	/**
	 * Stores SVGs for {@code mask-image:url(...)} by the color painted into the mask.
	 * DocumentContext is recreated for each pass, so these do not carry over to another document.
	 */
	private final Map<String, Image> maskImages = new HashMap<String, Image>();

	public Image getMaskImage(final String key) {
		return this.maskImages.get(key);
	}

	public void putMaskImage(final String key, final Image image) {
		if (key != null && image != null) {
			this.maskImages.put(key, image);
		}
	}

	/**
	 * The SVG subset of author CSS imported into inline SVG (2026-08-07).
	 * See {@link net.zamasoft.foliojet.css.SVGAuthorCss} for collection, injection, and var() resolution.
	 */
	private final net.zamasoft.foliojet.css.SVGAuthorCss svgAuthorCss = new net.zamasoft.foliojet.css.SVGAuthorCss();

	public net.zamasoft.foliojet.css.SVGAuthorCss getSVGAuthorCss() {
		return this.svgAuthorCss;
	}

	/**
	 * The identity of the document when several documents are laid out into one output (an EPUB spine item's path,
	 * 2026-10-08), or {@code null} for a single document. Unlike the base URI, {@code <base href>} does not change it.
	 * It keys the per-document carried style sheet and qualifies element ids in the output.
	 */
	private URI documentURI = null;

	public URI getDocumentURI() {
		return this.documentURI;
	}

	public void setDocumentURI(final URI documentURI) {
		this.documentURI = documentURI;
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
