package net.zamasoft.foliojet.impl.css.property.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.css.value.QuoteValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.value.ext.CSSJFirstHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJLastHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJPageRefValue;
import net.zamasoft.foliojet.css.value.ext.CSSJTitleValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class Content extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Content();

	public static Value[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	protected Content() {
		super("content");
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
		if (tokens.size() == 1 && (tokens.eat("none") || tokens.eat("normal"))) {
			return KeywordValue.NONE;
		}

		ArrayList<Value> values = new ArrayList<Value>();
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Str str) {// <string>
				values.add(new StringValue(str.value()));
			} else if (lu instanceof CssToken.Uri uriToken) {// <uri>
				try {
					values.add(ValueUtils.toURI(ua, uri, lu));
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, uriToken.uri());
				}
			} else if (lu instanceof CssToken.Ident ident) {// quote
				switch (ident.lower()) {
				case "open-quote":
					values.add(QuoteValue.OPEN_QUOTE_VALUE);
					break;
				case "close-quote":
					values.add(QuoteValue.CLOSE_QUOTE_VALUE);
					break;
				case "no-open-quote":
					values.add(QuoteValue.NO_OPEN_QUOTE_VALUE);
					break;
				case "no-close-quote":
					values.add(QuoteValue.NO_CLOSE_QUOTE_VALUE);
					break;
				case "-cssj-title":
					values.add(CSSJTitleValue.CSSJ_TITLE_VALUE);
					break;
				default:
					throw new PropertyException();
				}
			} else if (lu instanceof CssToken.Func func) {
				if (func.is("counter")) {// <counter>
					values.add(parseCounter(func.argStream()));
				} else if (func.is("counters")) {// <counters>
					values.add(parseCounters(func.argStream()));
				} else if (func.is("attr")) {// attr(x)
					final TokenStream params = func.argStream();
					final String name = params.ident();
					if (name == null || params.hasNext()) {
						throw new PropertyException();
					}
					values.add(new AttrValue(name));
				} else if (func.is("-cssj-heading") || func.is("-cssj-last-heading")) {
					values.add(new CSSJLastHeadingValue(parseHeadingLevel(func.argStream())));
				} else if (func.is("-cssj-first-heading")) {
					values.add(new CSSJFirstHeadingValue(parseHeadingLevel(func.argStream())));
				} else if (func.is("-cssj-page-ref")) {
					values.add(parsePageRef(func.argStream()));
				} else {
					throw new PropertyException();
				}
			} else {
				throw new PropertyException();
			}
		}
		if (values.isEmpty()) {
			throw new PropertyException();
		}
		return new ValueListValue((Value[]) values.toArray(new Value[values.size()]));
	}

	private static CounterValue parseCounter(TokenStream params) throws PropertyException {
		final String id = params.ident();
		if (id == null) {
			throw new PropertyException();
		}
		if (!params.hasNext()) {
			return new CounterValue(id);
		}
		params.eatComma();
		final String listStyle = identOrString(params);
		if (listStyle == null || params.hasNext()) {
			throw new PropertyException();
		}
		final ListStyleTypeValue styleType = GeneratedValueUtils.toListStyleType(listStyle);
		if (styleType == null) {
			throw new PropertyException();
		}
		return new CounterValue(id, styleType.getListStyleType());
	}

	private static CountersValue parseCounters(TokenStream params) throws PropertyException {
		final String id = params.ident();
		if (id == null) {
			throw new PropertyException();
		}
		params.eatComma();
		final String delimiter = params.string();
		if (delimiter == null) {
			throw new PropertyException();
		}
		if (!params.hasNext()) {
			return new CountersValue(id, delimiter);
		}
		params.eatComma();
		final String listStyle = identOrString(params);
		if (listStyle == null || params.hasNext()) {
			throw new PropertyException();
		}
		final ListStyleTypeValue styleType = GeneratedValueUtils.toListStyleType(listStyle);
		if (styleType == null) {
			throw new PropertyException();
		}
		return new CountersValue(id, delimiter, styleType);
	}

	private static int parseHeadingLevel(TokenStream params) throws PropertyException {
		if (!params.hasNext()) {
			return 1;
		}
		final CssToken.Num num = params.number();
		if (num == null || !num.integer() || params.hasNext()) {
			throw new PropertyException();
		}
		return num.intValue();
	}

	private static CSSJPageRefValue parsePageRef(TokenStream params) throws PropertyException {
		final CssToken first = params.next();
		final byte type;
		final String ref;
		if (first instanceof CssToken.Ident ident) {
			type = CSSJPageRefValue.REF;
			ref = ident.name();
		} else if (first instanceof CssToken.Str str) {
			type = CSSJPageRefValue.REF;
			ref = str.value();
		} else if (first instanceof CssToken.Func attr && attr.is("attr")) {
			type = CSSJPageRefValue.ATTR;
			final TokenStream attrParams = attr.argStream();
			ref = attrParams.ident();
			if (ref == null || attrParams.hasNext()) {
				throw new PropertyException("IDが必要です");
			}
		} else {
			throw new PropertyException("IDが必要です");
		}
		if (!params.eatComma()) {
			throw new PropertyException("カンマが必要です");
		}
		final String counter = identOrString(params);
		if (counter == null) {
			throw new PropertyException("カウンタ名が必要です");
		}
		short numberStyleType = ListStyleTypeValue.DECIMAL;
		String separator = null;
		if (params.hasNext()) {
			if (!params.eatComma()) {
				throw new PropertyException("カンマが必要です");
			}
			final String typeStr = params.ident();
			if (typeStr == null) {
				throw new PropertyException("数字タイプが必要です");
			}
			final ListStyleTypeValue typeValue = GeneratedValueUtils.toListStyleType(typeStr);
			if (typeValue == null) {
				throw new PropertyException("数字タイプが不正です");
			}
			numberStyleType = typeValue.getListStyleType();
			if (params.hasNext()) {
				if (!params.eatComma()) {
					throw new PropertyException("カンマが必要です");
				}
				separator = params.string();
				if (separator == null) {
					throw new PropertyException("区切り文字が必要です");
				}
			}
		}
		return new CSSJPageRefValue(type, ref, counter, numberStyleType, separator);
	}

	private static String identOrString(TokenStream params) {
		final CssToken token = params.next();
		if (token instanceof CssToken.Ident ident) {
			return ident.name();
		}
		if (token instanceof CssToken.Str str) {
			return str.value();
		}
		return null;
	}
}