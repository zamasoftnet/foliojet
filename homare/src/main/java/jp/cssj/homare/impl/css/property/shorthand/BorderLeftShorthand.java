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
import jp.cssj.homare.impl.css.property.BorderLeftColor;
import jp.cssj.homare.impl.css.property.BorderLeftStyle;
import jp.cssj.homare.impl.css.property.BorderLeftWidth;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderLeftShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderLeftShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderLeftShorthand();

	protected BorderLeftShorthand() {
		super("border-left");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(BorderLeftWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderLeftColor.INFO, InheritValue.INHERIT_VALUE);
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
		primitives.set(BorderLeftStyle.INFO, styleValue);
		primitives.set(BorderLeftColor.INFO, color);
	}

}