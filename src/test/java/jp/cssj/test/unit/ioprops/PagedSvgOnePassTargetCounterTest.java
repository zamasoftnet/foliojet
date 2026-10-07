package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that page-split SVG also outputs {@code target-counter()} numbers in one pass
 * (2026-10-04, docs/design/one-pass-target-counter-design.md §8).
 * Record and retain drawing for pages referencing later pages, then draw and emit them once targets
 * are available (at document end at the latest).
 */
public class PagedSvgOnePassTargetCounterTest extends TestCase {
	private static final String STYLE = """
			<style>
			nav a::after { content: leader(".") target-counter(attr(href), page) }
			.back::after { content: " (p. " target-counter(attr(href), page) ")" }
			h1 { break-before: page }
			</style>""";

	private static final String CHAPTERS = """
			<nav><p><a href="#one">One</a></p><p><a href="#two">Two</a></p></nav>
			<h1 id="one">Chapter One</h1><p>first</p>
			<h1 id="two">Chapter Two</h1><p>second <a class="back" href="#one">back</a></p>
			""";

	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final String uri = metadata.getURI().toString();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(uri, out);
			this.order.add(uri);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// Do nothing.
		}

		String text(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toString(StandardCharsets.UTF_8);
		}
	}

	private static CapturingResults convert(final String body, final List<String> warnings) throws Exception {
		return convert(body, warnings, new String[0]);
	}

	private static CapturingResults convert(final String body, final List<String> warnings, final String... props)
			throws Exception {
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ STYLE + "</head><body>" + body + "</body></html>";
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(results);
			session.setMessageHandler(new MessageHandler() {
				@Override
				public void message(final short code, final String[] args, final String message) {
					if (code == 0x2822 || code == 0x2823) {
						warnings.add(Integer.toHexString(code) + " " + message);
					}
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			for (int i = 0; i < props.length; i += 2) {
				session.property(props[i], props[i + 1]);
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///paged-svg-one-pass.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		return results;
	}

	/**
	 * The contents page is emitted last with forward-reference numbers. Backward references fill immediately;
	 * manifest uses page order.
	 */
	public void testTableOfContentsPageIsEmittedLastWithNumbers() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final CapturingResults results = convert(CHAPTERS, warnings);
		final String toc = results.text("pages/0001.svg");
		assertTrue(toc, toc.contains(">2</text>"));
		assertTrue(toc, toc.contains(">3</text>"));
		assertTrue("back reference", results.text("pages/0003.svg").contains(">2</text>"));
		final int first = results.order.indexOf("pages/0001.svg");
		final int last = results.order.indexOf("pages/0003.svg");
		assertTrue("the table of contents waits for its targets: " + results.order, first > last);
		final String manifest = results.text("manifest.json");
		final int p1 = manifest.indexOf("pages/0001.svg"), p2 = manifest.indexOf("pages/0002.svg"),
				p3 = manifest.indexOf("pages/0003.svg");
		assertTrue("manifest lists the pages in order: " + manifest, 0 <= p1 && p1 < p2 && p2 < p3);
		assertEquals("no warning: " + warnings, List.of(), warnings);
	}

	/** Documents without fields are emitted in page order as before. */
	public void testDocumentsWithoutSlotsKeepTheOrder() throws Exception {
		final CapturingResults results = convert("<p>a</p><h1>b</h1><h1>c</h1>", new ArrayList<>());
		final int p1 = results.order.indexOf("pages/0001.svg"), p2 = results.order.indexOf("pages/0002.svg"),
				p3 = results.order.indexOf("pages/0003.svg");
		assertTrue(results.order.toString(), 0 <= p1 && p1 < p2 && p2 < p3);
	}

	/**
	 * Recording preserves the direct drawing method: blur that SVG renders exactly remains a filter
	 * (if the recorder reports it unsupported, drawing uses approximate overpainting that replay cannot undo).
	 */
	public void testRecordedPageKeepsExactEffects() throws Exception {
		final CapturingResults results = convert(
				"<nav><p><a href=\"#one\">One</a></p><div style=\"width:50pt;height:20pt;background:#ccc;"
						+ "box-shadow:4pt 4pt 6pt #888\">shadow</div></nav><h1 id=\"one\">Chapter</h1>",
				new ArrayList<>());
		final int first = results.order.indexOf("pages/0001.svg");
		final int second = results.order.indexOf("pages/0002.svg");
		assertTrue("the page was held: " + results.order, first > second);
		assertTrue("blur stays an SVG filter", results.text("pages/0001.svg").contains("<filter"));
	}

	/** If waiting pages exceed the limit (64), emit older pages with numbers known then and warn once. */
	public void testHeldPagesAreCapped() throws Exception {
		final StringBuilder body = new StringBuilder();
		for (int i = 1; i <= 66; ++i) {
			body.append(i == 1 ? "<p>" : "<h1>").append("<a href=\"#end\">to end</a>").append(i == 1 ? "</p>" : "</h1>");
		}
		body.append("<h1 id=\"end\">End</h1>");
		final List<String> warnings = new ArrayList<>();
		final CapturingResults results = convert(
				"<style>a::after { content: target-counter(attr(href), page) }</style>" + body, warnings);
		assertNotNull(results.data.get("pages/0067.svg"));
		assertEquals(warnings.toString(), 1, warnings.stream().filter(w -> w.contains("64")).count());
		assertTrue("the last held page gets the number", results.text("pages/0066.svg").contains(">67</text>"));
	}

	/**
	 * Even when stopped at the page-count limit (abort=normal), deferred pages are emitted with
	 * the numbers known at that point (the form used when cti.li page_text specifies page).
	 */
	public void testPageLimitStillEmitsHeldPages() throws Exception {
		final CapturingResults results = convert(CHAPTERS, new ArrayList<>(), "output.page-limit", "1",
				"output.page-limit.abort", "normal");
		final String toc = results.text("pages/0001.svg");
		assertTrue(toc, toc.contains(">One</text>"));
		assertFalse("the target page was not laid out", toc.contains(">2</text>"));
	}

	/** Fields with no target remain empty without warnings (the same as PDF). */
	public void testMissingTargetIsEmptyWithoutWarning() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final CapturingResults results = convert("<nav><p><a href=\"#none\">Nowhere</a></p></nav>", warnings);
		final String page = results.text("pages/0001.svg");
		assertTrue(page, page.contains("Nowhere"));
		assertEquals(warnings.toString(), List.of(), warnings);
	}
}
