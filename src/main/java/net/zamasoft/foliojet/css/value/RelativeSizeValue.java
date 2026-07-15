package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum RelativeSizeValue implements Value {
	LARGER_VALUE(RelativeSizeValue.LARGER),

	SMALLER_VALUE(RelativeSizeValue.SMALLER);
	public static final short LARGER = 1;

	public static final short SMALLER = LARGER + 1;

	private final short relativeSize;

	private RelativeSizeValue(short relativeSize) {
		this.relativeSize = relativeSize;
	}

	public short getRelativeSize() {
		return this.relativeSize;
	}
}