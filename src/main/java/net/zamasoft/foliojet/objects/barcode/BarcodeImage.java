package net.zamasoft.foliojet.objects.barcode;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.SimpleLayoutGlyphHandler;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.graphics.Color;
import uk.org.okapibarcode.graphics.Rectangle;
import uk.org.okapibarcode.graphics.TextBox;
import uk.org.okapibarcode.output.Java2DRenderer;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * Draws OkapiBarcode output.
 */
public class BarcodeImage implements Image, ReplacedBoxImage {
	protected final UserAgent ua;
	protected final Symbol symbol;
	protected final String message;
	protected final double upm, width, height;
	protected Color color = Color.BLACK;

	/** Physical size of one module (mm). */
	protected final double unitMm;

	public BarcodeImage(UserAgent ua, Symbol symbol, String message, double unitMm) {
		this.ua = ua;
		this.symbol = symbol;
		this.message = message;
		this.unitMm = unitMm;
		// Okapi geometry uses integer "module" units. Scale to physical dimensions with 1 unit = unitMm (mm)
		// (see the unit-system comment in BarcodeInlineObject)
		this.upm = LengthUtils.convert(ua, unitMm, Unit.MM, Unit.PT);
		this.width = Math.max(1, symbol.getWidth()) * this.upm;
		this.height = Math.max(1, symbol.getHeight()) * this.upm;
	}

	public void setReplacedBox(AbstractReplacedBox box, double width, double height) {
		// Okapi renders with its own color type. Keep barcode output black for now.
	}

	public Image duplicate() {
		// symbol/message are immutable drawing content after construction, so they can be shared.
		// State received through setReplacedBox (currently only color is a candidate)
		// is independent per instance (E-6 increment 3b-3)
		final BarcodeImage duplicate = new BarcodeImage(this.ua, this.symbol, this.message, this.unitMm);
		duplicate.color = this.color;
		return duplicate;
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

	public void drawTo(GC gc) throws GraphicsException {
		try (final GC.State gcState = gc.begin()) {
			if (this.symbol instanceof final BookJanSymbol bookJan) {
				// Emitting 0.33 mm = 0.935433... pt in a PDF cm with the default precision=2 rounds
				// it to 0.94 pt, widening 95 modules to 31.503 mm.
				// Book JAN codes must not be scaled, so convert each coordinate to physical pt before drawing,
				// keeping endpoint rounding error to at most 0.005 pt per coordinate.
				this.drawBookJan(gc, bookJan);
				return;
			}
			gc.transform(AffineTransform.getScaleInstance(this.upm, this.upm));
			BridgeGraphics2D g2d = new BridgeGraphics2D(gc);
			try {
				g2d.setFontPolicy(this.ua.getDefaultFontPolicy().asFontPolicyList());
				Java2DRenderer renderer = new Java2DRenderer(g2d, 1, Color.WHITE, this.color);
				renderer.render(this.symbol);
			} catch (Exception e) {
				this.ua.message(MessageCodes.WARN_PLUGIN, "net.zamasoft.foliojet.objects.barcode",
						e.getLocalizedMessage());
				LayoutUtils.drawText(gc, ua.getDefaultFontPolicy().asFontPolicyList(), 5, e.getLocalizedMessage(), 3, 3,
						this.width - 6);
			} finally {
				g2d.dispose();
			}
		}
	}

	/**
	 * Draws Book JAN codes using Copper's actual font metrics.
	 *
	 * <p>Okapi's Java2DRenderer calculates JUSTIFY letter spacing using an AWT substitute font.
	 * Actual drawing, however, resolves to Copper's OCR-B through BridgeGraphics2D, so when their
	 * character widths differ, the human-readable digits do not span the 31.35 mm bar width.
	 * As in Copper 3.2's ISBNCanvasLogicHandler, calculates letter spacing from advances
	 * of the font actually selected.</p>
	 */
	private void drawBookJan(final GC gc, final BookJanSymbol symbol) throws GraphicsException {
		gc.setFillPaint(GrayColor.WHITE);
		gc.fill(new Rectangle2D.Double(0, 0, symbol.getWidth() * this.upm, symbol.getHeight() * this.upm));
		gc.setFillPaint(GrayColor.BLACK);

		final double marginX = symbol.getQuietZoneHorizontal() * this.upm;
		final double marginY = symbol.getQuietZoneVertical() * this.upm;
		for (final Rectangle rectangle : symbol.getRectangles()) {
			gc.fill(new Rectangle2D.Double(rectangle.x * this.upm + marginX, rectangle.y * this.upm + marginY,
					rectangle.width * this.upm, rectangle.height * this.upm));
		}

		final TextBox text = symbol.getHumanReadableBox();
		if (text == null || text.text.isEmpty()) {
			return;
		}

		final SimpleLayoutGlyphHandler measure = new SimpleLayoutGlyphHandler();
		this.layoutBookJanText(gc, symbol, text.text, measure);
		final int gaps = text.text.codePointCount(0, text.text.length()) - 1;
		final double letterSpacing = calculateJustifiedLetterSpacing(text.width * this.upm, measure.getAdvance(), gaps);

		try (final GC.State textState = gc.begin()) {
			gc.transform(AffineTransform.getTranslateInstance(text.x * this.upm + marginX,
					text.y * this.upm + marginY));
			final SimpleLayoutGlyphHandler draw = new SimpleLayoutGlyphHandler();
			draw.setGC(gc);
			draw.setLetterSpacing(letterSpacing);
			this.layoutBookJanText(gc, symbol, text.text, draw);
		}
	}

	private void layoutBookJanText(final GC gc, final BookJanSymbol symbol, final String text,
			final SimpleLayoutGlyphHandler glyphHandler) throws GraphicsException {
		try (final TextLayoutHandler layout = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("ja"),
				glyphHandler)) {
			layout.setFontFamilies(FontFamilyList.create(symbol.getFontName()));
			layout.setFontPolicy(this.ua.getDefaultFontPolicy().asFontPolicyList());
			layout.setFontSize(symbol.getFontSize() * this.upm);
			layout.characters(text);
		}
	}

	static double calculateJustifiedLetterSpacing(final double width, final double naturalAdvance, final int gaps) {
		return gaps <= 0 ? 0 : (width - naturalAdvance) / gaps;
	}
}
