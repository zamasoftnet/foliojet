package net.zamasoft.foliojet.css.value;

/**
 * A value with a counter-style code (2026-08-02). Provides a common interface
 * for built-in {@link ListStyleTypeValue} and author-defined {@link CounterStyleValue}.
 */
public interface ListStyleTypeSource extends Value {

	/** The counter-style code. */
	public short getListStyleType();
}
