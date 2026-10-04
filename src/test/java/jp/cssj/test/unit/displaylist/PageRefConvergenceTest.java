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
 * 最終パスの「前方参照が読んだ値が変わった」(非収束)の判定は、<b>読んだもの</b>だけを比べることを
 * 固定します(2026-10-04)。参照先の全部のカウンタ(総頁数など、参照が読んでいないもの)と本文を
 * 比べていたので、単純な目次を 2 パスで組んでも「pass-count を増やせ」の記録が出ていた。
 */
public class PageRefConvergenceTest extends TestCase {
	/** 単純な目次を 2 パスで組むと、収束しているので記録は出ない。 */
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

	/** 1 パス目の値を前方参照が読み、最終パスで参照先が書き直されるまでを再現する。 */
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
