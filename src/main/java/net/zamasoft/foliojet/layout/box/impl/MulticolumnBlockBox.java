package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

public class MulticolumnBlockBox extends FlowBlockBox {
	public MulticolumnBlockBox(BlockParams params, FlowPos pos) {
		super(params, pos);
	}

	protected MulticolumnBlockBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	public int getColumnCount() {
		return LayoutUtils.getColumnCount(this);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new MulticolumnBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
