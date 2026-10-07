package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

import junit.framework.TestCase;
import net.zamasoft.foliojet.ua.Counter;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.PageRef.Fragment;

/**
 * Verify that final-pass nonconvergence detection ("a value read by a forward reference changed")
 * compares only <b>what was read</b> (2026-10-04). Comparing all target counters (including total pages
 * and others not read by the reference) and body text logged "increase pass-count" even for a simple
 * table of contents laid out in two passes.
 */
public class PageRefConvergenceTest extends TestCase {
	/** A simple table of contents converges in two passes, so no log entry is emitted. */
	public void testSimpleTableOfContentsIsConverged() throws Exception {
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ "<style>nav a::after { content: leader('.') target-counter(attr(href), page) }"
				+ " h1 { break-before: page }</style></head><body>"
				+ "<nav><p><a href=\"#one\">One</a></p><p><a href=\"#two\">Two</a></p></nav>"
				+ "<h1 id=\"one\">One</h1><p>first</p><h1 id=\"two\">Two</h1><p>second</p></body></html>";
		final List<String> warnings = new ArrayList<>();
		final Logger logger = Logger.getLogger(DirectSession.class.getName());
		final Handler handler = new Handler() {
			@Override
			public void publish(final LogRecord record) {
				if (record.getMessage() != null && record.getMessage().contains("final layout pass")) {
					warnings.add(record.getMessage());
				}
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		logger.addHandler(handler);
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("processing.page-references", "true");
			session.property("processing.pass-count", "2");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///toc.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
			logger.removeHandler(handler);
		}
		assertEquals(warnings.toString(), List.of(), warnings);
	}
	private static final URI TARGET = URI.create("file:///doc.html#ch1");

	private static Counter[] counters(final int page, final int pages) {
		return new Counter[] { new Counter("page", page), new Counter("pages", pages) };
	}

	/**
	 * Reproduce a forward reference reading the first-pass value before the target is rewritten in the final pass.
	 */
	private static PageRef readThenRewrite(final int pageBefore, final int pagesBefore, final int pageAfter,
			final int pagesAfter, final boolean text, final String textBefore, final String textAfter) {
		final PageRef pageRef = new PageRef();
		pageRef.reset();
		pageRef.addFragment(TARGET, counters(pageBefore, pagesBefore), textBefore);
		pageRef.reset();
		final Fragment fragment = pageRef.getFragment(TARGET);
		if (text) {
			fragment.markStaleText();
		} else {
			fragment.markStaleCounter("page");
		}
		pageRef.addFragment(TARGET, counters(pageAfter, pagesAfter), textAfter);
		return pageRef;
	}

	public void testUnreadCounterChangeIsConverged() {
		assertFalse("only the total page count changed",
				readThenRewrite(3, 0, 3, 12, false, "Ch", "Ch").isUnconverged());
	}

	public void testReadCounterChangeIsUnconverged() {
		assertTrue(readThenRewrite(3, 12, 4, 12, false, "Ch", "Ch").isUnconverged());
	}

	public void testTextIsComparedOnlyWhenRead() {
		assertFalse("text changed but only the page was read",
				readThenRewrite(3, 12, 3, 12, false, "Ch", "Chapter").isUnconverged());
		assertTrue("target-text read the text", readThenRewrite(3, 12, 3, 12, true, "Ch", "Chapter").isUnconverged());
	}
}
