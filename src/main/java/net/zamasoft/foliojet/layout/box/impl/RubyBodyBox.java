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
		// 解決済み整列はFlowBlockBox.fragmentRecipe()と同様、断片間で
		// 共有される状態としてキャプチャ・引き継ぐ(2026-07-21、B5設計相談で
		// 発見された欠落を修正——RubyBodyBoxはshrinkToFit(table=true)の
		// 対象にならないため現状resolvedAlignがコンストラクタ既定値から
        // 変化することはなく実害はなかったが、契約としては親クラスと
		// 揃えておくべき)。
		final net.zamasoft.foliojet.layout.box.params.Align resolvedAlign = this.resolvedAlign;
		return (state, container) -> {
			final RubyBodyBox next = new RubyBodyBox(params, pos, state.nextSize(), state.nextMinSize(),
					state.nextFrame(), container);
			next.resolvedAlign = resolvedAlign;
			return next;
		};
	}
}
