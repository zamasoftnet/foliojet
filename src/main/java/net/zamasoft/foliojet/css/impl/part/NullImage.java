package net.zamasoft.foliojet.css.impl.part;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * @author MIYABE Tatsuhiko
 */
public class NullImage implements Image {
	protected static final double WIDTH = 40, HEIGHT = 40;

	protected final String alt;

	public NullImage(String alt) {
		this.alt = alt;
	}

	public double getWidth() {
		return WIDTH;
	}

	public double getHeight() {
		return HEIGHT;
	}

	public String getAltString() {
		return this.alt;
	}

	public void drawTo(GC gc) {
		// ignore
	}
}
