package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.BoxSubtype;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;

public class RubyBodyBox extends FlowBlockBox {
	public RubyBodyBox(BlockParams params, FlowPos pos) {
		super(params, pos);
	}

	public BoxSubtype getSubtype() {
		return BoxSubtype.RUBY_BODY;
	}

	protected RubyBodyBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new RubyBodyBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
