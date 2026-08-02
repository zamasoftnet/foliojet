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

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
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
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		this.licenseBlocked.clear();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
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
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
