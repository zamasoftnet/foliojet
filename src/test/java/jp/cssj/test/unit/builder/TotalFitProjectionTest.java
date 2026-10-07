package jp.cssj.test.unit.builder;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.builder.impl.TotalFitProjection;
import net.zamasoft.foliojet.layout.builder.impl.TotalFitProjection.Piece;
import net.zamasoft.foliojet.layout.builder.impl.TotalFitProjection.Plan;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit;

/**
 * Unit tests for Knuth-Plass projection ({@code TotalFitProjection}, M3c increment 3).
 * Checks breakpoint selection and fallback decisions using only synthetic measured event sequences
 * (Piece sequences), without starting TextBuilder.
 */
public class TotalFitProjectionTest extends TestCase {

	private static TotalFit.Parameters params() {
		return new TotalFit.Parameters(200, 10, 10000, 10000, 5000, TotalFit.LastLinePolicy.RAGGED);
	}

	/**
	 * A Latin-style sequence of {@code count} words of width {@code word}, separated by spaces.
	 * {@code em} corresponds to the font size passed to flush and serves as the expansion basis.
	 */
	private static List<Piece> latinWords(final int count, final double word, final double space, final double em) {
		final List<Piece> pieces = new ArrayList<>();
		int ordinal = 0;
		for (int i = 0; i < count; ++i) {
			pieces.add(new Piece.Box(word));
			if (i < count - 1) {
				pieces.add(new Piece.Space(space));
				pieces.add(new Piece.Flush(ordinal++, em * 0.5));
			}
		}
		return pieces;
	}

	/** A Japanese-style sequence with a flush after each indivisible unit of width {@code w}. */
	private static List<Piece> cjkUnits(final int count, final double w, final double stretch) {
		final List<Piece> pieces = new ArrayList<>();
		int ordinal = 0;
		for (int i = 0; i < count; ++i) {
			pieces.add(new Piece.Box(w));
			pieces.add(new Piece.Flush(ordinal++, stretch));
		}
		return pieces;
	}

	/**
	 * Computes each line's width when split at the selected breakpoints
	 * (excludes spaces broken at line end from the width, modeling trailing-space collapse).
	 */
	private static List<Double> lineWidths(final List<Piece> pieces, final BitSet chosen) {
		final List<Double> widths = new ArrayList<>();
		double current = 0;
		boolean lineHead = true;
		for (int i = 0; i < pieces.size(); ++i) {
			switch (pieces.get(i)) {
			case Piece.Box box -> {
				current += box.width();
				lineHead = false;
			}
			case Piece.Space space -> {
				if (i + 1 < pieces.size() && pieces.get(i + 1) instanceof Piece.Flush f
						&& chosen.get(f.ordinal())) {
					widths.add(current);
					current = 0;
					lineHead = true;
					++i;
				} else if (!lineHead) {
					current += space.width();
				}
			}
			case Piece.Hyphen hyphen -> {
				if (i + 1 < pieces.size() && pieces.get(i + 1) instanceof Piece.Flush f
						&& chosen.get(f.ordinal())) {
					widths.add(current + hyphen.width());
					current = 0;
					lineHead = true;
					++i;
				}
			}
			case Piece.LineFeed lf -> {
				// Force a line break at the next flush (this test puts a flush immediately afterward).
			}
			case Piece.Flush flush -> {
				if (chosen.get(flush.ordinal())) {
					widths.add(current);
					current = 0;
					lineHead = true;
				}
			}
			}
		}
		widths.add(current);
		return widths;
	}

	public void testLatinParagraphBreaksWithinMeasure() {
		// Ten words of width 30 + spaces of width 5, line width 100 → lines of exactly 3 words (100) are feasible.
		final List<Piece> pieces = latinWords(10, 30, 5, 10);
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		final BitSet chosen = plan.chosenOrdinals();
		assertTrue(chosen.cardinality() >= 2);
		for (final double w : lineWidths(pieces, chosen)) {
			assertTrue("line overflow: " + w, w <= 100 + 0.5);
		}
	}

	public void testFirstLineIndentNarrowsFirstLineOnly() {
		// Only the first line has width 50 (equivalent to text-indent 50). With word 30 + space 5,
		// the first line fits only one word (trailing Glue expansion makes even a one-word line feasible).
		final List<Piece> pieces = latinWords(6, 30, 5, 10);
		final Plan plan = TotalFitProjection.plan(pieces, 50, 100, params());
		assertNotNull(plan);
		final List<Double> widths = lineWidths(pieces, plan.chosenOrdinals());
		assertTrue("first line must fit 50: " + widths.get(0), widths.get(0) <= 50 + 0.5);
	}

