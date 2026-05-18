package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: DefaultValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class DefaultValue implements Value {
	public static final DefaultValue DEFAULT_VALUE = new DefaultValue();

	private DefaultValue() {
		// singleton
	}

	public short getValueType() {
		return TYPE_DEFAULT;
	}

}