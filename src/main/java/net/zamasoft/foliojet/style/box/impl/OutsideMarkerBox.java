package net.zamasoft.foliojet.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.InlinePos;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.style.builder.LayoutStack;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;

public class OutsideMarkerBox extends InlineBlockBox {
	private double lineAxis;

	public OutsideMarkerBox(BlockParams params, InlinePos pos) {
		super(params, pos);
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.textIndent = Length.ZERO_LENGTH;
	}

	public void firstPassLayout(AbstractContainerBox containerBox) {
		super.firstPassLayout(containerBox);
		if (StyleUtils.isVertical(this.params.flow)) {
			this.height = 0;
		} else {
			this.width = 0;
		}
	}

	public void shrinkToFit(LayoutStack builder, double minLineAxis, double maxLineAxis, boolean table) {
		super.shrinkToFit(builder, minLineAxis, maxLineAxis, table);
		this.lineAxis = maxLineAxis;
		final AbstractContainerBox containerBox = builder.getFlowBox();
		if (StyleUtils.isVertical(this.params.flow)) {
			this.lineAxis += containerBox.getFrame().getFrameTop();
			this.height = 0;
		} else {
			this.lineAxis += containerBox.getFrame().getFrameLeft();
			this.width = 0;
		}
	}

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (StyleUtils.isVertical(this.params.flow)) {
			y -= this.lineAxis;
		} else {
			x -= this.lineAxis;
		}
		super.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
	}
}
