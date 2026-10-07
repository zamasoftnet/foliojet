package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;

/**
 * Params for neutral and anonymous flex/grid items. Inherits the container's text properties,
 * and resets frames, sizes, alignment, and other properties to neutral values.
 *
 * <p>
 * FlexBuilder and GridBuilder had duplicate neutralization logic. Neutralization of align-content
 * was later added only to grid, leaving the container's align-content active on anonymous flex items
 * (2026-10-04, overall review).
 * </p>
 */
final class NeutralItemParams {
	private NeutralItemParams() {
	}

	static BlockParams of(final BlockParams container) {
		final BlockParams params = BlockParamsTemplate.freeze(container).materialize();
		params.frame = RectFrame.NULL_FRAME;
		params.element = null;
		params.footnoteId = -1;
		// **Inherit the container's effective opacity** (2026-08-18). Previously, it was reset to 1f,
		// but visibility:hidden maps to opacity 0
		// (BoxStyleMapper.setupParams), so only anonymous and neutral items of hidden
		// containers were drawn. This caused a real defect: e-Stat's dropdown menu
		// overlapped the body text (1,462 overlapping pairs). Authored items inherit
		// visibility from their own style and were already correct.
		params.opacity = container.opacity;
		params.zIndexType = Params.Z_INDEX_AUTO;
		params.zIndexValue = 0;
		params.transform = new AffineTransform();
		params.columns = Columns.NONE_COLUMNS;
		// Do not inherit the container's align-content (2026-08-29). The item box is
		// created from the container's params, so otherwise the container's
		// align-content: center would act as content alignment within the item itself.
		// This surfaced when items began stretching to the row height (in Chrome,
		// the item stretches to 30 pt, but its content stays at the top).
		params.blockAlignContent = BoxAlignment.NORMAL;
		// G3a addendum (review Q1): also neutralize sizes to keep the container's width/min/max-width
		// out of the item's intrinsic sizes.
		params.size = Dimension.AUTO_DIMENSION;
		params.minSize = Dimension.ZERO_DIMENSION;
		params.maxSize = Dimension.AUTO_DIMENSION;
		return params;
	}
}
