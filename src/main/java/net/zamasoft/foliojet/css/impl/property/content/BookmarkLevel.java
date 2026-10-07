package net.zamasoft.foliojet.css.impl.property.content;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.BookmarkSpec;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * GCPM {@code bookmark-level: auto | none | <integer>} (2026-10-04).
 *
 * <p>
 * The specified initial value is {@code none}, but Copper has always created bookmarks
 * using heading levels from h1–h6, so the initial value is {@code auto}
 * (a Copper extension that follows document heading levels).
 * {@code none} excludes a heading from bookmarks; an integer can turn non-heading elements
 * into bookmarks too.
 * </p>
 */
public class BookmarkLevel extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BookmarkLevel();

	/** 0 means none; {@link BookmarkSpec#LEVEL_AUTO} means auto. */
	public static int get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.AUTO) {
			return BookmarkSpec.LEVEL_AUTO;
		}
		if (value == KeywordValue.NONE) {
			return 0;
		}
		return ((IntegerValue) value).getInteger();
	}

	private BookmarkLevel() {
		super("bookmark-level");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("auto")) {
				return KeywordValue.AUTO;
			}
			if (ident.is("none")) {
				return KeywordValue.NONE;
			}
		} else if (lu instanceof CssToken.Num num && num.integer() && num.intValue() >= 1) {
			return IntegerValue.create(num.intValue());
		}
		throw new PropertyException();
	}
}
