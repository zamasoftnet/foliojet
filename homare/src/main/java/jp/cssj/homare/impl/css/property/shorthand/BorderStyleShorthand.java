package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.BorderValueUtils;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.BorderBottomStyle;
import jp.cssj.homare.impl.css.property.BorderLeftStyle;
import jp.cssj.homare.impl.css.property.BorderRightStyle;
import jp.cssj.homare.impl.css.property.BorderTopStyle;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderStyleShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderStyleShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderStyleShorthand();

	protected BorderStyleShorthand() {
		super("border-style");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value style1 = BorderValueUtils.toBorderStyle(lu);
		if (style1 == null) {
			throw new PropertyException();
		}
		if (style1.getValueType() == Value.TYPE_INHERIT) {
			primitives.set(BorderLeftStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomStyle.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftStyle.INFO, style1);
			primitives.set(BorderTopStyle.INFO, style1);
			primitives.set(BorderRightStyle.INFO, style1);
			primitives.set(BorderBottomStyle.INFO, style1);
			return;
		}
		final Value style2 = BorderValueUtils.toBorderStyle(lu);
		if (style2 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftStyle.INFO, style2);
			primitives.set(BorderTopStyle.INFO, style1);
			primitives.set(BorderRightStyle.INFO, style2);
			primitives.set(BorderBottomStyle.INFO, style1);
			return;
		}
		final Value style3 = BorderValueUtils.toBorderStyle(lu);
		if (style3 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftStyle.INFO, style2);
			primitives.set(BorderTopStyle.INFO, style1);
			primitives.set(BorderRightStyle.INFO, style2);
			primitives.set(BorderBottomStyle.INFO, style3);
			return;
		}
		final Value style4 = BorderValueUtils.toBorderStyle(lu);
		if (style4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderLeftStyle.INFO, style4);
		primitives.set(BorderTopStyle.INFO, style1);
		primitives.set(BorderRightStyle.INFO, style2);
		primitives.set(BorderBottomStyle.INFO, style3);
	}

}