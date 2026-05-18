package net.zamasoft.foliojet.css.property;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.impl.css.property.CSSFontFamily;
import net.zamasoft.foliojet.impl.css.property.CSSFontStyle;
import net.zamasoft.foliojet.impl.css.property.FontWeight;
import net.zamasoft.foliojet.impl.css.property.css3.CSSUnicodeRange;
import net.zamasoft.foliojet.impl.css.property.css3.Src;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: FontFacePropertySet.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class FontFacePropertySet extends PropertySet {
	private Map<String, PropertyInfo> nameToInfo;

	private void put(PropertyInfo p) {
		this.nameToInfo.put(p.getName(), p);
	}

	{
		this.nameToInfo = new HashMap<String, PropertyInfo>();
		this.put(CSSFontFamily.INFO);
		this.put(Src.INFO);
		this.put(FontWeight.INFO);
		this.put(CSSFontStyle.INFO);
		this.put(CSSUnicodeRange.INFO);

		this.nameToInfo = Collections.unmodifiableMap(this.nameToInfo);
	}

	private static final PropertySet INSTANCE = new FontFacePropertySet();

	private FontFacePropertySet() {
		// ignore
	}

	protected PropertyInfo getPropertyParser(String name) {
		return (PropertyInfo) nameToInfo.get(name);
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}