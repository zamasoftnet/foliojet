package net.zamasoft.foliojet.layout.text;

import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * Inline filler for {@code leader()} (css-content-3,
 * consult-codex-2026-07-31-leader.txt L1).
 *
 * <p>
 * Participates in line-break decisions with one shaped pattern period ({@link #runs}) as its minimum width.
 * When the line is finalized (allocation in {@code TextBuilder.drawLine}), receives a share of remaining
 * width that determines {@link #advance}. Multiple leaders on the same line share the remainder equally
 * (FolioJet's practical rule; the draft does not specify distribution among multiple leaders).
 * Rendering repeats the pattern on a fixed grid anchored at the line end without materializing it
 * as a glyph sequence (phase alignment: dots align vertically across lines).
 * Supplies only a single space to logical text ({@code getText}), without inserting a sequence of dots.
 * </p>
 *
 * <p>
 * Width is reallocated for each line, so allocation must first reset {@link #advance} to the minimum width
 * (to prevent previous allocations from leaking when TwoPass recording/replay executes the same instance again).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LeaderQuad extends Quad {
	/** One shaped period of the pattern (treated as immutable). */
	public final Text[] runs;

	/** Minimum width (= one shaped pattern period). */
	public final double minAdvance;

	/** Width allocated when the line is finalized (initially the minimum width). */
	public double advance;

	/**
	 * Distance to the line-end phase-alignment origin (from the leader's end to the end of the line's
	 * content; set during allocation).
	 */
	public double endOffset;

	public LeaderQuad(final Text[] runs) {
		assert runs.length > 0;
		this.runs = runs;
		double a = 0;
		for (final Text run : runs) {
			a += run.getAdvance();
		}
		this.minAdvance = a;
		this.advance = a;
	}

	public double getAdvance() {
		return this.advance;
	}

	public String getString() {
		// Do not break between the leader and following content (page number).
		return CONTINUE_BEFORE;
	}

	public String toString() {
		return "[LEADER]";
	}
}
