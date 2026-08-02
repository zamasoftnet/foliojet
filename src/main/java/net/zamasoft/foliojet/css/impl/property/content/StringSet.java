package net.zamasoft.foliojet.css.impl.property.content;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.ContentFunctionValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringSetEntryValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * GCPM {@code string-set: <ident> <value>+ [, <ident> <value>+]*}。
 * {@code <value>}は{@code <string>}/{@code counter()}/{@code counters()}/
 * {@code attr()}/{@code content()}。
 *
 * @author MIYABE Tatsuhiko
 */
public class StringSet extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new StringSet();

	public static Value[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	protected StringSet() {
		super("string-set");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.size() == 1 && ValueUtils.isNone(tokens.peek())) {// none
			return KeywordValue.NONE;
		}

		List<Value> entries = new ArrayList<Value>();
		for (TokenStream group : tokens.splitComma()) {
			final String name = group.ident();
			if (name == null) {
				throw new PropertyException();
			}
			List<Value> parts = new ArrayList<Value>();
			while (group.hasNext()) {
				final CssToken lu = group.next();
				if (lu instanceof CssToken.Str str) {
					parts.add(new StringValue(str.value()));
				} else if (lu instanceof CssToken.Func func) {
					if (func.is("counter")) {
						parts.add(Content.parseCounter(func.argStream(), ua));
					} else if (func.is("counters")) {
						parts.add(Content.parseCounters(func.argStream(), ua));
					} else if (func.is("attr")) {
						final TokenStream params = func.argStream();
						final String attrName = params.ident();
						if (attrName == null || params.hasNext()) {
							throw new PropertyException();
						}
						parts.add(new AttrValue(attrName));
					} else if (func.is("content")) {
						if (func.argStream().hasNext()) {
							throw new PropertyException();
						}
						parts.add(ContentFunctionValue.INSTANCE);
					} else {
						throw new PropertyException();
					}
				} else {
					throw new PropertyException();
				}
			}
			if (parts.isEmpty()) {
				throw new PropertyException();
			}
			entries.add(new StringSetEntryValue(name, (Value[]) parts.toArray(new Value[parts.size()])));
		}
		if (entries.isEmpty()) {
			throw new PropertyException();
		}
		return new ValueListValue((Value[]) entries.toArray(new Value[entries.size()]));
	}
}
