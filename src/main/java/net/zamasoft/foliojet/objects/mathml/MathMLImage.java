package net.zamasoft.foliojet.objects.mathml;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.content.BaselineImage;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.sourceforge.jeuclid.layout.JEuclidView;

/**
 * 組んだ数式です。基準線の下へ下がる深さ({@link #getDescent()})を持ち、
 * 行の中では下端でなく基準線を本文の基準線に合わせて置かれる
 * (2026-10-04。以前は下端を置いたので、添字・括弧・y のある式が浮いた)。
 */
public class MathMLImage implements Image, BaselineImage {
	protected final JEuclidView view;

	/** 拡大率(文字の拡大。CSS の大きさを受け取ったときは 1)。 */
	protected final double scale;

	public MathMLImage(JEuclidView view) {
		this(view, 1.0);
	}

	public MathMLImage(JEuclidView view, double scale) {
		this.view = view;
		this.scale = scale;
	}

	public double getWidth() {
		return this.view.getWidth() * this.scale;
	}

	public double getHeight() {
		return (this.view.getAscentHeight() + this.view.getDescentHeight()) * this.scale;
	}

	public double getDescent() {
		return this.view.getDescentHeight() * this.scale;
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			if (this.scale != 1.0) {
				gc.transform(AffineTransform.getScaleInstance(this.scale, this.scale));
			}
			Graphics2D g2d = new BridgeGraphics2D(gc);
			this.view.draw(g2d, 0, this.view.getAscentHeight());
			g2d.dispose();
		}
	}

	public String getAltString() {
		return null;
	}
}
