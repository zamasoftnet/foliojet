package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.BorderValueUtils;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.TransparentValue;
import jp.cssj.homare.css.value.Value;
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
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderShorthand();

	protected BorderShorthand() {
		super("border");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(BorderLeftWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomColor.INFO, InheritValue.INHERIT_VALUE);
			return;
		}

		Value width = null;
		Value styleValue = null;
		Value color = null;
		for (; lu != null; lu = lu.getNextLexicalUnit()) {
			if (width == null) {
				width = BorderValueUtils.toBorderWidth(ua, lu);
				if (width != null) {
					continue;
				}
			}
			if (styleValue == null) {
				styleValue = BorderValueUtils.toBorderStyle(lu);
				if (styleValue != null) {
					continue;
				}
			}
			if (color == null) {
				if (ColorValueUtils.isTransparent(lu)) {
					color = TransparentValue.TRANSPARENT_VALUE;
				} else {
					color = ColorValueUtils.toColor(ua, lu);
				}
				if (color != null) {
					continue;
				}
			}
			throw new PropertyException();
		}

		primitives.set(BorderLeftWidth.INFO, width);
		primitives.set(BorderTopWidth.INFO, width);
		primitives.set(BorderRightWidth.INFO, width);
		primitives.set(BorderBottomWidth.INFO, width);
		primitives.set(BorderLeftStyle.INFO, styleValue);
		primitives.set(BorderTopStyle.INFO, styleValue);
		primitives.set(BorderRightStyle.INFO, styleValue);
		primitives.set(BorderBottomStyle.INFO, styleValue);
		primitives.set(BorderLeftColor.INFO, color);
		primitives.set(BorderTopColor.INFO, color);
		primitives.set(BorderRightColor.INFO, color);
		primitives.set(BorderBottomColor.INFO, color);
	}

}