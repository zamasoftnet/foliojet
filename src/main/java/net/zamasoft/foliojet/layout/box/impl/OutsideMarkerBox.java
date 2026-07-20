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

	public OutsideMarkerBox(BlockParams params, InlinePos pos) {
		super(params, pos);
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.textIndent = Length.ZERO_LENGTH;
	}

	public void firstPassLayout(AbstractContainerBox containerBox) {
		super.firstPassLayout(containerBox);
		if (this.params.flow.isVertical()) {
			this.height = 0;
		} else {
			this.width = 0;
		}
	}

	public void shrinkToFit(LayoutStack builder, IntrinsicSizes sizes, boolean table) {
		super.shrinkToFit(builder, sizes, table);
		this.lineAxis = sizes.maxContent();
		final AbstractContainerBox containerBox = builder.getFlowBox();
		if (this.params.flow.isVertical()) {
			this.lineAxis += containerBox.getFrame().getFrameTop();
			this.height = 0;
		} else {
			this.lineAxis += containerBox.getFrame().getFrameLeft();
			this.width = 0;
		}
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (this.params.flow.isVertical()) {
			y -= this.lineAxis;
		} else {
			x -= this.lineAxis;
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}
}
