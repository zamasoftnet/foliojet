package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.params.BlockParams;

/**
 * Line-count state for {@code line-clamp} (css-overflow-4 §5, 2026-08-29).
 *
 * <p>
 * One instance lives in the clamping block's {@link LayoutContext.Flow}; text blocks inside it
 * ({@code TextBuilder}) increment the count whenever they finalize a line. The specification's
 * "line boxes of a block container" includes lines in nested blocks, so {@link #find} locates the
 * nearest clamping block on the same builder's flow stack and counts into the same state regardless
 * of depth. Floats, absolutely positioned content, and flex/grid items use separate builders and
 * are not counted (only in-flow lines, as specified).
 * </p>
 *
 * <p>
 * Do not truncate line N until subsequent content is known to exist (a paragraph with exactly N lines
 * must not receive an ellipsis). When line N closes, {@link #setPending} stores only the truncation
 * action; execute it when line N+1 or later is discarded ({@link #truncatePending}). If no line is
 * discarded, the action remains pending until completion, yielding no ellipsis.
 * </p>
 */
public final class LineClampState {
	private final int limit;

	private int count;

	private Runnable pending;

	public LineClampState(final int limit) {
		this.limit = limit;
	}

	/**
	 * Returns the nearest clamping block's state (null if absent).
	 */
	public static LineClampState find(final LayoutContext context) {
		for (int i = context.getFlowCount() - 1; i >= 0; --i) {
			final LayoutContext.Flow flow = context.getFlow(i);
			if (flow == null || flow.box == null) {
				continue;
			}
			final BlockParams params = flow.box.getBlockParams();
			if (params.lineClamp > 0) {
				if (flow.lineClamp == null) {
					flow.lineClamp = new LineClampState(params.lineClamp);
				}
				return flow.lineClamp;
			}
		}
		return null;
	}

	/** Whether N lines have been reached and further lines should be discarded. */
	public boolean exhausted() {
		return this.count >= this.limit;
	}

	/** Counts one line. true if it is exactly line N. */
	public boolean countLine() {
		++this.count;
		return this.count == this.limit;
	}

	/** Stores the truncation action for line N (runs when subsequent content appears). */
	public void setPending(final Runnable truncate) {
		this.pending = truncate;
	}

	/** Subsequent content has appeared, so executes the pending truncation of line N. */
	public void truncatePending() {
		final Runnable truncate = this.pending;
		if (truncate != null) {
			this.pending = null;
			truncate.run();
		}
	}
}
