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
 * GCPM {@code string-set: <ident> <value>+ [, <ident> <value>+]*}.
 * {@code <value>} is {@code <string>}/{@code counter()}/{@code counters()}/
 * {@code attr()}/{@code content()} ({@code text}, {@code before}, {@code after} or {@code first-letter}).
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

	/**
	 * One item of a GCPM {@code <content-list>} as string-set and bookmark-label take it (2026-10-23):
	 * {@code <string>}, {@code counter()}, {@code counters()}, {@code attr()} or
	 * {@code content( [ text | before | after | first-letter ]? )}.
	 */
	static Value parsePart(final CssToken lu, final UserAgent ua) throws PropertyException {
		if (lu instanceof CssToken.Str str) {
			return new StringValue(str.value());
		}
		if (!(lu instanceof CssToken.Func func)) {
			throw new PropertyException();
		}
		if (func.is("counter")) {
			return Content.parseCounter(func.argStream(), ua);
		}
		if (func.is("counters")) {
			return Content.parseCounters(func.argStream(), ua);
		}
		if (func.is("attr")) {
			final TokenStream params = func.argStream();
			final String attrName = params.ident();
			if (attrName == null || params.hasNext()) {
				throw new PropertyException();
			}
			return new AttrValue(attrName);
		}
		if (func.is("content")) {
			final TokenStream params = func.argStream();
			final boolean hasArgument = params.hasNext();
			final String ident = hasArgument ? params.ident() : null;
			final ContentFunctionValue content = hasArgument && ident == null ? null
					: ContentFunctionValue.forStringSet(ident == null ? null : ident.toLowerCase(java.util.Locale.ROOT));
			if (content == null || params.hasNext()) {
				throw new PropertyException();
			}
			return content;
		}
		throw new PropertyException();
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
				parts.add(parsePart(group.next(), ua));
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
