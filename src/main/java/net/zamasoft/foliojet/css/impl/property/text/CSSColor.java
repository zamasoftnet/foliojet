package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSColor();

	public static Color get(CSSStyle style) {
		Value value = style.get(INFO);
		return ((ColorValue) value).getColor();
	}

	protected CSSColor() {
		super("color");
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getDefaultColor();
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		// 型付き attr()(2026-08-03)。色も属性から取れる(bgcolor/text/link等の
		// 移送に要る)。解決の窓口は長さと同じ
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = ColorValueUtils.toColor(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}