	public void testForcedBreakIsAlwaysChosen() {
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(20));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(0, 0));
		pieces.add(new Piece.Box(20));
		pieces.add(new Piece.LineFeed());
		pieces.add(new Piece.Flush(1, 0));
		pieces.add(new Piece.Box(20));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(2, 0));
		pieces.add(new Piece.Box(20));
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		// The explicit line break (ordinal 1) is always selected; positions inside fitting lines (0, 2)
		// are not selected.
		final BitSet chosen = plan.chosenOrdinals();
		assertTrue(chosen.get(1));
		assertFalse(chosen.get(0));
		assertFalse(chosen.get(2));
	}

	public void testHyphenBreakAccountsHyphenWidth() {
		// [90][SHY(5)][flush][30], line width 100: a hyphenated break fits at 90+5=95.
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(90));
		pieces.add(new Piece.Hyphen(5));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Box(30));
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		assertTrue(plan.chosenOrdinals().get(0));
		for (final double w : lineWidths(pieces, plan.chosenOrdinals())) {
			assertTrue("line overflow: " + w, w <= 100 + 0.5);
		}
	}

	public void testCjkParagraphBreaksWithinMeasure() {
		// Thirty 10 pt characters, line width 95 → break around 9 characters (trailing Glue makes this feasible).
		final List<Piece> pieces = cjkUnits(30, 10, 5);
		final Plan plan = TotalFitProjection.plan(pieces, 95, 95, params());
		assertNotNull(plan);
		final BitSet chosen = plan.chosenOrdinals();
		assertTrue(chosen.cardinality() >= 2);
		for (final double w : lineWidths(pieces, chosen)) {
			assertTrue("line overflow: " + w, w <= 95 + 0.5);
		}
	}

	public void testSingleWordLinesFeasibleWithTailStretch() {
		// Only one word fits per line (80/100). This is within the expansion range of
		// trailing Glue (2em=20), so five lines are possible.
		final List<Piece> pieces = latinWords(5, 80, 5, 10);
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		final List<Double> widths = lineWidths(pieces, plan.chosenOrdinals());
		assertEquals(5, widths.size());
		for (final double w : widths) {
			assertTrue("line overflow: " + w, w <= 100 + 0.5);
		}
	}

	public void testInfeasibleLayoutFallsBack() {
		// With zero expansion (em=0), a one-word line is not feasible and the whole sequence
		// degenerates into an overflowing line; post-selection width validation detects this and falls back to legacy.
		final List<Piece> pieces = latinWords(5, 80, 5, 0);
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testOversizedUnbreakableRunFallsBack() {
		// An unbreakable sequence (200) between breakpoint candidates exceeds line width 100 → legacy.
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(200));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Box(30));
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testNowrapSpaceIsNotBreakable() {
		// Whitespace without a flush (nowrap) is not a breakpoint candidate, so an unbreakable
		// sequence exceeds the line width and triggers fallback.
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(60));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Box(60));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Box(10));
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testNoBreakCandidatesFallsBack() {
		// No optimization when there are zero breakpoint candidates (no flush).
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(30));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Box(30));
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testEmptyPiecesFallsBack() {
		assertNull(TotalFitProjection.plan(new ArrayList<>(), 100, 100, params()));
	}

	public void testMateriallessFlushIsNotACandidate() {
		// Do not place a penalty on a flush without material (consecutive flushes),
		// so no blank-line candidates are created.
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(80));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Flush(1, 5));
		pieces.add(new Piece.Box(80));
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		assertTrue(plan.chosenOrdinals().get(0));
		assertFalse(plan.chosenOrdinals().get(1));
	}

	public void testPlanConsumeOnce() {
		final List<Piece> pieces = cjkUnits(30, 10, 5);
		final Plan plan = TotalFitProjection.plan(pieces, 95, 95, params());
		assertNotNull(plan);
		final int ordinal = plan.chosenOrdinals().nextSetBit(0);
		assertTrue(ordinal >= 0);
		plan.arriveFlush(ordinal);
		// true only once at a selected flush (re-entering the while(flush()) loop
		// does not add a second line break).
		assertTrue(plan.takeBreakAtCursor());
		assertFalse(plan.takeBreakAtCursor());
		// Always false at an unselected flush.
		int notChosen = 0;
		while (plan.chosenOrdinals().get(notChosen)) {
			++notChosen;
		}
		plan.arriveFlush(notChosen);
		assertFalse(plan.takeBreakAtCursor());
	}
}
