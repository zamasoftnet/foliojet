package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.sizing.BasicGridTrackSizing.ItemContribution;

/**
 * Pure calculation tests for {@link BasicGridTrackSizing} (Grid G3b/c, span support added in G4d)
 * (required cases in consult-codex-2026-07-31-grid-g3.txt Q2 and -grid-g4.txt Q2).
 */
public class BasicGridTrackSizingTest extends TestCase {

	private static final GridTrackListValue.TrackSize AUTO = GridTrackListValue.Auto.INSTANCE;

	private static GridTrackListValue.TrackSize fixed(final double length) {
		return new GridTrackListValue.Fixed(length);
	}

	/** Builds per-column span1 contributions (min=max is also allowed). */
	private static List<ItemContribution> perColumn(final double[] colMin, final double[] colMax) {
		final List<ItemContribution> items = new ArrayList<>();
		for (int i = 0; i < colMin.length; ++i) {
			if (colMin[i] > 0 || colMax[i] > 0) {
				items.add(new ItemContribution(i, 1, colMin[i], colMax[i]));
			}
		}
		return items;
	}

	private static GridTrackListValue.TrackSize minmax(final GridTrackListValue.TrackSize min,
			final GridTrackListValue.TrackSize max) {
		return new GridTrackListValue.MinMax(min, max);
	}

	/**
	 * minmax(fixed, fr) (2026-08-29): fixed min sets the base; content does not enlarge it, and fr gets the
	 * remainder.
	 */
	public void testMinMaxFixedMinFr() {
		final List<GridTrackListValue.TrackSize> tracks = List.of(minmax(fixed(100), new GridTrackListValue.Fr(1)),
				fixed(50));
		double[] w = BasicGridTrackSizing.resolve(tracks, perColumn(new double[] { 10, 10 }, new double[] { 10, 10 }),
				300, 0);
		assertEquals(250.0, w[0], 0.001);
		assertEquals(50.0, w[1], 0.001);
		// Do not shrink even if available width falls below the sum of base widths (overflow).
		w = BasicGridTrackSizing.resolve(tracks, perColumn(new double[] { 10, 10 }, new double[] { 10, 10 }), 120, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(50.0, w[1], 0.001);
		// Content's min-content (150) does not enlarge the fixed min of 100.
		w = BasicGridTrackSizing.resolve(tracks, perColumn(new double[] { 150, 10 }, new double[] { 150, 10 }), 120,
				0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(50.0, w[1], 0.001);
		// Same as the existing minmax(0, <fr>) (ZeroMinFr): equal division even for long content.
		final double[] zero = BasicGridTrackSizing.resolve(
				List.of(minmax(fixed(0), new GridTrackListValue.Fr(1)), minmax(fixed(0), new GridTrackListValue.Fr(1))),
				perColumn(new double[] { 500, 0 }, new double[] { 500, 0 }), 200, 0);
		assertEquals(100.0, zero[0], 0.001);
		assertEquals(100.0, zero[1], 0.001);
	}

	/**
	 * minmax(auto, fixed) (2026-08-29): base width is min-content; the limit is fixed max, raised to base if
	 * smaller.
	 */
	public void testMinMaxAutoMinFixedMax() {
		final List<GridTrackListValue.TrackSize> tracks = List.of(minmax(AUTO, fixed(200)),
				new GridTrackListValue.Fr(1));
		double[] w = BasicGridTrackSizing.resolve(tracks, perColumn(new double[] { 10, 10 }, new double[] { 10, 10 }),
				300, 0);
		assertEquals(200.0, w[0], 0.001); // Maximize tracks up to their limits, then allocate to fr.
		assertEquals(100.0, w[1], 0.001);
		w = BasicGridTrackSizing.resolve(tracks, perColumn(new double[] { 250, 10 }, new double[] { 250, 10 }), 300,
				0);
		assertEquals(250.0, w[0], 0.001);
		assertEquals(50.0, w[1], 0.001);
		// minmax(auto, max-content), capped at max-content, grows like auto but does not stretch.
		final double[] mc = BasicGridTrackSizing.resolve(List.of(minmax(AUTO, GridTrackListValue.MaxContent.INSTANCE)),
				perColumn(new double[] { 40 }, new double[] { 80 }), 150, 0);
		assertEquals(80.0, mc[0], 0.001);
	}

	/**
	 * minmax(fixed, fixed) (2026-08-29): distribute the remainder equally up to limits (maximize tracks), without
	 * stretching beyond.
	 */
	public void testMinMaxFixedFixedMaximize() {
		final List<GridTrackListValue.TrackSize> tracks = List.of(minmax(fixed(50), fixed(100)),
				minmax(fixed(50), fixed(100)));
		double[] w = BasicGridTrackSizing.resolve(tracks, List.of(), 120, 0);
		assertEquals(60.0, w[0], 0.001);
		assertEquals(60.0, w[1], 0.001);
		w = BasicGridTrackSizing.resolve(tracks, List.of(), 300, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(100.0, w[1], 0.001);
		// If max<min, ignore max as specified.
		w = BasicGridTrackSizing.resolve(List.of(minmax(fixed(100), fixed(50))), List.of(), 300, 0);
		assertEquals(100.0, w[0], 0.001);
		// Intrinsic sizes: min=Σmin sides, max=Σmax sides.
		final BasicGridTrackSizing.Intrinsics in = BasicGridTrackSizing.intrinsics(tracks, List.of(), 10);
		assertEquals(110.0, in.min(), 0.001);
		assertEquals(210.0, in.max(), 0.001);
	}

	/** fixed+auto: stretch gives the remainder to the auto column (80+gap10+auto→300 gives 210). */
	public void testFixedAutoStretch() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(80), AUTO),
				perColumn(new double[] { 60, 70 }, new double[] { 60, 70 }), 300, 10);
		assertEquals(80.0, w[0], 0.001);
		assertEquals(210.0, w[1], 0.001);
	}

