package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class RectValue implements Value {
	private final LengthValue top, right, bottom, left;

	public RectValue(LengthValue top, LengthValue right, LengthValue bottom, LengthValue left) {
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
	}

	/*
	 * (non-Javadoc)
	 * 
	 */

	public LengthValue getTop() {
		return this.top;
	}

	public LengthValue getRight() {
		return this.right;
	}

	public LengthValue getBottom() {
		return this.bottom;
	}

	public LengthValue getLeft() {
		return this.left;
	}
}