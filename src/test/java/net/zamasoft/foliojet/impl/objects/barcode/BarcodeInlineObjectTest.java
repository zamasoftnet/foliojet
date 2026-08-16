package net.zamasoft.foliojet.objects.barcode;

import java.io.StringReader;
import java.lang.reflect.Proxy;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.util.XMLParsers;
import net.zamasoft.pdfg2d.gc.image.Image;
import uk.org.okapibarcode.backend.Ean;
import uk.org.okapibarcode.graphics.Rectangle;
import uk.org.okapibarcode.graphics.TextAlignment;
import uk.org.okapibarcode.graphics.TextBox;

public class BarcodeInlineObjectTest extends TestCase {
	public BarcodeInlineObjectTest(String name) {
		super(name);
	}

	public void testCreatesCode128Image() throws Exception {
		Image image = parse("<barcode xmlns=\"http://barcode4j.krysalis.org/ns\" message=\"123456789\">"
				+ "<code128><height>12</height><module-width>1</module-width></code128></barcode>");
		assertTrue(image.getWidth() > 0);
		assertTrue(image.getHeight() > 0);
		assertEquals("123456789", image.getAltString());
	}

	public void testCreatesQrCodeImage() throws Exception {
		Image image = parse("<barcode xmlns=\"http://barcode4j.krysalis.org/ns\" message=\"FolioJet\">"
				+ "<qrcode><module-width>2</module-width></qrcode></barcode>");
		assertTrue(image.getWidth() > 0);
		assertTrue(image.getHeight() > 0);
		assertEquals("FolioJet", image.getAltString());
	}

	public void testIsbnUsesJapaneseBookJanPresentation() throws Exception {
		BarcodeImage image = (BarcodeImage) parse(
				"<barcode xmlns=\"http://barcode4j.krysalis.org/ns\" message=\"9784908348143\">"
						+ "<isbn><height>7.6mm</height><module-width>0.33mm</module-width>"
						+ "<quiet-zone>0mm</quiet-zone><font-size>7.5pt</font-size>"
						+ "<human-readable><placement>bottom</placement><font-name>OCRB</font-name>"
						+ "</human-readable></isbn></barcode>");

		assertTrue(image.symbol instanceof BookJanSymbol);
		assertEquals(95, image.symbol.getWidth());
		assertEquals("100% book JAN must be 31.35mm wide", 31.35 * 72 / 25.4, image.getWidth(), 0.000001);
		assertEquals("7.6mm bar height rounds to 23 modules", 23.0,
				image.symbol.getRectangles().get(0).height, 0.000001);
		assertEquals("bar plus OCR-B row must stay within one module of the 11mm Book JAN height",
				11.0 * 72 / 25.4, image.getHeight(), 0.33 * 72 / 25.4);
		assertEquals("9784908348143", image.symbol.getHumanReadableText());
		assertEquals("OCRB", image.symbol.getFontName());
		assertEquals(1, image.symbol.getTexts().size());
		TextBox text = image.symbol.getTexts().get(0);
		assertEquals("9784908348143", text.text);
		assertEquals(0.0, text.x);
		assertEquals(95.0, text.width);
		assertEquals(TextAlignment.JUSTIFY, text.alignment);
		assertSame(text, ((BookJanSymbol) image.symbol).getHumanReadableBox());
		assertEquals(2.5, BarcodeImage.calculateJustifiedLetterSpacing(95, 65, 12), 0.000001);
		for (Rectangle rectangle : image.symbol.getRectangles()) {
			assertEquals("book JAN guard bars must not extend", image.symbol.getRectangles().get(0).height,
					rectangle.height);
		}
	}

	public void testEan13KeepsGeneralPurposePresentation() throws Exception {
		BarcodeImage image = (BarcodeImage) parse(
				"<barcode xmlns=\"http://barcode4j.krysalis.org/ns\" message=\"9784908348143\">"
						+ "<ean-13><module-width>0.33mm</module-width></ean-13></barcode>");

		assertTrue(image.symbol instanceof Ean);
		assertFalse(image.symbol instanceof BookJanSymbol);
		assertEquals(3, image.symbol.getTexts().size());
		double firstHeight = image.symbol.getRectangles().get(0).height;
		boolean foundShorterBar = false;
		for (Rectangle rectangle : image.symbol.getRectangles()) {
			foundShorterBar |= rectangle.height < firstHeight;
		}
		assertTrue("general EAN-13 must retain extended guard bars", foundShorterBar);
	}

	public void testJapanPostIgnoresCharactersOutsideDigitsLettersHyphen() throws Exception {
		// 4900_barcode.mdに明記されている「message内に含まれる数字、アルファベット、
		// ハイフン以外の文字は無視されます」という挙動をJapanPostで確認する。
		// スペースを含むメッセージがOkapiInputExceptionにならず正常に画像化できること。
		Image image = parse("<barcode xmlns=\"http://barcode4j.krysalis.org/ns\" message=\"1008798 1-3-2\">"
				+ "<japanpost><module-width>0.6mm</module-width></japanpost></barcode>");
		assertTrue(image.getWidth() > 0);
		assertTrue(image.getHeight() > 0);
	}

	public void testFactoryMatchesBarcodeNamespace() {
		BarcodeInlineObjectFactory factory = new BarcodeInlineObjectFactory();
		assertTrue(factory.match(new CSSElement("http://barcode4j.krysalis.org/ns", "barcode", null, null, null,
				null, null, null, null, -1, -1)));
		assertFalse(factory.match(new CSSElement("http://www.w3.org/1999/xhtml", "barcode", null, null, null, null,
				null, null, null, -1, -1)));
	}

	private static Image parse(String xml) throws Exception {
		BarcodeInlineObject object = new BarcodeInlineObject();
		XMLReader reader = XMLParsers.createXMLReader();
		reader.setContentHandler(object);
		reader.parse(new InputSource(new StringReader(xml)));
		return object.getImage(userAgent());
	}

	private static UserAgent userAgent() {
		return (UserAgent) Proxy.newProxyInstance(BarcodeInlineObjectTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "BarcodeInlineObjectTest.UserAgent";
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}
}
