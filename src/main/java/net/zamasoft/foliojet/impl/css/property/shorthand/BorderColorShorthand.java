package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.BorderBottomColor;
import net.zamasoft.foliojet.impl.css.property.BorderLeftColor;
import net.zamasoft.foliojet.impl.css.property.BorderRightColor;
import net.zamasoft.foliojet.impl.css.property.BorderTopColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderColorShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderColorShorthand();

	protected BorderColorShorthand() {
		super("border-color");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (tokens.isInherit()) {
			primitives.set(BorderLeftColor.INFO, KeywordValue.INHERIT);
			primitives.set(BorderTopColor.INFO, KeywordValue.INHERIT);
			primitives.set(BorderRightColor.INFO, KeywordValue.INHERIT);
			primitives.set(BorderBottomColor.INFO, KeywordValue.INHERIT);
			return;
		}
		final Value color1 = nextColor(tokens, ua);
		if (color1 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftColor.INFO, color1);
			primitives.set(BorderTopColor.INFO, color1);
			primitives.set(BorderRightColor.INFO, color1);
			primitives.set(BorderBottomColor.INFO, color1);
			return;
		}
		final Value color2 = nextColor(tokens, ua);
		if (color2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftColor.INFO, color2);
			primitives.set(BorderTopColor.INFO, color1);
			primitives.set(BorderRightColor.INFO, color2);
			primitives.set(BorderBottomColor.INFO, color1);
			return;
		}
		final Value color3 = nextColor(tokens, ua);
		if (color3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftColor.INFO, color2);
			primitives.set(BorderTopColor.INFO, color1);
			primitives.set(BorderRightColor.INFO, color2);
			primitives.set(BorderBottomColor.INFO, color3);
			return;
		}
		final Value color4 = nextColor(tokens, ua);
		if (color4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderLeftColor.INFO, color4);
		primitives.set(BorderTopColor.INFO, color1);
		primitives.set(BorderRightColor.INFO, color2);
		primitives.set(BorderBottomColor.INFO, color3);
	}

	private static Value nextColor(TokenStream tokens, UserAgent ua) {
		final CssToken token = tokens.next();
		if (ColorValueUtils.isTransparent(token)) {
			return KeywordValue.TRANSPARENT;
		}
		return ColorValueUtils.toColor(ua, token);
	}
}