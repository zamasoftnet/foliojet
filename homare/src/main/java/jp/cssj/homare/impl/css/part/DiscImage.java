package jp.cssj.homare.impl.css.part;

import java.awt.geom.Ellipse2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: DiscImage.java 1552 2018-04-26 01:43:24Z miyabe $
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
		gc.begin();

		gc.setFillPaint(this.color);

		double d = this.size * 0.35;
		gc.fill(new Ellipse2D.Double(this.size / 2.0 - d / 2.0, this.size * 0.2 + this.size / 2.0 - d / 2.0, d, d));

		gc.end();
	}
}
