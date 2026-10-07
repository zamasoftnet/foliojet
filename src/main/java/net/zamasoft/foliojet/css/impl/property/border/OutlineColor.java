package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * outline-color property (CSS UI 3 §4, 2026-08-29). Values are
 * {@code <color> | invert}. PDF cannot express invert, so treat it as currentColor,
 * the same as the border-color default.
 *
 * @author MIYABE Tatsuhiko
 */
public class OutlineColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new OutlineColor();

	/** Returns the color, or null for transparent. */
	public static Color get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.TRANSPARENT) {
			return null;
		}
		return ((ColorValue) value).getColor();
	}

	protected OutlineColor() {
		super("outline-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == KeywordValue.DEFAULT) {
			// invert / unspecified → currentColor
			return style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = toOutlineColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	/**
	 * Converts {@code <color> | invert} to a value. Returns null if it does not match.
	 * Shared with the outline shorthand.
	 */
	public static Value toOutlineColor(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Ident ident && ident.lower().equals("invert")) {
			return KeywordValue.DEFAULT;
		}
		if (ColorValueUtils.isTransparent(token)) {
			return KeywordValue.TRANSPARENT;
		}
		return ColorValueUtils.toColor(ua, token);
	}
}
