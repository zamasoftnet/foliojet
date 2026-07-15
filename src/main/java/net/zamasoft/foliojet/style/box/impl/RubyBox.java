package net.zamasoft.foliojet.style.box.impl;

import net.zamasoft.foliojet.style.box.BoxSubtype;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.content.FlowContainer;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.InlinePos;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.style.util.StyleUtils;

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
			return StyleUtils.NONE;
		}
		if (flow.box.getType() != BoxType.BLOCK) {
			return super.getLastDescent();
		}

		final AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
		final double firstAscent = containerBox.getFirstAscent();
		if (StyleUtils.isNone(firstAscent)) {
			return firstAscent;
		}
		double descent = firstAscent;
		descent += flow.pageAxis;

		switch (this.box.getBlockParams().flow) {
		case AbstractTextParams.FLOW_TB:
			// 横書き
			descent = this.box.getInnerHeight() - descent;
			descent += this.box.getFrame().getFrameBottom();
			break;
		case AbstractTextParams.FLOW_RL:
			// 縦書き(日本)
			descent = this.box.getInnerWidth() - descent;
			descent += this.box.getFrame().getFrameLeft();
			break;
		case AbstractTextParams.FLOW_LR:
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