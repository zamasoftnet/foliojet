package net.zamasoft.foliojet.css.impl.property.content;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.CounterSetValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code counter-set}です(CSS Lists 3、2026-08-02)。
 *
 * <p>
 * {@code counter-reset}との違いは<b>新しい入れ子のカウンタを作らない</b>
 * こと。既にどこかで定義されているカウンタは、その一番内側のものへ値を
 * 設定する({@code counter-increment}と同じ探索で、加算ではなく代入)。
 * どこにも無ければ、その要素に作る。省略時の値は0
 * ({@code counter-reset}と同じで、{@code counter-increment}の1とは違う)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class CounterSet extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CounterSet();

	public static Value[] get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	protected CounterSet() {
		super("counter-set");
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
		if (tokens.size() == 1 && ValueUtils.isNone(tokens.peek())) {
			return KeywordValue.NONE;
		}
		final List<CounterSetValue> values = new ArrayList<CounterSetValue>();
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
			return KeywordValue.NONE;
		}
		return new ValueListValue(values.toArray(new Value[values.size()]));
	}
}
