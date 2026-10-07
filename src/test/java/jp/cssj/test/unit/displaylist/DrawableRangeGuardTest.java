package jp.cssj.test.unit.displaylist;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;

/**
 * Verify that the <b>range guard for coordinates entering the display list</b> actually works
 * (introduced 2026-07-26).
 *
 * <p>
 * This guard <b>never fired</b> during a randomized sweep of 3000 documents.
 * A dormant detector could be protecting us or simply be unreachable dead code;
 * distinguish the two by making <b>activation itself</b> a regression requirement here.
 * </p>
 *
 * <p>
 * <b>What it protects.</b>{@link LayoutUtils#isNone(double)} matches only the sentinel
 * itself, so a sentinel that has undergone arithmetic even once, such as {@code NONE + 10},
 * passes through, as does {@code NaN}. Neither throws; instead,
 * <b>content silently disappears without appearing anywhere on the paper</b>.
 * This is the worst kind of failure for business forms, so reject these values by range.
 * </p>
 *
 * <p>
 * <b>Why add it now.</b>The next feature, CSS Grid, introduces division for {@code fr}
 * units. With zero available space, {@code 0/0} is NaN, so this detector
 * is worth having before implementing Grid.
 * </p>
 */
public class DrawableRangeGuardTest extends TestCase {
	public DrawableRangeGuardTest(String name) {
		super(name);
	}

	/** A dummy that draws nothing. The guard checks only coordinates, so no contents are needed. */
	private static final Drawable NOOP = new Drawable() {
		public void draw(GC gc, double x, double y) {
			// Draw nothing.
		}

		public String describe() {
			return "Noop";
		}
	};

	/** First verify that assertions are enabled; otherwise this test is meaningless. */
	public void testAssertionsAreEnabled() {
		boolean enabled = false;
		assert enabled = true;
		assertTrue("assertが無効。-ea なしではガードの回帰は検証できない", enabled);
	}

	public void testRejectsNaN() {
		assertRejected(Double.NaN, 0);
		assertRejected(0, Double.NaN);
	}

	public void testRejectsInfinity() {
		assertRejected(Double.POSITIVE_INFINITY, 0);
		assertRejected(0, Double.NEGATIVE_INFINITY);
	}

	/** The sentinel itself. The existing {@code isNone} already rejected it. */
	public void testRejectsSentinel() {
		assertRejected(LayoutUtils.NONE, 0);
		assertRejected(0, LayoutUtils.NONE);
	}

	/**
	 * <b>The main case.</b>Multiplying or dividing the sentinel produces values that bypass
	 * {@code isNone}, but they are still garbage coordinates on the order of 10<sup>307</sup>.
	 *
	 * <p>
	 * Also verify that <b>addition cannot escape detection</b>. Near 10<sup>308</sup>,
	 * a double's spacing (ULP) is about 10<sup>292</sup>, so {@code NONE + 10}
	 * <b>does not change even one bit</b> and remains the sentinel. Only operations that
	 * change its scale (scaling, halving, sign reversal) escape: the dangerous paths
	 * are those that apply coordinate transforms to the sentinel.
	 * </p>
	 */
	public void testRejectsSentinelAfterArithmetic() {
		assertTrue("前提が崩れた: この規模のdoubleでは加算は値を変えないはず",
				LayoutUtils.isNone(LayoutUtils.NONE + 10));

		final double halved = LayoutUtils.NONE / 2;
		assertFalse("前提が崩れた: 番兵の折半がまだ番兵と等しい", LayoutUtils.isNone(halved));
		assertRejected(halved, 0);
		assertRejected(0, -LayoutUtils.NONE);
		assertRejected(LayoutUtils.NONE * 0.5, 0);
	}

	/** A constraint meaning "no upper bound" leaks into a position. */
	public void testRejectsMaxValue() {
		assertRejected(Double.MAX_VALUE, 0);
	}

	/** Valid coordinates naturally pass. Negative coordinates are also valid (overflow and bleed). */
	public void testAcceptsRealisticCoordinates() {
		final Drawer drawer = new Drawer(0);
		drawer.visitDrawable(NOOP, 0, 0);
		drawer.visitDrawable(NOOP, 595.276, 841.89);
		drawer.visitDrawable(NOOP, -100, -100);
		// PDF page-size limit (200 inches). This must pass.
		drawer.visitDrawable(NOOP, 14400, 14400);
	}

	/**
	 * <b>Explicitly document a gap this guard cannot cover.</b>{@code NONE - NONE} is 0.
	 * Subtracting sentinels ("undefined position − undefined origin") yields <b>plausible
	 * coordinates</b>, so a range check can never detect it.
	 *
	 * <p>
	 * Detection would require making the sentinel {@code NaN} (NaN propagates, making the
	 * difference NaN too), but {@code NONE} is used in {@code ==} comparisons,
	 * so changing its type requires extensive revisions. Here we only <b>record the gap</b>.
	 * Do not overestimate the guard's coverage.
	 * </p>
	 */
	public void testKnownBlindSpotSentinelDifference() {
		final double difference = LayoutUtils.NONE - LayoutUtils.NONE;
		assertEquals("前提が崩れた", 0.0, difference, 0.0);
		// This passes. It is a known gap, not a test failure.
		new Drawer(0).visitDrawable(NOOP, difference, difference);
	}

	private static void assertRejected(double x, double y) {
		final Drawer drawer = new Drawer(0);
		try {
			drawer.visitDrawable(NOOP, x, y);
		} catch (final AssertionError expected) {
			return;
		}
		fail("異常な描画位置が素通りした: x=" + x + " y=" + y);
	}
}
