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
import net.zamasoft.foliojet.impl.css.property.BorderTopColor;
import net.zamasoft.foliojet.impl.css.property.BorderTopStyle;
import net.zamasoft.foliojet.impl.css.property.BorderTopWidth;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-top"> border-top
 * 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderTopShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderTopShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderTopShorthand();

	protected BorderTopShorthand() {
		super("border-top");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(BorderTopWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopColor.INFO, InheritValue.INHERIT_VALUE);
			return;
		}

		Value width = null;
		Value styleValue = null;
		Value color = null;
		for (; lu != null; lu = lu.getNextLexicalUnit()) {
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

		primitives.set(BorderTopWidth.INFO, width);
		primitives.set(BorderTopStyle.INFO, styleValue);
		primitives.set(BorderTopColor.INFO, color);
	}

}