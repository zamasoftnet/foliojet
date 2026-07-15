package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.ColumnSpanValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class ColumnSpan extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new ColumnSpan();

	public static byte get(CSSStyle style) {
		ColumnSpanValue value = (ColumnSpanValue) style.get(INFO);
		return value.getColumnSpan();
	}

	protected ColumnSpan() {
		super("-cssj-column-span");
	}

	public Value getDefault(CSSStyle style) {
		return ColumnSpanValue.SINGLE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Num num && num.integer()) {
			if (num.intValue() == 1) {
				return ColumnSpanValue.SINGLE_VALUE;
			}
		} else if (lu instanceof CssToken.Ident ident) {
			if (ident.is("all")) {
				return ColumnSpanValue.ALL_VALUE;
			}
		}
		throw new PropertyException();
	}

}