package net.zamasoft.foliojet.css.impl.part;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * The {@code circle} list marker (see {@link ListBulletImage}).
 *
 * @author MIYABE Tatsuhiko
 */
public class CircleImage extends ListBulletImage {
	public CircleImage(FontStyle fontStyle, Color color) {
		super(fontStyle, color);
	}

	public String getAltString() {
		return "○";
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			gc.setFillPaint(this.color);
			gc.setLineWidth(this.size / 24.0);
			gc.setLinePattern(GC.STROKE_SOLID);
			final Rectangle2D b = this.bullet();
			gc.draw(new Ellipse2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
		}
	}
}
