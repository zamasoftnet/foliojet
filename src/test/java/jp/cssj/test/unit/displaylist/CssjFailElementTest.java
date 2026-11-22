package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.xml.filter.CSSJMLHandlerFilter;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * {@code <cssj:fail>}, the element that makes a conversion fail on purpose for the tests of error handling, does
 * nothing unless the JVM runs with the system property {@link CSSJMLHandlerFilter#FAIL_ELEMENT_PROPERTY}. It was
 * always on: any document could end its conversion with an unexpected error, and one without the type attribute
 * did so with a {@code NullPointerException}.
 */
public class CssjFailElementTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	private static final String HTML = "<html xmlns=\"http://www.w3.org/1999/xhtml\""
			+ " xmlns:cssj=\"http://www.cssj.jp/ns/cssjml\"><body>";

	/** Without the property the element fails nothing, with or without a type. */
	public void testOffByDefault() throws Exception {
		assertFalse("this test runs without the property", Boolean.getBoolean(CSSJMLHandlerFilter.FAIL_ELEMENT_PROPERTY));
		assertKeepAndAfter(HTML + "<p>KEEP</p><cssj:fail/><p>AFTER</p></body></html>");
		assertKeepAndAfter(HTML + "<p>KEEP</p><cssj:fail type=\"runtime\"/><cssj:fail type=\"error\"/>"
				+ "<cssj:fail type=\"sax\"/><cssj:fail type=\"graphics\"/><cssj:fail type=\"abort-hard\"/>"
				+ "<p>AFTER</p></body></html>");
	}

	/** With the property the element fails the conversion, as the tests of error handling need it to. */
	public void testOnWithProperty() throws Exception {
		System.setProperty(CSSJMLHandlerFilter.FAIL_ELEMENT_PROPERTY, "true");
		try {
			final List<String> texts;
			try {
				texts = texts(HTML + "<p>KEEP</p><cssj:fail type=\"sax\"/><p>AFTER</p></body></html>");
			} catch (Exception e) {
				// The conversion failed, as asked
				return;
			}
			assertFalse("the conversion stops at the element: " + texts, texts.contains("AFTER"));
		} finally {
			System.clearProperty(CSSJMLHandlerFilter.FAIL_ELEMENT_PROPERTY);
		}
	}

	private static void assertKeepAndAfter(final String document) throws Exception {
		final List<String> texts = texts(document);
		assertTrue("KEEP is on the page: " + document + " -> " + texts, texts.contains("KEEP"));
		assertTrue("AFTER is on the page: " + document + " -> " + texts, texts.contains("AFTER"));
	}

	/** Converts the XHTML document and returns the texts drawn, in the order of the display lists of its pages. */
	private static List<String> texts(final String document) throws Exception {
		final File dir = Files.createTempDirectory("cssj-fail").toFile();
		final File input = new File(dir, "input.xhtml");
		Files.writeString(input.toPath(), document, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
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
