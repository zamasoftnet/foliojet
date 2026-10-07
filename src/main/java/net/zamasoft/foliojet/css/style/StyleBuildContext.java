package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;

/**
 * A narrow interface to the style-building state needed by {@link StyleBoxEmitter}
 * (StyleBuilder decomposition, increment 4a, 2026-07-30). State remains physically in
 * StyleBuilder for now; only the contract becomes a type.
 */
interface StyleBuildContext {
	CSSStyle getCurrentStyle();

	void setCurrentStyle(CSSStyle style);

	FlowBlockBox getHtmlRootBlock();

	void setHtmlRootBlock(FlowBlockBox box);

	boolean isInBody();

	void setInBody(boolean inBody);

	boolean isInTextBlock();

	void setInTextBlock(boolean inTextBlock);

	boolean isRightSide();

	void setRightSide(boolean rightSide);

	/** Outputs the pending list marker (the sole connection to the increment 5 area). */
	void checkMarker();
}
