package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;
import net.zamasoft.foliojet.xml.util.XMLUtils;

/**
 * The encoding an XML declaration names is read from it. The bytes of the declaration were collected as numbers,
 * so the encoding pseudo-attribute was never found and a document that starts with an XML declaration and comes
 * as text/html without a charset was decoded as UTF-8 whatever it declared (Shift_JIS, EUC-JP).
 */
public class XmlDeclEncodingTest extends TestCase {
	public void testDeclaredEncoding() throws Exception {
		assertEquals("Shift_JIS", encoding("<?xml version=\"1.0\" encoding=\"Shift_JIS\"?><html></html>"));
		assertEquals("EUC-JP", encoding("<?xml version='1.0' encoding='EUC-JP' ?>\n<html></html>"));
	}

	/** An XML declaration without an encoding: UTF-8, the default of XML. */
	public void testNoEncoding() throws Exception {
		assertEquals("UTF-8", encoding("<?xml version=\"1.0\"?><html></html>"));
	}

	/** No XML declaration, or one that names no charset: nothing is decided here. */
	public void testNothingDeclared() throws Exception {
		assertNull(encoding("<html><head><title>t</title></head><body>b</body></html>"));
		assertNull(encoding("<?xml version=\"1.0\" encoding=\"no-such-charset\"?><html></html>"));
		assertNull(encoding("<?xml version=\"1.0\" encoding=\"!\"?><html></html>"));
		assertNull(encoding("<?xml version=\"1.0\" encoding=\"UTF-8\""));
	}

	/** The stream is left at its start. */
	public void testStreamIsNotConsumed() throws Exception {
		final InputStream in = stream("<?xml version=\"1.0\" encoding=\"Shift_JIS\"?><html></html>");
		XMLUtils.checkXMLDeclEncoding(in);
		assertEquals('<', in.read());
		assertEquals('?', in.read());
	}

	private static String encoding(final String document) throws Exception {
		return XMLUtils.checkXMLDeclEncoding(stream(document));
	}

	private static InputStream stream(final String document) {
		return new ByteArrayInputStream(document.getBytes(StandardCharsets.US_ASCII));
	}
}
