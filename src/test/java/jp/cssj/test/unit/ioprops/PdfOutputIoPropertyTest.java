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
 * PDF出力まわりの入出力プロパティの検査です(2026-08-02新設、
 * 入出力プロパティ網羅の第4陣)。すかし・リンク・フォント方針・
 * 画像圧縮・文書情報の解釈を、出力PDFの中身で確かめる。
 */
public class PdfOutputIoPropertyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** リンクと画像と文書情報(title/meta)を持つ文書。 */
	private static final File DOCUMENT = new File("files/unittest/ioprops/link-and-image.html");

	/** 和文を含む文書(フォント埋め込みの検査用)。 */
	private static final File JAPANESE = new File("files/unittest/ioprops/japanese.html");

	/** Java2D橋渡しで可読数字を描くバーコード。 */
	private static final File BARCODE = new File("files/unittest/ioprops/barcode.html");

	/** 書籍JAN二段を規格位置へ絶対配置した文書。 */
	private static final File ABSOLUTE_BOOK_JAN = new File("files/unittest/ioprops/book-jan-absolute.html");

	private final List<String> licenseBlocked = new ArrayList<>();

	/** {@code output.pdf.hyperlinks}: リンク注釈が出ること。 */
	public void testHyperlinks() throws Exception {
		final String pdf = this.convert(props("output.pdf.hyperlinks", "true"));
		assertTrue("リンク注釈が出ること", pdf.contains("/Annots") && pdf.contains("/Link"));
	}

	/** {@code output.pdf.hyperlinks.base}: 相対リンクの基点が効くこと。 */
	public void testHyperlinkBase() throws Exception {
		// 既定の output.pdf.hyperlinks.href は relative なので、基点を
		// 効かせるには absolute を指定する
		final String pdf = this.convert(props("output.pdf.hyperlinks", "true",
				"output.pdf.hyperlinks.href", "absolute",
				"output.pdf.hyperlinks.base", "https://probe.example/base/"));
		if (this.skipped()) {
			return;
		}
		assertTrue("基点からのURIになること", pdf.contains("probe.example"));
	}

	/** {@code output.use-meta-info}: title/metaが文書情報になること。 */
	public void testUseMetaInfo() throws Exception {
		final String pdf = this.convert(props("output.use-meta-info", "true"));
		assertTrue("titleが文書情報のTitleになること", pdf.contains("PROBE-DOC-TITLE"));
		assertTrue("meta[author]が文書情報のAuthorになること", pdf.contains("PROBE-META-AUTHOR"));
	}

	/** {@code output.use-meta-info=false}: 解釈しないこと。 */
	public void testUseMetaInfoDisabled() throws Exception {
		final String pdf = this.convert(props("output.use-meta-info", "false"));
		assertFalse("文書情報に取り込まれないこと", pdf.contains("PROBE-META-AUTHOR"));
	}

	/** {@code output.pdf.fonts.policy}: 埋め込みでフォントファイルが出ること。 */
	public void testFontsPolicyEmbedded() throws Exception {
		// **コアフォント(Times-Roman等)は仕様上埋め込まない**ので、
		// 欧文だけの文書では FontFile は出ない。和文を含む文書で見る
		final String pdf = this.convert(JAPANESE, props("output.pdf.fonts.policy", "embedded"));
		if (this.skipped()) {
			return;
		}
		assertTrue("フォントが埋め込まれること(FontFile)", pdf.contains("/FontFile"));
	}

	/** {@code output.pdf.fonts.policy=cid-keyed}: 埋め込まないこと。 */
	public void testFontsPolicyCidKeyed() throws Exception {
		final String pdf = this.convert(JAPANESE, props("output.pdf.fonts.policy", "cid-keyed"));
		if (this.skipped()) {
			return;
		}
		assertFalse("cid-keyedではフォントを埋め込まない", pdf.contains("/FontFile"));
	}

	/**
	 * セッション指定はプロファイル既定値より強く、本文とトンボ注記の
	 * どちらにも {@code outlines} が効くこと。
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
			if (this.skipped()) {
				return;
			}
			try (PDDocument pdf = Loader.loadPDF(out)) {
				assertEquals("本文とトンボ注記が抽出可能なPDFテキストとして残らないこと", "",
						new PDFTextStripper().getText(pdf).trim());
			}
		} finally {
			Files.deleteIfExists(profile.toPath());
		}
	}

	/** バーコードの人間可読行も通常文字と同じoutlines方針に従うこと。 */
	public void testFontsPolicyOutlinesAppliesToBarcodeText() throws Exception {
		final File out = this.convertToFile(BARCODE, props("output.pdf.fonts.policy", "outlines"), null);
		if (this.skipped()) {
			return;
		}
		try (PDDocument pdf = Loader.loadPDF(out)) {
			assertEquals("バーコード数字が抽出可能なPDFテキストとして残らないこと", "",
					new PDFTextStripper().getText(pdf).trim());
			assertFalse("バーコード数字がページフォント資源を残さないこと",
					pdf.getPage(0).getResources().getFontNames().iterator().hasNext());
		}
	}

	/** 書籍JANの0.33mm/moduleをPDFのcm精度で0.94ptへ太らせないこと。 */
	public void testBookJanKeepsExactPhysicalWidth() throws Exception {
		final String pdf = this.convert(BARCODE, props("output.pdf.fonts.policy", "outlines"));
		if (this.skipped()) {
			return;
		}
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
			// 細く高い書籍JANバーだけを拾う。ページ/背景矩形や文字outlineを除く。
			if (height > 10.0 && width > 0 && width < 5.0) {
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x + width);
				++bars;
			}
		}
		assertTrue("書籍JANのバー矩形を検出すること", bars > 20);
		assertEquals("95 modules x 0.33mm = 31.35mm", 31.35 * 72 / 25.4, maxX - minX, 0.011);
	}

	/** 絶対配置した書籍JANの置換ボックスが指定した左・上位置を使うこと。 */
	public void testBookJanAbsolutePositionUsesSpecifiedTop() throws Exception {
		final File dumpDir = new File("local/unittest/pdf/book-jan-absolute-display-list");
		final File dump = new File(dumpDir, "page-0001.txt");
		Files.deleteIfExists(dump.toPath());
		final File out;
		try (AutoCloseable ignored = DisplayListDumper.scopedDir(dumpDir.getPath())) {
			out = this.convertToFile(ABSOLUTE_BOOK_JAN, props("output.pdf.fonts.policy", "outlines"), null);
		}
		if (this.skipped()) {
			return;
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

		// 旧Java2D経路はページ座標をBarcodeImage内へ逆流させ、その後の
		// 画像scaleで置換ボックスが正しくても内容だけ約0.87mm上へずれた。
		// PDFの実描画行列と白背景を合成し、完成シンボルの上端・全高・
		// 段間を物理寸法で検証する。GCのY軸反転により背景のlocal y自体は
		// 0にならないので、local座標だけを規格位置と混同しない。
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

	/** {@code output.pdf.image.compression}: 指定した圧縮方式が使われること。 */
	public void testImageCompressionJpeg() throws Exception {
		// 値は jpeg(dct ではない)。また lossless 閾値(既定200px)以下の
		// 画像は非可逆にしないので、閾値を下げてから見る
		final String pdf = this.convert(props("output.pdf.image.compression", "jpeg",
				"output.pdf.image.compression.lossless", "10"));
		if (this.skipped()) {
			return;
		}
		assertTrue("JPEG(DCTDecode)で圧縮されること", pdf.contains("/DCTDecode"));
	}

	/** {@code output.pdf.watermark.uri}: すかし画像が埋め込まれること。 */
	public void testWatermark() throws Exception {
		final Map<String, String> props = props("output.pdf.watermark.uri",
				new File("files/unittest/red.png").toURI().toString());
		final String pdf = this.convert(props);
		if (this.skipped()) {
			return;
		}
		// すかしは透明グループ(/Group)を持つ形で置かれる
		assertTrue("すかしが置かれること", pdf.contains("/Group") || pdf.contains("Watermark"));
	}

	/** ライセンスで使えないプロパティが混じったか。 */
	private boolean skipped() {
		return !this.licenseBlocked.isEmpty();
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
		this.licenseBlocked.clear();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				if (profile != null) {
					session.setProfileFile(profile);
				}
				session.setMessageHandler((code, args, mes) -> {
					if (code == net.zamasoft.foliojet.message.MessageCodes.WARN_LICENSE_CONSTRAINT_IO) {
						this.licenseBlocked.add(args != null && args.length > 0 ? args[0] : "?");
					}
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
