package net.zamasoft.foliojet.impl.css.part;

import java.awt.geom.Ellipse2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * @author MIYABE Tatsuhiko
 */
public class DiscImage implements Image {
	protected final double size;

	protected final Color color;

	public DiscImage(FontStyle fontStyle, Color color) {
		this.size = fontStyle.getSize();
		this.color = color;
	}

	public double getWidth() {
		return this.size;
	}

	public double getHeight() {
		return this.size;
	}

	public String getAltString() {
		return "●";
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {

			gc.setFillPaint(this.color);

			double d = this.size * 0.35;
			gc.fill(new Ellipse2D.Double(this.size / 2.0 - d / 2.0, this.size * 0.2 + this.size / 2.0 - d / 2.0, d, d));

		}
	}
}
