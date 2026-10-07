package net.zamasoft.foliojet.css.value;

/**
 * GCPM {@code content()} (no arguments). Created only within {@code string-set}
 * value lists: a marker replaced with the assigning element's own rendered text.
 * Never enters evaluation of the {@code content:} property.
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContentFunctionValue implements Value {
	public static final ContentFunctionValue INSTANCE = new ContentFunctionValue();

	private ContentFunctionValue() {
	}
}
