package jp.cssj.homare.style.part;

import java.awt.Shape;

import jp.cssj.homare.style.box.params.RectFrame;
import jp.cssj.homare.style.util.StyleUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteRectFrame.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class AbsoluteRectFrame {
	public RectFrame frame;

	public AbsoluteInsets margin;

	public AbsoluteInsets padding;

	public AbsoluteRectFrame(RectFrame frame) {
		this(frame, new AbsoluteInsets(), new AbsoluteInsets());
	}

	protected AbsoluteRectFrame(RectFrame frame, AbsoluteInsets margin, AbsoluteInsets padding) {
		this.frame = frame;
		this.margin = margin;
		this.padding = padding;
	}

	public final double getFrameTop() {
		return this.margin.top + this.frame.border.getTop().width + this.padding.top;
	}

	public final double getFrameLeft() {
		return this.margin.left + this.frame.border.getLeft().width + this.padding.left;
	}

	public final double getFrameBottom() {
		return this.padding.bottom + this.frame.border.getBottom().width + this.margin.bottom;
	}

	public final double getFrameRight() {
		return this.padding.right + this.frame.border.getRight().width + this.margin.right;
	}

	public final double getFrameHeight() {
		return this.getFrameTop() + this.getFrameBottom();
	}

	public final double getFrameWidth() {
		return this.getFrameLeft() + this.getFrameRight();
	}

	public final double getBorderHeight() {
		return this.frame.border.getTop().width + this.padding.top + this.frame.border.getBottom().width
				+ this.padding.bottom;
	}

	public final double getBorderWidth() {
		return this.frame.border.getLeft().width + this.padding.left + this.frame.border.getRight().width
				+ this.padding.right;
	}

	public boolean isVisible() {
		return this.frame.isVisible();
	}

	public void draw(GC gc, double x, double y, double width, double height, Shape textClip) throws GraphicsException {
		assert !StyleUtils.isNone(x) : "Undefined x";
		assert !StyleUtils.isNone(y) : "Undefined y";
		assert !StyleUtils.isNone(width) : "Undefined width";
		assert !StyleUtils.isNone(height) : "Undefined height";
		x += this.margin.left;
		y += this.margin.top;
		width -= this.margin.getFrameWidth();
		height -= this.margin.getFrameHeight();
		this.frame.background.draw(gc, x, y, width, height, this.frame.border, this.frame.padding, textClip);
		this.frame.border.draw(gc, x, y, width, height);
	}

	public AbsoluteRectFrame cut(boolean top, boolean right, boolean bottom, boolean left) {
		return new AbsoluteRectFrame(this.frame.cut(top, right, bottom, left),
				this.margin.cut(top, right, bottom, left), this.padding.cut(top, right, bottom, left));
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + ",margin=" + this.margin + ",padding=" + this.padding + "]";
	}
}
