package net.zamasoft.foliojet.impl.ua.svg;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import org.apache.batik.gvt.AbstractGraphicsNode;

import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

class MyImageNode extends AbstractGraphicsNode {
	protected final Image image;

	public MyImageNode(Image image) {
		this.image = image;
	}

	public Shape getOutline() {
		return this.getPrimitiveBounds();
	}

	public Rectangle2D getGeometryBounds() {
		return this.getPrimitiveBounds();
	}

	public Rectangle2D getSensitiveBounds() {
		return this.getPrimitiveBounds();
	}

	public void primitivePaint(Graphics2D g2d) {
		if (g2d instanceof BridgeGraphics2D) {
			GC gc = ((BridgeGraphics2D) g2d).getGC();
			gc.drawImage(this.image);
		}
	}

	public Rectangle2D getPrimitiveBounds() {
		if (this.image == null) {
			return new Rectangle2D.Double();
		}
		double imageWidth = this.image.getWidth();
		double imageHeight = this.image.getHeight();
		return new Rectangle2D.Double(0, 0, imageWidth, imageHeight);
	}
}
