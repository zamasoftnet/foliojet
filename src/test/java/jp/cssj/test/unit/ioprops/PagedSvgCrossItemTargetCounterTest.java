package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Page numbers of the whole book in an EPUB laid out to Paged SVG with two or more passes (2026-10-21, a report from
 * the paperback proofs, TECH-20261020-006): the table of contents had a number only on the line pointing into its own
 * spine item, because each item is laid out as its own document. The items now end each pass together and share
 * their page numbers and targets, so {@code target-counter()} into another item, {@code counter(page)} and
 * {@code counter(pages)} give the numbers of the book, as in PDF ({@code firstPage} of {@code index.json}).
 */
public class PagedSvgCrossItemTargetCounterTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String TOC_CSS = "@page { size: 148mm 210mm; margin: 20mm;"
			+ " @bottom-center { content: \"F\" counter(page) \"/\" counter(pages); } }"
			+ " nav.toc ol { list-style: none; margin: 0; padding: 0; } nav.toc li { margin: 2mm 0; }"
			+ " nav.toc a::after { content: leader('.') target-counter(attr(href), page); }";

	/**
	 * a.xhtml holds a table of contents of all three items; c.xhtml points back to a, and to b and to itself without a
	 * fragment.
	 */
	private static byte[] tocBook() throws Exception {
		return epub(TOC_CSS, "<h1 id=\"a\">AAA</h1><nav class=\"toc\"><ol>"
				+ "<li><a href=\"a.xhtml#a\">TOCA</a></li><li><a href=\"b.xhtml#b\">TOCB</a></li>"
				+ "<li><a href=\"c.xhtml#c\">TOCC</a></li></ol></nav>", "<h1 id=\"b\">BBB</h1>",
				"<h1 id=\"c\">CCC</h1><nav class=\"toc\"><ol>"
						+ "<li><a href=\"a.xhtml#a\">BACKA</a></li><li><a href=\"b.xhtml\">DOCB</a></li>"
						+ "<li><a href=\"c.xhtml\">SELF</a></li></ol></nav>");
	}

	/** The repro of the report: the line pointing into another item had no number. */
	public void testTargetCounterIntoAnotherItem() throws Exception {
		final Run r = run(tocBook(), Map.of("processing.pass-count", "2"));
		assertEquals("AAA|TOCA|1|TOCB|2|TOCC|3|F1/3|", r.texts("items/0001/pages/0001.svg"));
		assertEquals("BBB|F2/3|", r.texts("items/0002/pages/0001.svg"));
		// Back into an earlier item, and to the start of an item without a fragment, its own too (as PDF does)
		assertEquals("CCC|BACKA|1|DOCB|2|SELF|3|F3/3|", r.texts("items/0003/pages/0001.svg"));
		assertFalse("the numbers converged: " + r.codes, r.codes.contains(MessageCodes.WARN_UNCONVERGED_PAGE_REFERENCES));
		// The release still counts each item's pages from its own first page (index.json adds the earlier items)
		assertTrue(r.text("items/0002/pages/0001.json"), r.text("items/0002/pages/0001.json").contains("\"page\":1"));
		assertTrue(r.text("index.json"), r.text("index.json").contains("\"firstPage\":3,\"pageCount\":1"));

		final Run three = run(tocBook(), Map.of("processing.pass-count", "3"));
		assertEquals("AAA|TOCA|1|TOCB|2|TOCC|3|F1/3|", three.texts("items/0001/pages/0001.svg"));
		assertEquals("CCC|BACKA|1|DOCB|2|SELF|3|F3/3|", three.texts("items/0003/pages/0001.svg"));
	}

	/** Only the included items count, as in index.json; a target in an item left out stays empty. */
	public void testSpineSelectionCountsTheIncludedItems() throws Exception {
		final Run r = run(tocBook(), Map.of("processing.pass-count", "2", "input.epub.spine", "1, 3"));
		assertEquals("AAA|TOCA|1|TOCB|TOCC|2|F1/2|", r.texts("items/0001/pages/0001.svg"));
		assertEquals("CCC|BACKA|1|DOCB|SELF|2|F2/2|", r.texts("items/0003/pages/0001.svg"));
		assertTrue(r.text("index.json"), r.text("index.json").contains("\"firstPage\":2,\"pageCount\":1"));
	}

	/**
	 * A single pass keeps each item's own numbering: the other items are not laid out yet. (The one-pass number
	 * slots of target-counter() are images, not text, so the table of contents shows no number here either.)
	 */
	public void testOnePassKeepsTheItemsNumbering() throws Exception {
		final Run r = run(tocBook(), Map.of("processing.pass-count", "1"));
		assertEquals("BBB|F1/0|", r.texts("items/0002/pages/0001.svg"));
		assertEquals("CCC|BACKA|DOCB|SELF|F1/0|", r.texts("items/0003/pages/0001.svg"));
	}

	/**
	 * The sides and :first of the book in two passes (2026-10-28, tech: cti.li's reader numbered and sided each
	 * chapter from its first page): a 3, b 5 and c 3 pages, as one PDF gives them. Before, each item began on a
	 * right :first page.
	 */
	public void testPageSidesOfTheBookInTwoPasses() throws Exception {
		final String book = "1.1 A1|FIRST|RIGHT|p1|,1.2 A2|LEFT|p2|,1.3 A3|RIGHT|p3|,2.1 B1|LEFT|p4|,2.2 B2|RIGHT|p5|,"
				+ "2.3 B3|LEFT|p6|,2.4 B4|RIGHT|p7|,2.5 B5|LEFT|p8|,3.1 C1|RIGHT|p9|,3.2 C2|LEFT|p10|,3.3 C3|RIGHT|p11|";
		for (final String passes : new String[] { "2", "3" }) {
			final Run r = run(sideBook(), Map.of("processing.pass-count", passes));
			final List<String> pages = new ArrayList<>();
			for (final String uri : r.order) {
				if (uri.matches("items/\\d+/pages/\\d+\\.svg")) {
					pages.add(uri.replaceAll("items/0*(\\d+)/pages/0*(\\d+)\\.svg", "$1.$2") + " " + r.texts(uri));
				}
			}
			assertEquals("passes " + passes, book, String.join(",", pages));
		}
	}

	/** The items wait for each other between passes; the output stays the same at any parallelism. */
	public void testConcurrencyDoesNotChangeTheOutput() throws Exception {
		final Run sequential = run(tocBook(), Map.of("processing.pass-count", "2", "processing.concurrency", "1"));
		final Run parallel = run(tocBook(), Map.of("processing.pass-count", "2", "processing.concurrency", "3"));
		assertEquals(sequential.order, parallel.order);
		for (final String uri : sequential.order) {
			assertTrue(uri + " must be byte-identical",
					Arrays.equals(sequential.data.get(uri).toByteArray(), parallel.data.get(uri).toByteArray()));
		}
	}

	/**
	 * An item whose page count changes in the last pass (b.xhtml reads a long paragraph of c.xhtml with
	 * {@code target-text()}, which the first pass did not know) moves the pages after it: warn as for a single
	 * document. One more pass converges.
	 */
	public void testUnconvergedPageCountWarns() throws Exception {
		final String css = "@page { size: 120pt 90pt; margin: 8pt; @bottom-center { content: counter(page); } }"
				+ " body { margin: 0; font-size: 11pt } a.t::after { content: target-text(attr(href)) }";
		final byte[] book = epub(css, "<p>AAA</p>", "<p><a class=\"t\" href=\"c.xhtml#c\">B</a></p>",
				"<p id=\"c\">" + "甲乙丙丁戊己庚辛壬癸".repeat(8) + "</p>");
		final Run two = run(book, Map.of("processing.pass-count", "2"));
		assertTrue("b grows in the last pass: " + two.order, two.data.containsKey("items/0002/pages/0002.svg"));
		assertTrue("must warn: " + two.codes, two.codes.contains(MessageCodes.WARN_UNCONVERGED_PAGE_REFERENCES));

		final Run three = run(book, Map.of("processing.pass-count", "3"));
		assertFalse("must converge: " + three.codes, three.codes.contains(MessageCodes.WARN_UNCONVERGED_PAGE_REFERENCES));
		final long bPages = three.order.stream().filter(u -> u.matches("items/0002/pages/\\d+\\.svg")).count();
		assertTrue(three.texts("items/0003/pages/0001.svg"),
				three.texts("items/0003/pages/0001.svg").endsWith("|" + (bPages + 2) + "|"));
	}

	/**
	 * Two items with the same {@code <base>} share the keys of their targets ({@code OEBPS/#end}): each keeps its own
	 * target, read by its own forward reference, rather than the other item's.
	 */
	public void testOwnTargetWinsOverTheSameKeyInAnotherItem() throws Exception {
		final String css = "@page { size: 148mm 210mm; margin: 20mm; } a.r::after { content: target-counter(attr(href), page) }";
		final String body = "<p><a class=\"r\" href=\"#end\">R</a></p><p id=\"end\">END</p>";
		final String base = "<base href=\"./\"/>";
		final Run r = run(epub(css, base + body, base + body, "<p>CCC</p>"), Map.of("processing.pass-count", "2"));
		assertEquals("R|1|END|", r.texts("items/0001/pages/0001.svg"));
		assertEquals("R|2|END|", r.texts("items/0002/pages/0001.svg"));
	}

	/**
	 * A target text read from another item that changes in the last pass (c's text reaches b's target only in the
	 * last pass, after a has read b's target) warns; one more pass converges.
	 */
	public void testUnconvergedTargetTextWarns() throws Exception {
		final String css = "@page { size: 148mm 210mm; margin: 20mm; } a.t::after { content: target-text(attr(href)) }";
		final byte[] book = epub(css, "<p><a class=\"t\" href=\"b.xhtml#b\">A</a></p>",
				"<p id=\"b\">B<a class=\"t\" href=\"c.xhtml#c\"></a></p>", "<p id=\"c\">C</p>");
		final Run two = run(book, Map.of("processing.pass-count", "2"));
		assertTrue("must warn: " + two.codes, two.codes.contains(MessageCodes.WARN_UNCONVERGED_PAGE_REFERENCES));
		final Run three = run(book, Map.of("processing.pass-count", "3"));
		assertFalse("must converge: " + three.codes, three.codes.contains(MessageCodes.WARN_UNCONVERGED_PAGE_REFERENCES));
		assertEquals("A|BC|", three.texts("items/0001/pages/0001.svg"));

		// Only what was read counts: a reads the page of b's target, not its text, so the text changing is no warning
		final String counterCss = "@page { size: 148mm 210mm; margin: 20mm; }"
				+ " a.t::after { content: target-text(attr(href)) } a.n::after { content: target-counter(attr(href), page) }";
		final Run page = run(epub(counterCss, "<p><a class=\"n\" href=\"b.xhtml#b\">A</a></p>",
				"<p id=\"b\">B<a class=\"t\" href=\"c.xhtml#c\"></a></p>", "<p id=\"c\">C</p>"),
				Map.of("processing.pass-count", "2"));
		assertEquals("A|2|", page.texts("items/0001/pages/0001.svg"));
		assertFalse("no warning: " + page.codes, page.codes.contains(MessageCodes.WARN_UNCONVERGED_PAGE_REFERENCES));
	}

	/** Page sides and :first of the whole book (tech 2026-10-28, cti.li's reader): a 3, b 5, c 3 pages. */
	private static byte[] sideBook() throws Exception {
		final String css = "@page { size: 120pt 90pt; margin: 15pt; @bottom-center { content: \"p\" counter(page); } }"
				+ " @page :left { @top-left { content: \"LEFT\"; } } @page :right { @top-right { content: \"RIGHT\"; } }"
				+ " @page :first { @top-center { content: \"FIRST\"; } } body { margin: 0; font-size: 8pt }"
				+ " p { margin: 0; break-after: page } p:last-child { break-after: auto }";
		return epub(css, "<p>A1</p><p>A2</p><p>A3</p>", "<p>B1</p><p>B2</p><p>B3</p><p>B4</p><p>B5</p>",
				"<p>C1</p><p>C2</p><p>C3</p>");
	}

	private static byte[] epub(final String css, final String a, final String b, final String c) throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			entry(zip, "mimetype", "application/epub+zip");
			entry(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?>"
					+ "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
					+ "<rootfiles><rootfile full-path=\"OEBPS/content.opf\""
					+ " media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
			entry(zip, "OEBPS/content.opf", "<?xml version=\"1.0\"?>"
					+ "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\">"
					+ "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
					+ "<dc:identifier id=\"uid\">urn:uuid:cross-item</dc:identifier>"
					+ "<dc:title>t</dc:title><dc:language>ja</dc:language></metadata><manifest>"
					+ "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
					+ "<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>"
					+ "<item id=\"a\" href=\"a.xhtml\" media-type=\"application/xhtml+xml\"/>"
					+ "<item id=\"b\" href=\"b.xhtml\" media-type=\"application/xhtml+xml\"/>"
					+ "<item id=\"c\" href=\"c.xhtml\" media-type=\"application/xhtml+xml\"/>"
					+ "</manifest><spine><itemref idref=\"a\"/><itemref idref=\"b\"/><itemref idref=\"c\"/></spine>"
					+ "</package>");
			entry(zip, "OEBPS/nav.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
					+ "<head><title>nav</title></head><body><nav epub:type=\"toc\"><ol>"
					+ "<li><a href=\"a.xhtml\">a</a></li><li><a href=\"b.xhtml\">b</a></li><li><a href=\"c.xhtml\">c</a></li>"
					+ "</ol></nav></body></html>");
			entry(zip, "OEBPS/style.css", css);
			entry(zip, "OEBPS/a.xhtml", xhtml(a));
			entry(zip, "OEBPS/b.xhtml", xhtml(b));
			entry(zip, "OEBPS/c.xhtml", xhtml(c));
		}
		return bytes.toByteArray();
	}

	/** An item; a leading {@code <base/>} in {@code body} goes into the head. */
	private static String xhtml(final String body) {
		final int base = body.startsWith("<base ") ? body.indexOf("/>") + 2 : 0;
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>" + body.substring(0, base)
				+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body>" + body.substring(base)
				+ "</body></html>";
	}

	private static void entry(final ZipOutputStream zip, final String name, final String text) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(text.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static Run run(final byte[] book, final Map<String, String> props) throws Exception {
		final Run results = new Run();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setMessageHandler((code, args, message) -> {
				synchronized (results.codes) {
					results.codes.add(code);
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			session.property("processing.page-references", "true");
			for (final Map.Entry<String, String> e : props.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(book),
					URI.create("file:///cross-item.epub"), "application/epub+zip", null);
		} finally {
			session.close();
		}
		return results;
	}

	private static final class Run implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();
		final List<Short> codes = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public synchronized FragmentedOutput nextBuilder(final SourceMetadata metadata) {
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

		/** The text of a page SVG in drawing order, one run per bar, without the leader's dots. */
		String texts(final String uri) {
			final StringBuilder b = new StringBuilder();
			final Matcher m = Pattern.compile("<text\\b([^>]*)>([^<]*)</text>").matcher(this.text(uri));
			while (m.find()) {
				final Matcher a = Pattern.compile("data-copper-text=\"([^\"]*)\"").matcher(m.group(1));
				final String text = a.find() ? a.group(1) : m.group(2);
				if (!text.equals(".")) {
					b.append(text).append('|');
				}
			}
			return b.toString();
		}
	}
}
