package net.zamasoft.foliojet.impl.css.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * 画像(置換ボックス)の内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSJInternalLink extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJInternalLink();

	public static URI get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((URIValue) value).getURI();
	}

	public static void set(CSSStyle style, URI uri) {
		style.set(INFO, URIValue.create(uri));
	}

	public CSSJInternalLink() {
		super("-cssj-internal-link");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		throw new UnsupportedOperationException();
	}
}