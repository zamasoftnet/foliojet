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
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderBottomShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderBottomShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderBottomShorthand();

	protected BorderBottomShorthand() {
		super("border-bottom");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
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

		primitives.set(BorderBottomWidth.INFO, width);
		primitives.set(BorderBottomStyle.INFO, styleValue);
		primitives.set(BorderBottomColor.INFO, color);
	}
}