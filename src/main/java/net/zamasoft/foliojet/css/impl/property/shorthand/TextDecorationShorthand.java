package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.text.TextDecoration;
import net.zamasoft.foliojet.css.impl.property.text.TextDecorationAux;
import net.zamasoft.foliojet.css.impl.property.text.TextDecorationColor;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.TextDecorationValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-decoration} shorthand (css-text-decoration-3; separated from the
 * individual {@code text-decoration-line} on 2026-08-29).
 *
 * <p>
 * Accepts {@code <line>* || <style> || <color> || <thickness>} in any order and expands into
 * line types ({@link TextDecoration}), color ({@link TextDecorationColor}),
 * and line style/thickness ({@link TextDecorationAux}, accepted only).
 * Resets omitted components to initial values (the shorthand convention).
 * Previously, anything beyond line types, such as {@code underline dotted},
 * invalidated the entire declaration, removing the underline itself (14 of 50 real sites).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class TextDecorationShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new TextDecorationShorthand();

	protected TextDecorationShorthand() {
		super("text-decoration");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(TextDecoration.INFO, global);
			primitives.set(TextDecorationColor.INFO, global);
			primitives.set(TextDecorationAux.STYLE, global);
			primitives.set(TextDecorationAux.THICKNESS, global);
			return;
		}
		byte flags = 0;
		boolean line = false, none = false;
		Value color = null;
		Value style = null;
		Value thickness = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident ident) {
				final String name = ident.lower();
				switch (name) {
				case "none":
					if (line || none) {
						throw new PropertyException();
					}
					none = true;
					continue;
				case "underline":
				case "overline":
				case "line-through":
				case "blink":
					if (none) {
						throw new PropertyException();
					}
					line = true;
					flags |= TextDecoration.flagOf(name);
					continue;
				default:
					break;
				}
			}
			if (style == null) {
				style = TextDecorationAux.STYLE.toValue(ua, lu);
				if (style != null) {
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
			if (thickness == null) {
				thickness = TextDecorationAux.THICKNESS.toValue(ua, lu);
				if (thickness != null) {
					continue;
				}
			}
			throw new PropertyException();
		}
		if (!line && !none && color == null && style == null && thickness == null) {
			throw new PropertyException();
		}
		primitives.set(TextDecoration.INFO, TextDecorationValue.create(flags));
		primitives.set(TextDecorationColor.INFO, color != null ? color : KeywordValue.DEFAULT);
		primitives.set(TextDecorationAux.STYLE, style != null ? style : TextDecorationAux.SOLID);
		primitives.set(TextDecorationAux.THICKNESS, thickness != null ? thickness : KeywordValue.AUTO);
	}
}
