package net.zamasoft.foliojet.layout.builder.impl;

import java.util.function.Function;

import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.RectBorder;

/**
 * Collapsed-border edge selection (P2-3: §5.2b table-builder unification). Four projections from
 * physical borders (RectBorder) to the edges used for the start/end of grid H borders
 * (perpendicular to row progression) and the start/end of V borders. Vertical and horizontal
 * writing differ only in this projection within the collapse logic (shared by both TwoPass
 * batch application and OnePass streaming accumulation).
 */
record BorderAxes(Function<RectBorder, Border> hStart, Function<RectBorder, Border> hEnd,
		Function<RectBorder, Border> vStart, Function<RectBorder, Border> vEnd) {
	static final BorderAxes VERTICAL = new BorderAxes(RectBorder::getRight, RectBorder::getLeft, RectBorder::getTop,
			RectBorder::getBottom);
	/** vertical-lr, whose rows progress to the right (2026-10-28; it took the vertical-rl sides before). */
	static final BorderAxes VERTICAL_LR = new BorderAxes(RectBorder::getLeft, RectBorder::getRight, RectBorder::getTop,
			RectBorder::getBottom);

	static BorderAxes of(final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		return switch (flow) {
		case RL -> VERTICAL;
		case LR -> VERTICAL_LR;
		case TB -> HORIZONTAL;
		};
	}
	static final BorderAxes HORIZONTAL = new BorderAxes(RectBorder::getTop, RectBorder::getBottom, RectBorder::getLeft,
			RectBorder::getRight);
}
