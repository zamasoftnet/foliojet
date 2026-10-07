package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code order} (Flex F5a, 2026-08-02:
 * consult-codex-2026-08-02-flexbox.txt F5a). Integer (negative allowed), default 0.
 * Changes only visual order; Tagged PDF reading order and structure remain in source order
 * (FlexBuilder bind preserves source order).
 *
 * @author MIYABE Tatsuhiko
 */
public class OrderProperty extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new OrderProperty();

	public static int get(CSSStyle style) {
		return ((IntegerValue) style.get(INFO)).getInteger();
	}

	private OrderProperty() {
		super("order");
	}

	public Value getDefault(CSSStyle style) {
		return IntegerValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.next() instanceof CssToken.Num num && num.integer() && !tokens.hasNext()) {
			return IntegerValue.create(num.intValue());
		}
		throw new PropertyException();
	}
}
