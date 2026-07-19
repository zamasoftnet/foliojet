package net.zamasoft.foliojet.css.property;

import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontWeight;
import net.zamasoft.foliojet.css.impl.property.font.CSSUnicodeRange;
import net.zamasoft.foliojet.css.impl.property.font.Src;

/**
 * @author MIYABE Tatsuhiko
 */
public final class FontFacePropertySet extends PropertySet {
	private static final PropertySet INSTANCE = new FontFacePropertySet();

	private FontFacePropertySet() {
		put(CSSFontFamily.INFO, Src.INFO, FontWeight.INFO, CSSFontStyle.INFO, CSSUnicodeRange.INFO);
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}
