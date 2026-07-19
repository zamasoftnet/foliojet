package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.VisibilityValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class Visibility extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Visibility();

	public static byte get(CSSStyle style) {
		VisibilityValue value = (VisibilityValue) style.get(INFO);
		return value.getVisibility();
	}

	protected Visibility() {
		super("visibility");
	}

	public Value getDefault(CSSStyle style) {
		return VisibilityValue.VISIBLE_VALUE;
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
			if (ident.equals("visible")) {
				return VisibilityValue.VISIBLE_VALUE;
			} else if (ident.equals("hidden")) {
				return VisibilityValue.HIDDEN_VALUE;
			} else if (ident.equals("collapse")) {
				return VisibilityValue.COLLAPSE_VALUE;
			}
		}
		throw new PropertyException();
	}
}