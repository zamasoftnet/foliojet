package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.BorderBottomStyle;
import net.zamasoft.foliojet.impl.css.property.BorderLeftStyle;
import net.zamasoft.foliojet.impl.css.property.BorderRightStyle;
import net.zamasoft.foliojet.impl.css.property.BorderTopStyle;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderStyleShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderStyleShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderStyleShorthand();

	protected BorderStyleShorthand() {
		super("border-style");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value style1 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style1 == null) {
			throw new PropertyException();
		}
		if (style1.getValueType() == Value.TYPE_INHERIT) {
			primitives.set(BorderLeftStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomStyle.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftStyle.INFO, style1);
			primitives.set(BorderTopStyle.INFO, style1);
			primitives.set(BorderRightStyle.INFO, style1);
			primitives.set(BorderBottomStyle.INFO, style1);
			return;
		}
		final Value style2 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftStyle.INFO, style2);
			primitives.set(BorderTopStyle.INFO, style1);
			primitives.set(BorderRightStyle.INFO, style2);
			primitives.set(BorderBottomStyle.INFO, style1);
			return;
		}
		final Value style3 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftStyle.INFO, style2);
			primitives.set(BorderTopStyle.INFO, style1);
			primitives.set(BorderRightStyle.INFO, style2);
			primitives.set(BorderBottomStyle.INFO, style3);
			return;
		}
		final Value style4 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderLeftStyle.INFO, style4);
		primitives.set(BorderTopStyle.INFO, style1);
		primitives.set(BorderRightStyle.INFO, style2);
		primitives.set(BorderBottomStyle.INFO, style3);
	}

}