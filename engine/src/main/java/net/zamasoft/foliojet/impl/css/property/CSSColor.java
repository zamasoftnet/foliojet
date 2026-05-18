package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSColor.java 1624 2022-05-02 08:59:55Z miyabe $
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
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = ColorValueUtils.toColor(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}