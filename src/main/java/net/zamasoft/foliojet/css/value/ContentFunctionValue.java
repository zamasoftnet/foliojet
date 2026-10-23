package net.zamasoft.foliojet.css.value;

/**
 * GCPM {@code content()}. Created only within {@code string-set} and {@code bookmark-label} value lists: a marker
 * replaced with text of the assigning element. Never enters evaluation of the {@code content:} property.
 *
 * <p>
 * In {@code string-set} and {@code bookmark-label} (2026-10-23) {@code content()} and {@code content(text)} are
 * {@link #TEXT}, the element's text without its own {@code ::before} and {@code ::after}; {@code content(before)},
 * {@code content(after)} and {@code content(first-letter)} are the rest. The initial value of {@code bookmark-label} is
 * {@link #INSTANCE}, the whole rendered text.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContentFunctionValue implements Value {
	/** The element's whole rendered text, {@code ::before} and {@code ::after} included ({@code bookmark-label}). */
	public static final ContentFunctionValue INSTANCE = new ContentFunctionValue("all");

	public static final ContentFunctionValue TEXT = new ContentFunctionValue("text");

	public static final ContentFunctionValue BEFORE = new ContentFunctionValue("before");

	public static final ContentFunctionValue AFTER = new ContentFunctionValue("after");

	public static final ContentFunctionValue FIRST_LETTER = new ContentFunctionValue("first-letter");

	private final String kind;

	private ContentFunctionValue(final String kind) {
		this.kind = kind;
	}

	/**
	 * @param argument the argument of {@code content()} in {@code string-set} or {@code bookmark-label}, or null for none
	 * @return the value, or null if the argument is not one of text, before, after and first-letter
	 */
	public static ContentFunctionValue forStringSet(final String argument) {
		if (argument == null) {
			return TEXT;
		}
		return switch (argument) {
		case "text" -> TEXT;
		case "before" -> BEFORE;
		case "after" -> AFTER;
		case "first-letter" -> FIRST_LETTER;
		default -> null;
		};
	}

	@Override
	public String toString() {
		return "content(" + this.kind + ")";
	}
}
