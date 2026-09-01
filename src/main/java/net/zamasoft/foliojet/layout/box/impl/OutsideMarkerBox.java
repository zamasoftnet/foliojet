package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public class OutsideMarkerBox extends InlineBlockBox {
	private double lineAxis;

	/**
	 * 行が縦組みか。<b>マーカー自身の{@code params.flow}では決められない</b>
	 * (2026-09-01)。{@code ::marker}へ{@code text-combine-upright: all}を掛けると
	 * {@code TextCombineShorthand}が{@code block-flow}を横組みへ展開するので、
	 * 縦組みのページでもマーカーのflowだけが{@code TB}になる。それを軸の判定に
	 * 使うと、送りをゼロにする軸も、描画でずらす軸も入れ替わる——縦組みの
	 * 番号付きリストで数字が行から外れて出る原因だった。
	 * 判定は<b>マーカーを含む箱</b>の書字方向で行い、その結果をここへ持つ。
	 */
	private boolean verticalLine;

	/**
	 * 表がlist-itemの先頭子であるため、表の外で先行出力されたマーカーか。
	 * この場合のマーカー専用行は表の先頭位置へ重ね、通常フローを進めない。
	 */
	private boolean overlaysFollowingBlock;

	public OutsideMarkerBox(BlockParams params, InlinePos pos) {
		super(params, pos);
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.textIndent = Length.ZERO_LENGTH;
	}

	public final void setOverlaysFollowingBlock(final boolean overlaysFollowingBlock) {
		this.overlaysFollowingBlock = overlaysFollowingBlock;
	}

	public final boolean overlaysFollowingBlock() {
		return this.overlaysFollowingBlock;
	}

	public void firstPassLayout(AbstractContainerBox containerBox) {
		super.firstPassLayout(containerBox);
		this.verticalLine = containerBox.getBlockParams().flow.isVertical();
		// **ずらす軸は行(=含む箱)、ゼロにする成分はマーカー自身の書字方向**
		// で決まる。縦中横のマーカーは縦組みの行の中で横に組まれるので、
		// 行方向の送りはマーカーの`width`が担う。
		if (this.params.flow.isVertical()) {
			this.height = 0;
		} else {
			this.width = 0;
		}
	}

	public void shrinkToFit(LayoutStack builder, IntrinsicSizes sizes, boolean table) {
		super.shrinkToFit(builder, sizes, table);
		this.lineAxis = sizes.maxContent();
		if (this.params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL) {
			// 縦中横(all)は1emのセルへ収まる(css-writing-modes-3 §9.1)。
			// 送りは`startInline`が圧縮した後の1emなので、圧縮前の自然幅で
			// ずらすと3桁のマーカーだけ0.5em行方向へ浮く
			this.lineAxis = Math.min(this.lineAxis, this.params.fontStyle.getSize());
		}
		final AbstractContainerBox containerBox = builder.getFlowBox();
		this.verticalLine = containerBox.getBlockParams().flow.isVertical();
		if (this.verticalLine) {
			this.lineAxis += containerBox.getFrame().getFrameTop();
		} else {
			this.lineAxis += containerBox.getFrame().getFrameLeft();
		}
		if (this.params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL) {
			// **送りをゼロにする前に1emセルへ収める**。`startInline`の圧縮は
			// 自然幅(`width`)を見るので、先にゼロにすると早期に戻って
			// 圧縮が走らない——3桁のマーカーが1emに収まらない原因だった。
			this.compressTextCombine(this.params.fontStyle.getSize(), null);
		}
		if (this.params.flow.isVertical()) {
			this.height = 0;
		} else {
			this.width = 0;
		}
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (this.verticalLine) {
			y -= this.lineAxis;
		} else {
			x -= this.lineAxis;
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}
}
