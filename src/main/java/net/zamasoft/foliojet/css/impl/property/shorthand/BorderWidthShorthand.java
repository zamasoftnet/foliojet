package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.box.Side;

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
			primitives.set(BorderWidth.LEFT, KeywordValue.INHERIT);
			primitives.set(BorderWidth.TOP, KeywordValue.INHERIT);
			primitives.set(BorderWidth.RIGHT, KeywordValue.INHERIT);
			primitives.set(BorderWidth.BOTTOM, KeywordValue.INHERIT);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderWidth.LEFT, width1);
			primitives.set(BorderWidth.TOP, width1);
			primitives.set(BorderWidth.RIGHT, width1);
			primitives.set(BorderWidth.BOTTOM, width1);
			return;
		}
		final Value width2 = BorderValueUtils.toBorderWidth(ua, tokens.next());
		if (width2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderWidth.LEFT, width2);
			primitives.set(BorderWidth.TOP, width1);
			primitives.set(BorderWidth.RIGHT, width2);
			primitives.set(BorderWidth.BOTTOM, width1);
			return;
		}
		final Value width3 = BorderValueUtils.toBorderWidth(ua, tokens.next());
		if (width3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderWidth.LEFT, width2);
			primitives.set(BorderWidth.TOP, width1);
			primitives.set(BorderWidth.RIGHT, width2);
			primitives.set(BorderWidth.BOTTOM, width3);
			return;
		}
		final Value width4 = BorderValueUtils.toBorderWidth(ua, tokens.next());
		if (width4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderWidth.LEFT, width4);
		primitives.set(BorderWidth.TOP, width1);
		primitives.set(BorderWidth.RIGHT, width2);
		primitives.set(BorderWidth.BOTTOM, width3);
		return;
	}

}