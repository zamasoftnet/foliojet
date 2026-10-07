package net.zamasoft.foliojet.css.impl.property.page;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code page} property (named pages N1b, 2026-07-31:
 * consult-codex-2026-07-31-named-pages.txt Q1).
 * Values are {@code auto | <custom-ident>}. Not inherited, but the used value propagates
 * from the nearest non-auto ancestor (CSS Page 3; resolved via {@link #getUsed}
 * instead of the normal inheritance flag). Names are case-sensitive CSS identifiers;
 * only {@code auto} is reserved.
 *
 * @author MIYABE Tatsuhiko
 */
public class PageProperty extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageProperty();

	/**
	 * Used value (page name of the nearest non-auto ancestor). Null if absent
	 * (=unnamed page).
	 */
	public static String getUsed(CSSStyle style) {
		for (CSSStyle s = style; s != null; s = s.getParentStyle()) {
			final Value value = s.get(INFO);
			if (value != KeywordValue.AUTO) {
				return ((StringValue) value).getString();
			}
		}
		return null;
	}

	protected PageProperty() {
		super("page");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("auto")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.AUTO;
		}
		final String name = tokens.ident();
		if (name == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return new StringValue(name);
	}
}
