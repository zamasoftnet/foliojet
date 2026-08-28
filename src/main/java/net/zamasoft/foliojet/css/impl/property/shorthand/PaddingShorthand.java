package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.box.Padding;

/**
 * @author MIYABE Tatsuhiko
 */
public class PaddingShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new PaddingShorthand();

	protected PaddingShorthand() {
		super("padding");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { Padding.TOP, Padding.RIGHT, Padding.BOTTOM, Padding.LEFT };
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value padding1 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding1 == null) {
			throw new PropertyException();
		}
		if (padding1 == KeywordValue.INHERIT) {
			primitives.set(Padding.LEFT, KeywordValue.INHERIT);
			primitives.set(Padding.TOP, KeywordValue.INHERIT);
			primitives.set(Padding.RIGHT, KeywordValue.INHERIT);
			primitives.set(Padding.BOTTOM, KeywordValue.INHERIT);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(Padding.LEFT, padding1);
			primitives.set(Padding.TOP, padding1);
			primitives.set(Padding.RIGHT, padding1);
			primitives.set(Padding.BOTTOM, padding1);
			return;
		}
		final Value padding2 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(Padding.LEFT, padding2);
			primitives.set(Padding.TOP, padding1);
			primitives.set(Padding.RIGHT, padding2);
			primitives.set(Padding.BOTTOM, padding1);
			return;
		}
		final Value padding3 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(Padding.LEFT, padding2);
			primitives.set(Padding.TOP, padding1);
			primitives.set(Padding.RIGHT, padding2);
			primitives.set(Padding.BOTTOM, padding3);
			return;
		}
		final Value padding4 = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (padding4 == null) {
			throw new PropertyException();
		}
		primitives.set(Padding.LEFT, padding4);
		primitives.set(Padding.TOP, padding1);
		primitives.set(Padding.RIGHT, padding2);
		primitives.set(Padding.BOTTOM, padding3);
	}

}