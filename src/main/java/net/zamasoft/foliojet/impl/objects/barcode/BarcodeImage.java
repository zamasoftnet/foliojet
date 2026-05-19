package net.zamasoft.foliojet.impl.objects.barcode;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.style.box.AbstractReplacedBox;
import net.zamasoft.foliojet.style.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;

import org.krysalis.barcode4j.BarcodeDimension;
import org.krysalis.barcode4j.BarcodeGenerator;
import org.krysalis.barcode4j.TextAlignment;
import org.krysalis.barcode4j.output.AbstractCanvasProvider;
import org.krysalis.barcode4j.output.CanvasProvider;

/**
 * バーコードを描画します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class BarcodeImage implements Image, ReplacedBoxImage {
	protected final UserAgent ua;

	protected final BarcodeGenerator bg;

	protected final String message;

	protected final double upm, width, height;

	protected FontStyle fontStyle;

	protected Color color;

	public BarcodeImage(UserAgent ua, BarcodeGenerator bg, String message) {
		this.ua = ua;
		this.bg = bg;
		this.message = message;
		this.upm = LengthUtils.convert(ua, 1.0, LengthValue.UNIT_MM, LengthValue.UNIT_PT);
		double width, height;
		try {
			BarcodeDimension dim = this.bg.calcDimensions(message);
			width = dim.getWidthPlusQuiet() * this.upm;
			height = dim.getHeightPlusQuiet() * this.upm;
		} catch (Exception e) {
			width = 40;
			height = 40;
		}
		this.width = width;
		this.height = height;
	}

	public void setReplacedBox(AbstractReplacedBox box, double width, double height) {
		this.fontStyle = box.getReplacedParams().fontStyle;
		this.color = box.getReplacedParams().color;
	}

	public double getWidth() {
		return this.width;
	}

	public double getHeight() {
		return this.height;
	}

	public String getAltString() {
		return this.message;
	}

	public void drawTo(GC gc) {
		gc.begin();
		gc.transform(AffineTransform.getScaleInstance(this.upm, this.upm));
		gc.setFillPaint(this.color);
		gc.setStrokePaint(this.color);
		CanvasProvider cv = new MyCanvadProvider(gc, 0);
		try {
			this.bg.generateBarcode(cv, this.message);
		} catch (Exception e) {
			this.ua.message(MessageCodes.WARN_PLUGIN, "net.zamasoft.foliojet.impl.objects.barcode", e.getLocalizedMessage());
			StyleUtils.drawText(gc, ua.getDefaultFontPolicy().asFontPolicyList(), 5, e.getLocalizedMessage(), 3, 3, this.width - 6);
		}
		gc.end();
	}

	private class MyCanvadProvider extends AbstractCanvasProvider {
		private final GC gc;

		public MyCanvadProvider(GC gc, int orientation) {
			super(orientation);
			this.gc = gc;
		}

		public void deviceText(String text, double x, double xx, double y, String fontName, double fontSize,
				TextAlignment textAlign) {
			FontManager fm = ua.getFontManager();
			FontStyle font = new FontStyleImpl(fontStyle.getFamily(), fontSize, fontStyle.getStyle(),
					fontStyle.getWeight(), FontStyle.Direction.LTR, ua.getDefaultFontPolicy().asFontPolicyList());
			this.gc.begin();
			this.gc.transform(AffineTransform.getTranslateInstance(x, y));

			TextShaper glypher = fm.getTextShaper();
			MyGlyphHandler gh = new MyGlyphHandler();
			glypher.setGlyphHandler(gh);
			glypher.fontStyle(font);
			char[] ch = text.toCharArray();
			glypher.characters(-1, ch, 0, ch.length);
			glypher.flush();

			double width = xx - x;
			double a = 0, xs = 0;
			List<Element> list = gh.buffer;
			if (textAlign == TextAlignment.TA_RIGHT) {
				a = width - gh.advance;
			} else if (textAlign == TextAlignment.TA_CENTER) {
				a = (width - gh.advance) / 2;
			} else if (textAlign == TextAlignment.TA_JUSTIFY) {
				int count = -1;
				for (int i = 0; i < list.size(); ++i) {
					Element e = (Element) list.get(i);
					if (e instanceof Text) {
						Text t = (Text) e;
						count += t.getGlyphCount();
					} else if (!(e instanceof net.zamasoft.pdfg2d.gc.text.TextControl)) {
						throw new IllegalStateException();
					}
				}
				if (count >= 2) {
					xs = (width - gh.advance) / count;
				}
			}
			for (int i = 0; i < list.size(); ++i) {
				Element e = (Element) list.get(i);
				if (e instanceof Text) {
					TextImpl t = (TextImpl) e;
					t.setLetterSpacing(xs);
					this.gc.drawText(t, a, 0);
				} else if (!(e instanceof net.zamasoft.pdfg2d.gc.text.TextControl)) {
					throw new IllegalStateException();
				}
				a += e.getAdvance();
			}

			this.gc.end();
		}

		public void deviceFillRect(double x, double y, double w, double h) {
			Rectangle2D rect = new Rectangle2D.Double(x, y, w, h);
			this.gc.fill(rect);
		}
	}
}

class MyGlyphHandler implements GlyphHandler {
	List<Element> buffer = new ArrayList<Element>();
	double advance = 0;
	private TextImpl text;

	public void startTextRun(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		this.advance += this.text.appendGlyph(ch, coff, clen, gid);
	}

	public void control(TextControl quad) {
		if (this.text.glyphCount > 0) {
			this.buffer.add(this.text);
			this.text = new TextImpl(-1, this.text.fontStyle, this.text.fontMetrics);
		}
		this.buffer.add(quad);
		this.advance += quad.getAdvance();
	}

	public void endTextRun() {
		if (this.text.glyphCount > 0) {
			this.buffer.add(this.text);
			this.text = new TextImpl(-1, this.text.fontStyle, this.text.fontMetrics);
		}
	}

	public void flush() {
		if (this.text.glyphCount > 0) {
			this.buffer.add(this.text);
		}
	}

	public void close() {
		this.flush();
	}
}
