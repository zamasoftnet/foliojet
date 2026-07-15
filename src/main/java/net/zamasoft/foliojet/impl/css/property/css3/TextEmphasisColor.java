package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.CSSColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextEmphasisColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextEmphasisColor();

	public static Color get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.DEFAULT) {
			value = style.get(CSSColor.INFO);
		}
		return ((ColorValue) value).getColor();
	}

	protected TextEmphasisColor() {
		super("-cssj-text-emphasis-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
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