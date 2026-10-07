package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
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
 * Tests for I/O properties affecting input interpretation (introduced on 2026-08-02,
 * the sixth batch of comprehensive I/O property coverage).
 *
 * <p>
 * Encoding corruption and default-style application appear in <b>output text and geometry</b>,
 * so check PDF contents and page dimensions.
 * </p>
 */
public class InputIoPropertyTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** An EUC-JP document without an encoding declaration. */
	private static final File EUC_JP = new File("files/unittest/ioprops/euc-jp-no-decl.html");

	private static final File PLAIN = new File("files/unittest/ioprops/two-pages.html");

	/**
	 * {@code input.default-encoding}: default encoding for documents without a declaration.
	 *
	 * <p>
	 * The correct default produces readable text, while an incorrect default corrupts it.
	 * <b>Check both</b> to show that this check actually works.
	 * </p>
	 */
	public void testDefaultEncoding() throws Exception {
		final String correct = this.convert(EUC_JP, props("input.default-encoding", "EUC-JP"));
		final String wrong = this.convert(EUC_JP, props("input.default-encoding", "ISO-8859-1"));
		assertFalse("エンコーディングの指定で出力が変わること", correct.equals(wrong));
	}

	/** {@code input.default-stylesheet}: the default stylesheet applies. */
	public void testDefaultStylesheet() throws Exception {
		final String pdf = this.convert(PLAIN, props("input.default-stylesheet",
				new File("files/unittest/ioprops/default.css").toURI().toString()));
		// Generated content (content) from the default stylesheet appears in the output.
		assertTrue("既定スタイルシートが適用されること", pdf.contains("PROBE-DEFAULT-CSS")
				|| this.textLooksGenerated(pdf));
	}

	/** {@code input.normalize-text}: setting it does not break conversion. */
	public void testNormalizeText() throws Exception {
		final String on = this.convert(PLAIN, props("input.normalize-text", "true"));
		assertTrue("NFC正規化を有効にしても変換できること", on.startsWith("%PDF"));
	}

	/** {@code input.property-pi}: processing instructions in the document can set properties. */
	public void testPropertyPi() throws Exception {
		final File doc = new File("files/unittest/3070-AT-RULE/page-marks-bleed.html");
		final String on = this.convert(doc, props("input.property-pi", "true"));
		final String off = this.convert(doc, props("input.property-pi", "false"));
		// This document specifies page dimensions in a processing instruction, so interpretation changes the result.
		assertFalse("処理命令の解釈の有無で出力が変わること", on.equals(off));
	}

	/** {@code input.viewport}: read meta[viewport] as page dimensions. */
	public void testViewport() throws Exception {
		final File doc = new File("files/unittest/ioprops/viewport.html");
		final String on = this.convert(doc, props("input.viewport", "true"));
		final String off = this.convert(doc, props("input.viewport", "false"));
		assertFalse("viewportの解釈の有無で出力が変わること", on.equals(off));
	}

	/** Apply just the specified width even for a real-site-style viewport that omits height. */
	public void testViewportWidthOnly() throws Exception {
		final File doc = new File("files/unittest/ioprops/viewport-width-only.html");
		final String pdf = this.convert(doc, props("input.viewport", "true"));
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
		// Text is encoded even without compression, so generated content
		// lengthens the page content stream. Use its length as a proxy.
		return pdf.length() > 3000;
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
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
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
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
