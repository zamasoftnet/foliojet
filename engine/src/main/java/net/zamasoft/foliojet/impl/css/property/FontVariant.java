package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.FontVariantValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: FontVariant.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FontVariant extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariant();

	public static double get(CSSStyle style) {
		return ((FontVariantValue) style.get(INFO)).getFontVariant();
	}

	protected FontVariant() {
		super("font-variant");
	}

	public Value getDefault(CSSStyle style) {
		return FontVariantValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final FontVariantValue fontVariant = FontValueUtils.toFontVariant(lu);
		if (fontVariant == null) {
			throw new PropertyException();
		}
		return fontVariant;
	}

}