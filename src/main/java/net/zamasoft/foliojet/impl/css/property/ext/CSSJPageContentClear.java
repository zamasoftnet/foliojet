package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJPageContentClear extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJPageContentClear();

	public static String[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		ValueListValue valueList = (ValueListValue) value;
		Value[] values = valueList.getValues();
		String[] names = new String[values.length];
		for (int i = 0; i < names.length; ++i) {
			names[i] = ((StringValue) values[i]).getString();
		}
		return names;
	}

	private CSSJPageContentClear() {
		super("-cssj-page-content-clear");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.isInherit()) {
			return KeywordValue.INHERIT;
		}
		final List<Value> list = new ArrayList<Value>();
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			final Value value;
			if (lu instanceof CssToken.Ident ident) {
				if (ident.is("none")) {
					value = KeywordValue.NONE;
				} else {
					value = new StringValue(ident.name());
				}
			} else if (lu instanceof CssToken.Str str) {
				value = new StringValue(str.value());
			} else {
				throw new PropertyException();
			}
			list.add(value);
		}

		return new ValueListValue((Value[]) list.toArray(new Value[list.size()]));
	}
}