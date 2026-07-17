package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: InlineBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class InlineBlockBox extends AbstractStaticBlockBox implements IInlineBox {
	protected final InlinePos pos;

	public InlineBlockBox(BlockParams params, InlinePos pos) {
		super(params);
		this.pos = pos;
	}

	protected InlineBlockBox(BlockParams params, InlinePos pos, Dimension nextSize, Dimension nextMinSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, nextSize, nextMinSize, frame, container);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbstractStaticPos getStaticPos() {
		return this.pos;
	}

	public final InlinePos getInlinePos() {
		return this.pos;
	}

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		this.frames(pageBox, drawer, clip, transform, x, y);
		super.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final InlinePos pos = this.pos;
		return (state, container) -> new InlineBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
