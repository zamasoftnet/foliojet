package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.TransparentValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.BorderBottomColor;
import jp.cssj.homare.impl.css.property.BorderLeftColor;
import jp.cssj.homare.impl.css.property.BorderRightColor;
import jp.cssj.homare.impl.css.property.BorderTopColor;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderColorShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderColorShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderColorShorthand();

	protected BorderColorShorthand() {
		super("border-color");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(BorderLeftColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomColor.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		final Value color1;
		if (ColorValueUtils.isTransparent(lu)) {
			color1 = TransparentValue.TRANSPARENT_VALUE;
		} else {
			color1 = ColorValueUtils.toColor(ua, lu);
		}
		if (color1 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftColor.INFO, color1);
			primitives.set(BorderTopColor.INFO, color1);
			primitives.set(BorderRightColor.INFO, color1);
			primitives.set(BorderBottomColor.INFO, color1);
			return;
		}
		final Value color2;
		if (ColorValueUtils.isTransparent(lu)) {
			color2 = TransparentValue.TRANSPARENT_VALUE;
		} else {
			color2 = ColorValueUtils.toColor(ua, lu);
		}
		if (color2 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftColor.INFO, color2);
			primitives.set(BorderTopColor.INFO, color1);
			primitives.set(BorderRightColor.INFO, color2);
			primitives.set(BorderBottomColor.INFO, color1);
			return;
		}
		final Value color3;
		if (ColorValueUtils.isTransparent(lu)) {
			color3 = TransparentValue.TRANSPARENT_VALUE;
		} else {
			color3 = ColorValueUtils.toColor(ua, lu);
		}
		if (color3 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftColor.INFO, color2);
			primitives.set(BorderTopColor.INFO, color1);
			primitives.set(BorderRightColor.INFO, color2);
			primitives.set(BorderBottomColor.INFO, color3);
			return;
		}
		final Value color4;
		if (ColorValueUtils.isTransparent(lu)) {
			color4 = TransparentValue.TRANSPARENT_VALUE;
		} else {
			color4 = ColorValueUtils.toColor(ua, lu);
		}
		if (color4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderLeftColor.INFO, color4);
		primitives.set(BorderTopColor.INFO, color1);
		primitives.set(BorderRightColor.INFO, color2);
		primitives.set(BorderBottomColor.INFO, color3);
	}
}