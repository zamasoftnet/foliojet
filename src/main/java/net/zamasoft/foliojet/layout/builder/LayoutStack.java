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
	/**
	 * <b>直交フローの線軸(inline)の百分率の基準</b>です(2026-09-16 新設)。
	 *
	 * <p>
	 * {@link #getFixedWidth()}/{@link #getFixedHeight()} は「明示寸法を持つ祖先」を
	 * 遡る仕組みなので、縦組み文書の中の横組みの箱のように該当が無いと <b>0</b> を返す。
	 * 0 を基準にすると {@code max-width: 90%} が 0 になり、幅 0 の箱から内容が
	 * <b>紙面外へあふれる</b>(掃過の「全描画が紙面外」)。用紙の寸法は確定値なので、
	 * css-writing-modes-4 §7.3 のとおりフラグメンテナ(ページ)の内容域を最後の基準にする。
	 * </p>
	 *
	 * <p>
	 * <b>この判断はここだけに置く。</b>同じ退避を各所で書くと、直したはずの軸が
	 * 別経路で 0 に戻る(実際、表の寸法解決だけ直しても
	 * {@code AbstractStaticBlockBox} の fit-content 側が 0 のままだった)。
	 * </p>
	 *
	 * @param flow 基準を測る軸を決める書字方向(その箱自身の書字方向)
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
