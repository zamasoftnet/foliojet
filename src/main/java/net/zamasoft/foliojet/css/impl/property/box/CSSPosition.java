package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSPosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSPosition();

	public static byte get(CSSStyle style) {
		PositionValue value = (PositionValue) style.get(INFO);
		return value.getPosition();
	}

	private CSSPosition() {
		super("position");
	}

	public Value getDefault(CSSStyle style) {
		return PositionValue.STATIC_VALUE;
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
			if (ident.equals("static")) {
				return PositionValue.STATIC_VALUE;
			} else if (ident.equals("relative")) {
				return PositionValue.RELATIVE_VALUE;
			} else if (ident.equals("absolute")) {
				return PositionValue.ABSOLUTE_VALUE;
			} else if (ident.equals("fixed")) {
				return PositionValue.FIXED_VALUE;
			}
		}
		throw new PropertyException();
	}

}