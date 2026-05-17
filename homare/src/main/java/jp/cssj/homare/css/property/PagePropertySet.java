package jp.cssj.homare.css.property;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.homare.impl.css.property.BackgroundAttachment;
import jp.cssj.homare.impl.css.property.BackgroundColor;
import jp.cssj.homare.impl.css.property.BackgroundImage;
import jp.cssj.homare.impl.css.property.BackgroundPosition;
import jp.cssj.homare.impl.css.property.BackgroundRepeat;
import jp.cssj.homare.impl.css.property.BorderBottomColor;
import jp.cssj.homare.impl.css.property.BorderBottomStyle;
import jp.cssj.homare.impl.css.property.BorderBottomWidth;
import jp.cssj.homare.impl.css.property.BorderLeftColor;
import jp.cssj.homare.impl.css.property.BorderLeftStyle;
import jp.cssj.homare.impl.css.property.BorderLeftWidth;
import jp.cssj.homare.impl.css.property.BorderRightColor;
import jp.cssj.homare.impl.css.property.BorderRightStyle;
import jp.cssj.homare.impl.css.property.BorderRightWidth;
import jp.cssj.homare.impl.css.property.BorderTopColor;
import jp.cssj.homare.impl.css.property.BorderTopStyle;
import jp.cssj.homare.impl.css.property.BorderTopWidth;
import jp.cssj.homare.impl.css.property.CounterIncrement;
import jp.cssj.homare.impl.css.property.CounterReset;
import jp.cssj.homare.impl.css.property.MarginBottom;
import jp.cssj.homare.impl.css.property.MarginLeft;
import jp.cssj.homare.impl.css.property.MarginRight;
import jp.cssj.homare.impl.css.property.MarginTop;
import jp.cssj.homare.impl.css.property.PaddingBottom;
import jp.cssj.homare.impl.css.property.PaddingLeft;
import jp.cssj.homare.impl.css.property.PaddingRight;
import jp.cssj.homare.impl.css.property.PaddingTop;
import jp.cssj.homare.impl.css.property.css3.BackgroundSize;
import jp.cssj.homare.impl.css.property.shorthand.BackgroundShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderBottomShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderColorShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderLeftShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderRightShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderStyleShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderTopShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderWidthShorthand;
import jp.cssj.homare.impl.css.property.shorthand.MarginShorthand;
import jp.cssj.homare.impl.css.property.shorthand.PaddingShorthand;

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