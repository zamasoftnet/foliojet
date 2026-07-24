package net.zamasoft.foliojet.objects.barcode;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

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
import net.zamasoft.pdfg2d.gc.image.Image;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.graphics.Color;
import uk.org.okapibarcode.output.Java2DRenderer;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * OkapiBarcodeを描画します。
 */
public class BarcodeImage implements Image, ReplacedBoxImage {
	protected final UserAgent ua;
	protected final Symbol symbol;
	protected final String message;
	protected final double upm, width, height;
	protected Color color = Color.BLACK;

	public BarcodeImage(UserAgent ua, Symbol symbol, String message) {
		this.ua = ua;
		this.symbol = symbol;
		this.message = message;
		this.upm = LengthUtils.convert(ua, 1.0, Unit.MM, Unit.PT);
		this.width = Math.max(1, symbol.getWidth()) * this.upm;
		this.height = Math.max(1, symbol.getHeight()) * this.upm;
	}

	public void setReplacedBox(AbstractReplacedBox box, double width, double height) {
		// Okapi renders with its own color type. Keep barcode output black for now.
	}

	public Image duplicate() {
		// symbol/messageは構築後不変の描画内容なので共有してよい。
		// setReplacedBoxで受け取る状態(現状はcolorのみの候補)は
		// インスタンスごとに独立になる(E-6増分3b-3)
		final BarcodeImage duplicate = new BarcodeImage(this.ua, this.symbol, this.message);
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
			gc.transform(AffineTransform.getScaleInstance(this.upm, this.upm));
			Graphics2D g2d = new BridgeGraphics2D(gc);
			try {
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
}
