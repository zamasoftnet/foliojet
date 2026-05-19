package net.zamasoft.foliojet.style.box.impl;

import net.zamasoft.foliojet.style.box.AbstractBlockBox;
import net.zamasoft.foliojet.style.box.content.Container;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.FlowPos;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.style.util.StyleUtils;

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
