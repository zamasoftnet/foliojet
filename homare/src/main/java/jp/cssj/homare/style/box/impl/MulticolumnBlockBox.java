package jp.cssj.homare.style.box.impl;

import jp.cssj.homare.style.box.AbstractBlockBox;
import jp.cssj.homare.style.box.content.Container;
import jp.cssj.homare.style.box.params.BlockParams;
import jp.cssj.homare.style.box.params.Dimension;
import jp.cssj.homare.style.box.params.FlowPos;
import jp.cssj.homare.style.part.AbsoluteRectFrame;
import jp.cssj.homare.style.util.StyleUtils;

public class MulticolumnBlockBox extends FlowBlockBox {
	public MulticolumnBlockBox(BlockParams params, FlowPos pos) {
		super(params, pos);
	}

	protected MulticolumnBlockBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	public int getColumnCount() {
		return StyleUtils.getColumnCount(this);
	}

	protected final AbstractBlockBox splitPage(Dimension nextSize, Dimension nextMinSize, AbsoluteRectFrame nextFrame,
			Container container) {
		final BlockParams params = this.getBlockParams();
		return new MulticolumnBlockBox(params, this.getFlowPos(), nextSize, nextMinSize, nextFrame, container);
	}
}
