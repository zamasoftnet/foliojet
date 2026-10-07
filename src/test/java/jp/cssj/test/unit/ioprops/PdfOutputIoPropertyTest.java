package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Tests for PDF-output I/O properties (introduced on 2026-08-02,
 * the fourth batch of comprehensive I/O property coverage). Check watermark, link, font policy,
 * image compression, and document-information interpretation in the output PDF contents.
 */
public class PdfOutputIoPropertyTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** A document with links, images, and document information (title/meta). */
	private static final File DOCUMENT = new File("files/unittest/ioprops/link-and-image.html");

	/** A document containing Japanese text (for checking font embedding). */
	private static final File JAPANESE = new File("files/unittest/ioprops/japanese.html");

	/** A barcode whose human-readable digits are drawn through the Java2D bridge. */
	private static final File BARCODE = new File("files/unittest/ioprops/barcode.html");

	/** A document with two book JAN rows absolutely positioned at the standard locations. */
	private static final File ABSOLUTE_BOOK_JAN = new File("files/unittest/ioprops/book-jan-absolute.html");

	/** {@code output.pdf.hyperlinks}: link annotations appear. */
	public void testHyperlinks() throws Exception {
		final String pdf = this.convert(props("output.pdf.hyperlinks", "true"));
		assertTrue("リンク注釈が出ること", pdf.contains("/Annots") && pdf.contains("/Link"));
	}

	/** {@code output.pdf.hyperlinks.base}: the base for relative links takes effect. */
	public void testHyperlinkBase() throws Exception {
		// The default output.pdf.hyperlinks.href is relative, so specify
		// absolute to make the base take effect.
		final String pdf = this.convert(props("output.pdf.hyperlinks", "true",
				"output.pdf.hyperlinks.href", "absolute",
				"output.pdf.hyperlinks.base", "https://probe.example/base/"));
		assertTrue("基点からのURIになること", pdf.contains("probe.example"));
	}

	/** {@code output.use-meta-info}: title/meta become document information. */
	public void testUseMetaInfo() throws Exception {
		final String pdf = this.convert(props("output.use-meta-info", "true"));
		assertTrue("titleが文書情報のTitleになること", pdf.contains("PROBE-DOC-TITLE"));
		assertTrue("meta[author]が文書情報のAuthorになること", pdf.contains("PROBE-META-AUTHOR"));
	}

	/** {@code output.use-meta-info=false}: do not interpret them. */
	public void testUseMetaInfoDisabled() throws Exception {
		final String pdf = this.convert(props("output.use-meta-info", "false"));
		assertFalse("文書情報に取り込まれないこと", pdf.contains("PROBE-META-AUTHOR"));
	}

	/** {@code output.pdf.fonts.policy}: embedding produces a font file. */
	public void testFontsPolicyEmbedded() throws Exception {
		// **Core fonts (Times-Roman, etc.) are not embedded by design**, so
		// Latin-only documents produce no FontFile. Use a document containing Japanese text.
		final String pdf = this.convert(JAPANESE, props("output.pdf.fonts.policy", "embedded"));
		assertTrue("フォントが埋め込まれること(FontFile)", pdf.contains("/FontFile"));
	}

	/** {@code output.pdf.fonts.policy=cid-keyed}: do not embed. */
	public void testFontsPolicyCidKeyed() throws Exception {
		final String pdf = this.convert(JAPANESE, props("output.pdf.fonts.policy", "cid-keyed"));
		assertFalse("cid-keyedではフォントを埋め込まない", pdf.contains("/FontFile"));
	}

	/**
	 * Session settings override profile defaults, and {@code outlines} applies
	 * to both body text and crop-mark annotations.
	 */
	public void testFontsPolicyOutlinesOverridesProfileDefault() throws Exception {
		final File baseProfile = new File(System.getProperty("jp.cssj.driver.default"));
		final File profile = new File(baseProfile.getParentFile(),
				this.getClass().getSimpleName() + "-outlines.properties");
		Files.writeString(profile.toPath(), "system.fonts=fonts/fonts.xml\n"
				+ "output.pdf.fonts.policy=embedded cid-keyed\n", StandardCharsets.ISO_8859_1);
		try {
			final File out = this.convertToFile(JAPANESE,
					props("output.pdf.fonts.policy", "outlines", "output.marks", "crop", "output.trims", "10mm"),
					profile);
			try (PDDocument pdf = Loader.loadPDF(out)) {
				assertEquals("本文とトンボ注記が抽出可能なPDFテキストとして残らないこと", "",
						new PDFTextStripper().getText(pdf).trim());
			}
		} finally {
			Files.deleteIfExists(profile.toPath());
		}
	}

	/** Barcode human-readable lines follow the same outlines policy as ordinary text. */
	public void testFontsPolicyOutlinesAppliesToBarcodeText() throws Exception {
		final File out = this.convertToFile(BARCODE, props("output.pdf.fonts.policy", "outlines"), null);
		try (PDDocument pdf = Loader.loadPDF(out)) {
			assertEquals("バーコード数字が抽出可能なPDFテキストとして残らないこと", "",
					new PDFTextStripper().getText(pdf).trim());
			assertFalse("バーコード数字がページフォント資源を残さないこと",
					pdf.getPage(0).getResources().getFontNames().iterator().hasNext());
		}
	}

	/** PDF cm precision must not thicken book JAN's 0.33 mm/module to 0.94 pt. */
	public void testBookJanKeepsExactPhysicalWidth() throws Exception {
		final String pdf = this.convert(BARCODE, props("output.pdf.fonts.policy", "outlines"));
		assertFalse("0.33mm/moduleを0.94ptの拡大行列へ丸めないこと", pdf.contains("0.94 0 0 0.94"));
		final Pattern rect = Pattern.compile("(-?[0-9.]+) (-?[0-9.]+) ([0-9.]+) ([0-9.]+) re");
		final Matcher matcher = rect.matcher(pdf);
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		int bars = 0;
		while (matcher.find()) {
			final double x = Double.parseDouble(matcher.group(1));
			final double width = Double.parseDouble(matcher.group(3));
			final double height = Double.parseDouble(matcher.group(4));
			// Select only thin, tall book JAN bars. Exclude page/background rectangles and text outlines.
			if (height > 10.0 && width > 0 && width < 5.0) {
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x + width);
				++bars;
			}
		}
		assertTrue("書籍JANのバー矩形を検出すること", bars > 20);
		assertEquals("95 modules x 0.33mm = 31.35mm", 31.35 * 72 / 25.4, maxX - minX, 0.011);
	}

	/** The absolutely positioned book JAN replaced box uses the specified left and top positions. */
	public void testBookJanAbsolutePositionUsesSpecifiedTop() throws Exception {
		final File dumpDir = new File("local/unittest/pdf/book-jan-absolute-display-list");
		final File dump = new File(dumpDir, "page-0001.txt");
		Files.deleteIfExists(dump.toPath());
		final File out;
		try (AutoCloseable ignored = DisplayListDumper.scopedDir(dumpDir.getPath())) {
			out = this.convertToFile(ABSOLUTE_BOOK_JAN, props("output.pdf.fonts.policy", "outlines"), null);
		}
		final String displayList = Files.readString(dump.toPath(), StandardCharsets.UTF_8);
		final Pattern box = Pattern.compile("x=([0-9.]+) y=([0-9.]+) AbsoluteRectFrame\\[w=([0-9.]+) h=([0-9.]+)\\]");
		final Matcher matcher = box.matcher(displayList);
		final List<double[]> boxes = new ArrayList<>();
		while (matcher.find()) {
			final double width = Double.parseDouble(matcher.group(3));
			final double height = Double.parseDouble(matcher.group(4));
			if (Math.abs(width - 31.35 * 72 / 25.4) < 0.02 && Math.abs(height - 11 * 72 / 25.4) < 0.02) {
				boxes.add(new double[] { Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)) });
			}
		}
		assertEquals("書籍JAN二段の置換ボックス", 2, boxes.size());
		assertEquals("背から12mm", 12 * 72 / 25.4, boxes.get(0)[0], 0.011);
		assertEquals("上段上端10mm", 10 * 72 / 25.4, boxes.get(0)[1], 0.011);
		assertEquals("下段上端31mm", 31 * 72 / 25.4, boxes.get(1)[1], 0.011);

		// The old Java2D path fed page coordinates back into BarcodeImage, and the subsequent
		// image scale shifted only the contents about 0.87 mm upward even though the replaced box was correct.
		// Combine the actual PDF drawing matrix and white background to verify the finished symbol's top, total height,
		// and inter-row gap in physical dimensions. GC's Y-axis inversion means the background's local y itself
		// is not 0, so do not confuse local coordinates alone with the standard position.
		final String pdf = new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
		final Pattern backgroundPattern = Pattern.compile(
				"q 1 0 0 ([0-9.]+) ([0-9.-]+) ([0-9.-]+) cm 1 g 0 ([0-9.]+) ([0-9.]+) ([0-9.]+) re f");
		final Matcher backgrounds = backgroundPattern.matcher(pdf);
		final List<double[]> symbols = new ArrayList<>();
		final double pageHeight;
		try (PDDocument document = Loader.loadPDF(out)) {
			pageHeight = document.getPage(0).getMediaBox().getHeight();
		}
		while (backgrounds.find()) {
			final double scaleY = Double.parseDouble(backgrounds.group(1));
			final double translateX = Double.parseDouble(backgrounds.group(2));
			final double translateY = Double.parseDouble(backgrounds.group(3));
			final double localY = Double.parseDouble(backgrounds.group(4));
			final double width = Double.parseDouble(backgrounds.group(5));
			final double height = Double.parseDouble(backgrounds.group(6));
			if (Math.abs(width - 31.35 * 72 / 25.4) < 0.02 && height > 20) {
				final double physicalHeight = scaleY * height;
				final double top = pageHeight - (translateY + scaleY * (localY + height));
				symbols.add(new double[] { translateX, top, width, physicalHeight });
			}
		}
		assertEquals("書籍JAN二段の実描画背景", 2, symbols.size());
		assertEquals("上段実描画上端10mm", 10, symbols.get(0)[1] * 25.4 / 72, 0.02);
		assertEquals("下段実描画上端31mm", 31, symbols.get(1)[1] * 25.4 / 72, 0.02);
		assertEquals("上段全高11mm", 11, symbols.get(0)[3] * 25.4 / 72, 0.02);
		assertEquals("下段全高11mm", 11, symbols.get(1)[3] * 25.4 / 72, 0.02);
		final double gap = symbols.get(1)[1] - symbols.get(0)[1] - symbols.get(0)[3];
		assertEquals("上段下端から下段上端まで10mm", 10, gap * 25.4 / 72, 0.02);
	}

	/** {@code output.pdf.image.compression}: the specified compression method is used. */
	public void testImageCompressionJpeg() throws Exception {
		// The value is jpeg (not dct). Images at or below the lossless threshold (default 200 px)
		// are not compressed lossily, so lower the threshold before checking.
		final String pdf = this.convert(props("output.pdf.image.compression", "jpeg",
				"output.pdf.image.compression.lossless", "10"));
		assertTrue("JPEG(DCTDecode)で圧縮されること", pdf.contains("/DCTDecode"));
	}

	/** {@code output.pdf.watermark.uri}: the watermark image is embedded. */
	public void testWatermark() throws Exception {
		final Map<String, String> props = props("output.pdf.watermark.uri",
				new File("files/unittest/red.png").toURI().toString());
		final String pdf = this.convert(props);
		// The watermark is placed with a transparency group (/Group).
		assertTrue("すかしが置かれること", pdf.contains("/Group") || pdf.contains("Watermark"));
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private String convert(final Map<String, String> properties) throws Exception {
		return this.convert(DOCUMENT, properties);
	}

	private String convert(final File document, final Map<String, String> properties) throws Exception {
		final File out = this.convertToFile(document, properties, null);
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}

	private File convertToFile(final File document, final Map<String, String> properties, final File profile)
			throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + '-'
				+ document.getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				if (profile != null) {
					session.setProfileFile(profile);
				}
				session.setMessageHandler((code, args, mes) -> {
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
		return out;
	}
}
