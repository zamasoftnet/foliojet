package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.OverflowValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Overflow.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Overflow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Overflow();

	public static byte get(CSSStyle style) {
		OverflowValue value = (OverflowValue) style.get(INFO);
		return value.getOverflow();
	}

	private Overflow() {
		super("overflow");
	}

	public Value getDefault(CSSStyle style) {
		return OverflowValue.VISIBLE_VALUE;
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
			if (ident.equals("visible")) {
				return OverflowValue.VISIBLE_VALUE;
			} else if (ident.equals("hidden")) {
				return OverflowValue.HIDDEN_VALUE;
			} else if (ident.equals("scroll")) {
				return OverflowValue.SCROLL_VALUE;
			} else if (ident.equals("auto")) {
				return OverflowValue.AUTO_VALUE;
			}
		}
		throw new PropertyException();
	}

}