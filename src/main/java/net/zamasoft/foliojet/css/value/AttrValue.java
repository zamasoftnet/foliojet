package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AttrValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AttrValue implements Value {
	private final String name;

	public AttrValue(String name) {
		this.name = name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see info.port4.cssj.media.values.Value#getValueType()
	 */
	public short getValueType() {
		return TYPE_ATTR;
	}

	public String getName() {
		return this.name;
	}
}