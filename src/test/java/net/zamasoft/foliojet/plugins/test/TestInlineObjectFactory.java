package net.zamasoft.foliojet.plugins.test;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.InlineObjectFactory;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

import org.xml.sax.helpers.DefaultHandler;

public class TestInlineObjectFactory implements InlineObjectFactory {
	public boolean match(CSSElement key) {
		CSSElement ce = (CSSElement) key;
		return "urn:test".equals(ce.uri);
	}

	public int priority() {
		return 1000;
	}

	public InlineObject createInlineObject() {
		return new TestInlineObject();
	}
}

class TestInlineObject extends DefaultHandler implements InlineObject {
	public Image getImage(UserAgent ua) throws IOException {
		return new Image() {

			public void drawTo(GC gc) throws GraphicsException {
				try (final var gcState = gc.begin()) {
					gc.setFillPaint(ColorValueUtils.RED.getColor());
					gc.fill(new Ellipse2D.Double(25, 25, 50, 50));
					gc.setStrokePaint(ColorValueUtils.BLACK.getColor());
					gc.draw(new Rectangle2D.Double(0, 0, 100, 100));
				}
			}

			public String getAltString() {
				return "";
			}

			public double getHeight() {
				return 100;
			}

			public double getWidth() {
				return 100;
			}

		};
	}
}
