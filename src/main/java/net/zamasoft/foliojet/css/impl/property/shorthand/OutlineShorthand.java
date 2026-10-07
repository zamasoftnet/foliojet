package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.border.OutlineColor;
import net.zamasoft.foliojet.css.impl.property.border.OutlineStyle;
import net.zamasoft.foliojet.css.impl.property.border.OutlineWidth;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * outline shorthand (CSS UI 3 §4, 2026-08-29).
 * Accepts {@code <outline-width> || <outline-style> || <outline-color>} in any order.
 * Does not include outline-offset. Assembled like border-top, with only {@code auto} added
 * for style and {@code invert} for color.
 *
 * @author MIYABE Tatsuhiko
 */
public class OutlineShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new OutlineShorthand();

	protected OutlineShorthand() {
		super("outline");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(OutlineWidth.INFO, global);
			primitives.set(OutlineStyle.INFO, global);
			primitives.set(OutlineColor.INFO, global);
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
				styleValue = OutlineStyle.toOutlineStyle(lu);
				if (styleValue != null) {
					continue;
				}
			}
			if (color == null) {
				color = OutlineColor.toOutlineColor(ua, lu);
				if (color != null) {
					continue;
				}
			}
			throw new PropertyException();
		}

		primitives.set(OutlineWidth.INFO, width);
		primitives.set(OutlineStyle.INFO, styleValue);
		primitives.set(OutlineColor.INFO, color);
	}
}
