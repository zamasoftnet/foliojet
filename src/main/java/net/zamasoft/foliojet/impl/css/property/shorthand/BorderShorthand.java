package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.impl.css.property.border.BorderWidth;
import net.zamasoft.foliojet.impl.css.property.border.BorderStyle;
import net.zamasoft.foliojet.impl.css.property.box.Side;
import net.zamasoft.foliojet.impl.css.property.border.BorderColor;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderShorthand();

	protected BorderShorthand() {
		super("border");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (tokens.isInherit()) {
			primitives.set(BorderWidth.LEFT, KeywordValue.INHERIT);
			primitives.set(BorderStyle.LEFT, KeywordValue.INHERIT);
			primitives.set(BorderColor.LEFT, KeywordValue.INHERIT);
			primitives.set(BorderWidth.TOP, KeywordValue.INHERIT);
			primitives.set(BorderStyle.TOP, KeywordValue.INHERIT);
			primitives.set(BorderColor.TOP, KeywordValue.INHERIT);
			primitives.set(BorderWidth.RIGHT, KeywordValue.INHERIT);
			primitives.set(BorderStyle.RIGHT, KeywordValue.INHERIT);
			primitives.set(BorderColor.RIGHT, KeywordValue.INHERIT);
			primitives.set(BorderWidth.BOTTOM, KeywordValue.INHERIT);
			primitives.set(BorderStyle.BOTTOM, KeywordValue.INHERIT);
			primitives.set(BorderColor.BOTTOM, KeywordValue.INHERIT);
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

		primitives.set(BorderWidth.LEFT, width);
		primitives.set(BorderWidth.TOP, width);
		primitives.set(BorderWidth.RIGHT, width);
		primitives.set(BorderWidth.BOTTOM, width);
		primitives.set(BorderStyle.LEFT, styleValue);
		primitives.set(BorderStyle.TOP, styleValue);
		primitives.set(BorderStyle.RIGHT, styleValue);
		primitives.set(BorderStyle.BOTTOM, styleValue);
		primitives.set(BorderColor.LEFT, color);
		primitives.set(BorderColor.TOP, color);
		primitives.set(BorderColor.RIGHT, color);
		primitives.set(BorderColor.BOTTOM, color);
	}

}