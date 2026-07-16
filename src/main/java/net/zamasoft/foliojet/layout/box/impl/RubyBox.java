package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.box.BoxSubtype;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

public class RubyBox extends InlineBlockBox {
	public RubyBox(BlockParams params, InlinePos pos) {
		super(params, pos, params.size, params.minSize, new AbsoluteRectFrame(params.frame), new RubyContainer());
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.textIndent = Length.ZERO_LENGTH;
		params.lineHeight = this.params.fontStyle.getSize();
	}

	public BoxSubtype getSubtype() {
		return BoxSubtype.RUBY;
	}

	public void setPageAxis(double newSize) {
		newSize = Math.max(newSize, ((RubyContainer) this.container).lineHeight());
		super.setPageAxis(newSize);
	}
}

class RubyContainer extends FlowContainer {

	double lineHeight() {
		final Flow flow = this.getFirstFlow();
		if (flow == null) {
			return 0;
		}
		final AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
		return containerBox.getFirstAscent() + containerBox.getLastDescent();
	}

	public double getLastDescent() {
		final Flow flow = this.getFirstFlow();
		if (flow == null) {
			return LayoutUtils.NONE;
		}
		if (flow.box.getType() != BoxType.BLOCK) {
			return super.getLastDescent();
		}

		final AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
		final double firstAscent = containerBox.getFirstAscent();
		if (LayoutUtils.isNone(firstAscent)) {
			return firstAscent;
		}
		double descent = firstAscent;
		descent += flow.pageAxis;

		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			descent = this.box.getInnerHeight() - descent;
			descent += this.box.getFrame().getFrameBottom();
			break;
		case WritingMode.RL:
			// 縦書き(日本)
			descent = this.box.getInnerWidth() - descent;
			descent += this.box.getFrame().getFrameLeft();
			break;
		case WritingMode.LR:
			// 縦書き(モンゴル)
			descent = this.box.getInnerWidth() - descent;
			descent += this.box.getFrame().getFrameRight();
			break;
		default:
			throw new IllegalStateException();
		}
		return descent;
	}
}