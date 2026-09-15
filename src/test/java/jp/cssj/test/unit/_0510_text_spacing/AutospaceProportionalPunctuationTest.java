package jp.cssj.test.unit._0510_text_spacing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.TextAutospaceValue;
import net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker;
import net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * <b>比例幅の句読点の後ろの欧文・数字に和欧間アキが入る</b>ことを固定します(2026-09-14、
 * 利用者報告「palt 指定時、約物と欧文の間に和欧間アキが入らない」)。
 *
 * <p>
 * JLREQ 3.2.8 の和欧間アキは漢字等・仮名と欧文用文字の間のもので句読点は対象外だが、
 * それは全角の句読点が自身の後ろに二分の空きを持つ前提。IPA P 系や {@code palt} で
 * 句読点が比例幅(送り 0.75em 以下)になるとその空きが無いので、和字と同じ扱いにする。
 * 全角の句読点は従来どおり入れない。
 * </p>
 */
public class AutospaceProportionalPunctuationTest extends TestCase {
	private static final byte FLAGS = (byte) (TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC);

	/** IPA P ゴシックの「、」(0.5em)の後ろの数字・欧文: 0.25em。仮名の前は入れない。 */
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

	/** 全角(1em)の句読点は自身の空きがあるので従来どおり入れない。 */
	public void testWideCommaBeforeLatinGetsNothing() throws Exception {
		final FontStyle style = InkGapTestSupport.style(12, FontStyle.Direction.LTR);
		final FontMetricsImpl metrics = InkGapTestSupport.metrics(style, FontStyle.Direction.LTR, gid -> null, 880,
				0, 0);
		assertEquals(0.0, gap(style, metrics, '、', '6'), 1e-9);
		assertEquals("和字→数字は従来どおり", 3.0, gap(style, metrics, '約', '6'), 1e-9);
	}

	/** 純関数側: 句読点は PUNCTUATION に分類され、比例幅の印があるときだけ和字扱い。 */
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
