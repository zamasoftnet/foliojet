package net.zamasoft.foliojet.css.property;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.impl.css.property.BackgroundAttachment;
import net.zamasoft.foliojet.impl.css.property.BackgroundColor;
import net.zamasoft.foliojet.impl.css.property.BackgroundImage;
import net.zamasoft.foliojet.impl.css.property.BackgroundPosition;
import net.zamasoft.foliojet.impl.css.property.BackgroundRepeat;
import net.zamasoft.foliojet.impl.css.property.BorderBottomColor;
import net.zamasoft.foliojet.impl.css.property.BorderBottomStyle;
import net.zamasoft.foliojet.impl.css.property.BorderBottomWidth;
import net.zamasoft.foliojet.impl.css.property.BorderLeftColor;
import net.zamasoft.foliojet.impl.css.property.BorderLeftStyle;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.impl.css.property.BorderRightColor;
import net.zamasoft.foliojet.impl.css.property.BorderRightStyle;
import net.zamasoft.foliojet.impl.css.property.BorderRightWidth;
import net.zamasoft.foliojet.impl.css.property.BorderTopColor;
import net.zamasoft.foliojet.impl.css.property.BorderTopStyle;
import net.zamasoft.foliojet.impl.css.property.BorderTopWidth;
import net.zamasoft.foliojet.impl.css.property.CounterIncrement;
import net.zamasoft.foliojet.impl.css.property.CounterReset;
import net.zamasoft.foliojet.impl.css.property.MarginBottom;
import net.zamasoft.foliojet.impl.css.property.MarginLeft;
import net.zamasoft.foliojet.impl.css.property.MarginRight;
import net.zamasoft.foliojet.impl.css.property.MarginTop;
import net.zamasoft.foliojet.impl.css.property.PaddingBottom;
import net.zamasoft.foliojet.impl.css.property.PaddingLeft;
import net.zamasoft.foliojet.impl.css.property.PaddingRight;
import net.zamasoft.foliojet.impl.css.property.PaddingTop;
import net.zamasoft.foliojet.impl.css.property.css3.BackgroundSize;
import net.zamasoft.foliojet.impl.css.property.shorthand.BackgroundShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderBottomShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderColorShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderLeftShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderRightShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderStyleShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderTopShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderWidthShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.MarginShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.PaddingShorthand;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PagePropertySet.java 1633 2023-02-12 03:22:32Z miyabe $
 */
public final class PagePropertySet extends PropertySet {
	private Map<String, PropertyInfo> nameToInfo;

	private void put(PropertyInfo p) {
		this.nameToInfo.put(p.getName(), p);
	}

	{
		this.nameToInfo = new HashMap<String, PropertyInfo>();
		this.put(MarginShorthand.INFO);
		this.put(MarginTop.INFO);
		this.put(MarginLeft.INFO);
		this.put(MarginRight.INFO);
		this.put(MarginBottom.INFO);
		this.put(BorderShorthand.INFO);
		this.put(BorderTopShorthand.INFO);
		this.put(BorderLeftShorthand.INFO);
		this.put(BorderRightShorthand.INFO);
		this.put(BorderBottomShorthand.INFO);
		this.put(BorderColorShorthand.INFO);
		this.put(BorderTopColor.INFO);
		this.put(BorderLeftColor.INFO);
		this.put(BorderRightColor.INFO);
		this.put(BorderBottomColor.INFO);
		this.put(BorderStyleShorthand.INFO);
		this.put(BorderTopStyle.INFO);
		this.put(BorderLeftStyle.INFO);
		this.put(BorderRightStyle.INFO);
		this.put(BorderBottomStyle.INFO);
		this.put(BorderWidthShorthand.INFO);
		this.put(BorderTopWidth.INFO);
		this.put(BorderLeftWidth.INFO);
		this.put(BorderRightWidth.INFO);
		this.put(BorderBottomWidth.INFO);
		this.put(PaddingShorthand.INFO);
		this.put(PaddingTop.INFO);
		this.put(PaddingLeft.INFO);
		this.put(PaddingRight.INFO);
		this.put(PaddingBottom.INFO);
		this.put(BackgroundColor.INFO);
		this.put(BackgroundImage.INFO);
		this.put(BackgroundRepeat.INFO);
		this.put(BackgroundAttachment.INFO);
		this.put(BackgroundPosition.INFO_X);
		this.put(BackgroundPosition.INFO_Y);
		this.put(BackgroundShorthand.INFO);
		this.put(CounterIncrement.INFO);
		this.put(CounterReset.INFO);

		this.put(BackgroundSize.INFO_WIDTH);
		this.put(BackgroundSize.INFO_HEIGHT);
		this.nameToInfo = Collections.unmodifiableMap(this.nameToInfo);
	}

	private static final PropertySet INSTANCE = new PagePropertySet();

	private PagePropertySet() {
		// ignore
	}

	protected PropertyInfo getPropertyParser(String name) {
		return (PropertyInfo) nameToInfo.get(name);
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}