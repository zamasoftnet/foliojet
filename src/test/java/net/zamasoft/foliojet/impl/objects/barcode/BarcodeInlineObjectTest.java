package net.zamasoft.foliojet.impl.objects.barcode;

import java.io.StringReader;
import java.lang.reflect.Proxy;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.util.XMLParsers;
import net.zamasoft.pdfg2d.gc.image.Image;

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

	public void testFactoryMatchesBarcodeNamespace() {
		BarcodeInlineObjectFactory factory = new BarcodeInlineObjectFactory();
		assertTrue(factory.match(new CSSElement("http://barcode4j.krysalis.org/ns", "barcode", null, null, null,
				null, null, null, null, -1)));
		assertFalse(factory.match(new CSSElement("http://www.w3.org/1999/xhtml", "barcode", null, null, null, null,
				null, null, null, -1)));
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
