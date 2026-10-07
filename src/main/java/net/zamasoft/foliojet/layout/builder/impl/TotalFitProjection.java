package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.text.pipeline.BreakNode;
import net.zamasoft.pdfg2d.gc.text.pipeline.LineMeasure;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit;

/**
 * Projects the measured event sequence (a sequence of {@link Piece}s) from a TextBuilder construction
 * session onto Knuth-Plass {@link BreakNode}s, and returns the breakpoints selected by {@link TotalFit}
 * as a {@link Plan} (a set of flush ordinals)
 * (introduced on 2026-07-23, M3c increment 3).
 *
 * <p>
 * BreakNode is <b>only a projection for selection</b>, not the sole intermediate representation
 * (design doc the development records). The existing {@link TextBuilder} handles all physical line construction
 * (kinsoku (line-breaking rules), hyphen materialization, inline regeneration, and justification);
 * this class only supplies "which flush causes a line break." Kinsoku maps as follows: a flush
 * actually delivered downstream becomes a normal penalty, a flush immediately after SoftHyphen
 * becomes a hyphen penalty, and a flush for an explicit line break (toLineFeed) becomes a forced
 * penalty. Whitespace becomes virtual Glue; only when a flush follows it immediately do we place
 * a zero-cost penalty before it to make it a candidate (the TeX convention). Breaking there excludes
 * the whitespace width from the line width, matching trailing whitespace collapse.
 * </p>
 *
 * <p>
 * <b>Stretch/shrink model</b>: foliojet4 justification only stretches; it does not implement shrinking
 * ({@code AbstractLineBox.align}), so Glue shrink is always 0. This prevents K-P from selecting
 * a "squeezed line" that cannot be physically reproduced. Stretch is a conservative approximation
 * used only for selection: whitespace Glue gets half its width, and zero-width trailing Glue
 * (about 2 em), protected by a forbidden-break guard, precedes each breakpoint candidate.
 * <b>Stretch is intentionally kept small</b>: {@link TotalFit} does not remove active nodes
 * (TeX deactivation), so a model with enough stretch to make "almost every breakpoint feasible"
 * explodes the candidate set to O(n²) and cannot finish in practical time (effectively hung in
 * measurements; a handoff item for increment 4). As a result, layouts with large gaps in lines
 * are not feasible K-P solutions; the subsequent width validation detects this and falls back
 * to legacy.
 * </p>
 *
 * <p>
 * Returns null for sequences that cannot be projected (no breakpoint candidates, an indivisible
 * run between candidates exceeding the line width, overflowing lines remaining in the selection,
 * etc.), and the caller falls back to legacy. This is a pure function that can be unit tested
 * with synthetic inputs.
 * </p>
 */
public final class TotalFitProjection {

	/** Hyphenation cost equivalent to TeX's \hyphenpenalty. */
	static final int HYPHEN_COST = 50;

	private TotalFitProjection() {
	}

	/** A measured event. {@link TotalFitSession} constructs it during recording. */
	public sealed interface Piece {
		/** Total width of indivisible material (glyphs, etc.) between breakpoint candidates. */
		record Box(double width) implements Piece {
		}

		/** Whitespace ({@code WhiteSpace}). The width is the advance before collapse. */
		record Space(double width) implements Piece {
		}

		/**
		 * A soft hyphen ({@code SoftHyphen}). The width is the advance of the hyphen glyph
		 * materialized at a break.
		 */
		record Hyphen(double width) implements Piece {
		}

		/** An explicit line break ({@code '\n'}, forcing a break at the next flush). */
		record LineFeed() implements Piece {
		}

		/**
		 * A flush delivered downstream (= a breakpoint candidate).
		 *
		 * @param ordinal zero-based flush ordinal within the session
		 * @param stretch base virtual stretch at this candidate (half the current font size).
		 *                The trailing Glue stretch is four times this amount (about 2 em)
		 */
		record Flush(int ordinal, double stretch) implements Piece {
		}
	}

	/**
	 * The selected breakpoints (a set of flush ordinals). During replay, {@link TotalFitSession}
	 * advances the cursor with {@link #arriveFlush} immediately before each flush event, and
	 * {@link TextBuilder} consumes "whether to break at this flush" exactly once via
	 * {@link #takeBreakAtCursor} (consume-once prevents a duplicate line break on reentry into
	 * the legacy {@code while(flush())} loop).
	 */
	public static final class Plan {
		private final BitSet chosen;

		private boolean pending = false;

		Plan(final BitSet chosen) {
			this.chosen = chosen;
		}

		/** Signals the arrival of the flush event with ordinal {@code ordinal}. */
		public void arriveFlush(final int ordinal) {
			this.pending = this.chosen.get(ordinal);
		}

