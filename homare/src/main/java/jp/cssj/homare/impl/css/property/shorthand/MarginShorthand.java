package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.BoxValueUtils;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.MarginBottom;
import jp.cssj.homare.impl.css.property.MarginLeft;
import jp.cssj.homare.impl.css.property.MarginRight;
import jp.cssj.homare.impl.css.property.MarginTop;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: MarginShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class MarginShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new MarginShorthand();

	protected MarginShorthand() {
		super("margin");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value margin1 = BoxValueUtils.toMarginWidth(ua, lu);
		if (margin1 == null) {
			throw new PropertyException();
		}
		if (margin1.getValueType() == Value.TYPE_INHERIT) {
			primitives.set(MarginTop.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(MarginRight.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(MarginBottom.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(MarginLeft.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(MarginTop.INFO, margin1);
			primitives.set(MarginRight.INFO, margin1);
			primitives.set(MarginBottom.INFO, margin1);
			primitives.set(MarginLeft.INFO, margin1);
			return;
		}
		final Value margin2 = BoxValueUtils.toMarginWidth(ua, lu);
		if (margin2 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(MarginTop.INFO, margin1);
			primitives.set(MarginRight.INFO, margin2);
			primitives.set(MarginBottom.INFO, margin1);
			primitives.set(MarginLeft.INFO, margin2);
			return;
		}
		final Value margin3 = BoxValueUtils.toMarginWidth(ua, lu);
		if (margin3 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(MarginTop.INFO, margin1);
			primitives.set(MarginRight.INFO, margin2);
			primitives.set(MarginBottom.INFO, margin3);
			primitives.set(MarginLeft.INFO, margin2);
			return;
		}
		final Value margin4 = BoxValueUtils.toMarginWidth(ua, lu);
		if (margin4 == null) {
			throw new PropertyException();
		}
		primitives.set(MarginTop.INFO, margin1);
		primitives.set(MarginRight.INFO, margin2);
		primitives.set(MarginBottom.INFO, margin3);
		primitives.set(MarginLeft.INFO, margin4);
		return;
	}

}