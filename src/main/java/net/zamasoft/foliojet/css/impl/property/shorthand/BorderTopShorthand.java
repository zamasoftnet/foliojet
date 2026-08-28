package net.zamasoft.foliojet.css.impl.property.shorthand;

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
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-top"> border-top
 * 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class BorderTopShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderTopShorthand();

	protected BorderTopShorthand() {
		super("border-top");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(BorderWidth.TOP, global);
			primitives.set(BorderStyle.TOP, global);
			primitives.set(BorderColor.TOP, global);
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
					color = ColorValueUtils.toColorOrCurrent(ua, lu);
				}
				if (color != null) {
					continue;
				}
			}
			throw new PropertyException();
		}

		primitives.set(BorderWidth.TOP, width);
		primitives.set(BorderStyle.TOP, styleValue);
		primitives.set(BorderColor.TOP, color);
	}

}