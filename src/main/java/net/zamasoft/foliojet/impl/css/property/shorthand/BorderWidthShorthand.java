package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.BorderBottomWidth;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.impl.css.property.BorderRightWidth;
import net.zamasoft.foliojet.impl.css.property.BorderTopWidth;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderWidthShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderWidthShorthand();

	protected BorderWidthShorthand() {
		super("border-width");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value width1 = BorderValueUtils.toBorderWidth(ua, tokens.next());
		if (width1 == null) {
			throw new PropertyException();
		}
		if (width1 == KeywordValue.INHERIT) {
			primitives.set(BorderLeftWidth.INFO, KeywordValue.INHERIT);
			primitives.set(BorderTopWidth.INFO, KeywordValue.INHERIT);
			primitives.set(BorderRightWidth.INFO, KeywordValue.INHERIT);
			primitives.set(BorderBottomWidth.INFO, KeywordValue.INHERIT);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftWidth.INFO, width1);
			primitives.set(BorderTopWidth.INFO, width1);
			primitives.set(BorderRightWidth.INFO, width1);
			primitives.set(BorderBottomWidth.INFO, width1);
			return;
		}
		final Value width2 = BorderValueUtils.toBorderWidth(ua, tokens.next());
		if (width2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftWidth.INFO, width2);
			primitives.set(BorderTopWidth.INFO, width1);
			primitives.set(BorderRightWidth.INFO, width2);
			primitives.set(BorderBottomWidth.INFO, width1);
			return;
		}
		final Value width3 = BorderValueUtils.toBorderWidth(ua, tokens.next());
		if (width3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderLeftWidth.INFO, width2);
			primitives.set(BorderTopWidth.INFO, width1);
			primitives.set(BorderRightWidth.INFO, width2);
			primitives.set(BorderBottomWidth.INFO, width3);
			return;
		}
		final Value width4 = BorderValueUtils.toBorderWidth(ua, tokens.next());
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