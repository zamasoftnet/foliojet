package jp.cssj.homare.style.box.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.homare.style.box.AbstractBlockBox;
import jp.cssj.homare.style.box.IAbsoluteBox;
import jp.cssj.homare.style.box.IBox;
import jp.cssj.homare.style.box.IFloatBox;
import jp.cssj.homare.style.box.IPageBreakableBox;
import jp.cssj.homare.style.box.content.BreakMode;
import jp.cssj.homare.style.box.content.Container;
import jp.cssj.homare.style.box.content.FlowContainer;
import jp.cssj.homare.style.box.params.AbsolutePos;
import jp.cssj.homare.style.box.params.AbstractTextParams;
import jp.cssj.homare.style.box.params.BlockParams;
import jp.cssj.homare.style.box.params.Dimension;
import jp.cssj.homare.style.box.params.Insets;
import jp.cssj.homare.style.box.params.PagePos;
import jp.cssj.homare.style.box.params.Pos;
import jp.cssj.homare.style.box.params.RectFrame;
import jp.cssj.homare.style.builder.impl.BlockBuilder;
import jp.cssj.homare.style.draw.Drawer;
import jp.cssj.homare.style.part.AbsoluteRectFrame;
import jp.cssj.homare.style.util.StyleUtils;
import jp.cssj.homare.style.visitor.Visitor;
import jp.cssj.homare.ua.UserAgent;

/**
 * ページです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBox.java 1561 2018-07-04 11:44:21Z miyabe $
 */
public class PageBox extends AbstractBlockBox {
	protected final UserAgent ua;

	/**
	 * 固定配置ブロックです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: PageBox.java 1561 2018-07-04 11:44:21Z miyabe $
	 */
	protected static class Fixed {
		public final IAbsoluteBox box;
		public final double x, y;

