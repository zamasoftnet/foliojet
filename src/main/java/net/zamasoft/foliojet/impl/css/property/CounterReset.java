package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.CounterSetValue;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CounterReset.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CounterReset extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CounterReset();

	public static Value[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_NONE) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	protected CounterReset() {
		super("counter-reset");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.size() == 1 && ValueUtils.isNone(tokens.peek())) {// none
			return NoneValue.NONE_VALUE;
		}

		List<CounterSetValue> values = new ArrayList<CounterSetValue>();

		while (tokens.hasNext()) {
			final String ident = tokens.ident();
			if (ident == null) {
				throw new PropertyException();
			}
			final int value;
			if (tokens.peek() instanceof CssToken.Num num && num.integer()) {
				tokens.next();
				value = num.intValue();
			} else {
				value = 0;
			}
			values.add(new CounterSetValue(ident, value));
		}
		if (values.isEmpty()) {
			return NoneValue.NONE_VALUE;
		}

		final ValueListValue fvalues = new ValueListValue((Value[]) values.toArray(new Value[values.size()]));
		return fvalues;
	}

}