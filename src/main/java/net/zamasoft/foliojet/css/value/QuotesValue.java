package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class QuotesValue implements Value {
	private final String open, close;

	public QuotesValue(String open, String close) {
		this.open = open;
		this.close = close;
	}

	public String getOpen() {
		return this.open;
	}

	public String getClose() {
		return this.close;
	}
}