		public Fixed(IAbsoluteBox box, double x, double y) {
			this.box = box;
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * 固定位置指定されたコンテンツ。
	 */
	protected List<Fixed> fixeds = null;

	protected List<Fixed> toAddFixeds = null;

	protected List<IAbsoluteBox> pageContents = null;
	
	/**
	 * 表示上のサイズ。
	 */
	protected double visualWidth = 0, visualHeight = 0;

	public PageBox(BlockParams params, UserAgent ua) {
		this(params, ua, new FlowContainer());
	}

	public PageBox(BlockParams params, UserAgent ua, Container container) {
		super(params, params.size, params.minSize, new AbsoluteRectFrame(params.frame), container);
		assert this.size.getWidthType() != Dimension.TYPE_RELATIVE;
		assert this.size.getHeightType() != Dimension.TYPE_RELATIVE;

		this.ua = ua;

		double lineWidth;
		switch (params.flow) {
		case AbstractTextParams.FLOW_TB:
			// 横書き
			assert this.size.getWidthType() == Dimension.TYPE_ABSOLUTE;
			lineWidth = this.size.getWidth();
			break;
		case AbstractTextParams.FLOW_LR:
		case AbstractTextParams.FLOW_RL:
			// 縦書き
			assert this.size.getHeightType() == Dimension.TYPE_ABSOLUTE;
			lineWidth = this.size.getHeight();
			break;
		default:
			throw new IllegalStateException();
		}

		RectFrame frame = this.frame.frame;
		{
			Insets insets = frame.margin;
			double top, right, bottom, left;
			switch (insets.getTopType()) {
			case Insets.TYPE_ABSOLUTE:
				top = insets.getTop();
				break;
			case Insets.TYPE_RELATIVE:
				top = insets.getTop() * lineWidth;
				break;
			case Insets.TYPE_AUTO:
				top = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getBottomType()) {
			case Insets.TYPE_ABSOLUTE:
				bottom = insets.getBottom();
				break;
			case Insets.TYPE_RELATIVE:
				bottom = insets.getBottom() * lineWidth;
				break;
			case Insets.TYPE_AUTO:
				bottom = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getLeftType()) {
			case Insets.TYPE_ABSOLUTE:
				left = insets.getLeft();
				break;
			case Insets.TYPE_RELATIVE:
				left = insets.getLeft() * lineWidth;
				break;
			case Insets.TYPE_AUTO:
				left = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getRightType()) {
			case Insets.TYPE_ABSOLUTE:
				right = insets.getRight();
				break;
			case Insets.TYPE_RELATIVE:
				right = insets.getRight() * lineWidth;
				break;
			case Insets.TYPE_AUTO:
				right = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			this.frame.margin.top = top;
			this.frame.margin.right = right;
			this.frame.margin.bottom = bottom;
			this.frame.margin.left = left;
			if (this.size.getWidthType() == Dimension.TYPE_ABSOLUTE) {
				this.visualWidth = this.width = this.size.getWidth() - this.frame.getFrameWidth();
			}
			if (this.size.getHeightType() == Dimension.TYPE_ABSOLUTE) {
				this.visualHeight = this.height = this.size.getHeight() - this.frame.getFrameHeight();
			}
		}
	}

	public final byte getType() {
		return TYPE_PAGE;
	}

	public final Pos getPos() {
		return PagePos.POS;
	}

	public final UserAgent getUserAgent() {
		return this.ua;
	}

	public final boolean isSpecifiedPageSize() {
		return false;
	}

	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		throw new UnsupportedOperationException();
	}

	public final void setPageAxis(final double newSize) {
		assert !StyleUtils.isNone(newSize);
		final BlockParams params = this.getBlockParams();
		switch (params.flow) {
		case AbstractTextParams.FLOW_TB: {
			// 横書き
			this.visualHeight = Math.max(this.visualHeight, newSize);
			if (this.size.getHeightType() != Dimension.TYPE_AUTO || newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
			break;
		case AbstractTextParams.FLOW_LR:
		case AbstractTextParams.FLOW_RL: {
			// 縦書き
			this.visualWidth = Math.max(this.visualWidth, newSize);
			if (this.size.getWidthType() != Dimension.TYPE_AUTO || newSize <= this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}
	
	public double getVisualWidth() {
		return this.visualWidth + this.frame.getFrameWidth();
	}

	public double getVisualHeight() {
		return this.visualHeight + this.frame.getFrameHeight();
	}

	public final void addFixed(Drawer drawer, Visitor visitor, IAbsoluteBox box, double x, double y) {
		AbsolutePos pos = box.getAbsolutePos();
		if (pos.location.getLeftType() != Insets.TYPE_AUTO || pos.location.getRightType() != Insets.TYPE_AUTO) {
			x = 0;
		}
		if (pos.location.getTopType() != Insets.TYPE_AUTO || pos.location.getBottomType() != Insets.TYPE_AUTO) {
			y = 0;
		}
		box.finishLayout(this);
		Fixed fixed = new Fixed(box, x, y);
		if (this.toAddFixeds == null) {
			this.toAddFixeds = new ArrayList<Fixed>();
		}
		this.toAddFixeds.add(fixed);

		x = this.offsetX + this.frame.getFrameLeft() - this.frame.margin.left;
		y = this.offsetY + this.frame.getFrameTop() - this.frame.margin.top;
		fixed.box.draw(this, drawer, visitor, null, new AffineTransform(), x, y, fixed.x, fixed.y);
	}

	public final void addPageContent(IAbsoluteBox box) {
		if (this.pageContents == null) {
			this.pageContents = new ArrayList<IAbsoluteBox>();
		}
		this.pageContents.add(box);
	}

	public final boolean isContextBox() {
		return true;
	}

	public final IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, byte flags) {
		throw new UnsupportedOperationException();
	}

	protected final AbstractBlockBox splitPage(Dimension nextSize, Dimension nextMinSize, AbsoluteRectFrame nextFrame,
			Container container) {
		return new PageBox(this.params, this.ua, container);
	}

	public final void drawFlow(Drawer drawer, Visitor visitor) {
		double x = -this.frame.margin.left;
		double y = -this.frame.margin.top;
		this.frames(this, drawer, null, new AffineTransform(), x, y);
		this.draw(this, drawer, visitor, null, new AffineTransform(), x, y, x, y);
	}

	public final void drawFixed(Drawer drawer, Visitor visitor) {
		double x = this.offsetX + this.frame.getFrameLeft() - this.frame.margin.left;
		double y = this.offsetY + this.frame.getFrameTop() - this.frame.margin.top;
		if (this.fixeds != null) {
			for (int i = 0; i < this.fixeds.size(); ++i) {
				Fixed c = (Fixed) this.fixeds.get(i);
				c.box.draw(this, drawer, visitor, null, new AffineTransform(), x, y, c.x, c.y);
			}
		}
		if (this.toAddFixeds != null && !this.toAddFixeds.isEmpty()) {
			if (this.fixeds == null) {
				this.fixeds = new ArrayList<Fixed>();
			}
			this.fixeds.addAll(this.toAddFixeds);
			this.toAddFixeds.clear();
		}
	}

	public final void drawPageContents(Drawer drawer, Visitor visitor) {
		if (this.pageContents == null) {
			return;
		}
		double x = this.offsetX + this.frame.getFrameLeft() - this.frame.margin.left;
		double y = this.offsetY + this.frame.getFrameTop() - this.frame.margin.top;
		for (int i = 0; i < this.pageContents.size(); ++i) {
			IBox box = (IBox) this.pageContents.get(i);
			box.draw(this, drawer, visitor, null, new AffineTransform(), x, y, x, y);
		}
	}

	public final void restyle(BlockBuilder builder, int depth) {
		if (this.fixeds == null) {
			return;
		}
		for (int i = 0; i < this.fixeds.size(); ++i) {
			Fixed fixed = (Fixed) this.fixeds.get(i);
			builder.addBound(fixed.box);
		}
	}
}
