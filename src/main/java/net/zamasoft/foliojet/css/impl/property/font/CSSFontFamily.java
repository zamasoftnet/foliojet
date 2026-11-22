package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSFontFamily extends AbstractPrimitivePropertyInfo {
	public static final AbstractPrimitivePropertyInfo INFO = new CSSFontFamily();

	public static FontFamilyList get(CSSStyle style) {
		return ((FontFamilyValue) style.get(INFO)).asFontFamilyList();
	}

	protected CSSFontFamily() {
		super("font-family");
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getDefaultFontFamily();
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		// attr() is not a font family: the declaration is invalid. It was once stored unresolved here
		// (2026-08-03, for <font face>, which stayed in HTMLStyle), but nothing resolved it, and both
		// get() and the @font-face rule read the value as a family list.
		final FontFamilyValue fontFamily = FontValueUtils.toFontFamily(ua, tokens);
		if (fontFamily == null) {
			throw new PropertyException();
		}
		return fontFamily;
	}

}
