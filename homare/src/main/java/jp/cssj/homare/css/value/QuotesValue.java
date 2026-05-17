package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: QuotesValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class QuotesValue implements Value {
	private final String open, close;

	public QuotesValue(String open, String close) {
		this.open = open;
		this.close = close;
	}

	public short getValueType() {
		return TYPE_QUOTES;
	}

	public String getOpen() {
		return this.open;
	}

	public String getClose() {
		return this.close;
	}
}