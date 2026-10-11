package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.builder.LayoutStack;

public class ColumnBuilder extends BreakableBuilder {
	public ColumnBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox) {
		super(layoutStack, contextBox, MODE_AUTO);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Within multi-column layout, the last resort is {@link #pageBreak}, a column break
	 * in this multi-column box. Thus, once {@code column-count} is exhausted,
	 * <b>no more fragments can be created</b>.
	 * </p>
	 *
	 * <p>
	 * <b>Do not rely on nested multi-column layout</b> (observed in a 50,000-seed sweep,
	 * 2026-07-28). Initially, {@code findColumnBreak() != null} also counted as true,
	 * but {@code columnBreak()} can fail with {@code Keep}/{@code Move} <b>even when</b>
	 * {@code flowStack} contains a box that allows column breaks. Failure falls back to
	 * {@link #pageBreak}; if its columns are exhausted, it returns {@code false} and
	 * triggers {@code assert this.textBuilder != null} in {@link BreakableBuilder#flush()}
	 * (3 strict + 5 wild cases; seed 2928 was the smallest). <b>This check errs on the
	 * side of guaranteeing "failure is safe", rather than "every attempt succeeds"</b>.
	 * The skipped column break merely lets content overflow in place; it neither
	 * disappears nor jumps outside the sheet.
	 * </p>
	 */
	@Override
	protected final boolean canFragmentFurther() {
		// A builder without page context, such as one remeasuring table cells, cannot register
		// a continuation destination with RootBuilder. Reporting "column break available" here
		// would proceed to columnBreak() and then violate the rootless invariant.
		// For a closed measurement fragment, letting content overflow in place is safe.
		return this.getPageContext() != null && this.contextFlow.box.canColumnBreak();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>Within multi-column layout, a "page break" is a column break.</b> This is the last resort
	 * when {@code BreakableBuilder.autoBreak()} finds no column break point. It used to
	 * unconditionally add a column to {@code contextFlow} (the multi-column box itself).
	 * {@link BreakableBuilder#findColumnBreak()} only inspects {@code flowStack}, whereas
	 * the multi-column owner is {@code contextFlow}, outside {@code flowStack}, so
	 * <b>nothing on this path checked the {@code column-count} limit</b> (2026-07-28).
	 * </p>
	 *
	 * <p>
	 * Columns are arranged along the <b>line axis</b> (column {@code i} is at
	 * {@code i×(column width+column gap)}). The line axis cannot be fragmented, so adding
	 * columns sends content straight off the sheet. For seed 46577, {@code column-count:4}
	 * became <b>14 columns</b>, drawn at y=2,835 on an 842 pt sheet.
	 * </p>
	 *
	 * <p>
	 * Observe the limit <b>only for automatic page breaks</b>. Forced page breaks
	 * ({@link ForceBreakMode}) mean <b>the author requested a column</b>, so do not count them —
	 * the same reason {@code ContinuationStats.guardBreakProgress} monitors only automatic breaks.
	 * </p>
	 *
	 * <p>
	 * When returning {@code false}, call {@link BreakableBuilder#beginBreak()}
	 * <b>first, without exception</b>. This is the same contract as {@code RootBuilder.pageBreak()} returning
	 * {@code false} for "no page break point". An empty {@code breakFloats} is the termination
	 * condition of the float splitting loop in {@link BreakableBuilder#endFlowBlock()}
	 * (returning {@code false} without clearing it causes an <b>infinite loop</b>,
	 * observed on 2026-07-28).
	 * </p>
	 */
	protected final boolean pageBreak(BreakMode mode, byte flags) {
		if (mode instanceof AutoBreakMode
				&& (this.getPageContext() == null || !this.contextFlow.box.canColumnBreak())) {
			// All columns are exhausted. Adding another would extend content off the sheet along the line axis,
			// so let it overflow within the last column (the same behavior as browsers
			// when they exhaust column-count).
			this.beginBreak();
			return false;
		}
		int depth;
		if (this.flowStack != null && !this.flowStack.isEmpty()) {
			depth = this.flowStack.size() + 1;
		} else {
			depth = 1;
		}
		flags |= IPageBreakableBox.FLAGS_LAST;
		final double lastFrame = this.lastFrame(this.contextFlow, depth);
		return this.columnBreak(this.contextFlow, mode, flags, lastFrame, depth);
	}

	public final void finish() {
		assert this.flowStack == null || this.flowStack.isEmpty();
		this.requireNoOpenTextBuilder("(no context)");
	}
}
