package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Test that <b>{@code text-shadow} does not contaminate extracted text</b>
 * (introduced on 2026-08-30).
 *
 * <p>
 * Drawing shadows as glyphs makes them extractable as PDF body text. A real document showed
 * alternating duplicate characters such as "減減税税と と…" with vertical writing and emphasis marks.
 * Emphasis marks split drawing into one-character units, each emitting shadow then body.
 * <b>Shadow duplication itself occurs in both vertical and horizontal writing, even without emphasis
 * marks</b>: twice for a sharp shadow, and 13 times for blur approximated by 12 overpainting steps.
 *
 * <p>
 * The fix draws shadows as <b>outline paths</b>. With no glyph information, they do not appear in
 * extraction and become {@code /Artifact} in tagged PDF. Outlines are available only with policies
 * that provide font glyph data ({@code embedded}, {@code cid-identity}, etc.).
 * <b>Externally referenced {@code cid-keyed} fonts have no local glyph data</b>, so shadows must remain
 * text; fix that distinction here too.
 */
public class TextShadowExtractionTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File DOCUMENT = new File("files/unittest/ioprops/text-shadow-vertical.html");

	/** Control document with only the shadows removed from the above. */
	private static final File CONTROL = new File("files/unittest/ioprops/text-shadow-vertical-none.html");

	/**
	 * <b>Adding shadows does not add any PDF text-showing operators</b>.
	 *
	 * <p>
	 * Counting extracted text strings gives inconsistent results: some extractors (PDFBox) collapse
	 * overlapping identical characters into one, while others (pdfium) return both.
	 * Compare the unambiguous <b>count of {@code Tj}/{@code TJ} in content streams</b> between
	 * two documents differing only in the presence of shadows.
	 */
	public void testShadowAddsNoTextOperators() throws Exception {
		final Map<String, String> embedded = props("output.pdf.fonts.policy", "embedded cid-keyed");
		final int withShadow = textOperators(this.convert(DOCUMENT, embedded, "with"));
		final int without = textOperators(this.convert(CONTROL, embedded, "without"));
		assertTrue("対照文書がテキストを描いていない(検査が空虚)", without > 0);
		assertEquals("影がテキスト表示演算子を増やしている", without, withShadow);
	}

	/** Extracted text also contains no shadow duplicates. */
	public void testShadowDoesNotDuplicateExtractedText() throws Exception {
		final String text = this.extract(props("output.pdf.fonts.policy", "embedded cid-keyed"));
		assertEquals("鮮明な影の文字が二重にならないこと", 1, count(text, "減税"));
		assertEquals("ぼかし付きの影が13重にならないこと", 1, count(text, "社会"));
		assertEquals("縦組み+圏点で1文字ずつ交互にならないこと", 1, count(text, "保"));
		assertEquals("同上", 1, count(text, "障"));
	}

	/**
	 * <b>Fonts without glyph data cannot produce outlines</b>, so their shadows remain text
	 * (though they become {@code /Artifact} in tagged PDF).
	 *
	 * <p>
	 * Externally referenced {@code cid-keyed} fonts have no font files, and Core-14 Type1 fonts
	 * (Times/Helvetica/Courier) also lack glyph outlines. This is a fundamental limitation, not an
	 * approximation. Keep this test so that <b>if it becomes fixable, a failure alerts us</b>.
	 * For clean extracted text in production, put {@code embedded} first in {@code output.pdf.fonts.policy}.
	 */
	public void testFontsWithoutGlyphDataStillDuplicateShadowText() throws Exception {
		final Map<String, String> cidKeyed = props("output.pdf.fonts.policy", "cid-keyed");
		final int withShadow = textOperators(this.convert(DOCUMENT, cidKeyed, "cid-with"));
		final int without = textOperators(this.convert(CONTROL, cidKeyed, "cid-without"));
		assertTrue("字形データが無いと影はテキストのまま増える", withShadow > without);
	}

	/** Count {@code Tj}/{@code TJ} operators in content streams. */
	private static int textOperators(final String pdf) {
		final java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("<[0-9A-Fa-f]+>\\s*Tj|\\]\\s*TJ").matcher(pdf);
		int n = 0;
		while (matcher.find()) {
			++n;
		}
		return n;
	}

	private String convert(final File document, final Map<String, String> properties, final String label)
			throws Exception {
		final File out = this.write(document, properties, label);
		return new String(java.nio.file.Files.readAllBytes(out.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
	}

	private static int count(final String text, final String needle) {
		int n = 0;
		for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
			++n;
		}
		return n;
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private String extract(final Map<String, String> properties) throws Exception {
		final File out = this.write(DOCUMENT, properties, "extract");
		try (PDDocument pdf = Loader.loadPDF(out)) {
			return new PDFTextStripper().getText(pdf);
		}
	}

	private File write(final File document, final Map<String, String> properties, final String label)
			throws Exception {
		final File out = new File(
				"local/unittest/pdf/" + this.getClass().getName() + '-' + label + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
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
