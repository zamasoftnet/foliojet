package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: RectValue.java 1552 2018-04-26 01:43:24Z miyabe $
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
	 * @see info.port4.cssj.media.values.Value#getValueType()
	 */
	public short getValueType() {
		return Value.TYPE_RECT;
	}

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