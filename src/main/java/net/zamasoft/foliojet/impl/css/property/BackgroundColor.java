package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.TransparentValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundColor.java 1626 2022-05-03 00:35:38Z miyabe $
 */
public class BackgroundColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundColor();

	public static PaintValue get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_TRANSPARENT) {
			return null;
		}
		return (PaintValue)value;
	}

	protected BackgroundColor() {
		super("background-color");
	}

	public Value getDefault(CSSStyle style) {
		return TransparentValue.TRANSPARENT_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ColorValueUtils.isTransparent(lu)) {
			return TransparentValue.TRANSPARENT_VALUE;
		}
		Value value = ColorValueUtils.toPaint(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}