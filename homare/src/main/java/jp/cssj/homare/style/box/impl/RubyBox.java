package jp.cssj.homare.style.box.impl;

import jp.cssj.homare.style.box.AbstractContainerBox;
import jp.cssj.homare.style.box.IBox;
import jp.cssj.homare.style.box.content.FlowContainer;
import jp.cssj.homare.style.box.params.AbstractTextParams;
import jp.cssj.homare.style.box.params.BlockParams;
import jp.cssj.homare.style.box.params.InlinePos;
import jp.cssj.homare.style.box.params.Length;
import jp.cssj.homare.style.part.AbsoluteRectFrame;
import jp.cssj.homare.style.util.StyleUtils;

public class RubyBox extends InlineBlockBox {
	public RubyBox(BlockParams params, InlinePos pos) {
		super(params, pos, params.size, params.minSize, new AbsoluteRectFrame(params.frame), new RubyContainer());
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.textIndent = Length.ZERO_LENGTH;
		params.lineHeight = this.params.fontStyle.getSize();
	}

	public byte getSubtype() {
		return SUBTYPE_RUBY;
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
		if (flow.box.getType() != IBox.TYPE_BLOCK) {
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