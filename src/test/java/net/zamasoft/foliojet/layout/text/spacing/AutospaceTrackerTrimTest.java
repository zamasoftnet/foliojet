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
 * Deterministic tests for {@link AutospaceTracker#trimBefore} (Japanese spacing trim T1a/T1b).
 * Stub metrics without real-font dependencies (full-width = width 12, no GPOS) lock down punctuation
 * pair trimming, GPOS precedence, run boundaries, vertical writing, and disabling via space-all.
 * Corpus fonts either have GPOS or half-width punctuation, so whether resolver trim fires in real
 * documents depends on the environment; these tests are the authoritative verification.
 */
public class AutospaceTrackerTrimTest extends TestCase {

	/** A full-width stub (width 12 = font size), without GPOS. */
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

	/** The pair eligible for trimming (」、) trims by 0.5em = 6 pt. With trimOff, it is 0. */
	public void testTrimAndSpaceAll() {
		final WideMetrics metrics = new WideMetrics(0);
		final TextImpl run = text(metrics, FontStyle.Direction.LTR, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(6.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12,
				style(FontStyle.Direction.LTR)), 0.001);

		tracker.setTrimOff(true);
		assertEquals(0.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12,
				style(FontStyle.Direction.LTR)), 0.001);
		tracker.setTrimOff(false);
		assertEquals(6.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12,
				style(FontStyle.Direction.LTR)), 0.001);
	}

	/** Skip pairs with nonzero GPOS (font takes precedence, as in the original implementation). */
	public void testGposWins() {
		final WideMetrics gpos = new WideMetrics(3);
		final TextImpl run = text(gpos, FontStyle.Direction.LTR, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(0.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, gpos, 12,
				style(FontStyle.Direction.LTR)), 0.001);
	}

	/** Trim across a zero-width style-run boundary as the same pair (JLREQ E). */
	public void testRunBoundaryIncluded() {
		final WideMetrics metrics = new WideMetrics(0);
		final TextImpl run1 = text(metrics, FontStyle.Direction.LTR, "」");
		final TextImpl run2 = text(metrics, FontStyle.Direction.LTR, "、");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run1, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(6.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run2, metrics, 12,
				style(FontStyle.Direction.LTR)), 0.001);
	}

	/** Vertical writing runs are also classified by Unicode cluster and trimmed. */
	public void testVerticalTrim() {
		final WideMetrics metrics = new WideMetrics(0);
		final TextImpl run = text(metrics, FontStyle.Direction.TB, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(6.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, metrics, 12,
				style(FontStyle.Direction.TB)), 0.001);
	}

	/** TB's wide gate and run postprocessing use vertical advance rather than horizontal width. */
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

	/** Do not trim half-width punctuation (width≤0.75em), to protect proportional punctuation. */
	public void testNarrowExcluded() {
		// The wide check uses getAdvance (including palt GPOS adjustments) (2026-09-14); stub width equals advance.
		final FontMetrics narrow = new WideMetrics(0) {
			private static final long serialVersionUID = 1L;

			@Override
			public double getWidth(final int gid) {
				return 6;
			}

			@Override
			public double getAdvance(final int gid) {
				return 6;
			}
		};
		final TextImpl run = text(narrow, FontStyle.Direction.LTR, "」");
		final AutospaceTracker tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 12, new char[] { '」' }, 0, (byte) 1, 100);
		assertEquals(0.0, tracker.trimBefore(new char[] { '、' }, 0, 101, run, narrow, 12,
				style(FontStyle.Direction.LTR)), 0.001);
	}
}