	/** Two auto columns: grow equally up to growth limits, then stretch equally with any remainder. */
	public void testAutoGrowthThenStretch() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO),
				perColumn(new double[] { 40, 30 }, new double[] { 80, 30 }), 150, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(50.0, w[1], 0.001);
	}

	/** A remainder insufficient to reach the growth limits stops below them. */
	public void testAutoPartialGrowth() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO),
				perColumn(new double[] { 40 }, new double[] { 80 }), 60, 0);
		assertEquals(60.0, w[0], 0.001);
	}

	/** min-content floor: overflow without shrinking, even when available width is exceeded. */
	public void testOverflowKeepsMinContent() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO),
				perColumn(new double[] { 60 }, new double[] { 60 }), 30, 0);
		assertEquals(60.0, w[0], 0.001);

		final double[] w2 = BasicGridTrackSizing.resolve(List.of(fixed(100), fixed(100)), List.of(), 150, 10);
		assertEquals(100.0, w2[0], 0.001);
		assertEquals(100.0, w2[1], 0.001);
	}

	/** Fixed only: leave the remainder at the end without distributing it (same as G1). */
	public void testFixedOnlyLeavesRemainder() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(100), fixed(100)), List.of(), 300, 20);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(100.0, w[1], 0.001);
	}

	/** Proportional fr distribution and coexistence with fixed/auto (G3c). */
	public void testFrProportionalAndMixed() {
		final double[] w = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(2)), List.of(), 300, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(200.0, w[1], 0.001);

		final double[] m = BasicGridTrackSizing.resolve(List.of(fixed(60), AUTO, new GridTrackListValue.Fr(1)),
				perColumn(new double[] { 0, 30, 0 }, new double[] { 0, 50, 0 }), 300, 0);
		assertEquals(60.0, m[0], 0.001);
		assertEquals(50.0, m[1], 0.001);
		assertEquals(190.0, m[2], 0.001);
	}

	/** A single 0.5fr fills only 50% of the remainder (round the weight sum up to 1 if below 1). */
	public void testFrPartialFill() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(new GridTrackListValue.Fr(0.5)), List.of(), 200, 0);
		assertEquals(100.0, w[0], 0.001);
	}

	/** fr min-content floor: freeze columns that would fall below their floors and recalculate the remainder. */
	public void testFrBaseFloorFreeze() {
		final double[] w = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(1)),
				perColumn(new double[] { 80, 0 }, new double[] { 80, 0 }), 100, 0);
		assertEquals(80.0, w[0], 0.001);
		assertEquals(20.0, w[1], 0.001);
	}

	/**
	 * fr edge cases: base sums exceeding capacity, zero width, and zero weight must not yield NaN or negative
	 * values.
	 */
	public void testFrDegenerateCases() {
		final double[] over = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(1)),
				perColumn(new double[] { 80, 70 }, new double[] { 80, 70 }), 100, 0);
		assertEquals(80.0, over[0], 0.001);
		assertEquals(70.0, over[1], 0.001);

		final double[] zero = BasicGridTrackSizing.resolve(List.of(new GridTrackListValue.Fr(1)), List.of(), 0, 0);
		assertEquals(0.0, zero[0], 0.001);

		final double[] w0 = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(0), new GridTrackListValue.Fr(1)), List.of(), 100, 0);
		assertEquals(0.0, w0[0], 0.001);
		assertEquals(100.0, w0[1], 0.001);
		for (final double v : new double[] { over[0], over[1], zero[0], w0[0], w0[1] }) {
			assertFalse(Double.isNaN(v));
			assertTrue(v >= 0);
		}
	}

	/** An empty auto column (no items) starts at 0 and only stretches. */
	public void testEmptyAutoColumnAndZeroContainer() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO),
				perColumn(new double[] { 50, 0 }, new double[] { 50, 0 }), 100, 0);
		assertEquals(75.0, w[0], 0.001);
		assertEquals(25.0, w[1], 0.001);
	}

	/** G5c: positional justify-content stops stretching auto columns with the remainder. */
	public void testPositionalNoAutoStretch() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(80), AUTO),
				perColumn(new double[] { 0, 30 }, new double[] { 0, 70 }), 300, 10, false);
		assertEquals(80.0, w[0], 0.001);
		assertEquals(70.0, w[1], 0.001); // Up to the max-content limit. The remaining 140 goes to the offset.
	}

	/** Span deficit distribution (G4d): for a span across fixed+auto, only the auto side grows. */
	public void testSpanDeficitToAuto() {
		// [40pt auto], span1 auto contribution 20, span2 item min=max=100.
		// → Deficit 100-(40+20)=40 goes to auto → base 60. Intrinsics min=max=100.
		final List<ItemContribution> items = List.of(new ItemContribution(1, 1, 20, 20),
				new ItemContribution(0, 2, 100, 100));
		final BasicGridTrackSizing.Intrinsics in = BasicGridTrackSizing.intrinsics(List.of(fixed(40), AUTO), items,
				0);
		assertEquals(100.0, in.min(), 0.001);
		assertEquals(100.0, in.max(), 0.001);
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(40), AUTO), items, 100, 0);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(60.0, w[1], 0.001);
	}

	/** Span deficit distribution: deduct internal gaps; equal span lengths use the maximum required increase. */
	public void testSpanDeficitGapAndBatch() {
		// [auto auto], gap10. Two span2 items (min 90 and 70); only the larger affects the result
		// (planned increase). Deficit=90-10-0=80 → 40 to each auto.
		final List<ItemContribution> items = List.of(new ItemContribution(0, 2, 90, 90),
				new ItemContribution(0, 2, 70, 70));
		final BasicGridTrackSizing.Intrinsics in = BasicGridTrackSizing.intrinsics(List.of(AUTO, AUTO), items, 10);
		assertEquals(90.0, in.min(), 0.001);
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO), items, 90, 10);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(40.0, w[1], 0.001);
	}

	/** Span deficit distribution: when crossing fr tracks, distribute to them by weight as a floor. */
	public void testSpanDeficitToFr() {
		// [40pt 1fr], span2 item min=max=100 → fr floor=60. available 80 <
		// 40+60 → overflow with fr=60 (floor preserved).
		final List<ItemContribution> items = List.of(new ItemContribution(0, 2, 100, 100));
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(40), new GridTrackListValue.Fr(1)), items, 80,
				0);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(60.0, w[1], 0.001);

		// With enough room, fr takes the remainder (greater than the floor).
		final double[] wide = BasicGridTrackSizing.resolve(List.of(fixed(40), new GridTrackListValue.Fr(1)), items,
				200, 0);
		assertEquals(160.0, wide[1], 0.001);
	}

	/** A span across only fixed tracks does not enlarge the tracks (overflow allowed). */
	public void testSpanOverFixedOnly() {
		final List<ItemContribution> items = List.of(new ItemContribution(0, 2, 300, 300));
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(40), fixed(40)), items, 200, 0);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(40.0, w[1], 0.001);
	}
}
