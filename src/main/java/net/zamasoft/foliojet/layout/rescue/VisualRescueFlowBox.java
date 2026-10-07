package net.zamasoft.foliojet.layout.rescue;

import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * A rescue-fragment adapter for normal flow (introduced 2026-07-25, increment 3;
 * <b>not yet wired into production paths</b>).
 *
 * <p>
 * A thin wrapper that only adds the two {@link IFlowBox} methods to {@link VisualRescueBox} .
 * Break avoidance is meaningful only at fragment edges:
 * </p>
 *
 * <ul>
 * <li>{@code avoidBreakBefore} — Only the first fragment inherits the original box's setting.
 * Before a continuation fragment is a cut surface created by rescue splitting itself;
 * prohibiting a break there would stop progress.</li>
 * <li>{@code avoidBreakAfter} — Only the final fragment inherits the original box's setting.
 * After intermediate fragments, breaks are not prohibited for the same reason.</li>
 * </ul>
 */
public class VisualRescueFlowBox extends VisualRescueBox implements IFlowBox {

	/**
	 * @param source laid-out original box
	 * @param progression writing direction determining the page axis
	 * @param sourcePageExtent original box's page-direction occupancy
	 * @param offset page-direction position where this fragment starts
	 * @param sliceExtent this fragment's occupancy
	 */
	public VisualRescueFlowBox(final IFlowBox source, final WritingMode progression, final double sourcePageExtent,
			final double offset, final double sliceExtent) {
		super(source, progression, sourcePageExtent, offset, sliceExtent);
	}

	private IFlowBox flowSource() {
		return (IFlowBox) this.getSource();
	}

	public boolean avoidBreakBefore() {
		return this.isFirstFragment() && this.flowSource().avoidBreakBefore();
	}

	public boolean avoidBreakAfter() {
		return this.isLastFragment() && this.flowSource().avoidBreakAfter();
	}
}
