package net.zamasoft.foliojet.css.impl.property.content;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.ContentFunctionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * GCPM {@code bookmark-label: <content-list>} (2026-10-04).
 *
 * <p>
 * Accepts the same items as {@code string-set} (2026-10-23, reported by tech; before then only strings,
 * {@code attr()} and {@code content()}): {@code <string>}, {@code counter()}, {@code counters()},
 * {@code attr()} and {@code content( [ text | before | after | first-letter ]? )}, where {@code content()} is the
 * element's text without its own ::before and ::after. The initial value stays the element's whole text, ::before
 * included, so a number put in {@code ::before} is in the bookmark as before. Counters are read after the element's
 * {@code counter-increment} ({@code StyleEventMachine.applyBookmarkLabel}).
 * </p>
 */
public class BookmarkLabel extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BookmarkLabel();

	private static final ValueListValue DEFAULT = new ValueListValue(new Value[] { ContentFunctionValue.INSTANCE });

	/** Sequence of components (null for the default). */
	public static Value[] get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == DEFAULT) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	private BookmarkLabel() {
		super("bookmark-label");
	}

	public Value getDefault(final CSSStyle style) {
		return DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final List<Value> parts = new ArrayList<Value>();
		while (tokens.hasNext()) {
			parts.add(StringSet.parsePart(tokens.next(), ua));
		}
		if (parts.isEmpty()) {
			throw new PropertyException();
		}
		return new ValueListValue(parts.toArray(new Value[parts.size()]));
	}
}
