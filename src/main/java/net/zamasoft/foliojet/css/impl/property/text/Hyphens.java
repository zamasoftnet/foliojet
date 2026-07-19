package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.HyphensValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * hyphens: none | manual | auto SPEC css-text-4
 *
 * @author MIYABE Tatsuhiko
 */
public class Hyphens extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Hyphens();

	public static byte get(CSSStyle style) {
		return ((HyphensValue) style.get(INFO)).getHyphens();
	}

	protected Hyphens() {
		super("hyphens");
	}

	public Value getDefault(CSSStyle style) {
		return HyphensValue.MANUAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				return HyphensValue.NONE_VALUE;
			} else if (ident.equals("manual")) {
				return HyphensValue.MANUAL_VALUE;
			} else if (ident.equals("auto")) {
				return HyphensValue.AUTO_VALUE;
			}
		}
		throw new PropertyException();
	}

}
