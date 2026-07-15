package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJPageRefValue implements Value {
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