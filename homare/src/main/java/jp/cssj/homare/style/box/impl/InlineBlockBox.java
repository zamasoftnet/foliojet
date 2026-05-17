package jp.cssj.homare.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import jp.cssj.homare.style.box.AbstractBlockBox;
import jp.cssj.homare.style.box.AbstractStaticBlockBox;
import jp.cssj.homare.style.box.IInlineBox;
import jp.cssj.homare.style.box.content.Container;
import jp.cssj.homare.style.box.params.AbstractStaticPos;
import jp.cssj.homare.style.box.params.BlockParams;
import jp.cssj.homare.style.box.params.Dimension;
import jp.cssj.homare.style.box.params.InlinePos;
import jp.cssj.homare.style.box.params.Params;
import jp.cssj.homare.style.box.params.Pos;
import jp.cssj.homare.style.draw.Drawer;
import jp.cssj.homare.style.part.AbsoluteRectFrame;
import jp.cssj.homare.style.visitor.Visitor;

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

	protected final AbstractBlockBox splitPage(Dimension nextSize, Dimension nextMinSize, AbsoluteRectFrame nextFrame,
			Container container) {
		return new InlineBlockBox(params, this.pos, nextSize, nextMinSize, nextFrame, container);
	}
}
