package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.AttributesImpl;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.filter.XHTMLPreprocessFilter;
import net.zamasoft.foliojet.xml.vocab.CSSJML;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The text buffer of {@code <title>} is released at the end of the element, also with
 * {@code output.use-meta-info=false}. It stayed open then: every later character of the document was kept in it, and a
 * later style element that is skipped (one for another medium) found the buffer open and passed all that text on as a
 * style sheet.
 */
public class TitleBufferReleaseTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/**
	 * The filter alone, fed SAX events: a title, a style element for a medium the user agent is not, and a body. No
	 * style sheet may come out of it: one does when the title's buffer is still collecting at the end of the style
	 * element.
	 */
	public void testNoStyleSheetFromTitleBuffer() throws Exception {
		final List<String[]> off = feed("false");
		for (final String[] pi : off) {
			assertFalse("style sheet made of the title buffer: " + pi[1], CSSJML.PI_STYLESHEET.equals(pi[0]));
			assertFalse("document information is off: " + pi[1], CSSJML.PI_DOCUMENT_INFO.equals(pi[0]));
		}

		// With the document information on (the default), the title is passed on and the buffer is released as before
		final List<String[]> on = feed(null);
		assertEquals("processing instructions", 1, on.size());
		assertEquals(CSSJML.PI_DOCUMENT_INFO, on.get(0)[0]);
		assertTrue(on.get(0)[1], on.get(0)[1].contains("TITLE"));
	}

	/** The same in a conversion: the style element for another medium does not hide the paragraph. */
	public void testSkippedStyleElementAfterTitle() throws Exception {
		final File dir = Files.createTempDirectory("title-buffer").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><title>x{} </title>"
				+ "<style media=\"tty\">p.k{display:none}</style></head>"
				+ "<body><p class=\"k\">KEEP</p><p>AFTER</p></body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.property("output.use-meta-info", "false");
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> texts = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				texts.add(m.group(1));
			}
		}
		assertTrue("KEEP is on the page: " + texts, texts.contains("KEEP"));
		assertTrue("AFTER is on the page: " + texts, texts.contains("AFTER"));
	}

	/** Feeds a document to the filter and returns the processing instructions it passes on, as {target, data}. */
	private static List<String[]> feed(final String useMetaInfo) throws SAXException {
		final List<String[]> pis = new ArrayList<>();
		final DefaultHandler2 out = new DefaultHandler2() {
			@Override
			public void processingInstruction(final String target, final String data) {
				pis.add(new String[] { target, data });
			}
		};
		final XHTMLPreprocessFilter filter = new XHTMLPreprocessFilter(userAgent(useMetaInfo));
		filter.setXMLHandler(XMLHandler.of(out, out));

		final AttributesImpl none = new AttributesImpl();
		final AttributesImpl otherMedium = new AttributesImpl();
		otherMedium.addAttribute(XHTML.URI, "media", "media", "CDATA", "tty");
		filter.startDocument();
		filter.startElement(XHTML.URI, "html", "html", none);
		filter.startElement(XHTML.URI, "head", "head", none);
		filter.startElement(XHTML.URI, "title", "title", none);
		characters(filter, "TITLE");
		filter.endElement(XHTML.URI, "title", "title");
		filter.startElement(XHTML.URI, "style", "style", otherMedium);
		characters(filter, "p{display:none}");
		filter.endElement(XHTML.URI, "style", "style");
		filter.endElement(XHTML.URI, "head", "head");
		filter.startElement(XHTML.URI, "body", "body", none);
		filter.startElement(XHTML.URI, "p", "p", none);
		characters(filter, "BODY");
		filter.endElement(XHTML.URI, "p", "p");
		filter.endElement(XHTML.URI, "body", "body");
		filter.endElement(XHTML.URI, "html", "html");
		filter.endDocument();
		return pis;
	}

	private static void characters(final XHTMLPreprocessFilter filter, final String text) throws SAXException {
		filter.characters(text.toCharArray(), 0, text.length());
	}

	/**
	 * A user agent that answers what the filter asks: the property, and that it is of no medium a style element names.
	 *
	 * @param useMetaInfo the value of output.use-meta-info, or null for its default
	 */
	private static UserAgent userAgent(final String useMetaInfo) {
		return (UserAgent) Proxy.newProxyInstance(TitleBufferReleaseTest.class.getClassLoader(),
				new Class<?>[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getProperty":
						return "output.use-meta-info".equals(args[0]) ? useMetaInfo : null;
					case "is":
						return Boolean.FALSE;
					case "toString":
						return "TitleBufferReleaseTest.UserAgent";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}
}
