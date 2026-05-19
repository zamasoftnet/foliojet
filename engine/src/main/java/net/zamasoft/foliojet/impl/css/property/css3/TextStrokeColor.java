package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.DefaultValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.CSSColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextStrokeColor.java 1630 2022-05-12 07:40:11Z miyabe $
 */
public class TextStrokeColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextStrokeColor();

	public static net.zamasoft.pdfg2d.gc.paint.Color get(CSSStyle style) {
		Value value = style.get(TextStrokeColor.INFO);
		if (value == DefaultValue.DEFAULT_VALUE) {
			return CSSColor.get(style);
		}
		return ((ColorValue) value).getColor();
	}

	protected TextStrokeColor() {
		super("-cssj-text-stroke-color");
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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT && lu.getStringValue().equalsIgnoreCase("currentcolor")) {
			return DefaultValue.DEFAULT_VALUE;
		}
		Value value = ColorValueUtils.toColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	public int getPriority() {
		return 1;
	}
}