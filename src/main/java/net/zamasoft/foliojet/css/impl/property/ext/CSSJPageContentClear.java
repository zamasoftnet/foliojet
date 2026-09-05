package net.zamasoft.foliojet.css.impl.property.ext;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;

/** 再生成を止める名前の列です。R1a では値を保持し、配置には作用しません。 */
public final class CSSJPageContentClear extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJPageContentClear();

	private CSSJPageContentClear() {
		super("-cssj-page-content-clear");
	}

	public static String[] get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return new String[0];
		}
		final Value[] values = ((ValueListValue) value).getValues();
		final String[] names = new String[values.length];
		for (int i = 0; i < values.length; ++i) {
			names[i] = ((StringValue) values[i]).getString();
		}
		return names;
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri)
			throws PropertyException {
		final List<Value> names = new ArrayList<Value>();
		do {
			final String name = CSSJPageContent.parseName(tokens);
			if (name == null) {
				if (names.isEmpty() && !tokens.hasNext()) {
					return KeywordValue.NONE;
				}
				throw new PropertyException();
			}
			names.add(new StringValue(name));
		} while (tokens.hasNext());
		return new ValueListValue(names.toArray(new Value[names.size()]));
	}
}
