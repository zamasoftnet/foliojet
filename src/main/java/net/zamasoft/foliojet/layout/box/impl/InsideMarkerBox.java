package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;

/**
 * An inside marker (list-style-position: inside).
 *
 * <p>
 * Although implemented as an inline block, its marker text includes trailing whitespace
 * whose advance creates the gap between the marker and body text (legacy semantics).
 * Distinguish it by type to exclude it from actual layout measurement
 * (MeasuredIntrinsics, which correctly removes trailing whitespace).
 * Keep this distinction until marker placement is redesigned with an explicit gap
 * (css-lists ::marker).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class InsideMarkerBox extends InlineBlockBox {
	public InsideMarkerBox(final BlockParams params, final InlinePos pos) {
		super(params, pos);
	}
}
