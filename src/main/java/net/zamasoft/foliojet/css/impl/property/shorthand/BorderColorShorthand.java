package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderColorShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderColorShorthand();

	protected BorderColorShorthand() {
		super("border-color");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(BorderColor.LEFT, global);
			primitives.set(BorderColor.TOP, global);
			primitives.set(BorderColor.RIGHT, global);
			primitives.set(BorderColor.BOTTOM, global);
			return;
		}
		final Value color1 = nextColor(tokens, ua);
		if (color1 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderColor.LEFT, color1);
			primitives.set(BorderColor.TOP, color1);
			primitives.set(BorderColor.RIGHT, color1);
			primitives.set(BorderColor.BOTTOM, color1);
			return;
		}
		final Value color2 = nextColor(tokens, ua);
		if (color2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderColor.LEFT, color2);
			primitives.set(BorderColor.TOP, color1);
			primitives.set(BorderColor.RIGHT, color2);
			primitives.set(BorderColor.BOTTOM, color1);
			return;
		}
		final Value color3 = nextColor(tokens, ua);
		if (color3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderColor.LEFT, color2);
			primitives.set(BorderColor.TOP, color1);
			primitives.set(BorderColor.RIGHT, color2);
			primitives.set(BorderColor.BOTTOM, color3);
			return;
		}
		final Value color4 = nextColor(tokens, ua);
		if (color4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderColor.LEFT, color4);
		primitives.set(BorderColor.TOP, color1);
		primitives.set(BorderColor.RIGHT, color2);
		primitives.set(BorderColor.BOTTOM, color3);
	}

	private static Value nextColor(TokenStream tokens, UserAgent ua) {
		final CssToken token = tokens.next();
		if (ColorValueUtils.isTransparent(token)) {
			return KeywordValue.TRANSPARENT;
		}
		// currentcolor は DEFAULT 番兵(2026-08-29)
		return ColorValueUtils.toColorOrCurrent(ua, token);
	}
}