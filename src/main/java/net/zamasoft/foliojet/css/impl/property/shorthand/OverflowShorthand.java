package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.box.Overflow;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * overflow一括指定。CSS Overflow 3の2値構文
 * ({@code overflow: <x> <y>})に対応し、1値なら両軸へ展開します。
 *
 * @author MIYABE Tatsuhiko
 */
public class OverflowShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new OverflowShorthand();

	protected OverflowShorthand() {
		super("overflow");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { Overflow.INFO_X, Overflow.INFO_Y };
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value x = Overflow.toValue(lu);
		if (x == null) {
			throw new PropertyException();
		}
		Value y = x;
		if (tokens.hasNext()) {
			final CssToken lu2 = tokens.next();
			y = Overflow.toValue(lu2);
			if (y == null || tokens.hasNext()) {
				throw new PropertyException();
			}
		}
		primitives.set(Overflow.INFO_X, x);
		primitives.set(Overflow.INFO_Y, y);
	}

}