		/** Returns true exactly once if a line break is required at the current flush. */
		public boolean takeBreakAtCursor() {
			if (!this.pending) {
				return false;
			}
			this.pending = false;
			return true;
		}

		/** Returns the set of selected flush ordinals (for tests). */
		public BitSet chosenOrdinals() {
			return (BitSet) this.chosen.clone();
		}
	}

	/**
	 * Selects breakpoints from an event sequence.
	 *
	 * @param pieces         measured event sequence
	 * @param firstLineWidth available width of the first line (with text-indent applied)
	 * @param lineWidth      available width of the second and subsequent lines
	 * @param params         parameters for {@link TotalFit}
	 * @return the selected breakpoints, or null if projection is impossible
	 *         (the caller falls back to legacy)
	 */
	public static Plan plan(final List<Piece> pieces, final double firstLineWidth, final double lineWidth,
			final TotalFit.Parameters params) {
		if (!(firstLineWidth > 0) || !(lineWidth > 0) || Double.isInfinite(firstLineWidth)
				|| Double.isInfinite(lineWidth)) {
			return null;
		}
		final List<BreakNode> nodes = new ArrayList<>();
		final List<Integer> ordinals = new ArrayList<>();
		// Indivisible run width between breakpoint candidates. Do not project a sequence whose width
		// exceeds the minimum line width: K-P may exhaust feasible solutions and degenerate globally.
		// (Legacy also overflows in this case, so fallback produces the same output as legacy.)
		final double maxUsable = Math.min(firstLineWidth, lineWidth);
		double unbreakable = 0;
		// Whether material with nonzero width has appeared since the previous breakpoint candidate.
		// Suppress penalties for flushes with no material, since they would create empty-line candidates.
		boolean material = false;
		boolean pendingForced = false;
		boolean anyCandidate = false;

		final int n = pieces.size();
		// Use zero-width Glue for whitespace known to be line-final (no material with nonzero width
		// or flush from there to LineFeed or the sequence end). This whitespace is not a candidate
		// (it has a forbidden guard), and must end up at a forced break or the end of the paragraph.
		// Legacy TextBuilder collapses trailing whitespace, so counting its width makes K-P see
		// an overflow in a line that actually fits exactly, systematically moving the break
		// to the preceding candidate. (Trailing whitespace from a newline before a closing tag
		// is extremely common in HTML. Discovered in E-2.)
		final boolean[] lineFinal = new boolean[n];
		{
			boolean finalRun = true;
			for (int i = n - 1; i >= 0; --i) {
				switch (pieces.get(i)) {
				case Piece.Space space -> lineFinal[i] = finalRun;
				case Piece.LineFeed lf -> finalRun = true;
				// Flush is a candidate marker with no width, so it does not change whether whitespace is line-final.
				// (The whitespace in [Space, Flush] at the end of a paragraph is also line-final.)
				case Piece.Flush flush -> {
				}
				default -> finalRun = false;
				}
			}
		}
		for (int i = 0; i < n; ++i) {
			switch (pieces.get(i)) {
			case Piece.Box box -> {
				nodes.add(new BreakNode.Box(box.width()));
				ordinals.add(-1);
				unbreakable += box.width();
				material = true;
				if (LayoutUtils.compare(unbreakable, maxUsable) > 0) {
					return null;
				}
			}

			case Piece.Space space -> {
				final Piece.Flush flush = !pendingForced && material && i + 1 < n
						&& pieces.get(i + 1) instanceof Piece.Flush f ? f : null;
				if (flush != null) {
					// Flush immediately after whitespace: place a zero-cost penalty before the whitespace Glue
					// (the TeX convention). Breaking there excludes the whitespace width from the line width,
					// and the next line discards it at the start, matching whitespace collapse.
					// Using a penalty includes the trailing Glue stretch in the line.
					// (Breaking at the Glue itself would exclude that Glue's stretch
					// from the line.)
					addTailGlue(nodes, ordinals, flush);
					nodes.add(new BreakNode.Penalty(0, 0, false));
					ordinals.add(flush.ordinal());
					nodes.add(new BreakNode.Glue(space.width(), space.width() * 0.5, 0));
					ordinals.add(-1);
					++i;
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				} else {
					// Whitespace without a flush (nowrap, etc.), or leading whitespace:
					// Glue with breaks forbidden. Leave material unchanged (whitespace alone does not make
					// a following flush a breakpoint candidate, so it cannot create a whitespace-only line).
					// Line-final whitespace has zero width (see the lineFinal note above).
					final double w = lineFinal[i] ? 0 : space.width();
					nodes.add(BreakNode.Penalty.forbidden());
					ordinals.add(-1);
					nodes.add(new BreakNode.Glue(w, w * 0.5, 0));
					ordinals.add(-1);
					unbreakable += w;
				}
			}

			case Piece.Hyphen hyphen -> {
				if (!pendingForced && material && i + 1 < n && pieces.get(i + 1) instanceof Piece.Flush f) {
					// Flush immediately after SoftHyphen: a hyphen penalty. Its width counts toward the line width
					// only at a break, matching the width of the materialized hyphen.
					addTailGlue(nodes, ordinals, f);
					nodes.add(new BreakNode.Penalty(hyphen.width(), HYPHEN_COST, true));
					ordinals.add(f.ordinal());
					++i;
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				}
				// A soft hyphen without a flush has zero width and no effect (not a candidate).
			}

			case Piece.LineFeed lf -> pendingForced = true;

			case Piece.Flush flush -> {
				if (pendingForced) {
					if (material) {
						addTailGlue(nodes, ordinals, flush);
					}
					nodes.add(BreakNode.Penalty.forced());
					ordinals.add(flush.ordinal());
					pendingForced = false;
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				} else if (material) {
					addTailGlue(nodes, ordinals, flush);
					nodes.add(new BreakNode.Penalty(0, 0, false));
					ordinals.add(flush.ordinal());
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				}
				// A flush without material is not a candidate.
			}
			}
		}
		if (!anyCandidate || nodes.isEmpty()) {
			// No breakpoint candidates (a single-line paragraph, nowrap, etc.): nothing to optimize.
			return null;
		}

		// firstLineThenConstant declares easyLine=1. Unless line numbers from the second line onward
		// collapse into dominance equivalence classes, Japanese text with justify (every character is
		// a breakpoint) retains active nodes for every line-count variant, taking minutes to solve (E-2 measurements).
		final LineMeasure measure = LineMeasure.firstLineThenConstant(firstLineWidth, lineWidth);
		final List<TotalFit.BrokenLine> lines = TotalFit.totalFit(nodes, measure, params);

		// Post-selection width validation: treat remaining overflowing lines (fit-anyway degeneration
		// after feasible solutions are exhausted) as projection failure.
		final double[] sumWidth = new double[nodes.size() + 1];
		for (int i = 0; i < nodes.size(); ++i) {
			final BreakNode node = nodes.get(i);
			final double w = node instanceof BreakNode.Penalty ? 0 : node.width();
			sumWidth[i + 1] = sumWidth[i] + w;
		}
		final BitSet chosen = new BitSet();
		for (int lineIndex = 0; lineIndex < lines.size(); ++lineIndex) {
			final TotalFit.BrokenLine line = lines.get(lineIndex);
			final int breakIndex = line.breakIndex();
			// Natural width excluding Glue discarded at the line start.
			int lineStart = line.begin();
			while (lineStart < breakIndex && nodes.get(lineStart) instanceof BreakNode.Glue) {
				++lineStart;
			}
			double natural = sumWidth[Math.min(breakIndex, nodes.size())] - sumWidth[lineStart];
			if (breakIndex < nodes.size() && nodes.get(breakIndex) instanceof BreakNode.Penalty penalty) {
				natural += penalty.width();
			}
			if (LayoutUtils.compare(natural, measure.width(lineIndex)) > 0) {
				return null;
			}
			if (line.kind() == TotalFit.BreakKind.PARAGRAPH_END) {
				continue;
			}
			if (breakIndex < 0 || breakIndex >= ordinals.size()) {
				return null;
			}
			final int ordinal = ordinals.get(breakIndex);
			if (ordinal < 0) {
				// Broke at a node that is not a candidate: inconsistent projection. Fail safely.
				return null;
			}
			chosen.set(ordinal);
		}
		return new Plan(chosen);
	}

	/**
	 * Places zero-width trailing Glue with a forbidden-break guard immediately before a breakpoint
	 * candidate (the virtual stretch available to a line broken here, about 2 em). Without the guard,
	 * an alternative breakpoint with no cost or flag would appear (an empty-line candidate before
	 * a forced break, or bypassed hyphen demerits).
	 */
	private static void addTailGlue(final List<BreakNode> nodes, final List<Integer> ordinals,
			final Piece.Flush flush) {
		final double stretch = flush.stretch() * 4;
		if (stretch <= 0) {
			return;
		}
		nodes.add(BreakNode.Penalty.forbidden());
		ordinals.add(-1);
		nodes.add(new BreakNode.Glue(0, stretch, 0));
		ordinals.add(-1);
	}
}
