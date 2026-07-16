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
	 * 幅が決まっているフローを返します。
	 * 
	 * @return
	 */
	public AbstractContainerBox getFixedWidthFlowBox();

	public AbstractContainerBox getFixedHeightFlowBox();

	/**
	 * 幅が決まっているコンテキストルートを返します。
	 * 
	 * @return
	 */
	public AbstractContainerBox getFixedWidthContextBox();

	public AbstractContainerBox getFixedHeightContextBox();
}
