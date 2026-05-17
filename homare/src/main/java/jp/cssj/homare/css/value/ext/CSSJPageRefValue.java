package jp.cssj.homare.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJPageRefValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJPageRefValue implements ExtValue {
	public static final byte REF = 1;
	public static final byte ATTR = 2;

	private final byte type;

	private final String ref, counter, separator;

	private final short numberStyleType;

	public CSSJPageRefValue(byte type, String ref, String counter, short numberStyleType, String separator) {
		this.type = type;
		this.ref = ref;
		this.counter = counter;
		this.numberStyleType = numberStyleType;
		this.separator = separator;
	}

	public short getValueType() {
		return TYPE_CSSJ_PAGE_REF;
	}

	public byte getType() {
		return this.type;
	}

	public String getRef() {
		return this.ref;
	}

	public String getCounter() {
		return this.counter;
	}

	public short getNumberStyleType() {
		return this.numberStyleType;
	}

	public String getSeparator() {
		return this.separator;
	}

	public String toString() {
		return "-cssj-page-ref(" + this.ref + "," + this.counter + "," + this.numberStyleType + ")";
	}
}