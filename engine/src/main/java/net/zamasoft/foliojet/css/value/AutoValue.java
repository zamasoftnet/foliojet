package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AutoValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AutoValue implements Value {
	public static final AutoValue AUTO_VALUE = new AutoValue();

	private AutoValue() {
		// singleton
	}

	public short getValueType() {
		return TYPE_AUTO;
	}

	public String toString() {
		return "auto";
	}
}