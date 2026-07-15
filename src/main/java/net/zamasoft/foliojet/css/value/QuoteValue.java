package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum QuoteValue implements Value {
	OPEN_QUOTE_VALUE(QuoteValue.OPEN_QUOTE),

	CLOSE_QUOTE_VALUE(QuoteValue.CLOSE_QUOTE),

	NO_OPEN_QUOTE_VALUE(QuoteValue.NO_OPEN_QUOTE),

	NO_CLOSE_QUOTE_VALUE(QuoteValue.NO_CLOSE_QUOTE);
	public static final short OPEN_QUOTE = 0;

	public static final short CLOSE_QUOTE = OPEN_QUOTE + 1;

	public static final short NO_OPEN_QUOTE = CLOSE_QUOTE + 1;

	public static final short NO_CLOSE_QUOTE = NO_OPEN_QUOTE + 1;

	private final short quote;

	private QuoteValue(short quote) {
		this.quote = quote;
	}

	/*
	 * (non-Javadoc)
	 * 
	 */

	public short getQuote() {
		return this.quote;
	}

	public String toString() {
		switch (this.quote) {
		case OPEN_QUOTE:
			return "open-quote";

		case CLOSE_QUOTE:
			return "close-quote";

		case NO_OPEN_QUOTE:
			return "no-open-quote";

		case NO_CLOSE_QUOTE:
			return "no-close-quote";

		default:
			throw new IllegalStateException();
		}
	}
}