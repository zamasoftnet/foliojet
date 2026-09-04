package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * PDF では filter 付き要素(と子孫)だけを 1 枚の画像にし、周囲の本文を文字として保つことの試験。
 * 2026-09-03 に描画単位ごとの画像化から要素ごとへまとめた(filter-element-group-design.md)。
 */
public class PdfFilterRasterTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String FILTERED_TEXT = "FILTERED-ELEMENT-TEXT";
	private static final String OUTSIDE_TEXT = "OUTSIDE-PARAGRAPH-TEXT";
	private static final String PNG =
			"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGO4w8AAAAKYAN3rxP+VAAAAAElFTkSuQmCC";
	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:100mm 80mm;margin:10mm}body{margin:0;font:14pt serif}";

	public void testPdf17RasterizesFilteredElement() throws Exception {
		final Conversion exact = convert(filteredHtml(), "1.7", false);
		assertTrue("PDF 1.7 must report that the filtered element was rasterized",
				exact.hasApproximation("filter", "2822.filter-rasterized"));
		assertFalse("PDF 1.7 must not use the old per-drawable approximation",
				exact.hasApproximation("filter", "2822.per-drawable"));
		try (PDDocument pdf = Loader.loadPDF(exact.pdf)) {
			java.nio.file.Files.write(java.nio.file.Path.of("build/tmp/pdf-filter-raster-17.pdf"), exact.pdf);
			// 要素(背景・文字・画像)がまとめて 1 枚の生成画像(SMask 付き)になり、頁全体ではなく
			// 内容の範囲だけを覆う。頁の資源には未使用の元画像 XObject も載るので SMask 付きだけ数える
			final PDImageXObject generated = singleGeneratedImage(pdf);
			final PDPage first = pdf.getPage(0);
			final double pagePx = first.getMediaBox().getWidth() / 72 * 300;
			assertTrue("the generated image must cover the element, not the page: " + describePageImages(pdf),
					generated.getWidth() < pagePx * 0.9);
			dumpPng(pdf, "pdf-filter-raster-17");
			final String text = new PDFTextStripper().getText(pdf);
			assertTrue("text outside the filter must remain extractable", text.contains(OUTSIDE_TEXT));
			assertFalse("text inside the rasterized element must not remain extractable",
					text.contains(FILTERED_TEXT));
		}

		final Conversion tagged = convert(filteredHtml(), "1.7", true);
		assertTrue("tagged PDF must report the filtered rasterization",
				tagged.hasApproximation("filter", "2822.filter-rasterized"));
		// 要素ごとに 1 枚なので Figure もちょうど 1 つ。要素自身(Div)の構造の下に入る
		final List<PDStructureElement> figures = figures(tagged.pdf);
		assertEquals("the rasterized element must be exactly one Figure", 1, figures.size());
		assertEquals("the Figure must sit under the filtered element's own structure", "Div",
				((PDStructureElement) figures.get(0).getParent()).getStructureType());
	}

	public void testPdfA1UsesPerDrawableApproximation() throws Exception {
		final Conversion pdfa = convert(filteredHtml(), "1.4A-1", false);
		assertTrue("PDF/A-1 must retain the per-drawable approximation message",
				pdfa.hasApproximation("filter", "2822.per-drawable"));
		assertFalse("PDF/A-1 must not report an element rasterization",
				pdfa.hasApproximation("filter", "2822.filter-rasterized"));
		try (PDDocument pdf = Loader.loadPDF(pdfa.pdf)) {
			assertEquals("PDF/A-1 must not create a filtered image with an SMask", 0,
					countPageImages(pdf, true));
		}
	}

	public void testOpacityOnlyStaysVector() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:80mm 60mm;margin:10mm}body{margin:0}.filtered{filter:opacity(.5);font:16pt serif}"
				+ "</style></head><body><div class=\"filtered\">VECTOR-OPACITY</div></body></html>";
		final Conversion vector = convert(html, "1.7", false);
		try (PDDocument pdf = Loader.loadPDF(vector.pdf)) {
			assertEquals("opacity-only filter must remain a vector Form", 0, countPageImages(pdf, false));
		}
		assertFalse("opacity-only filter must not report rasterization",
				vector.hasApproximation("filter", "2822.filter-rasterized"));
	}

	/**
	 * 入れ子の filter: 内側の層の効果は外側の録画に吸収され、頁の生成画像は 1 枚。内側(sepia)の
	 * 領域にも外側(grayscale)が掛かるので画素は r≈g≈b。
	 */
	public void testNestedFiltersCollapseIntoOneImage() throws Exception {
		final String html = HEAD + ".outer{width:60mm;padding:4mm;background:#f36;filter:grayscale(1)}"
				+ ".inner{height:10mm;background:#08f;filter:sepia(1)}"
				+ "</style></head><body><div class=\"outer\"><div class=\"inner\"></div></div></body></html>";
		final Conversion nested = convert(html, "1.7", false);
		assertFalse("nested filters must not fall back to the per-drawable approximation",
				nested.hasApproximation("filter", "2822.per-drawable"));
		try (PDDocument pdf = Loader.loadPDF(nested.pdf)) {
			singleGeneratedImage(pdf);
			// 内側の箱の中央(x=10+4+30mm, y=10+4+5mm)
			final int rgb = render(pdf).getRGB(mm(44), mm(19));
			assertGray("the inner element must be desaturated by the outer grayscale", rgb);
		}
	}

	/** 同じルールを親子両方に当てても、要素ごとに別の層になる(解析値の共有に依存しない)。 */
	public void testSameRuleOnParentAndChild() throws Exception {
		final String html = HEAD + ".f{padding:4mm;filter:grayscale(1)}.p{background:#f36}.c{background:#08f;height:8mm}"
				+ "</style></head><body><div class=\"f p\"><div class=\"f c\"></div></div></body></html>";
		final Conversion same = convert(html, "1.7", false);
		assertFalse(same.hasApproximation("filter", "2822.per-drawable"));
		try (PDDocument pdf = Loader.loadPDF(same.pdf)) {
			singleGeneratedImage(pdf);
			final BufferedImage png = render(pdf);
			assertGray("the parent background must be gray", png.getRGB(mm(12), mm(12)));
			assertGray("the child background must be gray", png.getRGB(mm(30), mm(22)));
		}
	}

	/** filter 付き要素の中のリンクは、層の中でも頁の注釈として残る(PageOutputDrawable)。 */
	public void testLinkInsideFilteredElementKeepsItsAnnotation() throws Exception {
		final String html = HEAD + ".f{filter:grayscale(1)}"
				+ "</style></head><body><div class=\"f\"><a href=\"https://example.com/\">LINK</a></div></body></html>";
		final Conversion linked = convert(html, "1.7", false, "output.pdf.hyperlinks", "true");
		try (PDDocument pdf = Loader.loadPDF(linked.pdf)) {
			assertEquals("the link annotation must survive the filter layer", 1,
					pdf.getPage(0).getAnnotations().size());
			singleGeneratedImage(pdf);
		}
	}

	/** transform の内側で filter を掛け、ぼかしと影の方向が要素座標に従うことの試験。 */
	public void testTransformedElementGetsEffectsInLocalSpace() throws Exception {
		final String scaledHtml = HEAD + ".f{width:20mm;height:10mm;margin:20mm;background:#f36;"
				+ "transform:scale(2);filter:blur(2pt)}"
				+ "</style></head><body><div class=\"f\"></div></body></html>";
		final Conversion scaled = convert(scaledHtml, "1.7", false);
		assertTrue(scaled.hasApproximation("filter", "2822.filter-rasterized"));
		try (PDDocument pdf = Loader.loadPDF(scaled.pdf)) {
			final PDImageXObject generated = singleGeneratedImage(pdf);
			// 局所 300dpi は scale(2) で 8.333px/pt、σ=16.667px、余白は片側51px。
			assertTrue("the local-space image width must be 575±4px: " + generated.getWidth(),
					Math.abs(generated.getWidth() - 575) <= 4);
		}

		final String rotatedHtml = HEAD + "@page{background:#fff}html{background:#fff}"
				+ ".r{width:20mm;height:10mm;margin:20mm;background:#000;transform:rotate(90deg);"
				+ "transform-origin:50% 50%;filter:drop-shadow(6pt 0 0 #000)}"
				+ "</style></head><body><div class=\"r\"></div></body></html>";
		final Conversion rotated = convert(rotatedHtml, "1.7", false);
		java.nio.file.Files.write(java.nio.file.Path.of("build/tmp/pdf-filter-rotated.pdf"), rotated.pdf);
		try (PDDocument pdf = Loader.loadPDF(rotated.pdf)) {
			singleGeneratedImage(pdf);
			dumpPng(pdf, "pdf-filter-rotated");
			final BufferedImage png = render(pdf);
			// 頁余白10mm+要素margin 20mm。20×10mm箱は中心(40,35)mmのまま90度回転し、
			// 回転後の外接箱は left=35, top=25, right=45, bottom=45mm になる。
			final double boxLeft = 10 + 20;
			final double boxTop = 10 + 20;
			final double centerX = boxLeft + 20 / 2.0;
			final double centerY = boxTop + 10 / 2.0;
			final double rotatedRight = centerX + 10 / 2.0;
			final double rotatedBottom = centerY + 20 / 2.0;
			final int below = png.getRGB(mm(centerX), mm(rotatedBottom) + 3);
			final int right = png.getRGB(mm(rotatedRight) + 3, mm(centerY));
			assertTrue("local +x shadow must appear below the rotated box: " + luma(below), luma(below) < 128);
			assertTrue("the shadow must not remain on the page-right side: " + luma(right), luma(right) > 200);
		}
	}

	/** TableBox と外側の配置用ブロックが同じ TableParams を共有しても filter は一度だけ掛ける。 */
	public void testSharedParamsTableFilterAppliesOnce() throws Exception {
		final String html = HEAD + "table{filter:grayscale(1);border-spacing:0}td{width:20mm;height:10mm;background:#f00}"
				+ "</style></head><body><table><tr><td>RED</td></tr></table></body></html>";
		final Conversion table = convert(html, "1.7", false);
		assertEquals("the shared table filter must report one rasterized element", 1,
				table.countApproximation("filter", "2822.filter-rasterized"));
		try (PDDocument pdf = Loader.loadPDF(table.pdf)) {
			singleGeneratedImage(pdf);
			assertGray("the red cell must be gray", render(pdf).getRGB(mm(12), mm(12)));
		}
	}

	/** 変換・clip・透明度・blend・リンクをまたぐ入れ子の filter 統合試験。 */
	public void testNestedTransformedFiltersKeepLinkAndOuterImage() throws Exception {
		final String html = HEAD
				+ ".outer{width:50mm;height:28mm;margin:8mm;background:#f36;clip-path:inset(1mm);"
				+ "transform:rotate(4deg);transform-origin:50% 50%;filter:blur(1pt)}"
				+ ".inner{width:28mm;height:12mm;background:#08f;transform:translate(6mm,4mm) rotate(-12deg);"
				+ "transform-origin:50% 50%;filter:sepia(1);opacity:.5;mix-blend-mode:multiply}"
				+ "a{color:#000}</style></head><body><div class=\"outer\"><div class=\"inner\">"
				+ "<a href=\"https://example.com/\">LINK</a></div></div></body></html>";
		final Conversion integrated = convert(html, "1.7", true, "output.pdf.hyperlinks", "true");
		assertTrue("the outer element must be rasterized",
				integrated.hasApproximation("filter", "2822.filter-rasterized"));
		assertFalse(integrated.hasApproximation("filter", "2822.filter-limit"));
		java.nio.file.Files.write(java.nio.file.Path.of("build/tmp/pdf-filter-nested-transformed.pdf"), integrated.pdf);
		try (PDDocument pdf = Loader.loadPDF(integrated.pdf)) {
			singleGeneratedImage(pdf);
			dumpPng(pdf, "pdf-filter-nested-transformed");
			assertEquals("the transformed link annotation must remain on the page", 1,
					pdf.getPage(0).getAnnotations().size());
		}
		// 層の画像は外側の要素(Div)の内容として構造に入る(構造内容が開いていれば MCID、
		// 無ければ文書直下の Figure)。内側の要素は層に吸収されるので Figure は増えない
		assertTrue("the layer must not add more than one Figure", figures(integrated.pdf).size() <= 1);
	}

	/** 表のセルの filter: 背景は表の枠パスで描かれるが、セルの層(pendingDrawer)へ入る。 */
	public void testTableCellFilterGroupsBackgroundAndContent() throws Exception {
		final String html = HEAD + "td{padding:3mm;background:#f36}td.f{filter:grayscale(1)}"
				+ "</style></head><body><table><tr><td class=\"f\">CELL-TEXT</td><td>PLAIN</td></tr></table>"
				+ "</body></html>";
		final Conversion cell = convert(html, "1.7", false);
		assertTrue(cell.hasApproximation("filter", "2822.filter-rasterized"));
		assertFalse("the cell background must be inside the cell's layer",
				cell.hasApproximation("filter", "2822.per-drawable"));
		try (PDDocument pdf = Loader.loadPDF(cell.pdf)) {
			singleGeneratedImage(pdf);
			final String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("PLAIN"));
			assertFalse(text.contains("CELL-TEXT"));
			assertGray("the cell background must be gray", render(pdf).getRGB(mm(11), mm(11)));
		}
	}

	private static String filteredHtml() {
		return HEAD + ".filtered{width:55mm;height:18mm;padding:2mm;background:#f36;"
				+ "filter:grayscale(1) blur(2pt)}img{width:8mm;height:8mm}p{margin-top:8mm}"
				+ "</style></head><body><div class=\"filtered\">" + FILTERED_TEXT
				+ "<img alt=\"sample\" src=\"" + PNG + "\"></div><p>" + OUTSIDE_TEXT
				+ "</p></body></html>";
	}

	private static Conversion convert(final String html, final String version, final boolean tagged,
			final String... extraProps) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final Messages messages = new Messages();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(messages);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			session.property("output.pdf.version", version);
			session.property("output.pdf.fonts.policy", "embedded");
			if (tagged) {
				session.property("output.pdf.tagged", "true");
				session.property("output.pdf.tagged.lang", "en");
			}
			for (int i = 0; i + 1 < extraProps.length; i += 2) {
				session.property(extraProps[i], extraProps[i + 1]);
			}
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///pdf-filter-raster.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return new Conversion(out.toByteArray(), List.copyOf(messages.messages));
	}

	/** 診断用: 1 頁目の描画(PDFBox、144dpi)を build/tmp に残す。 */
	private static void dumpPng(final PDDocument pdf, final String name) throws IOException {
		final java.io.File dir = new java.io.File("build/tmp");
		dir.mkdirs();
		javax.imageio.ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 144), "png",
				new java.io.File(dir, name + ".png"));
	}

	/** 1 頁目を 72dpi(1px=1pt)で描く。 */
	private static BufferedImage render(final PDDocument pdf) throws IOException {
		return new PDFRenderer(pdf).renderImageWithDPI(0, 72);
	}

	private static int mm(final double mm) {
		return (int) Math.round(mm / 25.4 * 72);
	}

	private static int luma(final int rgb) {
		final int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
		return (299 * r + 587 * g + 114 * b + 500) / 1000;
	}

	private static void assertGray(final String message, final int rgb) {
		final int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
		assertTrue(message + ": " + Integer.toHexString(rgb), Math.abs(r - g) <= 8 && Math.abs(g - b) <= 8);
	}

	/** SMask 付きの生成画像がちょうど 1 つあることを確かめて返す。 */
	private static PDImageXObject singleGeneratedImage(final PDDocument pdf) throws IOException {
		PDImageXObject found = null;
		int count = 0;
		for (final PDPage page : pdf.getPages()) {
			final PDResources resources = page.getResources();
			if (resources == null) {
				continue;
			}
			for (final COSName name : resources.getXObjectNames()) {
				if (resources.getXObject(name) instanceof PDImageXObject image
						&& image.getCOSObject().containsKey(COSName.SMASK)) {
					found = image;
					++count;
				}
			}
		}
		assertEquals("exactly one generated (SMask-backed) image: " + describePageImages(pdf), 1, count);
		return found;
	}

	private static String describePageImages(final PDDocument pdf) throws IOException {
		final StringBuilder sb = new StringBuilder();
		for (final PDPage page : pdf.getPages()) {
			final PDResources resources = page.getResources();
			if (resources == null) {
				continue;
			}
			for (final COSName name : resources.getXObjectNames()) {
				final PDXObject object = resources.getXObject(name);
				if (object instanceof PDImageXObject image) {
					sb.append(name.getName()).append('=').append(image.getWidth()).append('x').append(image.getHeight())
							.append(image.getCOSObject().containsKey(COSName.SMASK) ? "+smask " : " ");
				}
			}
		}
		return sb.toString();
	}

	private static int countPageImages(final PDDocument pdf, final boolean requireSoftMask) throws IOException {
		int count = 0;
		for (final PDPage page : pdf.getPages()) {
			final PDResources resources = page.getResources();
			if (resources == null) {
				continue;
			}
			for (final COSName name : resources.getXObjectNames()) {
				final PDXObject object = resources.getXObject(name);
				if (object instanceof PDImageXObject image
						&& (!requireSoftMask || image.getCOSObject().containsKey(COSName.SMASK))) {
					++count;
				}
			}
		}
		return count;
	}

	private static List<PDStructureElement> figures(final byte[] bytes) throws IOException {
		try (PDDocument pdf = Loader.loadPDF(bytes)) {
			final PDStructureTreeRoot root = pdf.getDocumentCatalog().getStructureTreeRoot();
			assertNotNull("tagged output must contain a structure tree", root);
			final List<PDStructureElement> figures = new ArrayList<>();
			collectFigures(root, figures);
			return figures;
		}
	}

	private static void collectFigures(final PDStructureNode node, final List<PDStructureElement> figures) {
		final List<Object> kids = node.getKids();
		if (kids == null) {
			return;
		}
		for (final Object kid : kids) {
			if (kid instanceof PDStructureElement element) {
				if ("Figure".equals(element.getStructureType())) {
					figures.add(element);
				}
				collectFigures(element, figures);
			}
		}
	}

	private record CapturedMessage(short code, String[] args) {
	}

	private record Conversion(byte[] pdf, List<CapturedMessage> messages) {
		boolean hasApproximation(final String property, final String detailKey) {
			return this.countApproximation(property, detailKey) != 0;
		}

		int countApproximation(final String property, final String detailKey) {
			final String detail = MessageCodeUtils.detail(detailKey);
			int count = 0;
			for (final CapturedMessage message : this.messages) {
				if ((message.code & 0xffff) == MessageCodes.WARN_APPROXIMATED_RENDERING
						&& message.args != null && message.args.length >= 3
						&& property.equals(message.args[0]) && detail.equals(message.args[2])) {
					++count;
				}
			}
			return count;
		}
	}

	private static final class Messages implements MessageHandler {
		final List<CapturedMessage> messages = new ArrayList<>();

		@Override
		public void message(final short code, final String[] args, final String message) {
			this.messages.add(new CapturedMessage(code, args == null ? null : args.clone()));
		}
	}
}
