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
 * OkapiBarcodeを描画します。
 */
public class BarcodeImage implements Image, ReplacedBoxImage {
	protected final UserAgent ua;
	protected final Symbol symbol;
	protected final String message;
	protected final double upm, width, height;
	protected Color color = Color.BLACK;

	/** 1モジュール単位の物理寸法(mm)。 */
	protected final double unitMm;

	public BarcodeImage(UserAgent ua, Symbol symbol, String message, double unitMm) {
		this.ua = ua;
		this.symbol = symbol;
		this.message = message;
		this.unitMm = unitMm;
		// Okapiの幾何は「モジュール」単位の整数。1単位=unitMm(mm)として
		// 物理寸法へ倍率をかける(BarcodeInlineObjectの単位系コメント参照)
		this.upm = LengthUtils.convert(ua, unitMm, Unit.MM, Unit.PT);
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
				// 0.33mm = 0.935433...ptをPDFの既定precision=2のcmへ出すと
				// 0.94ptに丸められ、95モジュールが31.503mmへ太る。
				// 書籍JANは拡縮不可なので、各座標を物理ptへ直してから描き、
				// 端点の丸め誤差を1座標あたり0.005pt以下に抑える。
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
	 * 書籍JANをCopperの実フォントメトリクスで描画する。
	 *
	 * <p>OkapiのJava2DRendererはJUSTIFYの字間をAWT側の代替フォントで
	 * 計算する。一方、実際の描画はBridgeGraphics2DがCopper側のOCR-Bへ
	 * 解決するため、両者の字幅が違うと添え数字が31.35mmのバー幅まで
	 * 広がらない。旧Copper 3.2のISBNCanvasLogicHandlerと同様に、実際に
	 * 選ばれたフォントの送り幅から字間を求める。</p>
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
