package net.zamasoft.foliojet.impl.objects.mathml;

import java.awt.Graphics2D;

import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.sourceforge.jeuclid.layout.JEuclidView;

public class MathMLImage implements Image {
	protected final JEuclidView view;

	public MathMLImage(JEuclidView view) {
		this.view = view;
	}

	public double getWidth() {
		return this.view.getWidth();
	}

	public double getHeight() {
		return this.view.getAscentHeight() + this.view.getDescentHeight();
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			Graphics2D g2d = new BridgeGraphics2D(gc);
			this.view.draw(g2d, 0, this.view.getAscentHeight());
			g2d.dispose();
		}
	}

	public String getAltString() {
		return null;
	}
}
