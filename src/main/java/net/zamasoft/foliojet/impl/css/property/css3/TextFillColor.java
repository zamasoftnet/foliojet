package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.DefaultValue;
import net.zamasoft.foliojet.css.value.TransparentValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.CSSColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextFillColor.java 1624 2022-05-02 08:59:55Z miyabe $
 */
public class TextFillColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextFillColor();

	public static net.zamasoft.pdfg2d.gc.paint.Color get(CSSStyle style) {
		Value value = style.get(TextFillColor.INFO);
		if (value.getValueType() == Value.TYPE_TRANSPARENT) {
			return null;
		}
		if (value == DefaultValue.DEFAULT_VALUE) {
			return CSSColor.get(style);
		}
		return ((ColorValue) value).getColor();
	}

	protected TextFillColor() {
		super("-cssj-text-fill-color");
	}

	public Value getDefault(CSSStyle style) {
		return DefaultValue.DEFAULT_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && ident.is("currentcolor")) {
			return DefaultValue.DEFAULT_VALUE;
		}
		if (ColorValueUtils.isTransparent(lu)) {
			return TransparentValue.TRANSPARENT_VALUE;
		}
		Value value = ColorValueUtils.toColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}