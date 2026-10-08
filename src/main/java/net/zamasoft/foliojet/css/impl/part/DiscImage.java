package net.zamasoft.foliojet.css.impl.part;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * The {@code disc} list marker (see {@link ListBulletImage}).
 *
 * @author MIYABE Tatsuhiko
 */
public class DiscImage extends ListBulletImage {
	public DiscImage(FontStyle fontStyle, Color color) {
		super(fontStyle, color);
	}

	public String getAltString() {
		return "●";
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			gc.setFillPaint(this.color);
			final Rectangle2D b = this.bullet();
			gc.fill(new Ellipse2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
		}
	}
}
