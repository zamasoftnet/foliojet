package net.zamasoft.foliojet.css.impl.part;

import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;

/**
 * @author MIYABE Tatsuhiko
 */
public class BrokenImage implements Image {
	protected static final double WIDTH = 40, HEIGHT = 40;

	protected final String alt;

	protected final UserAgent ua;

	public BrokenImage(UserAgent ua, String alt) {
		this.ua = ua;
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

	public void drawTo(GC gc) throws GraphicsException {
		GeneralPath path = new GeneralPath();
		try (final var gcState = gc.begin()) {

			float x = 1f;
			float y = 1f;
			float w = (float) WIDTH - 2;
			float h = (float) HEIGHT - 2;

			gc.setLineWidth(2.0);
			gc.setLinePattern(GC.STROKE_SOLID);
			gc.setStrokePaint(GrayColor.BLACK);
			path.moveTo(x + w, y);
			path.lineTo(x, y);
			path.lineTo(x, y + h);
			gc.draw(path);

			gc.setStrokePaint(GrayColor.create(0.82f));
			path.reset();
			path.moveTo(x, y + h);
			path.lineTo(x + w, y + h);
			path.lineTo(x + w, y);
			gc.draw(path);

			gc.setLineWidth(3.0);
			gc.setLinePattern(GC.STROKE_SOLID);
			gc.setStrokePaint(ColorValueUtils.RED.getColor());

			path.reset();
			path.moveTo(x + 5f, y + 5f);
			path.lineTo(x + 15f, y + 15f);
			gc.draw(path);

			path.reset();
			path.moveTo(x + 15f, y + 5f);
			path.lineTo(x + 5f, y + 15f);
			gc.draw(path);

			if (this.alt != null) {
				LayoutUtils.drawText(gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), 5, this.alt, 3, 3, WIDTH - 6);
		}

		}
	}
}
