package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.TextDecorationValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextDecoration.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextDecoration extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextDecoration();

	public static byte get(CSSStyle style) {
		TextDecorationValue value = (TextDecorationValue) style.get(INFO);
		return value.getFlags();
	}

	protected TextDecoration() {
		super("text-decoration");
	}

	public Value getDefault(CSSStyle style) {
		return TextDecorationValue.NONE_DECORATION;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		TextDecorationValue value;
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				value = TextDecorationValue.NONE_DECORATION;
			} else {
				byte flags = 0;
				for (;;) {
					if (ident.equals("underline")) {
						flags |= TextDecorationValue.UNDERLINE;
					} else if (ident.equals("overline")) {
						flags |= TextDecorationValue.OVERLINE;
					} else if (ident.equals("line-through")) {
						flags |= TextDecorationValue.LINE_THROUGH;
					} else if (ident.equals("blink")) {
						flags |= TextDecorationValue.BLINK;
					} else {
						throw new PropertyException();
					}

					if (!tokens.hasNext()) {
						break;
					}
					ident = tokens.ident();
					if (ident == null) {
						throw new PropertyException();
					}
					ident = ident.toLowerCase();
				}
				value = TextDecorationValue.create(flags);
			}
			return value;
		}
		throw new PropertyException();
	}

}