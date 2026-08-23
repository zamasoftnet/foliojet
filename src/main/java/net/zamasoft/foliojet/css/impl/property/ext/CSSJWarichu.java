package net.zamasoft.foliojet.css.impl.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJWarichuValue;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code -cssj-warichu: none | auto}。 */
public final class CSSJWarichu extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJWarichu();

	public static boolean isEnabled(final CSSStyle style) {
		return style.get(INFO) == CSSJWarichuValue.AUTO;
	}

	private CSSJWarichu() {
		super("-cssj-warichu");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return CSSJWarichuValue.NONE;
	}

	@Override
	public boolean isInherited() {
		return false;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CSSJWarichuValue value;
		if (tokens.eat("none")) {
			value = CSSJWarichuValue.NONE;
		} else if (tokens.eat("auto")) {
			value = CSSJWarichuValue.AUTO;
		} else {
			throw new PropertyException();
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
