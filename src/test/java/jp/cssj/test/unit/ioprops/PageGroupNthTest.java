package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Page groups and {@code @page :nth()} (CSS GCPM 3 §3.1 and §3.2, 2026-10-28, TECH-20261028-004): the html or body of a
 * document (an EPUB spine item) with a page name makes all its pages a page group, so cti.li's book templates can say
 * "the first page of each chapter" with {@code body { page: chapter }} and {@code @page :nth(1 of chapter)} once
 * {@code :first} is the book's first page only. ph-css could not read {@code :nth()} and dropped the whole sheet.
 */
public class PageGroupNthTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String PAGE = "@page { size: 120pt 90pt; margin: 15pt; @bottom-center { content: \"p\" counter(page); } }"
			+ " body { margin: 0; font-size: 8pt } p { margin: 0; break-after: page } p:last-child { break-after: auto }";

	/** One document: the group of its body, the pages of the output, a named rule, a page's margin. */
	public void testOneDocument() throws Exception {
		final List<String> pages = html(PAGE + " body { page: chapter }"
				+ " @page :nth(1 of chapter) { @bottom-center { content: none } @top-left { content: \"CH1\" } }"
				+ " @page :nth(2n) { @top-right { content: \"EVEN\" } } @page chapter:nth(2) { @top-center { content: \"C2\" } }"
				+ " @page :nth(odd of other) { @top-center { content: \"WRONG\" } }",
				"<p>A1</p><p>A2</p><p>A3</p><p>A4</p>");
		assertEquals(List.of("A1|CH1|", "A2|C2|EVEN|p2|", "A3|p3|", "A4|EVEN|p4|"), pages);
	}

	/** An+B: odd, -n+3 (the first three), 2n + 1 with spaces, a plain B; the margin of the selected pages. */
	public void testAnPlusB() throws Exception {
		final List<String> pages = html(PAGE + " @page :nth(-n+3) { @top-left { content: \"F3\" } }"
				+ " @page :nth(2n + 1) { @top-right { content: \"O\" } } @page :nth(4) { @top-center { content: \"FOUR\" } }",
				"<p>A1</p><p>A2</p><p>A3</p><p>A4</p><p>A5</p>");
		assertEquals(List.of("A1|F3|O|p1|", "A2|F3|p2|", "A3|F3|O|p3|", "A4|FOUR|p4|", "A5|O|p5|"), pages);
	}

	/** :nth() with :first and a compound :nth():nth() (both must match). */
	public void testCompound() throws Exception {
		final List<String> pages = html(PAGE + " @page :nth(n+2):nth(-n+3) { @top-left { content: \"MID\" } }",
				"<p>A1</p><p>A2</p><p>A3</p><p>A4</p>");
		assertEquals(List.of("A1|p1|", "A2|MID|p2|", "A3|MID|p3|", "A4|p4|"), pages);
	}

	/** An argument that is not An+B drops its rule only, with a warning; the sheet and its other rules live. */
	public void testInvalidArgumentDropsItsRule() throws Exception {
		final List<String> pages = html(PAGE + " @page :nth(foo) { @top-left { content: \"BAD\" } }"
				+ " @page :nth(1) { @top-right { content: \"ONE\" } } p { color: red }", "<p>A1</p><p>A2</p>");
		assertEquals(List.of("A1|ONE|p1|", "A2|p2|"), pages);
	}

	/**
	 * What codex found (2026-10-28): numbers past int (an exception stopped the sheet), comments in the prelude, a
	 * keyword as the group's name, an html::before laid out before the body.
	 */
	public void testEdges() throws Exception {
		assertEquals(List.of("A1|ALL|p1|", "A2|ALL|p2|"), html(PAGE + " @page :nth(2147483648) { @top-left { content: \"BIG\" } }"
				+ " @page :nth(n-2147483647) { @top-left { content: \"ALL\" } }", "<p>A1</p><p>A2</p>"));
		assertEquals(List.of("A1|ONE|p1|", "A2|p2|"), html(PAGE + " @page /* :nth( } */ :nth(1) { @top-left { content: \"ONE\" } }"
				+ " p { color: red }", "<p>A1</p><p>A2</p>"));
		assertEquals(List.of("A1|p1|"), html(PAGE + " body { page: initial } @page :nth(1 of initial) { @top-left { content: \"KW\" } }",
				"<p>A1</p>"));
		// The unnamed X page comes first (the change of name breaks the page), and the group starts with the body's
		assertEquals(List.of("X|p1|", "A1|CH1|", "A2|p3|"), html(PAGE + " html::before { content: \"X\"; display: block }"
				+ " body { page: chapter } @page :nth(1 of chapter) { @bottom-center { content: none } @top-left { content: \"CH1\" } }",
				"<p>A1</p><p>A2</p>"));
	}

	/** EPUB to PDF: each item's body starts its own group; :nth(1) is the book's first page only, as :first. */
	public void testEpubPdfGroupPerItem() throws Exception {
		final List<String> pages = epubPdf(chapterBook());
		assertEquals(List.of("A1|BOOK1|", "A2|p2|", "B1|", "B2|p4|", "B3|p5|", "C1|"), pages);
	}

	/** EPUB to Paged SVG in two passes: the same pages, and the numbers of the book. */
	public void testEpubPagedSvgTwoPasses() throws Exception {
		final Map<String, ByteArrayOutputStream> out = epubPagedSvg(chapterBook(), "2");
		assertEquals("A1|BOOK1|", texts(out, "items/0001/pages/0001.svg"));
		assertEquals("A2|p2|", texts(out, "items/0001/pages/0002.svg"));
		assertEquals("B1|", texts(out, "items/0002/pages/0001.svg"));
		assertEquals("B2|p4|", texts(out, "items/0002/pages/0002.svg"));
		assertEquals("C1|", texts(out, "items/0003/pages/0001.svg"));
	}

	private static byte[] chapterBook() throws Exception {
		return epub(PAGE + " body { page: chapter } @page :nth(1 of chapter) { @bottom-center { content: none } }"
				+ " @page :nth(1) { @top-left { content: \"BOOK1\" } }", "<p>A1</p><p>A2</p>",
				"<p>B1</p><p>B2</p><p>B3</p>", "<p>C1</p>");
	}

	private static List<String> html(final String css, final String body) throws Exception {
		final File dir = Files.createTempDirectory("page-group-nth").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + css
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return dumped(dir);
	}

	private static List<String> epubPdf(final byte[] book) throws Exception {
		final File dir = Files.createTempDirectory("page-group-nth-epub").toFile();
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(book), URI.create("file:///book.epub"),
					"application/epub+zip", null);
		}
		return dumped(dir);
	}

	/** The texts of each dumped page in drawing order, one run per bar. */
	private static List<String> dumped(final File dir) throws Exception {
		final List<String> pages = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File f = new File(dir, String.format("page-%04d.txt", i));
			if (!f.exists()) {
				return pages;
			}
			final StringBuilder b = new StringBuilder();
			final Matcher m = Pattern.compile("Text\\[\"([^\"]*)\"").matcher(Files.readString(f.toPath()));
			while (m.find()) {
				b.append(m.group(1)).append('|');
			}
			pages.add(b.toString());
		}
	}

	private static Map<String, ByteArrayOutputStream> epubPagedSvg(final byte[] book, final String passes)
			throws Exception {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new Results() {
				@Override
				public boolean hasNext() {
					return true;
				}

				@Override
				public synchronized FragmentedOutput nextBuilder(final SourceMetadata metadata) {
					final ByteArrayOutputStream out = new ByteArrayOutputStream();
					data.put(metadata.getURI().toString(), out);
					return new StreamFragmentedOutput(out);
				}

				@Override
				public void end() {
					// Do nothing.
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			session.property("processing.pass-count", passes);
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(book), URI.create("file:///book.epub"),
					"application/epub+zip", null);
		} finally {
			session.close();
		}
		return data;
	}

	/** The text of a page SVG in drawing order, one run per bar. */
	private static String texts(final Map<String, ByteArrayOutputStream> data, final String uri) {
		final ByteArrayOutputStream out = data.get(uri);
		assertNotNull(uri + " must be emitted: " + data.keySet(), out);
		final StringBuilder b = new StringBuilder();
		final Matcher m = Pattern.compile("<text\\b([^>]*)>([^<]*)</text>").matcher(out.toString(StandardCharsets.UTF_8));
		while (m.find()) {
			final Matcher a = Pattern.compile("data-copper-text=\"([^\"]*)\"").matcher(m.group(1));
			b.append(a.find() ? a.group(1) : m.group(2)).append('|');
		}
		return b.toString();
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
					+ "<dc:identifier id=\"uid\">urn:uuid:page-groups</dc:identifier>"
					+ "<dc:title>t</dc:title><dc:language>en</dc:language></metadata><manifest>"
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
					+ "<li><a href=\"a.xhtml\">a</a></li></ol></nav></body></html>");
			entry(zip, "OEBPS/style.css", css);
			for (final String[] item : new String[][] { { "a", a }, { "b", b }, { "c", c } }) {
				entry(zip, "OEBPS/" + item[0] + ".xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
						+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>"
						+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body>" + item[1]
						+ "</body></html>");
			}
		}
		return bytes.toByteArray();
	}

	private static void entry(final ZipOutputStream zip, final String name, final String text) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(text.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}
}
