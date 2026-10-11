package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A balanced multicol broken across pages counts the retained text of the fragment it holds, not of all its pages
 * (2026-10-11). The page breaks emit what it held, and balancing at its end lays out only the last page's fragment
 * again; it counted all its text until it closed, so seven pages of two columns (about 4,800 bytes of text, 700 a page)
 * failed a limit of 2,000 bytes that the same multicol with {@code column-fill: auto} passed.
 */
public class MulticolRetainedTextTest extends TestCase {
	private static final int LIMIT = 2000;

	private static final String STYLE = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page { size: 200pt 200pt; margin: 10pt } body { margin: 0; font: 10pt/12pt serif }"
			+ " p { margin: 0 } div.mc { column-count: 2; column-gap: 10pt }"
			+ " .next { break-before: page } .flat { height: 0; font: 1pt/1pt serif }</style></head><body>";

	private static String paragraphs(final int count, final String attrs) {
		final StringBuilder paras = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			paras.append("<p").append(attrs).append(">P").append(10 + i)
					.append(" lorem ipsum dolor sit amet consectetur adipiscing elit sed.</p>");
		}
		return paras.toString();
	}

	/** Seven pages of text, about 700 bytes a page, pass a limit of 2,000 bytes. */
	public void testBalancedColumnsCountOnePage() throws Exception {
		final Result balanced = convert("balance",
				STYLE + "<div class=\"mc\">" + paragraphs(40, "") + "</div><p>END</p></body></html>");
		assertTrue("pages " + balanced.pages, balanced.pages >= 5);
		assertTrue("retained " + balanced.retained, balanced.retained <= LIMIT);
	}

	/**
	 * The fragment the multicol still holds after a page break counts: 40 paragraphs of no height, all on the second
	 * page (about 4,800 bytes), exceed the limit (codex review: counting stopped at the first page break let the last
	 * fragment hold any amount of text).
	 */
	public void testLastFragmentCounts() throws Exception {
		assertLimitReached("last-fragment", STYLE + "<div class=\"mc\"><p>A</p><div class=\"next\">"
				+ paragraphs(40, " class=\"flat\"") + "</div></div></body></html>");
	}

	/**
	 * The same after a table inside the multicol broke across the page (codex review): a scope reopened for the
	 * multicol after the table's was closed together with the table's, and the paragraphs went uncounted.
	 */
	public void testLastFragmentCountsAfterTable() throws Exception {
		assertLimitReached("after-table", STYLE + "<div class=\"mc\"><table><tr><td>A</td></tr>"
				+ "<tr style=\"break-before: page\"><td>B</td></tr></table>" + paragraphs(40, " class=\"flat\"")
				+ "</div></body></html>");
	}

	private static void assertLimitReached(final String name, final String html) throws Exception {
		try {
			convert(name, html);
			fail("the retained text limit was not reached");
		} catch (final TranscoderException e) {
			assertTrue(String.valueOf(e.getMessage()), String.valueOf(e.getMessage()).contains("retained text limit"));
		}
	}

	private record Result(int pages, long retained) {
	}

	private static Result convert(final String name, final String html) throws Exception {
		final File dir = Files.createTempDirectory("multicol-retained-" + name).toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		final PDFUserAgent ua = new PDFUserAgent() {
		};
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.property("processing.retained-text-limit", Integer.toString(LIMIT));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(name + ": no pages", pages);
		return new Result(pages.length, ua.getRetainedTextLimit().getHighWater());
	}
}
