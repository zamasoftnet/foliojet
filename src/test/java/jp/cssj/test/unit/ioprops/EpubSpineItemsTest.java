package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.TranscoderException;
import junit.framework.TestCase;

/**
 * The spine items of an EPUB laid out one after another into one PDF behave as separate documents
 * that share pages (2026-10-08, the EPUB brush-up test: tmp/epub/NOTES.md D-1/2/4/5/6/9/10/12/13/16/20).
 *
 * <ul>
 * <li>Style sheets of an item apply to that item only, in every pass (D-13).</li>
 * <li>Structural selector facts of the two-pass scan (:last-child, ...) do not collide between items (D-20).</li>
 * <li>{@code page-spread-left/right} puts an item on that side, adding a blank page only when needed (D-16).</li>
 * <li>Links to an item or to a fragment in another item are internal links, and target-counter() resolves both
 * (D-1, D-2). Element ids may repeat across items.</li>
 * <li>The document information comes from the package (dc:title, dc:creator, dc:description, dc:language),
 * not from the last item's {@code <title>} (D-4).</li>
 * <li>Nothing is inserted at the start of body ({@code :first-child} holds, D-6).</li>
 * <li>The page number in the slug and n-up sheets continue across items (D-9, D-10).</li>
 * <li>An item that is not XML (an encrypted book) fails with one message naming the item (D-5).</li>
 * <li>An SVG image inside the book can embed data: URIs (D-12).</li>
 * <li>A fixed-layout (pre-paginated) item uses its viewport as the page (D-21).</li>
 * <li>target-counter() finds ids with spaces, which book tools write in indexes.</li>
 * </ul>
 */
public class EpubSpineItemsTest extends TestCase {
	private static final String PAGE = "<style type=\"text/css\">@page{size:200pt 150pt;margin:10pt}"
			+ "body{margin:0;font-size:10pt}p{margin:0}</style>";

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

	// ---- D-13

	private static EpubBooks leakingBook() {
		return new EpubBooks()
				.item("c1", "c1.xhtml", item("<style type=\"text/css\">h1{visibility:hidden}"
						+ "p.a::before{content:\"LEAK-STYLE \"}</style><link rel=\"stylesheet\" href=\"one.css\"/>",
						"<h1>FIRST</h1><p class=\"a\">one</p><p class=\"b\">two</p><p class=\"c\">three</p>"))
				.item("c2", "c2.xhtml", item("<style type=\"text/css\">p.c::before{content:\"LEAK-BACK \"}</style>",
						"<h1>SECOND</h1><p class=\"a\">four</p><p class=\"b\">five</p><p class=\"c\">six</p>"))
				.file("one.css", "p.b::after{content:\" LEAK-LINK\"}");
	}

	private void assertStyleSheetsStayInTheirItem(final String passCount) throws Exception {
		final EpubBooks.Converted r = leakingBook().convert("processing.pass-count", passCount);
		final List<String> pages = pageTexts(r.first());
		assertEquals("頁数 " + pages, 2, pages.size());
		final String first = pages.get(0);
		final String second = pages.get(1);
		assertTrue("項目 1 には自分の規則が効く: " + first,
				first.contains("LEAK-STYLE one") && first.contains("two LEAK-LINK") && !first.contains("FIRST"));
		assertFalse("後ろの項目の規則が項目 1 に効いている(2 パスの持ち越し): " + first, first.contains("LEAK-BACK"));
		assertTrue("項目 2 の見出しが項目 1 の h1{visibility:hidden} で消えた: " + second, second.contains("SECOND"));
		assertFalse("項目 1 の <style> が項目 2 に効いている: " + second, second.contains("LEAK-STYLE"));
		assertFalse("項目 1 の <link> が項目 2 に効いている: " + second, second.contains("LEAK-LINK"));
		assertTrue("項目 2 の自分の規則: " + second, second.contains("LEAK-BACK six"));
	}

	public void testStyleSheetsStayInTheirItemOnePass() throws Exception {
		this.assertStyleSheetsStayInTheirItem("1");
	}

