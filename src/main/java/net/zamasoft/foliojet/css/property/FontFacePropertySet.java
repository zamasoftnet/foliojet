package net.zamasoft.foliojet.css.property;

import net.zamasoft.foliojet.impl.css.property.CSSFontFamily;
import net.zamasoft.foliojet.impl.css.property.CSSFontStyle;
import net.zamasoft.foliojet.impl.css.property.FontWeight;
import net.zamasoft.foliojet.impl.css.property.css3.CSSUnicodeRange;
import net.zamasoft.foliojet.impl.css.property.css3.Src;

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
