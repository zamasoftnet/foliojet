package net.zamasoft.foliojet.layout.rescue;

import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * A rescue-fragment adapter for floating boxes (introduced 2026-07-25, increment 3;
 * <b>not yet wired into production paths</b>).
 *
 * <p>
 * A thin wrapper that only adds {@link IFloatBox#getFloatPos()} to {@link VisualRescueBox} .
 * Returns the original box's positioning parameters unchanged; fragments never modify its Params or Pos.
 * The exclusion-area height becomes the fragment's occupancy ({@code sliceExtent}),
 * but this derives from {@code getPageExtent()} , so no state is held here.
 * </p>
 */
public class VisualRescueFloatBox extends VisualRescueBox implements IFloatBox {

	/**
	 * @param source laid-out original box
	 * @param progression writing direction determining the page axis
	 * @param sourcePageExtent original box's page-direction occupancy
	 * @param offset page-direction position where this fragment starts
	 * @param sliceExtent this fragment's occupancy
	 */
	public VisualRescueFloatBox(final IFloatBox source, final WritingMode progression, final double sourcePageExtent,
			final double offset, final double sliceExtent) {
		super(source, progression, sourcePageExtent, offset, sliceExtent);
	}

	public FloatPos getFloatPos() {
		return ((IFloatBox) this.getSource()).getFloatPos();
	}
}
