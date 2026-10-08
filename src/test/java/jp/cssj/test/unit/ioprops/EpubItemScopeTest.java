package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * What belongs to one spine item of an EPUB, and where links and destinations land (2026-10-08, the codex review of the
 * day's EPUB work; each case was reproduced with the 19124 release before the fix).
 *
 * <ul>
 * <li>An image item of a fixed-layout book is a page like the others: it takes a page number, and a link to it lands
 * on it.</li>
 * <li>Links whose href is already resolved (image map areas, {@code output.pdf.hyperlinks.href=absolute}) and links
 * that spell an item with percent-escapes of unreserved characters stay inside the book.</li>
 * <li>A link to an element split across pages lands on its first page (the PDF name tree kept the last), in a book
 * and in a single document.</li>
 * <li>Two books converted into one output ({@code setContinuous}) keep their own destinations.</li>
 * <li>{@code @counter-style} and {@code @font-face} belong to the item that declares them.</li>
 * <li>A {@code <base>} in an item does not hide its elements from {@code target-counter()} in other items.</li>
 * <li>Paged SVG reports a style sheet warning the items share once; a fixed-layout item without a viewport is reported
 * once over two passes.</li>
 * </ul>
 */
public class EpubItemScopeTest extends TestCase {
	private static final String PAGE = "<style type=\"text/css\">@page{size:200pt 150pt;margin:10pt}"
			+ "body{margin:0;font-size:10pt}p{margin:0}.br{page-break-before:always}</style>";

	private static final String FIXED_LAYOUT = "<dc:title>Test Book</dc:title><dc:language>en</dc:language>"
			+ "<meta property=\"rendition:layout\">pre-paginated</meta>";

	private static String item(final String head, final String body) {
		return EpubBooks.xhtml("t", PAGE + head, body);
	}

	private static List<String> pageTexts(final byte[] pdf) throws Exception {
		final List<String> texts = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDFTextStripper stripper = new PDFTextStripper();
			for (int i = 1; i <= doc.getNumberOfPages(); ++i) {
				stripper.setStartPage(i);
				stripper.setEndPage(i);
				texts.add(stripper.getText(doc).replaceAll("\\s+", " ").trim());
			}
		}
		return texts;
	}

	/** The page numbers (1-based) the link annotations of a page jump to, in annotation order; 0 for no page. */
	private static List<Integer> linkTargets(final byte[] pdf, final int pageIndex) throws Exception {
		final List<Integer> targets = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			for (final PDAnnotation annotation : doc.getPage(pageIndex).getAnnotations()) {
				if (!(annotation instanceof PDAnnotationLink link)) {
					continue;
				}
				PDDestination dest = link.getDestination();
				final PDAction action = link.getAction();
				if (dest == null && action instanceof PDActionGoTo goTo) {
					dest = goTo.getDestination();
				}
				PDPageDestination page = null;
				if (dest instanceof PDNamedDestination named) {
					page = doc.getDocumentCatalog().findNamedDestinationPage(named);
				} else if (dest instanceof PDPageDestination direct) {
					page = direct;
				}
				targets.add(page == null ? 0 : doc.getPages().indexOf(page.getPage()) + 1);
			}
		}
		return targets;
	}

	// ---- fixed-layout image items

	private static final byte[] COVER_SVG = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 267 200\">"
			+ "<rect x=\"0\" y=\"0\" width=\"267\" height=\"200\" fill=\"#ff0000\"/></svg>").getBytes(StandardCharsets.UTF_8);

	public void testFixedLayoutImageItemIsAPageOfTheBook() throws Exception {
		final String page = EpubBooks.xhtml("p", "<meta name=\"viewport\" content=\"width=267, height=200\"/>"
				+ "<style type=\"text/css\">body{margin:0}p.n::after{content:\" \" counter(page)}</style>",
				"<p class=\"n\">PAGE</p><p><a href=\"cover.svg\">BACK</a></p>");
		final byte[] pdf = new EpubBooks().metadata(FIXED_LAYOUT).spineFile("cv", "cover.svg", null, COVER_SVG)
				.item("p1", "p1.xhtml", page).convert().first();
		final List<String> pages = pageTexts(pdf);
		assertEquals(pages.toString(), 2, pages.size());
		assertTrue("表紙の後の頁は 2 頁目: " + pages.get(1), pages.get(1).contains("PAGE 2"));
		assertEquals("表紙へのリンク", List.of(1), linkTargets(pdf, 1));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals("SVG の大きさが頁", 200.25f, doc.getPage(0).getMediaBox().getWidth(), 0.5f);
			assertEquals(150f, doc.getPage(0).getMediaBox().getHeight(), 0.5f);
		}
	}

	// ---- links that are already resolved, and percent-escapes

	private static EpubBooks subdirectoryBook() {
		return new EpubBooks()
				.item("a", "text/a.xhtml", item("", "<p><a href=\"b.xhtml#s\">LINK</a></p>"
						+ "<p><img src=\"../img.svg\" usemap=\"#m\" width=\"100\" height=\"50\" alt=\"map\"/></p>"
						+ "<map name=\"m\"><area shape=\"rect\" coords=\"0,0,100,50\" href=\"b.xhtml#s\" alt=\"area\"/></map>"
						+ "<p><a href=\"b%2Exhtml#s\">ESCAPED</a></p>"))
				.item("b", "text/b.xhtml", item("", "<p>b first</p><p class=\"br\" id=\"s\">TARGET</p>"))
				.file("img.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\">"
						+ "<rect width=\"100\" height=\"50\" fill=\"#fc8\"/></svg>");
	}

	public void testLinksStayInsideTheBook() throws Exception {
		final byte[] pdf = subdirectoryBook().convert().first();
		assertEquals("リンク・画像マップ・%エスケープの綴り", List.of(3, 3, 3), linkTargets(pdf, 0));
	}

	public void testAbsoluteHrefLinksStayInsideTheBook() throws Exception {
		final byte[] pdf = subdirectoryBook().convert("output.pdf.hyperlinks.href", "absolute").first();
		assertEquals("リンク・画像マップ・%エスケープの綴り", List.of(3, 3, 3), linkTargets(pdf, 0));
	}

	// ---- an element split across pages

	public void testLinkToAnElementSplitAcrossPagesLandsOnItsStart() throws Exception {
		final byte[] pdf = new EpubBooks()
				.item("a", "a.xhtml", item("", "<p><a href=\"b.xhtml#sec\">LINK</a></p>"))
				.item("b", "b.xhtml", item("", "<p>intro</p><div id=\"sec\" class=\"br\"><p>FIRST</p>"
						+ "<p class=\"br\">SECOND</p><p class=\"br\">THIRD</p></div>"))
				.convert().first();
		assertEquals(pageTexts(pdf).toString(), "FIRST", pageTexts(pdf).get(2));
		assertEquals("分割された要素の最初の頁", List.of(3), linkTargets(pdf, 0));
	}

	public void testLinkToAnElementSplitAcrossPagesInOneDocument() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>" + PAGE + "</head><body>"
				+ "<p><a href=\"#sec\">LINK</a></p><div id=\"sec\" class=\"br\"><p>FIRST</p><p class=\"br\">SECOND</p>"
				+ "<p class=\"br\">THIRD</p></div></body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///split.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		assertEquals("分割された要素の最初の頁", List.of(2), linkTargets(out.toByteArray(), 0));
	}

	// ---- two books in one output

	private static byte[] continuousBooks(final EpubBooks... books) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<File> files = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.setContinuous(true);
			for (final EpubBooks book : books) {
				final File file = File.createTempFile("epub-continuous", ".epub");
				files.add(file);
				Files.write(file.toPath(), book.build());
				session.transcode(file.toURI());
			}
			session.join();
		} finally {
			session.close();
			for (final File file : files) {
				file.delete();
			}
		}
		return out.toByteArray();
	}

	private static EpubBooks book(final String name) {
		return new EpubBooks()
				.item("toc", "toc.xhtml", item("", "<p>" + name + "</p><p><a href=\"chapter.xhtml\">START</a></p>"
						+ "<p><a href=\"chapter.xhtml#s\">PART</a></p>"))
				.item("ch", "chapter.xhtml", item("", "<p>" + name + "-START</p><p class=\"br\" id=\"s\">" + name
						+ "-PART</p>"));
	}

	public void testContinuousBooksKeepTheirDestinations() throws Exception {
		final byte[] pdf = continuousBooks(book("ONE"), book("TWO"));
		final List<String> pages = pageTexts(pdf);
		assertEquals(pages.toString(), 6, pages.size());
		assertEquals("1 冊目のリンク", List.of(2, 3), linkTargets(pdf, 0));
		assertEquals("2 冊目のリンク", List.of(5, 6), linkTargets(pdf, 3));
	}

	// ---- named definitions

	private void assertCounterStylesBelongToTheirItem(final String passCount) throws Exception {
		final byte[] pdf = new EpubBooks()
				.item("a", "a.xhtml", item("<style type=\"text/css\">@counter-style special{system:cyclic;"
						+ "symbols:\"X\";suffix:\" \"}ol{list-style-type:special}</style>", "<ol><li>A1</li></ol>"))
				.item("b", "b.xhtml", item("<style type=\"text/css\">ol{list-style-type:special}</style>",
						"<ol><li>B1</li></ol>"))
				.convert("processing.pass-count", passCount).first();
		final List<String> pages = pageTexts(pdf);
		assertTrue("項目 1 の定義: " + pages, pages.get(0).contains("X A1"));
		assertFalse("項目 1 の定義が項目 2 に効いた: " + pages, pages.get(1).contains("X"));
		assertTrue("定義の無い名前は decimal: " + pages, pages.get(1).contains("1. B1"));
	}

	public void testCounterStylesBelongToTheirItemOnePass() throws Exception {
		this.assertCounterStylesBelongToTheirItem("1");
	}

	public void testCounterStylesBelongToTheirItemTwoPass() throws Exception {
		this.assertCounterStylesBelongToTheirItem("2");
	}

	private static List<String> pageFonts(final byte[] pdf, final int pageIndex) throws Exception {
		final List<String> names = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDResources resources = doc.getPage(pageIndex).getResources();
			for (final COSName name : resources.getFontNames()) {
				names.add(resources.getFont(name).getName());
			}
		}
		return names;
	}

	public void testFontFacesBelongToTheirItem() throws Exception {
		final byte[] minion = Files.readAllBytes(new File("files/unittest/1080-FONT/MinionPro-Regular.otf").toPath());
		final byte[] ipam = Files.readAllBytes(new File("files/unittest/1080-FONT/ipam.otf").toPath());
		for (final String passCount : new String[] { "1", "2" }) {
			final byte[] pdf = new EpubBooks()
					.item("a", "a.xhtml", item("<style type=\"text/css\">@font-face{font-family:own;src:url(minion.otf)}"
							+ "p{font-family:own}</style>", "<p>ITEM A</p>"))
					.item("b", "b.xhtml", item("<style type=\"text/css\">@font-face{font-family:own;src:url(ipam.otf)}"
							+ "p{font-family:own}</style>", "<p>ITEM B</p>"))
					.file("minion.otf", minion).file("ipam.otf", ipam)
					.convert("processing.pass-count", passCount).first();
			assertTrue(passCount + " 項目 1 の書体: " + pageFonts(pdf, 0), pageFonts(pdf, 0).toString().contains("Minion"));
			assertTrue(passCount + " 項目 2 は自分の @font-face の書体: " + pageFonts(pdf, 1),
					pageFonts(pdf, 1).toString().contains("IPA"));
		}
	}

	// ---- <base>

	public void testTargetCounterFindsAnElementOfAnItemWithBase() throws Exception {
		final byte[] pdf = new EpubBooks()
				.item("toc", "toc.xhtml", item("<style type=\"text/css\">a.r::after{content:\" p\" "
						+ "target-counter(attr(href), page)}</style>", "<p><a class=\"r\" href=\"chapter.xhtml#s\">REF</a></p>"))
				.item("ch", "chapter.xhtml", item("<base href=\"assets/\"/><style type=\"text/css\">a.r::after{"
						+ "content:\" p\" target-counter(attr(href), page)}</style>",
						"<p>first</p><p class=\"br\" id=\"s\">TARGET</p><p><a class=\"r\" href=\"#s\">SAME</a></p>"))
				.convert("processing.pass-count", "2").first();
		final List<String> pages = pageTexts(pdf);
		assertTrue("<base> のある項目の要素の頁番号: " + pages, Pattern.compile("REF p\\s*3\\b").matcher(pages.get(0)).find());
		assertTrue("同じ項目の中の参照は <base> で解決したまま: " + pages,
				Pattern.compile("SAME p\\s*3\\b").matcher(pages.get(2)).find());
	}

	// ---- warnings

	public void testPagedSvgStyleWarningComesOnce() throws Exception {
		final String head = "<link rel=\"stylesheet\" href=\"s.css\"/>";
		final EpubBooks.Converted r = new EpubBooks().item("c1", "c1.xhtml", item(head, "<p>one</p>"))
				.item("c2", "c2.xhtml", item(head, "<p>two</p>")).item("c3", "c3.xhtml", item(head, "<p>three</p>"))
				.file("s.css", "p { foo-unsupported-20261008: 1 }")
				.convert("output.type", "application/vnd.copper.paged-svg");
		assertEquals("未対応の警告は 1 回: " + r.messages, 1,
				r.messages.stream().filter(m -> m.startsWith("2802 ")).count());
	}

	public void testFixedLayoutItemWithoutViewportIsReportedOnceInTwoPasses() throws Exception {
		final String noViewport = EpubBooks.xhtml("p", "<style type=\"text/css\">body{margin:0}</style>", "<p>PAGE</p>");
		final EpubBooks.Converted r = new EpubBooks().metadata(FIXED_LAYOUT).item("p1", "p1.xhtml", noViewport)
				.convert("processing.pass-count", "2");
		assertEquals("2 パスでも 1 回: " + r.messages, 1, r.messages.stream().filter(m -> m.startsWith("2826 ")).count());
	}
}
