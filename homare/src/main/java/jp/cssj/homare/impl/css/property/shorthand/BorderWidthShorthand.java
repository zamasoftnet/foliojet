package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.BorderValueUtils;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.BorderBottomWidth;
import jp.cssj.homare.impl.css.property.BorderLeftWidth;
import jp.cssj.homare.impl.css.property.BorderRightWidth;
import jp.cssj.homare.impl.css.property.BorderTopWidth;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderWidthShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderWidthShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderWidthShorthand();

	protected BorderWidthShorthand() {
		super("border-width");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value width1 = BorderValueUtils.toBorderWidth(ua, lu);
		if (width1 == null) {
			throw new PropertyException();
		}
		if (width1.getValueType() == Value.TYPE_INHERIT) {
			primitives.set(BorderLeftWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderRightWidth.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomWidth.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftWidth.INFO, width1);
			primitives.set(BorderTopWidth.INFO, width1);
			primitives.set(BorderRightWidth.INFO, width1);
			primitives.set(BorderBottomWidth.INFO, width1);
			return;
		}
		final Value width2 = BorderValueUtils.toBorderWidth(ua, lu);
		if (width2 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftWidth.INFO, width2);
			primitives.set(BorderTopWidth.INFO, width1);
			primitives.set(BorderRightWidth.INFO, width2);
			primitives.set(BorderBottomWidth.INFO, width1);
			return;
		}
		final Value width3 = BorderValueUtils.toBorderWidth(ua, lu);
		if (width3 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(BorderLeftWidth.INFO, width2);
			primitives.set(BorderTopWidth.INFO, width1);
			primitives.set(BorderRightWidth.INFO, width2);
			primitives.set(BorderBottomWidth.INFO, width3);
			return;
		}
		final Value width4 = BorderValueUtils.toBorderWidth(ua, lu);
		if (width4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderLeftWidth.INFO, width4);
		primitives.set(BorderTopWidth.INFO, width1);
		primitives.set(BorderRightWidth.INFO, width2);
		primitives.set(BorderBottomWidth.INFO, width3);
		return;
	}

}