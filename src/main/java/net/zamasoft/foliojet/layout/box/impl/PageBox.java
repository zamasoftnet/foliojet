package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.PagePos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.UserAgent;

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

	/**
	 * 表示上のサイズ。
	 */
	protected double visualWidth = 0, visualHeight = 0;

	public PageBox(BlockParams params, UserAgent ua) {
		this(params, ua, new FlowContainer());
	}

	public PageBox(BlockParams params, UserAgent ua, Container container) {
		super(params, params.size, params.minSize, new AbsoluteRectFrame(params.frame), container);
		assert this.size.getWidthType() != LengthType.RELATIVE;
		assert this.size.getHeightType() != LengthType.RELATIVE;

		this.ua = ua;

		double lineWidth;
		switch (params.flow) {
		case WritingMode.TB:
			// 横書き
			assert this.size.getWidthType() == LengthType.ABSOLUTE;
			lineWidth = this.size.getWidth();
			break;
		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			assert this.size.getHeightType() == LengthType.ABSOLUTE;
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
			case ABSOLUTE:
				top = insets.getTop();
				break;
			case RELATIVE:
				top = insets.getTop() * lineWidth;
				break;
			case AUTO:
				top = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getBottomType()) {
			case ABSOLUTE:
				bottom = insets.getBottom();
				break;
			case RELATIVE:
				bottom = insets.getBottom() * lineWidth;
				break;
			case AUTO:
				bottom = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getLeftType()) {
			case ABSOLUTE:
				left = insets.getLeft();
				break;
			case RELATIVE:
				left = insets.getLeft() * lineWidth;
				break;
			case AUTO:
				left = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getRightType()) {
			case ABSOLUTE:
				right = insets.getRight();
				break;
			case RELATIVE:
				right = insets.getRight() * lineWidth;
				break;
			case AUTO:
				right = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			this.frame.margin.top = top;
			this.frame.margin.right = right;
			this.frame.margin.bottom = bottom;
			this.frame.margin.left = left;
			if (this.size.getWidthType() == LengthType.ABSOLUTE) {
				this.visualWidth = this.width = this.size.getWidth() - this.frame.getFrameWidth();
			}
			if (this.size.getHeightType() == LengthType.ABSOLUTE) {
				this.visualHeight = this.height = this.size.getHeight() - this.frame.getFrameHeight();
			}
		}
	}

	public final BoxType getType() {
		return BoxType.PAGE;
	}

	public final Pos getPos() {
		return PagePos.POS;
	}

	public final UserAgent getUserAgent() {
		return this.ua;
	}

	/** Elements whose tagged-PDF structure element is currently open. */
	private final java.util.Set<Object> openStructElements = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());

	/**
	 * Inserts tagged-PDF structure begin markers for a box's element and
	 * returns how many were opened (0 when tagging is off, the element is not
	 * mappable, or the same element is already open — the outer box owns it).
	 * A list item additionally opens an {@code LBody} wrapper.
	 *
	 * @param drawer  the drawer to add markers to
	 * @param element the box's element
	 * @param x       the box x position
	 * @param y       the box y position
	 * @return the number of structure elements opened (to pass to
	 *         {@link #endStruct})
	 */
	public int beginStruct(final Drawer drawer, final Object element, final double x, final double y) {
		final String role = net.zamasoft.foliojet.ua.props.TaggedPdf.roleIfActive(this.ua, element);
		if (role == null || !this.openStructElements.add(element)) {
			return 0;
		}
		if (role.equals("TH")) {
			// PDF/UA: a header cell needs a Scope when the table has no
			// Headers/IDs association.
			drawer.visitDrawable(
					net.zamasoft.foliojet.layout.draw.StructDrawable.begin("TH", net.zamasoft.foliojet.ua.props.TaggedPdf
							.headerScope(element)),
					x, y);
			return 1;
		}
		drawer.visitDrawable(net.zamasoft.foliojet.layout.draw.StructDrawable.begin(role), x, y);
		if (role.equals("LI")) {
			// PDF/UA: an LI's content must sit in an LBody.
			drawer.visitDrawable(net.zamasoft.foliojet.layout.draw.StructDrawable.begin("LBody"), x, y);
			return 2;
		}
		return 1;
	}

	/**
	 * Closes the structure elements opened by a matching {@link #beginStruct}.
	 *
	 * @param drawer  the drawer to add markers to
	 * @param element the box's element
	 * @param count   the value returned by {@link #beginStruct}
	 * @param x       the box x position
	 * @param y       the box y position
	 */
	public void endStruct(final Drawer drawer, final Object element, final int count, final double x, final double y) {
		if (count == 0) {
			return;
		}
		for (int i = 0; i < count; ++i) {
			drawer.visitDrawable(net.zamasoft.foliojet.layout.draw.StructDrawable.end(), x, y);
		}
		this.openStructElements.remove(element);
	}

	public final boolean isSpecifiedPageSize() {
		return false;
	}

	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		throw new UnsupportedOperationException();
	}

	public final void setPageAxis(final double newSize) {
		assert !LayoutUtils.isNone(newSize);
		final BlockParams params = this.getBlockParams();
		switch (params.flow) {
		case WritingMode.TB: {
			// 横書き
			this.visualHeight = Math.max(this.visualHeight, newSize);
			if (this.size.getHeightType() != LengthType.AUTO || newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
			break;
		case WritingMode.LR:
		case WritingMode.RL: {
			// 縦書き
			this.visualWidth = Math.max(this.visualWidth, newSize);
			if (this.size.getWidthType() != LengthType.AUTO || newSize <= this.width) {
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
		if (pos.location.getLeftType() != LengthType.AUTO || pos.location.getRightType() != LengthType.AUTO) {
			x = 0;
		}
		if (pos.location.getTopType() != LengthType.AUTO || pos.location.getBottomType() != LengthType.AUTO) {
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

	public final boolean isContextBox() {
		return true;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		throw new UnsupportedOperationException();
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final net.zamasoft.foliojet.ua.UserAgent ua = this.ua;
		return (state, container) -> new PageBox(params, ua, container);
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

	public final void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (this.fixeds == null) {
			return;
		}
		for (int i = 0; i < this.fixeds.size(); ++i) {
			Fixed fixed = (Fixed) this.fixeds.get(i);
			builder.addBound(fixed.box);
		}
	}
}
