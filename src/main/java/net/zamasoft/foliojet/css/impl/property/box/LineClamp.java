package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code line-clamp} / {@code -webkit-line-clamp} (css-overflow-4, 2026-08-29).
 *
 * <p>
 * Specifies "truncate at N lines and hide the rest". Used by 24 of 50 sites to truncate
 * excerpts or headings (the standard idiom {@code display:-webkit-box; -webkit-box-orient:vertical;
 * -webkit-line-clamp:3; overflow:hidden}). There is no line-count truncation mechanism,
 * so {@code BoxStyleMapper} approximates it with a height limit of N×line-height and
 * overflow:hidden. Adds no ellipsis. Discarding it exposes the entire excerpt, overlapping
 * subsequent content.
 * </p>
 */
public class LineClamp extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineClamp();

	/** Line count. 0 for none. */
	public static int get(final CSSStyle style) {
		final Value value = style.get(INFO);
		return value instanceof IntegerValue integer ? integer.getInteger() : 0;
	}

	protected LineClamp() {
		super("line-clamp");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
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
		if (ValueUtils.isNone(lu)) {
			return KeywordValue.NONE;
		}
		if (lu instanceof CssToken.Num num && num.integer() && num.intValue() >= 1) {
			return IntegerValue.create(num.intValue());
		}
		throw new PropertyException();
	}
}
