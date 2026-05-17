package jp.cssj.homare.css.value.internal;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJImageValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJImageValue implements InternalValue {
	private final Image image;

	public CSSJImageValue(Image image) {
		this.image = image;
	}

	public short getValueType() {
		return TYPE_CSSJ_IMAGE;
	}

	public Image getImage() {
		return this.image;
	}
}