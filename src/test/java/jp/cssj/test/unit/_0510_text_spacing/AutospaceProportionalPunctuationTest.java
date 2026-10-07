package jp.cssj.test.unit._0510_text_spacing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.TextAutospaceValue;
import net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker;
import net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * Verifies that <b>spacing between Japanese and Latin text is added before Latin letters/digits
 * following proportional-width punctuation</b> (2026-09-14, user report:
 * "With palt, no spacing is added between punctuation and Latin text").
 *
 * <p>
 * JLREQ 3.2.8 specifies spacing between ideographs/kana and Latin characters, excluding punctuation,
 * but assumes fullwidth punctuation has its own trailing half-em space. When IPA P fonts or
 * {@code palt} make punctuation proportional-width (advance ≤0.75em), that space is absent,
 * so treat it like a Japanese character. Fullwidth punctuation still receives no added spacing.
 * </p>
 */
public class AutospaceProportionalPunctuationTest extends TestCase {
	private static final byte FLAGS = (byte) (TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC);

	/** After IPA P Gothic's 、 (0.5em), digits/Latin letters get 0.25em. No spacing is added before kana. */
	public void testProportionalCommaBeforeLatinGetsQuarterEm() throws Exception {
		final FontStyle style = InkGapTestSupport.style(12, FontStyle.Direction.LTR);
		final FontMetricsImpl metrics = InkGapTestSupport.realMetrics("ipagp.otf", style);
		assertEquals("IPAPGothic の「、」は比例幅", 6.0, metrics.getAdvance(metrics.getFont().toGID('、')), 0.01);
		assertEquals(3.0, gap(style, metrics, '、', '6'), 1e-9);
		assertEquals(3.0, gap(style, metrics, '。', 'A'), 1e-9);
		assertEquals(3.0, gap(style, metrics, '，', '6'), 1e-9);
		assertEquals("句読点と仮名の間には入れない", 0.0, gap(style, metrics, '、', 'あ'), 1e-9);
		assertEquals("欧文→句読点には入れない", 0.0, gap(style, metrics, '6', '、'), 1e-9);
		assertEquals("フラグ無しでは入れない", 0.0, gap(style, metrics, '、', '6', (byte) 0), 1e-9);
	}

	/** Fullwidth (1em) punctuation has its own space, so no spacing is added, as before. */
	public void testWideCommaBeforeLatinGetsNothing() throws Exception {
		final FontStyle style = InkGapTestSupport.style(12, FontStyle.Direction.LTR);
		final FontMetricsImpl metrics = InkGapTestSupport.metrics(style, FontStyle.Direction.LTR, gid -> null, 880,
				0, 0);
		assertEquals(0.0, gap(style, metrics, '、', '6'), 1e-9);
		assertEquals("和字→数字は従来どおり", 3.0, gap(style, metrics, '約', '6'), 1e-9);
	}

	/** Pure function: punctuation is classified as PUNCTUATION and treated as Japanese only when flagged proportional. */
	public void testClasses() {
		assertEquals(TextAutospaceClasses.Kind.PUNCTUATION, TextAutospaceClasses.of('、'));
		assertEquals(TextAutospaceClasses.Kind.PUNCTUATION, TextAutospaceClasses.of('。'));
		assertEquals("括弧は対象外", TextAutospaceClasses.Kind.OTHER, TextAutospaceClasses.of('（'));
		assertEquals(0.0, TextAutospaceClasses.gapEm('、', '6', FLAGS), 1e-9);
		assertEquals(0.0, TextAutospaceClasses.gapEm('、', '6', FLAGS, false), 1e-9);
		assertEquals(0.25, TextAutospaceClasses.gapEm('、', '6', FLAGS, true), 1e-9);
		assertEquals("句読点同士は 0", 0.0, TextAutospaceClasses.gapEm('、', '。', FLAGS, true), 1e-9);
		assertTrue(TextAutospaceClasses.ideographFirst('、'));
	}

	private static double gap(final FontStyle style, final FontMetricsImpl metrics, final char prev,
			final char next) {
		return gap(style, metrics, prev, next, FLAGS);
	}

	private static double gap(final FontStyle style, final FontMetricsImpl metrics, final char prev,
			final char next, final byte flags) {
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.setFlags(flags);
		final TextImpl text = InkGapTestSupport.text(style, metrics, String.valueOf(prev));
		final char[] pc = { prev };
		tracker.glyphAdded(text, style.getSize(), pc, 0, (byte) 1, metrics.getFont().toGID(prev));
		final char[] nc = { next };
		return tracker.gapBefore(nc, 0, style.getSize());
	}
}
