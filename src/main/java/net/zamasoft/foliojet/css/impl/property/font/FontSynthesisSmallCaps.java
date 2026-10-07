package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-synthesis-small-caps} (css-fonts-4, added 2026-08-30).
 *
 * <p>
 * Values are {@code auto | none}; defaults to {@code auto} and is inherited.
 * This engine has no small-caps synthesis mechanism, so the property is only accepted,
 * cascaded, and inherited; it is not yet used for actual synthesis.
 * </p>
 */
public class FontSynthesisSmallCaps extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontSynthesisSmallCaps();

	protected FontSynthesisSmallCaps() {
		super("font-synthesis-small-caps");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("auto")) {
				return KeywordValue.AUTO;
			}
			if (ident.is("none")) {
				return KeywordValue.NONE;
			}
		}
		throw new PropertyException();
	}
}
