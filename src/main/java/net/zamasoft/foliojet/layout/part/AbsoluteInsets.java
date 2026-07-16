package net.zamasoft.foliojet.layout.part;

import net.zamasoft.foliojet.layout.util.StyleUtils;

public class AbsoluteInsets {
	public double top;
	public double right;
	public double bottom;
	public double left;

	public AbsoluteInsets() {
		// do nothing
	}

	public AbsoluteInsets(double top, double right, double bottom, double left) {
		assert !StyleUtils.isNone(top);
		assert !StyleUtils.isNone(right);
		assert !StyleUtils.isNone(bottom);
		assert !StyleUtils.isNone(left);
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
	}

	public double getFrameWidth() {
		return this.left + this.right;
	}

	public double getFrameHeight() {
		return this.top + this.bottom;
	}

	public boolean isEmpty() {
		return this.top == 0 && this.right == 0 && this.bottom == 0 && this.left == 0;
	}

	public void set(AbsoluteInsets insets) {
		this.top = insets.top;
		this.right = insets.right;
		this.bottom = insets.bottom;
		this.left = insets.left;
	}

	public AbsoluteInsets cut(boolean top, boolean right, boolean bottom, boolean left) {
		AbsoluteInsets insets = this.isEmpty() ? this
				: new AbsoluteInsets(top ? this.top : 0, right ? this.right : 0, bottom ? this.bottom : 0,
						left ? this.left : 0);
		return insets;
	}

	public String toString() {
		return super.toString() + "[top=" + this.top + ",right=" + this.right + ",bottom=" + this.bottom + ",left="
				+ this.left + "]";
	}
}
