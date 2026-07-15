package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BoxSizingValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WordWrap.java 1485 2016-12-16 06:41:11Z miyabe $
 */
public class BoxSizing extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BoxSizing();

	public static byte get(CSSStyle style) {
		return ((BoxSizingValue) style.get(INFO)).getBoxSizing();
	}

	protected BoxSizing() {
		super("box-sizing");
	}

	public Value getDefault(CSSStyle style) {
		return BoxSizingValue.CONTENT_BOX_VALUE;
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
			if (ident.equals("content-box")) {
				return BoxSizingValue.CONTENT_BOX_VALUE;
			} else if (ident.equals("border-box")) {
				return BoxSizingValue.BORDER_BOX_VALUE;
			}
		}
		throw new PropertyException();
	}

}