	public void testStyleSheetsStayInTheirItemTwoPass() throws Exception {
		this.assertStyleSheetsStayInTheirItem("2");
	}

	// ---- D-20

	public void testStructuralFactsDoNotCollideBetweenItems() throws Exception {
		final EpubBooks book = new EpubBooks()
				.item("c1", "c1.xhtml", item("<link rel=\"stylesheet\" href=\"s.css\"/>", "<p>a1</p><p>a2</p>"))
				.item("c2", "c2.xhtml", item("<link rel=\"stylesheet\" href=\"s.css\"/>",
						"<p>b1</p><p>b2</p><p>b3</p>"))
				.file("s.css", "p:last-child::after{content:\" LAST\"}");
		final List<String> pages = pageTexts(book.convert("processing.pass-count", "2").first());
		assertEquals(pages.toString(), List.of("a1 a2 LAST", "b1 b2 b3 LAST"), pages);
	}

	// ---- D-16

	private static EpubBooks spreadBook(final String direction, final String... spreads) {
		final EpubBooks book = new EpubBooks().progression(direction);
		for (int i = 0; i < spreads.length; ++i) {
			final int n = i + 1;
			book.item("p" + n, "p" + n + ".xhtml", spreads[i], item("", "<p>ITEM " + n + "</p>"));
		}
		return book;
	}

	private static void assertPages(final List<String> pages, final String... expected) {
		assertEquals("頁の並び " + pages, List.of(expected), pages);
	}

	public void testRightToLeftSpreadsNeedNoBlankPages() throws Exception {
		// Right binding: page 1 is a left page, then (2 right, 3 left), (4 right, 5 left).
		final byte[] pdf = spreadBook("rtl", "rendition:page-spread-center", "page-spread-right",
				"page-spread-left", "page-spread-right", "page-spread-left").convert().first();
		assertPages(pageTexts(pdf), "ITEM 1", "ITEM 2", "ITEM 3", "ITEM 4", "ITEM 5");
	}

	public void testLeftToRightSpreadsNeedNoBlankPages() throws Exception {
		// Left binding: page 1 is a right page, then (2 left, 3 right), (4 left, 5 right).
		final byte[] pdf = spreadBook("ltr", "rendition:page-spread-center", "page-spread-left",
				"page-spread-right", "page-spread-left", "page-spread-right").convert().first();
		assertPages(pageTexts(pdf), "ITEM 1", "ITEM 2", "ITEM 3", "ITEM 4", "ITEM 5");
	}

