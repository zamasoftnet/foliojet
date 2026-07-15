package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.impl.css.property.border.BorderStyle;
import net.zamasoft.foliojet.impl.css.property.box.Side;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderStyleShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderStyleShorthand();

	protected BorderStyleShorthand() {
		super("border-style");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value style1 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style1 == null) {
			throw new PropertyException();
		}
		if (style1 == KeywordValue.INHERIT) {
			primitives.set(BorderStyle.LEFT, KeywordValue.INHERIT);
			primitives.set(BorderStyle.TOP, KeywordValue.INHERIT);
			primitives.set(BorderStyle.RIGHT, KeywordValue.INHERIT);
			primitives.set(BorderStyle.BOTTOM, KeywordValue.INHERIT);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderStyle.LEFT, style1);
			primitives.set(BorderStyle.TOP, style1);
			primitives.set(BorderStyle.RIGHT, style1);
			primitives.set(BorderStyle.BOTTOM, style1);
			return;
		}
		final Value style2 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderStyle.LEFT, style2);
			primitives.set(BorderStyle.TOP, style1);
			primitives.set(BorderStyle.RIGHT, style2);
			primitives.set(BorderStyle.BOTTOM, style1);
			return;
		}
		final Value style3 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(BorderStyle.LEFT, style2);
			primitives.set(BorderStyle.TOP, style1);
			primitives.set(BorderStyle.RIGHT, style2);
			primitives.set(BorderStyle.BOTTOM, style3);
			return;
		}
		final Value style4 = BorderValueUtils.toBorderStyle(tokens.next());
		if (style4 == null) {
			throw new PropertyException();
		}
		primitives.set(BorderStyle.LEFT, style4);
		primitives.set(BorderStyle.TOP, style1);
		primitives.set(BorderStyle.RIGHT, style2);
		primitives.set(BorderStyle.BOTTOM, style3);
	}

}