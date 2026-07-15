package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.ClearValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Clear.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Clear extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Clear();

	public static byte get(CSSStyle style) {
		ClearValue value = (ClearValue) style.get(INFO);
		return value.getClear();
	}

	private Clear() {
		super("clear");
	}

	public Value getDefault(CSSStyle style) {
		return ClearValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				return ClearValue.NONE_VALUE;
			} else if (ident.equals("left")) {
				return ClearValue.LEFT_VALUE;
			} else if (ident.equals("right")) {
				return ClearValue.RIGHT_VALUE;
			} else if (ident.equals("start")) {
				return ClearValue.START_VALUE;
			} else if (ident.equals("end")) {
				return ClearValue.END_VALUE;
			} else if (ident.equals("both")) {
				return ClearValue.BOTH_VALUE;
			}
		}
		throw new PropertyException();
	}

}