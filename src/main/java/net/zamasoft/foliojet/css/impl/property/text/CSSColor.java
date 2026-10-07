package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
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
		// Typed attr() (2026-08-03). Colors can also come from attributes (needed to move
		// bgcolor/text/link, etc.). Uses the same resolution entry point as lengths.
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		// Typed attr() (2026-08-03). ColorValueUtils.toColor is contracted to return
		// a concrete ColorValue, so handle unresolved values here.
		final Value attr = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, lu,
				net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR);
		if (attr != null) {
			return attr;
		}
		if (ColorValueUtils.isCurrentColor(lu)) {
			// currentcolor on color itself means the parent's (inherited) color (CSS Color 4 §7.1).
			// CSSStyle.get treats raw INHERIT as delegation to the parent (2026-08-29).
			return KeywordValue.INHERIT;
		}
		final Value value = ColorValueUtils.toColor(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}