	public void testSpreadOnTheWrongSideGetsOneBlankPageOfTheSameSize() throws Exception {
		// Right binding without a cover: page 1 is a left page, so an item for the right side starts on page 2.
		final byte[] pdf = spreadBook("rtl", "page-spread-right", "page-spread-left", "page-spread-right",
				"page-spread-left").convert().first();
		assertPages(pageTexts(pdf), "", "ITEM 1", "ITEM 2", "ITEM 3", "ITEM 4");
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDPage blank = doc.getPage(0);
			final PDPage item = doc.getPage(1);
			assertEquals("白頁の幅", item.getMediaBox().getWidth(), blank.getMediaBox().getWidth(), 0.01);
			assertEquals("白頁の高さ", item.getMediaBox().getHeight(), blank.getMediaBox().getHeight(), 0.01);
		}
	}

	public void testPagedSvgItemBundlesHaveNoSpreadBlankPages() throws Exception {
		// Left binding: page 1 is a right page; the old blank-page logic added one before a right-side first item
		final EpubBooks.Converted r = spreadBook("ltr", "page-spread-right", "page-spread-left")
				.convert("output.type", "application/vnd.copper.paged-svg", "output.paged-svg.compression", "none");
		assertTrue(r.order.toString(), r.data.containsKey("items/0001/pages/0001.svg"));
		assertFalse("項目の束の頭に白頁: " + r.order, r.data.containsKey("items/0001/pages/0002.svg"));
	}

	// ---- D-1, D-2

	private static final String REFS = "<style type=\"text/css\">a.n::after{content:\" p\" target-counter(attr(href), page)}"
			+ ".br{page-break-before:always}</style>";

	private static EpubBooks linkedBook() {
		return new EpubBooks()
				.item("toc", "toc.xhtml", item(REFS, "<p id=\"loc\">TOC</p>"
						+ "<p><a class=\"n\" href=\"ch1.xhtml\">A</a></p>"
						+ "<p><a class=\"n\" href=\"ch1.xhtml#s12\">B</a></p>"
						+ "<p><a class=\"n\" href=\"text/ch2.xhtml\">C</a></p>"
						+ "<p><a class=\"n\" href=\"text/ch2.xhtml#s22\">D</a></p>"
						+ "<p><a class=\"n\" href=\"text/ch2.xhtml#dup\">E</a></p>"
						+ "<p><a class=\"n\" href=\"#loc\">F</a></p>"))
				.item("ch1", "ch1.xhtml", item(REFS, "<p id=\"dup\">CH1</p><p class=\"br\">CH1-2</p>"
						+ "<p class=\"br\" id=\"s12\">S12</p>"))
				.item("ch2", "text/ch2.xhtml", item(REFS, "<p>CH2</p><p class=\"br\" id=\"s22\">S22</p>"
						+ "<p id=\"dup\">DUP2</p><p><a class=\"n\" href=\"../ch1.xhtml#s12\">G</a></p>"));
	}

	/** The page numbers (1-based) the link annotations of a page jump to, in annotation order. */
	private static List<Integer> linkTargets(final PDDocument doc, final int pageIndex) throws Exception {
		final List<Integer> targets = new ArrayList<>();
		for (final PDAnnotation annotation : doc.getPage(pageIndex).getAnnotations()) {
			if (!(annotation instanceof PDAnnotationLink link)) {
				continue;
			}
			PDDestination dest = link.getDestination();
			final PDAction action = link.getAction();
			if (action instanceof PDActionURI uri) {
				fail("本の中への外部 URI リンク: " + uri.getURI());
			}
			if (dest == null && action instanceof PDActionGoTo goTo) {
				dest = goTo.getDestination();
			}
			PDPageDestination page = null;
			if (dest instanceof PDNamedDestination named) {
				page = doc.getDocumentCatalog().findNamedDestinationPage(named);
				assertNotNull("名前付き宛先が無い: " + named.getNamedDestination(), page);
			} else if (dest instanceof PDPageDestination direct) {
				page = direct;
			}
			assertNotNull("行き先の無いリンク", page);
			targets.add(doc.getPages().indexOf(page.getPage()) + 1);
		}
		return targets;
	}

	private void assertLinksBetweenItems(final String... properties) throws Exception {
		final byte[] pdf = linkedBook().convert(properties).first();
		final List<String> pages = pageTexts(pdf);
		assertEquals("頁数 " + pages, 6, pages.size());
		final String toc = pages.get(0);
		for (final String expected : new String[] { "A p\\s*2", "B p\\s*4", "C p\\s*5", "D p\\s*6", "E p\\s*6",
				"F p\\s*1" }) {
			assertTrue("目次の頁番号 " + expected + ": " + toc, Pattern.compile(expected + "\\b").matcher(toc).find());
		}
		assertTrue("項目をまたいだ戻り: " + pages.get(5), Pattern.compile("G p\\s*4\\b").matcher(pages.get(5)).find());
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals("目次のリンクの行き先", List.of(2, 4, 5, 6, 6, 1), linkTargets(doc, 0));
			assertEquals("戻りのリンクの行き先", List.of(4), linkTargets(doc, 5));
		}
	}

	public void testReferenceToAnIdWithSpaces() throws Exception {
		// Book tools write index ids with spaces and Japanese (2026-09-02 cti.li handoff, 2026-10-08 af.epub)
		final EpubBooks.Converted r = new EpubBooks()
				.item("ix", "ix.xhtml", item(REFS, "<p><a class=\"n\" href=\"ch1.xhtml#ix_PC 遠隔操作\">PC</a></p>"))
				.item("ch1", "ch1.xhtml", item(REFS, "<p>one</p><p class=\"br\" id=\"ix_PC 遠隔操作\">term</p>"))
				.convert();
		final List<String> pages = pageTexts(r.first());
		assertTrue("空白を含む id の頁番号: " + pages.get(0), Pattern.compile("PC p\\s*3\\b").matcher(pages.get(0)).find());
		for (final String message : r.messages) {
			assertFalse("不正な URI の警告: " + r.messages, message.contains("Invalid link URI"));
		}
		try (PDDocument doc = Loader.loadPDF(r.first())) {
			assertEquals(List.of(3), linkTargets(doc, 0));
		}
	}

	public void testLinksBetweenItemsOnePass() throws Exception {
		this.assertLinksBetweenItems();
	}

	public void testLinksBetweenItemsTwoPass() throws Exception {
		this.assertLinksBetweenItems("processing.pass-count", "2", "processing.page-references", "true");
	}

	// ---- D-21: fixed layout

	public void testFixedLayoutItemUsesItsViewportAsThePage() throws Exception {
		final String fixed = EpubBooks.xhtml("p", "<meta name=\"viewport\" content=\"width=300, height=400\"/>"
				+ "<style type=\"text/css\">body{margin:0}div{width:300px;height:400px;background:#c00}</style>",
				"<div>PAGE</div>");
		final byte[] pdf = new EpubBooks()
				.metadata("<dc:title>t</dc:title><dc:language>en</dc:language>"
						+ "<meta property=\"rendition:layout\">pre-paginated</meta>")
				.item("p1", "p1.xhtml", fixed)
				.item("p2", "p2.xhtml", "rendition:layout-reflowable", item("", "<p>REFLOWED</p>"))
				.convert().first();
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals("固定レイアウトの項目が 1 頁に収まる", 2, doc.getNumberOfPages());
			assertEquals("ビューポートの幅 300px", 225f, doc.getPage(0).getMediaBox().getWidth(), 0.01f);
			assertEquals("ビューポートの高さ 400px", 300f, doc.getPage(0).getMediaBox().getHeight(), 0.01f);
			assertEquals("リフローの項目は自分の @page", 200f, doc.getPage(1).getMediaBox().getWidth(), 0.01f);
		}
	}

	// ---- D-4

	private static EpubBooks describedBook() {
		return new EpubBooks()
				.metadata("<dc:title>Book Title</dc:title><dc:creator>Alice</dc:creator><dc:creator>Bob</dc:creator>"
						+ "<dc:description>About the book</dc:description><dc:language>ja</dc:language>")
				.item("c1", "c1.xhtml", EpubBooks.xhtml("Chapter One",
						PAGE + "<meta name=\"author\" content=\"Item Author\"/>", "<p>one</p>"))
				.item("c2", "c2.xhtml", EpubBooks.xhtml("Colophon", PAGE, "<p>two</p>"));
	}

	public void testDocumentInformationComesFromThePackage() throws Exception {
		try (PDDocument doc = Loader.loadPDF(describedBook().convert().first())) {
			assertEquals("Book Title", doc.getDocumentInformation().getTitle());
			assertEquals("Alice, Bob", doc.getDocumentInformation().getAuthor());
			assertEquals("About the book", doc.getDocumentInformation().getSubject());
		}
	}

	public void testPackageLanguageIsTheTaggedDefault() throws Exception {
		try (PDDocument doc = Loader.loadPDF(describedBook().convert("output.pdf.tagged", "true").first())) {
			assertEquals("ja", doc.getDocumentCatalog().getLanguage());
		}
		try (PDDocument doc = Loader.loadPDF(describedBook()
				.convert("output.pdf.tagged", "true", "output.pdf.tagged.lang", "en").first())) {
			assertEquals("指定が優先", "en", doc.getDocumentCatalog().getLanguage());
		}
	}

	public void testMetaInfoOffIgnoresThePackageToo() throws Exception {
		try (PDDocument doc = Loader.loadPDF(describedBook().convert("output.use-meta-info", "false").first())) {
			assertFalse(String.valueOf(doc.getDocumentInformation().getTitle()),
					"Book Title".equals(doc.getDocumentInformation().getTitle()));
		}
	}

	// ---- D-6

	public void testNothingIsInsertedAtTheStartOfBody() throws Exception {
		final EpubBooks book = new EpubBooks().item("c1", "c1.xhtml", item("<style type=\"text/css\">"
				+ "body > *:first-child::before{content:\"FIRST \"}"
				+ "body[class] p::after{content:\" CLASSED\"}</style>", "<h1>Heading</h1><p>text</p>"));
		final byte[] pdf = book.convert().first();
		assertEquals(List.of("FIRST Heading text"), pageTexts(pdf));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final var names = doc.getDocumentCatalog().getNames();
			if (names != null && names.getDests() != null && names.getDests().getNames() != null) {
				for (final String name : names.getDests().getNames().keySet()) {
					assertFalse("差し込まれた要素の宛先: " + name, name.contains("nombre"));
				}
			}
		}
	}

	// ---- D-9, D-10

	private static EpubBooks threeItems() {
		return new EpubBooks()
				.item("c1", "c1.xhtml", item("", "<p>ONE</p>"))
				.item("c2", "c2.xhtml", item("", "<p>TWO</p><p style=\"page-break-before:always\">THREE</p>"))
				.item("c3", "c3.xhtml", item("", "<p>FOUR</p>"));
	}

	public void testSlugPageNumbersContinueAcrossItems() throws Exception {
		final List<String> pages = pageTexts(threeItems().convert("output.marks", "both").first());
		assertEquals("頁数 " + pages, 4, pages.size());
		for (int i = 0; i < pages.size(); ++i) {
			assertTrue((i + 1) + " 頁目のスラグ: " + pages.get(i),
					Pattern.compile("\\bpage " + (i + 1) + "\\b").matcher(pages.get(i)).find());
		}
	}

	public void testNUpSheetsContinueAcrossItems() throws Exception {
		try (PDDocument doc = Loader.loadPDF(threeItems().convert("output.n-up", "2").first())) {
			assertEquals("4 頁を 2 面付けで 2 枚(項目ごとに新しい用紙にしない)", 2, doc.getNumberOfPages());
		}
	}

	// ---- D-5

	public void testItemThatIsNotXmlFailsWithOneMessage() throws Exception {
		final EpubBooks book = new EpubBooks().item("c1", "c1.xhtml", "IGEF\u0002\u0000\u0000\u0000encrypted body");
		try {
			book.convert();
			fail("暗号化された項目で変換が成功した");
		} catch (final TranscoderException e) {
			assertEquals(e.getMessage(), (short) 0x3815, e.getCode());
			assertTrue("項目の名前を出す: " + e.getMessage(), e.getMessage().contains("OEBPS/c1.xhtml"));
		}
	}

	// ---- D-12

	private static String redPngBase64() throws Exception {
		final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		final java.awt.Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, 8, 8);
		g.dispose();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return Base64.getEncoder().encodeToString(out.toByteArray());
	}

	public void testSvgImageWithDataUriIsLoaded() throws Exception {
		final String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\""
				+ " width=\"100\" height=\"100\" viewBox=\"0 0 100 100\">"
				+ "<image x=\"0\" y=\"0\" width=\"100\" height=\"100\" xlink:href=\"data:image/png;base64,"
				+ redPngBase64() + "\"/></svg>";
		final EpubBooks.Converted r = new EpubBooks()
				.item("c1", "text/c1.xhtml", EpubBooks.xhtml("t", "<style type=\"text/css\">"
						+ "@page{size:100pt 100pt;margin:0}body{margin:0}img{display:block;width:100pt;height:100pt}"
						+ "</style>", "<img src=\"../images/red.svg\" alt=\"\"/>"))
				.file("images/red.svg", svg).convert("output.type", "image/png");
		for (final String message : r.messages) {
			assertFalse("SVG が読めない: " + r.messages, message.startsWith("2811"));
		}
		final BufferedImage page = ImageIO.read(new ByteArrayInputStream(r.first()));
		assertEquals(r.messages.toString(), 0xff0000, page.getRGB(page.getWidth() / 2, page.getHeight() / 2) & 0xffffff);
	}
}
