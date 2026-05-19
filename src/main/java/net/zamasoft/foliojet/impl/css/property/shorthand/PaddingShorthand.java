package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.PaddingBottom;
import net.zamasoft.foliojet.impl.css.property.PaddingLeft;
import net.zamasoft.foliojet.impl.css.property.PaddingRight;
import net.zamasoft.foliojet.impl.css.property.PaddingTop;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PaddingShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PaddingShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new PaddingShorthand();

	protected PaddingShorthand() {
		super("padding");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value padding1 = BoxValueUtils.toPositiveLength(ua, lu);
		if (padding1 == null) {
			throw new PropertyException();
		}
		if (padding1.getValueType() == Value.TYPE_INHERIT) {
			primitives.set(PaddingLeft.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(PaddingTop.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(PaddingRight.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(PaddingBottom.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(PaddingLeft.INFO, padding1);
			primitives.set(PaddingTop.INFO, padding1);
			primitives.set(PaddingRight.INFO, padding1);
			primitives.set(PaddingBottom.INFO, padding1);
			return;
		}
		final Value padding2 = BoxValueUtils.toPositiveLength(ua, lu);
		if (padding2 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(PaddingLeft.INFO, padding2);
			primitives.set(PaddingTop.INFO, padding1);
			primitives.set(PaddingRight.INFO, padding2);
			primitives.set(PaddingBottom.INFO, padding1);
			return;
		}
		final Value padding3 = BoxValueUtils.toPositiveLength(ua, lu);
		if (padding3 == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			primitives.set(PaddingLeft.INFO, padding2);
			primitives.set(PaddingTop.INFO, padding1);
			primitives.set(PaddingRight.INFO, padding2);
			primitives.set(PaddingBottom.INFO, padding3);
			return;
		}
		final Value padding4 = BoxValueUtils.toPositiveLength(ua, lu);
		if (padding4 == null) {
			throw new PropertyException();
		}
		primitives.set(PaddingLeft.INFO, padding4);
		primitives.set(PaddingTop.INFO, padding1);
		primitives.set(PaddingRight.INFO, padding2);
		primitives.set(PaddingBottom.INFO, padding3);
	}

}