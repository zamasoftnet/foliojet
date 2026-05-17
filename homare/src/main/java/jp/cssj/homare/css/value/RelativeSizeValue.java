package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: RelativeSizeValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class RelativeSizeValue implements Value {
	public static final short LARGER = 1;

	public static final short SMALLER = LARGER + 1;

	public static final RelativeSizeValue LARGER_VALUE = new RelativeSizeValue(LARGER);

	public static final RelativeSizeValue SMALLER_VALUE = new RelativeSizeValue(SMALLER);

	private final short relativeSize;

	private RelativeSizeValue(short relativeSize) {
		this.relativeSize = relativeSize;
	}

	public short getValueType() {
		return TYPE_RELATIVE_SIZE;
	}

	public short getRelativeSize() {
		return this.relativeSize;
	}
}