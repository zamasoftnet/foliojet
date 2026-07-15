package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJRegeneratable extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJRegeneratable();

	public static String get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((StringValue) value).getString();
	}

	private CSSJRegeneratable() {
		super("-cssj-regeneratable");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.isInherit()) {
			return KeywordValue.INHERIT;
		}
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("none")) {
				return KeywordValue.NONE;
			}
			return new StringValue(ident.name());
		}
		if (lu instanceof CssToken.Str str) {
			return new StringValue(str.value());
		}
		throw new PropertyException();
	}
}