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
		// Descriptors for display during loading and metric adjustments (2026-08-29). Accept
		// and ignore them: they have no meaning for print, or actual font metrics are used.
		for (final String name : new String[] { "font-display", "size-adjust", "ascent-override",
				"descent-override", "line-gap-override", "font-variation-settings",
				"font-feature-settings", "font-named-instance" }) {
			put(new net.zamasoft.foliojet.css.property.IgnoredPropertyInfo(name));
		}
		// The font-stretch descriptor defines the face's width class (2026-08-29, FontFace.widthClass)
		put(CSSFontFamily.INFO, Src.INFO, FontWeight.INFO, CSSFontStyle.INFO, CSSUnicodeRange.INFO,
				net.zamasoft.foliojet.css.impl.property.font.FontStretch.INFO,
				net.zamasoft.foliojet.css.impl.property.font.FontVariationSettings.INFO);
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}
