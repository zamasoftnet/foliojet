package net.zamasoft.foliojet.css.impl.part;

import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * The {@code square} list marker (see {@link ListBulletImage}).
 *
 * @author MIYABE Tatsuhiko
 */
public class SquareImage extends ListBulletImage {
	public SquareImage(FontStyle fontStyle, Color color) {
		super(fontStyle, color);
	}

	public String getAltString() {
		return "■";
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			gc.setFillPaint(this.color);
			gc.fill(this.bullet());
		}
	}
}
