package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;

/**
 * {@link StyleBoxEmitter}が必要とするスタイル構築状態への狭い窓口です
 * (StyleBuilder解体・増分4a、2026-07-30)。状態の物理置き場は
 * 当面StyleBuilderのままで、契約だけを型にする。
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

	/** 保留中のリストマーカーの出力(増分5領域への唯一の接点)。 */
	void checkMarker();
}
