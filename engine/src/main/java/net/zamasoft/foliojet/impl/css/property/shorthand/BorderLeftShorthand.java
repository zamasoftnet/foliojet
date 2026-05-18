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
import net.zamasoft.foliojet.impl.css.property.BorderLeftColor;
import net.zamasoft.foliojet.impl.css.property.BorderLeftStyle;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderLeftShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderLeftShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderLeftShorthand();

	protected BorderLeftShorthand() {
		super("border-left");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(BorderLeftWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftColor.INFO, InheritValue.INHERIT_VALUE);
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

		primitives.set(BorderLeftWidth.INFO, width);
		primitives.set(BorderLeftStyle.INFO, styleValue);
		primitives.set(BorderLeftColor.INFO, color);
	}

}