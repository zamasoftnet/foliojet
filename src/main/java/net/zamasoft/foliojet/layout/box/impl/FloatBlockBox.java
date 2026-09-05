package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FloatBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FloatBlockBox extends AbstractStaticBlockBox implements IFloatBox {
	protected final FloatPos pos;
	/**
	 * 改ページ分割の続き断片か(2026-08-29)。{@code shape-outside}の解決
	 * ({@code FloatShapeResolver})が、元の寸法・位置を持たない続き断片で
	 * 形状をマージンボックス矩形へ退避するために使う。続き断片は
	 * {@link #fragmentRecipe}経由の保護コンストラクタでしか作られない。
	 */
	private final boolean continuation;

	public FloatBlockBox(final BlockParams params, final FloatPos pos) {
		super(params);
		this.pos = pos;
		this.continuation = false;
	}

	protected FloatBlockBox(final BlockParams params, final FloatPos pos, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
		this.continuation = true;
	}

	public final boolean isContinuationFragment() {
		return this.continuation;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbstractStaticPos getStaticPos() {
		return this.pos;
	}

	public final FloatPos getFloatPos() {
		return this.pos;
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		this.frames(pageBox, drawer, clip, transform, x, y);
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			// 負の z-index の子はここまで(自分の背景・枠)の後、残りの内容の前に描く(Appendix E ③)
			drawer.markOwnDecorationEnd();
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FloatPos pos = this.getFloatPos();
		return (state, container) -> new FloatBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
