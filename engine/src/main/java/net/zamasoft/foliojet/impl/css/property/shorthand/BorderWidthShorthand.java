package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.BorderBottomWidth;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.impl.css.property.BorderRightWidth;
import net.zamasoft.foliojet.impl.css.property.BorderTopWidth;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

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