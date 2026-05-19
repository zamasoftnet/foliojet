package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: QuoteValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class QuoteValue implements Value {
	public static final short OPEN_QUOTE = 0;

	public static final short CLOSE_QUOTE = OPEN_QUOTE + 1;

	public static final short NO_OPEN_QUOTE = CLOSE_QUOTE + 1;

	public static final short NO_CLOSE_QUOTE = NO_OPEN_QUOTE + 1;

	public static final QuoteValue OPEN_QUOTE_VALUE = new QuoteValue(OPEN_QUOTE);

	public static final QuoteValue CLOSE_QUOTE_VALUE = new QuoteValue(CLOSE_QUOTE);

	public static final QuoteValue NO_OPEN_QUOTE_VALUE = new QuoteValue(NO_OPEN_QUOTE);

	public static final QuoteValue NO_CLOSE_QUOTE_VALUE = new QuoteValue(NO_CLOSE_QUOTE);

	private final short quote;

	private QuoteValue(short quote) {
		this.quote = quote;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see info.port4.cssj.media.values.Value#getValueType()
	 */
	public short getValueType() {
		return TYPE_QUOTE;
	}

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