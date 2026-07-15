package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.ListStylePositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ListStylePosition.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ListStylePosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ListStylePosition();

	public static short get(CSSStyle style) {
		ListStylePositionValue value = (ListStylePositionValue) style.get(INFO);
		return value.getListStylePosition();
	}

	protected ListStylePosition() {
		super("list-style-position");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return ListStylePositionValue.OUTSIDE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final String text;
		if (lu instanceof CssToken.Ident ident) {
			text = ident.name();
		} else if (lu instanceof CssToken.Str str) {
			text = str.value();
		} else {
			throw new PropertyException();
		}
		final Value value = GeneratedValueUtils.toListStylePosition(text);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}