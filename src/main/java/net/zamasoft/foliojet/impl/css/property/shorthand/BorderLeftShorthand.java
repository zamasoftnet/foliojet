package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.BorderLeftColor;
import net.zamasoft.foliojet.impl.css.property.BorderLeftStyle;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderLeftShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderLeftShorthand();

	protected BorderLeftShorthand() {
		super("border-left");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (tokens.isInherit()) {
			primitives.set(BorderLeftWidth.INFO, KeywordValue.INHERIT);
			primitives.set(BorderLeftStyle.INFO, KeywordValue.INHERIT);
			primitives.set(BorderLeftColor.INFO, KeywordValue.INHERIT);
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
					color = KeywordValue.TRANSPARENT;
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