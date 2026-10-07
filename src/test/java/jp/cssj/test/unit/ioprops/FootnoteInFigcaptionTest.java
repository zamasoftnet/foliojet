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
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Tests for footnotes inside figure captions ({@code <figcaption>}) (2026-09-03).
 *
 * <p>
 * cti.li report (2026-09-02, production build 19051): with a horizontally written
 * {@code <figure>} (orthogonal flow) inside vertically written body text and a
 * {@code float: footnote} note in its {@code <figcaption>}, the note remained on the original page
 * when the figure moved to the next page. Its number also stayed document-wide instead of resetting
 * per page. A note must be on the same page as its call, with numbering restarting at 1 on each page
 * and the same value at the call and the note's start.
 * </p>
 */
public class FootnoteInFigcaptionTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String STYLE = "@page{size:100mm 150mm;margin:10mm}"
			+ "body{margin:0;font-size:10pt;line-height:1.5;writing-mode:vertical-rl}"
			+ "p{margin:0}" + ".note{float:footnote;font-size:8pt}"
			+ "figure{margin:0;break-inside:avoid;writing-mode:horizontal-tb}"
			+ ".art{background:#ccc}";

	/**
	 * Fill most of page 1 with paragraphs, followed by a 60 mm wide figure (horizontal writing).
	 * The figure does not fit in the remaining space and moves to page 2.
	 * The caption's note is the first note on page 2, so its number is 1.
	 */
	private static String pushedFigure() {
		final StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">").append(STYLE)
				.append("</style></head><body>");
		// Each line (vertical column) is 15 pt wide. Type-area line axis: 80 mm≈227 pt; 11 columns≈165 pt; left≈62 pt<60 mm.
		for (int i = 0; i < 11; ++i) {
			sb.append("<p>本文の段落").append(i).append("です。</p>");
		}
		sb.append("<figure><div class=\"art\" style=\"width:60mm;height:40mm\"></div>")
				.append("<figcaption>図の説明<span class=\"note\">図の注ALPHA</span>です。</figcaption></figure>")
				.append("<p>図の後の本文<span class=\"note\">本文の注BRAVO</span>です。</p>");
		sb.append("</body></html>");
		return sb.toString();
	}

	public void testNoteInPushedFigureFollowsTheFigure() throws Exception {
		final CapturingResults r = convert(pushedFigure());
		assertEquals("two pages are expected: " + r.order + "\n" + dump(r), 2, pageCount(r));
		final String page1 = r.text("pages/0001.json");
		final String page2 = r.text("pages/0002.json");
		assertFalse("the figure was pushed to page 2, so its note must not stay on page 1:\n" + dump(r),
				page1.contains("ALPHA"));
		assertTrue("the caption's note must be on page 2 with the figure:\n" + dump(r), page2.contains("ALPHA"));
		assertTrue("the paragraph's note must be on page 2:\n" + dump(r), page2.contains("BRAVO"));
		// Numbering restarts at 1 on each page: calls 1,2 and note starts 1,2.
		assertEquals("calls on page 2: " + dump(r), List.of("1", "2"), calls(page2));
		assertEquals("markers on page 2: " + dump(r), List.of("1", "2"), markers(page2));
	}

	/**
	 * Three large figures that avoid page breaks are followed by a paragraph with a note.
	 * The figures are divided across one page each, and the note appears on the paragraph's page
	 * (page 4) as number 1. Previously, while the note accumulated on the figure pages,
	 * the engine judged it stalled and placed it two pages before its call with a document-wide number.
	 */
	public void testNoteAfterQueuedFiguresWaitsForItsCall() throws Exception {
		final StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">").append(STYLE)
				.append("</style></head><body>");
		for (int i = 0; i < 3; ++i) {
			sb.append("<figure><div class=\"art\" style=\"width:75mm;height:40mm\"></div>")
					.append("<figcaption>図").append(i).append("の説明です。</figcaption></figure>");
		}
		sb.append("<p>図の後の本文<span class=\"note\">本文の注ALPHA</span>です。</p>");
		sb.append("</body></html>");
		final CapturingResults r = convert(sb.toString());
		assertEquals("four pages are expected: " + r.order + "\n" + dump(r), 4, pageCount(r));
		for (int i = 1; i <= 3; ++i) {
			final String page = r.text(String.format("pages/%04d.json", i));
			assertFalse("page " + i + " holds a figure only:\n" + dump(r), page.contains("ALPHA"));
		}
		final String page4 = r.text("pages/0004.json");
		assertTrue("the note must be on the paragraph's page:\n" + dump(r), page4.contains("ALPHA"));
		assertEquals("call on page 4: " + dump(r), List.of("1"), calls(page4));
		assertEquals("marker on page 4: " + dump(r), List.of("1"), markers(page4));
	}

	/** When a figure fits on the same page, calls inside its orthogonal flow also use that page's numbering. */
	public void testNoteInFittingFigureIsNumberedOnItsPage() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">" + STYLE
				+ "</style></head><body>"
				+ "<p>本文の段落<span class=\"note\">本文の注ALPHA</span>です。</p>"
				+ "<figure><div class=\"art\" style=\"width:30mm;height:30mm\"></div>"
				+ "<figcaption>図の説明<span class=\"note\">図の注BRAVO</span>です。</figcaption></figure>"
				+ "</body></html>";
		final CapturingResults r = convert(html);
		assertEquals("one page is expected: " + r.order + "\n" + dump(r), 1, pageCount(r));
		final String page1 = r.text("pages/0001.json");
		assertTrue(page1.contains("ALPHA") && page1.contains("BRAVO"));
		assertEquals("calls on page 1: " + dump(r), List.of("1", "2"), calls(page1));
		assertEquals("markers on page 1: " + dump(r), List.of("1", "2"), markers(page1));
	}

	/** Digit-only strings in page JSON (::footnote-call numbers). */
	private static List<String> calls(final String pageJson) {
		return values(pageJson, "\"value\":\"(\\d{1,2})\"");
	}

	/** "N. " in page JSON (::footnote-marker numbers). */
	private static List<String> markers(final String pageJson) {
		return values(pageJson, "\"value\":\"(\\d{1,2})\\. \"");
	}

	private static List<String> values(final String pageJson, final String regex) {
		final List<String> labels = new ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(pageJson);
		while (m.find()) {
			labels.add(m.group(1));
		}
		return labels;
	}

	private static String dump(final CapturingResults r) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= pageCount(r); ++i) {
			final String json = r.text(String.format("pages/%04d.json", i));
			sb.append("page ").append(i).append(": ");
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"value\":\"([^\"]*)\"").matcher(json);
			while (m.find()) {
				sb.append('[').append(m.group(1)).append(']');
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static int pageCount(final CapturingResults r) {
		return (int) r.order.stream().filter(u -> u.startsWith("pages/") && u.endsWith(".svg")).count();
	}

	private CapturingResults convert(final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///footnote-in-figcaption.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results;
	}

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
}
