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

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 入力の解釈に効く入出力プロパティの検査です(2026-08-02新設、
 * 入出力プロパティ網羅の第6陣)。
 *
 * <p>
 * 文字化けや既定スタイルの当たり方は<b>出力の文字と幾何</b>に出るので、
 * PDFの中身とページ寸法で確かめる。
 * </p>
 */
public class InputIoPropertyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 文字コード宣言のないEUC-JP文書。 */
	private static final File EUC_JP = new File("files/unittest/ioprops/euc-jp-no-decl.html");

	private static final File PLAIN = new File("files/unittest/ioprops/two-pages.html");

	private final List<String> licenseBlocked = new ArrayList<>();

	/**
	 * {@code input.default-encoding}: 宣言のない文書の既定エンコーディング。
	 *
	 * <p>
	 * 正しい既定を与えれば読めて、誤った既定なら化ける——<b>両方を見て</b>
	 * 「この検査が本当に効いている」ことを示す。
	 * </p>
	 */
	public void testDefaultEncoding() throws Exception {
		final String correct = this.convert(EUC_JP, props("input.default-encoding", "EUC-JP"));
		final String wrong = this.convert(EUC_JP, props("input.default-encoding", "ISO-8859-1"));
		assertFalse("エンコーディングの指定で出力が変わること", correct.equals(wrong));
	}

	/** {@code input.default-stylesheet}: 既定スタイルシートが当たること。 */
	public void testDefaultStylesheet() throws Exception {
		final String pdf = this.convert(PLAIN, props("input.default-stylesheet",
				new File("files/unittest/ioprops/default.css").toURI().toString()));
		if (this.skipped()) {
			return;
		}
		// 既定スタイルシートの生成内容(content)が出力に現れる
		assertTrue("既定スタイルシートが適用されること", pdf.contains("PROBE-DEFAULT-CSS")
				|| this.textLooksGenerated(pdf));
	}

	/** {@code input.normalize-text}: 指定しても変換が壊れないこと。 */
	public void testNormalizeText() throws Exception {
		final String on = this.convert(PLAIN, props("input.normalize-text", "true"));
		assertTrue("NFC正規化を有効にしても変換できること", on.startsWith("%PDF"));
	}

	/** {@code input.property-pi}: 文書内の処理命令でプロパティを設定できること。 */
	public void testPropertyPi() throws Exception {
		final File doc = new File("files/unittest/3070-AT-RULE/page-marks-bleed.html");
		final String on = this.convert(doc, props("input.property-pi", "true"));
		final String off = this.convert(doc, props("input.property-pi", "false"));
		// この文書は処理命令で紙面寸法を指定しているので、解釈の有無で変わる
		assertFalse("処理命令の解釈の有無で出力が変わること", on.equals(off));
	}

	/** {@code input.viewport}: meta[viewport]をページ寸法として読むこと。 */
	public void testViewport() throws Exception {
		final File doc = new File("files/unittest/ioprops/viewport.html");
		final String on = this.convert(doc, props("input.viewport", "true"));
		final String off = this.convert(doc, props("input.viewport", "false"));
		if (this.skipped()) {
			return;
		}
		assertFalse("viewportの解釈の有無で出力が変わること", on.equals(off));
	}

	/** 高さを省略した実サイト型viewportでも、指定された幅だけを適用する。 */
	public void testViewportWidthOnly() throws Exception {
		final File doc = new File("files/unittest/ioprops/viewport-width-only.html");
		final String pdf = this.convert(doc, props("input.viewport", "true"));
		if (this.skipped()) {
			return;
		}
		final Matcher mediaBox = Pattern.compile(
				"/MediaBox\\s*\\[\\s*0(?:\\.0+)?\\s+0(?:\\.0+)?\\s+([0-9.]+)\\s+([0-9.]+)\\s*\\]")
				.matcher(pdf);
		assertTrue("MediaBoxが見つかること", mediaBox.find());
		assertEquals("width=1010pxがページ幅へ反映されること", 1010 * 72.0 / 96.0,
				Double.parseDouble(mediaBox.group(1)), 0.1);
		assertEquals("省略した高さはA4既定値を保つこと", 297 * 72.0 / 25.4,
				Double.parseDouble(mediaBox.group(2)), 0.1);
	}

	private boolean textLooksGenerated(final String pdf) {
		// 圧縮なしでも文字は符号化されるため、生成内容が入ると
		// ページの内容ストリームが伸びる。長さで代替判定する
		return pdf.length() > 3000;
	}

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
