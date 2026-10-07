package net.zamasoft.foliojet.css.value.internal;

import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJImageValue implements Value {
	private final Image image;
	private final SvgSource svgSource;

	/** Inline SVG detached from DOM/GVT/UA. A null document means capture failed, e.g., due to a budget overrun. */
	public record SvgSource(String document, String baseURI) {
	}

	public CSSJImageValue(Image image) {
		this(image, null);
	}

	public CSSJImageValue(final Image image, final SvgSource svgSource) {
		this.image = image;
		this.svgSource = svgSource;
	}

	public Image getImage() {
		return this.image;
	}

	public SvgSource getSvgSource() {
		return this.svgSource;
	}
}
