package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Side;

/**
 * @author MIYABE Tatsuhiko
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
		if (margin1 == KeywordValue.INHERIT) {
			primitives.set(Margin.TOP, KeywordValue.INHERIT);
			primitives.set(Margin.RIGHT, KeywordValue.INHERIT);
			primitives.set(Margin.BOTTOM, KeywordValue.INHERIT);
			primitives.set(Margin.LEFT, KeywordValue.INHERIT);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(Margin.TOP, margin1);
			primitives.set(Margin.RIGHT, margin1);
			primitives.set(Margin.BOTTOM, margin1);
			primitives.set(Margin.LEFT, margin1);
			return;
		}
		final Value margin2 = BoxValueUtils.toMarginWidth(ua, tokens.next());
		if (margin2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(Margin.TOP, margin1);
			primitives.set(Margin.RIGHT, margin2);
			primitives.set(Margin.BOTTOM, margin1);
			primitives.set(Margin.LEFT, margin2);
			return;
		}
		final Value margin3 = BoxValueUtils.toMarginWidth(ua, tokens.next());
		if (margin3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(Margin.TOP, margin1);
			primitives.set(Margin.RIGHT, margin2);
			primitives.set(Margin.BOTTOM, margin3);
			primitives.set(Margin.LEFT, margin2);
			return;
		}
		final Value margin4 = BoxValueUtils.toMarginWidth(ua, tokens.next());
		if (margin4 == null) {
			throw new PropertyException();
		}
		primitives.set(Margin.TOP, margin1);
		primitives.set(Margin.RIGHT, margin2);
		primitives.set(Margin.BOTTOM, margin3);
		primitives.set(Margin.LEFT, margin4);
		return;
	}

}