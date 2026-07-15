package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.PaddingBottom;
import net.zamasoft.foliojet.impl.css.property.PaddingLeft;
import net.zamasoft.foliojet.impl.css.property.PaddingRight;
import net.zamasoft.foliojet.impl.css.property.PaddingTop;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class PaddingShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new PaddingShorthand();

	protected PaddingShorthand() {
		super("padding");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value padding1 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding1 == null) {
			throw new PropertyException();
		}
		if (padding1 == KeywordValue.INHERIT) {
			primitives.set(PaddingLeft.INFO, KeywordValue.INHERIT);
			primitives.set(PaddingTop.INFO, KeywordValue.INHERIT);
			primitives.set(PaddingRight.INFO, KeywordValue.INHERIT);
			primitives.set(PaddingBottom.INFO, KeywordValue.INHERIT);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(PaddingLeft.INFO, padding1);
			primitives.set(PaddingTop.INFO, padding1);
			primitives.set(PaddingRight.INFO, padding1);
			primitives.set(PaddingBottom.INFO, padding1);
			return;
		}
		final Value padding2 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(PaddingLeft.INFO, padding2);
			primitives.set(PaddingTop.INFO, padding1);
			primitives.set(PaddingRight.INFO, padding2);
			primitives.set(PaddingBottom.INFO, padding1);
			return;
		}
		final Value padding3 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(PaddingLeft.INFO, padding2);
			primitives.set(PaddingTop.INFO, padding1);
			primitives.set(PaddingRight.INFO, padding2);
			primitives.set(PaddingBottom.INFO, padding3);
			return;
		}
		final Value padding4 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding4 == null) {
			throw new PropertyException();
		}
		primitives.set(PaddingLeft.INFO, padding4);
		primitives.set(PaddingTop.INFO, padding1);
		primitives.set(PaddingRight.INFO, padding2);
		primitives.set(PaddingBottom.INFO, padding3);
	}

}