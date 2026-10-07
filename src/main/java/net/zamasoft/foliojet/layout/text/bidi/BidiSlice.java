package net.zamasoft.foliojet.layout.text.bidi;

import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.InlineBox;

/**
 * UBA metadata retained on the Folio side alongside shaped runs/clusters.
 * Keeps layout-specific information out of pdfg2d Text.
 */
public record BidiSlice(long paragraphId, int syntheticStart, int syntheticLimit, byte paragraphLevel,
		byte level, List<InlineBox> inlineAncestry) {
	public BidiSlice {
		inlineAncestry = List.copyOf(inlineAncestry);
	}
}
