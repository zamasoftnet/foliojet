package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;

public interface LayoutStack {
	public RootBuilder getPageContext();

	public Builder getParentBuilder();

	public AbstractContainerBox getRootBox();

	public AbstractContainerBox getFlowBox();

	public AbstractContainerBox getContextBox();

	public AbstractContainerBox getMulticolumnBox();

	public double getFixedWidth();

	public double getFixedHeight();

	/**
	 * Returns a flow whose width is determined.
	 *
	 * @return
	 */
	public AbstractContainerBox getFixedWidthFlowBox();

	public AbstractContainerBox getFixedHeightFlowBox();

	/**
	 * <b>Percentage reference for the line (inline) axis of an orthogonal flow</b> (added 2026-09-16).
	 *
	 * <p>
	 * {@link #getFixedWidth()}/{@link #getFixedHeight()} walk ancestors with explicit dimensions,
	 * so they return <b>0</b> when none qualifies, as for a horizontal-writing box inside a vertical-writing
	 * document. Using 0 as the reference makes {@code max-width: 90%} equal 0, and content from the
	 * zero-width box <b>overflows off the page</b> (the sweep's "all drawing outside the page" finding).
	 * Since paper dimensions are definite, use the fragmentainer (page) content area as the final
	 * reference, per css-writing-modes-4 §7.3.
	 * </p>
	 *
	 * <p>
	 * <b>Keep this decision only here.</b> Duplicating the fallback lets an axis thought to be fixed revert
	 * to 0 on another path (indeed, fixing only table sizing left fit-content in
	 * {@code AbstractStaticBlockBox} at 0).
	 * </p>
	 *
	 * @param flow writing mode determining the axis on which to measure the reference (the box's own writing mode)
	 */
	public default double getOrthogonalLineBasis(final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		final double fixed = flow.isVertical() ? this.getFixedHeight() : this.getFixedWidth();
		if (!net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(fixed)
				&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(fixed, 0) > 0) {
			return fixed;
		}
		final RootBuilder root = this.getPageContext();
		if (root == null) {
			return fixed;
		}
		final AbstractContainerBox page = root.getRootBox();
		if (page == null) {
			return fixed;
		}
		final double extent = page.getInnerLineExtent(flow);
		return !net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(extent)
				&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(extent, 0) > 0 ? extent : fixed;
	}

	public AbstractContainerBox getFixedWidthContextBox();

	public AbstractContainerBox getFixedHeightContextBox();
}
