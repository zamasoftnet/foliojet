package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.MarginBottom;
import net.zamasoft.foliojet.impl.css.property.MarginLeft;
import net.zamasoft.foliojet.impl.css.property.MarginRight;
import net.zamasoft.foliojet.impl.css.property.MarginTop;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: MarginShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class MarginShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new MarginShorthand();

	protected MarginShorthand() {
		super("margin");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value margin1 = BoxValueUtils.toMarginWidth(ua, tokens.next());
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
		if (!tokens.hasNext()) {
			primitives.set(MarginTop.INFO, margin1);
			primitives.set(MarginRight.INFO, margin1);
			primitives.set(MarginBottom.INFO, margin1);
			primitives.set(MarginLeft.INFO, margin1);
			return;
		}
		final Value margin2 = BoxValueUtils.toMarginWidth(ua, tokens.next());
		if (margin2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(MarginTop.INFO, margin1);
			primitives.set(MarginRight.INFO, margin2);
			primitives.set(MarginBottom.INFO, margin1);
			primitives.set(MarginLeft.INFO, margin2);
			return;
		}
		final Value margin3 = BoxValueUtils.toMarginWidth(ua, tokens.next());
		if (margin3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(MarginTop.INFO, margin1);
			primitives.set(MarginRight.INFO, margin2);
			primitives.set(MarginBottom.INFO, margin3);
			primitives.set(MarginLeft.INFO, margin2);
			return;
		}
		final Value margin4 = BoxValueUtils.toMarginWidth(ua, tokens.next());
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