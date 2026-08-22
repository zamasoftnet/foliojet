package net.zamasoft.foliojet.layout.text.spacing;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * {@link AutospaceTracker#trimBefore}(和文詰めT1a/T1b)の決定的テスト
 * です。実フォント依存を避けたstub metrics(全角=幅12、GPOSなし)で、
 * 約物pairの詰め・GPOS優先・run境界・縦書き・space-all無効化を固定する
 * (コーパスのフォントはGPOS持ちまたは半角約物のため、resolver trimの
 * 発火は実文書では環境依存——ここが正本の検証)。
 */
public class AutospaceTrackerTrimTest extends TestCase {

	/** 全角(幅12=フォントサイズと同値)・GPOSなしのstub。 */
	private static class WideMetrics implements FontMetrics {
		private static final long serialVersionUID = 1L;

		private final double kern;

		WideMetrics(final double kern) {
			this.kern = kern;
		}

		@Override
		public double getFontSize() {
			return 12;
		}

		@Override
		public double getXHeight() {
			return 6;
		}

		@Override
		public double getAscent() {
			return 10;
		}

		@Override
		public double getDescent() {
			return 2;
		}

		@Override
		public double getAdvance(final int gid) {
			return 12;
		}

		@Override
		public double getWidth(final int gid) {
			return 12;
		}

		@Override
		public double getSpaceAdvance() {
			return 12;
		}

		@Override
		public double getKerning(final int gid, final int sgid) {
			return this.kern;
		}

		@Override
		public FontSource getFontSource() {
			return null;
		}
	}

	private static FontStyle style(final FontStyle.Direction direction) {
		return new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400, direction,
				FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
	}

	private static TextImpl text(final FontMetrics metrics, final FontStyle.Direction direction, final String s) {
		final TextImpl text = new TextImpl(0, style(direction), metrics);
		for (int i = 0; i < s.length(); ++i) {
			text.appendGlyph(new char[] { s.charAt(i) }, 0, (byte) 1, 100 + i);
		}
		return text;
	}

	/** 詰め対象pair(」、)は0.5em=6pt。trimOffで0。 */
	public void testTrimAndSpaceAll() {
		final WideMetrics metrics = new WideMetrics(0);
		final TextImpl run = text(metrics, FontStyle.Direction.LTR, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(6.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12), 0.001);

		tracker.setTrimOff(true);
		assertEquals(0.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12), 0.001);
		tracker.setTrimOff(false);
		assertEquals(6.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12), 0.001);
	}

	/** GPOS非0のpairはスキップ(font優先——移管元と同じ)。 */
	public void testGposWins() {
		final WideMetrics gpos = new WideMetrics(3);
		final TextImpl run = text(gpos, FontStyle.Direction.LTR, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(0.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, gpos, 12), 0.001);
	}

	/** run境界は対象外(移管元のfont層kernと同範囲)。 */
	public void testRunBoundaryExcluded() {
		final WideMetrics metrics = new WideMetrics(0);
		final TextImpl run1 = text(metrics, FontStyle.Direction.LTR, "」");
		final TextImpl run2 = text(metrics, FontStyle.Direction.LTR, "、");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run1, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(0.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run2, metrics, 12), 0.001);
	}

	/** 縦書きrunもUnicode clusterで分類して詰める。 */
	public void testVerticalTrim() {
		final WideMetrics metrics = new WideMetrics(0);
		final TextImpl run = text(metrics, FontStyle.Direction.TB, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(6.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12), 0.001);
	}

	/** TBのwide gateとrun後処理はhorizontal widthでなくvertical advanceを使う。 */
	public void testVerticalRunTrimUsesInlineAdvance() {
		final FontMetrics vertical = new WideMetrics(0) {
			private static final long serialVersionUID = 1L;

			@Override
			public double getWidth(final int gid) {
				return 6;
			}
		};
		final TextImpl run = text(vertical, FontStyle.Direction.TB, "」「");
		assertEquals(24.0, run.getAdvance(), 0.001);
		JapaneseSpacingResolver.applyRunTrims(run);
		assertEquals(18.0, run.getAdvance(), 0.001);
		assertEquals(-6.0, run.xAdvances().get(1), 0.001);
	}

	/** 半角約物(width≤0.75em)は詰めない(プロポーショナル約物の保護)。 */
	public void testNarrowExcluded() {
		final FontMetrics narrow = new WideMetrics(0) {
			private static final long serialVersionUID = 1L;

			@Override
			public double getWidth(final int gid) {
				return 6;
			}
		};
		final TextImpl run = text(narrow, FontStyle.Direction.LTR, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(0.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, narrow, 12), 0.001);
	}
}
