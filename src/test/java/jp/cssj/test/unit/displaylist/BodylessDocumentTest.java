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

import org.xml.sax.Attributes;
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
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A document without a {@code body} element is printed. XHTML and XML input, read by an XML parser, gets no body it
 * does not have, and the filter that keeps the head back until the body starts kept such a document whole and never
 * passed it on: the result was an empty page, and the whole document was in memory.
 */
public class BodylessDocumentTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** XHTML whose content follows the head directly. */
	public void testXhtmlWithoutBody() throws Exception {
		final List<String> texts = texts("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>"
				+ "<style>p.h{display:none}</style></head><div>KEEP</div><p class=\"h\">HIDDEN</p><p>AFTER</p></html>",
				"input.xhtml", "application/xhtml+xml");
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("AFTER: " + texts, texts.contains("AFTER"));
		assertFalse("the style sheet of the head applies: " + texts, texts.contains("HIDDEN"));
	}

	/** XML of another vocabulary, whose elements are taken as XHTML ones. */
	public void testXmlWithoutBody() throws Exception {
		final List<String> texts = texts("<doc><p>KEEP</p><p>AFTER</p></doc>", "input.xml", "text/xml");
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("AFTER: " + texts, texts.contains("AFTER"));
	}

	/**
	 * The filter alone, fed SAX events: the head is kept back, and all of it is passed on when the first element
	 * that is content starts, not at the end of the document.
	 */
	public void testContentIsPassedOnWhenItStarts() throws Exception {
		final List<String> seen = new ArrayList<>();
		final XHTMLPreprocessFilter filter = filter(seen);
		final AttributesImpl none = new AttributesImpl();
		filter.startDocument();
		filter.startElement(XHTML.URI, "html", "html", none);
		characters(filter, "\n");
		filter.startElement(XHTML.URI, "head", "head", none);
		filter.startElement(XHTML.URI, "title", "title", none);
		characters(filter, "TITLE");
		filter.endElement(XHTML.URI, "title", "title");
		filter.startElement(XHTML.URI, "noscript", "noscript", none);
		filter.endElement(XHTML.URI, "noscript", "noscript");
		filter.endElement(XHTML.URI, "head", "head");
		characters(filter, "\n");
		assertEquals("the head is kept back", List.of(), seen);

		filter.startElement(XHTML.URI, "div", "div", none);
		assertEquals("the head and the element are passed on when the content starts",
				List.of("<html>", "<head>", "<title>", "TITLE", "<noscript>", "<div>"), seen);
		characters(filter, "TEXT");
		assertEquals("text is passed on at once", "TEXT", seen.get(seen.size() - 1));
		filter.endElement(XHTML.URI, "div", "div");
		filter.endElement(XHTML.URI, "html", "html");
		filter.endDocument();
		assertEquals("END", seen.get(seen.size() - 1));
	}

	/** A root element that is no html element is content itself; so is text directly in the html element. */
	public void testRootAndTextAreContent() throws Exception {
		final AttributesImpl none = new AttributesImpl();
		final List<String> root = new ArrayList<>();
		XHTMLPreprocessFilter filter = filter(root);
		filter.startDocument();
		filter.startElement(XHTML.URI, "doc", "doc", none);
		assertEquals(List.of("<doc>"), root);

		final List<String> text = new ArrayList<>();
		filter = filter(text);
		filter.startDocument();
		filter.startElement(XHTML.URI, "html", "html", none);
		characters(filter, "TEXT");
		assertEquals(List.of("<html>", "TEXT"), text);
	}

	/** A document that is a head and nothing else is passed on at its end. */
	public void testHeadOnly() throws Exception {
		final List<String> seen = new ArrayList<>();
		final XHTMLPreprocessFilter filter = filter(seen);
		final AttributesImpl none = new AttributesImpl();
		filter.startDocument();
		filter.startElement(XHTML.URI, "html", "html", none);
		filter.startElement(XHTML.URI, "head", "head", none);
		filter.endElement(XHTML.URI, "head", "head");
		filter.endElement(XHTML.URI, "html", "html");
		assertEquals(List.of(), seen);
		filter.endDocument();
		assertEquals(List.of("<html>", "<head>", "END"), seen);
	}

	/** A filter whose output is recorded: the start of each element, non-blank text, and the end of the document. */
	private static XHTMLPreprocessFilter filter(final List<String> seen) {
		final DefaultHandler2 out = new DefaultHandler2() {
			@Override
			public void startElement(final String uri, final String lName, final String qName, final Attributes atts) {
				seen.add("<" + lName + ">");
			}

			@Override
			public void characters(final char[] ch, final int start, final int length) {
				final String text = new String(ch, start, length);
				if (!text.isBlank()) {
					seen.add(text);
				}
			}

			@Override
			public void endDocument() {
				seen.add("END");
			}
		};
		final XHTMLPreprocessFilter filter = new XHTMLPreprocessFilter(userAgent());
		filter.setXMLHandler(XMLHandler.of(out, out));
		return filter;
	}

	private static void characters(final XHTMLPreprocessFilter filter, final String text) throws SAXException {
		filter.characters(text.toCharArray(), 0, text.length());
	}

	/** A user agent that answers what the filter asks: no property is set, and it is of no medium. */
	private static UserAgent userAgent() {
		return (UserAgent) Proxy.newProxyInstance(BodylessDocumentTest.class.getClassLoader(),
				new Class<?>[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getProperty":
						return null;
					case "is":
						return Boolean.FALSE;
					case "toString":
						return "BodylessDocumentTest.UserAgent";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}

	/** Converts the document and returns the texts drawn, in the order of the display lists of its pages. */
	private static List<String> texts(final String document, final String name, final String mimeType)
			throws Exception {
		final File dir = Files.createTempDirectory("bodyless").toFile();
		final File input = new File(dir, name);
		Files.writeString(input.toPath(), document, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, mimeType, null);
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
		return texts;
	}
}
