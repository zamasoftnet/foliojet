package net.zamasoft.foliojet.style.box.impl;

import net.zamasoft.foliojet.style.box.AbstractBlockBox;
import net.zamasoft.foliojet.style.box.content.Container;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.FlowPos;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;

public class RubyBodyBox extends FlowBlockBox {
	public RubyBodyBox(BlockParams params, FlowPos pos) {
		super(params, pos);
	}

	public byte getSubtype() {
		return SUBTYPE_RUBY_BODY;
	}

	protected RubyBodyBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	protected final AbstractBlockBox splitPage(Dimension nextSize, Dimension nextMinSize, AbsoluteRectFrame nextFrame,
			Container container) {
		final BlockParams params = this.getBlockParams();
		return new RubyBodyBox(params, this.getFlowPos(), nextSize, nextMinSize, nextFrame, container);
	}
}
