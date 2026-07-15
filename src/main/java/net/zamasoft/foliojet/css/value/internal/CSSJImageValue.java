package net.zamasoft.foliojet.css.value.internal;

import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJImageValue implements Value {
	private final Image image;

	public CSSJImageValue(Image image) {
		this.image = image;
	}

	public Image getImage() {
		return this.image;
	}
}