package net.zamasoft.foliojet.css.value;

/**
 * A {@code list-style-type} value referencing an author-defined counter style
 * ({@code @counter-style}) (2026-08-02).
 *
 * <p>
 * The per-document registry holds the definition (how symbols are arranged); the value
 * carries only a code. Using the same representation as built-in styles allows existing
 * counter paths ({@code counter()}, markers, {@code target-counter()}) to work unchanged
 * with author-defined styles.
 * </p>
 */
public final class CounterStyleValue implements ListStyleTypeSource {

	private final short code;

	public CounterStyleValue(final short code) {
		this.code = code;
	}

	public short getListStyleType() {
		return this.code;
	}
}
