package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.TransparentValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.BorderBottomColor;
import net.zamasoft.foliojet.impl.css.property.BorderBottomStyle;
import net.zamasoft.foliojet.impl.css.property.BorderBottomWidth;
import net.zamasoft.foliojet.impl.css.property.BorderLeftColor;
import net.zamasoft.foliojet.impl.css.property.BorderLeftStyle;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.impl.css.property.BorderRightColor;
import net.zamasoft.foliojet.impl.css.property.BorderRightStyle;
import net.zamasoft.foliojet.impl.css.property.BorderRightWidth;
import net.zamasoft.foliojet.impl.css.property.BorderTopColor;
import net.zamasoft.foliojet.impl.css.property.BorderTopStyle;
import net.zamasoft.foliojet.impl.css.property.BorderTopWidth;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderShorthand();

	protected BorderShorthand() {
		super("border");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (tokens.isInherit()) {
			primitives.set(BorderLeftWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomColor.INFO, InheritValue.INHERIT_VALUE);
			return;
		}

		Value width = null;
		Value styleValue = null;
		Value color = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (width == null) {
				width = BorderValueUtils.toBorderWidth(ua, lu);
				if (width != null) {
					continue;
				}
			}
			if (styleValue == null) {
				styleValue = BorderValueUtils.toBorderStyle(lu);
				if (styleValue != null) {
					continue;
				}
			}
			if (color == null) {
				if (ColorValueUtils.isTransparent(lu)) {
					color = TransparentValue.TRANSPARENT_VALUE;
				} else {
					color = ColorValueUtils.toColor(ua, lu);
				}
				if (color != null) {
					continue;
				}
			}
			throw new PropertyException();
		}

		primitives.set(BorderLeftWidth.INFO, width);
		primitives.set(BorderTopWidth.INFO, width);
		primitives.set(BorderRightWidth.INFO, width);
		primitives.set(BorderBottomWidth.INFO, width);
		primitives.set(BorderLeftStyle.INFO, styleValue);
		primitives.set(BorderTopStyle.INFO, styleValue);
		primitives.set(BorderRightStyle.INFO, styleValue);
		primitives.set(BorderBottomStyle.INFO, styleValue);
		primitives.set(BorderLeftColor.INFO, color);
		primitives.set(BorderTopColor.INFO, color);
		primitives.set(BorderRightColor.INFO, color);
		primitives.set(BorderBottomColor.INFO, color);
	}

}