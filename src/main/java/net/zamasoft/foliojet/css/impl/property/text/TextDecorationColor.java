package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;

/**
 * {@code text-decoration-color} (css-text-decoration-3, added 2026-08-29).
 *
 * <p>
 * Defaults to {@code currentcolor} (the {@link KeywordValue#DEFAULT} sentinel, as with border-color)
 * and resolves to the element's {@code color} at the computed-value stage.
 * Rendering receives it via {@code AbstractTextParams.decorationColor} (null means text color).
 * Used on 10–16 of 50 real sites in declarations such as
 * {@code text-decoration: underline dotted #999}, or as a longhand.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class TextDecorationColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextDecorationColor();

	/**
	 * Returns the decoration line color. Null if it matches text color (currentcolor).
	 * Returns {@code transparent} as a fully transparent color (equivalent to not drawing the line).
	 */
	public static Color get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.TRANSPARENT) {
			return RGBAColor.create(0, 0, 0, 0);
		}
		if (value instanceof ColorValue color) {
			return color.getColor();
		}
		return null;
	}

	protected TextDecorationColor() {
		super("text-decoration-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		value = ValueUtils.emExToAbsoluteLength(value, style);
		if (value == KeywordValue.DEFAULT || value == KeywordValue.NONE) {
			return style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		final Value value = ColorValueUtils.toColorOrCurrent(ua, lu);
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
