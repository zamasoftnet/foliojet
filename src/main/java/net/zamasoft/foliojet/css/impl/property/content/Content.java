package net.zamasoft.foliojet.css.impl.property.content;

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
import net.zamasoft.foliojet.css.value.LeaderValue;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.css.value.QuoteValue;
import net.zamasoft.foliojet.css.value.StringFunctionValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TargetCounterValue;
import net.zamasoft.foliojet.css.value.TargetTextValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.NamedStringState;
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
				} else if (func.is("-cssj-page-ref")) {
					values.add(parsePageRef(func.argStream()));
				} else if (func.is("target-counter")) {
					values.add(parseTargetCounter(func.argStream()));
				} else if (func.is("target-counters")) {
					values.add(parseTargetCounters(func.argStream()));
				} else if (func.is("target-text")) {
					values.add(parseTargetText(func.argStream()));
				} else if (func.is("string")) {
					values.add(parseStringFunc(func.argStream()));
				} else if (func.is("leader")) {
					values.add(parseLeader(func.argStream()));
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

	/** package-visible: {@link StringSet}が{@code counter()}パースを再利用する。 */
	static CounterValue parseCounter(TokenStream params) throws PropertyException {
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

	/** package-visible: {@link StringSet}が{@code counters()}パースを再利用する。 */
	static CountersValue parseCounters(TokenStream params) throws PropertyException {
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

	/** {@code string(name[, first|start|last|first-except])} */
	private static StringFunctionValue parseStringFunc(TokenStream params) throws PropertyException {
		final String name = params.ident();
		if (name == null) {
			throw new PropertyException();
		}
		byte mode = NamedStringState.FIRST;
		if (params.hasNext()) {
			if (!params.eatComma()) {
				throw new PropertyException();
			}
			final String modeStr = params.ident();
			if (modeStr == null) {
				throw new PropertyException();
			}
			switch (modeStr) {
			case "first":
				mode = NamedStringState.FIRST;
				break;
			case "start":
				mode = NamedStringState.START;
				break;
			case "last":
				mode = NamedStringState.LAST;
				break;
			case "first-except":
				mode = NamedStringState.FIRST_EXCEPT;
				break;
			default:
				throw new PropertyException();
			}
			if (params.hasNext()) {
				throw new PropertyException();
			}
		}
		return new StringFunctionValue(name, mode);
	}

	/**
	 * {@code leader(dotted|solid|space|<string>)}(css-content-3、
	 * consult-codex-2026-07-31-leader.txt)。キーワードは正規化し、
	 * 改行除去後に空になる文字列は構文エラー(ゼロ周期の無限反復を
	 * 避ける)。
	 */
	private static LeaderValue parseLeader(TokenStream params) throws PropertyException {
		final CssToken first = params.hasNext() ? params.next() : null;
		if (first == null || params.hasNext()) {
			throw new PropertyException();
		}
		if (first instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "dotted":
				return LeaderValue.DOTTED;
			case "solid":
				return LeaderValue.SOLID;
			case "space":
				return LeaderValue.SPACE;
			default:
				throw new PropertyException();
			}
		}
		if (first instanceof CssToken.Str str) {
			final String pattern = str.value().replaceAll("[\\r\\n]", "");
			if (pattern.isEmpty()) {
				throw new PropertyException();
			}
			return new LeaderValue(pattern);
		}
		throw new PropertyException();
	}

	/** ref/attr()解決先の型+ID文字列。 */
	private record TargetRef(byte type, String ref) {
	}

	/**
	 * 参照先の解決(ident/string/url()はREF、attr()はATTR)。
	 * {@code -cssj-page-ref()}・{@code target-counter()}・
	 * {@code target-counters()}・{@code target-text()}で共通。
	 */
	private static TargetRef parseTargetRef(TokenStream params) throws PropertyException {
		final CssToken first = params.next();
		if (first instanceof CssToken.Ident ident) {
			return new TargetRef(TargetCounterValue.REF, ident.name());
		} else if (first instanceof CssToken.Str str) {
			return new TargetRef(TargetCounterValue.REF, str.value());
		} else if (first instanceof CssToken.Uri uri) {
			return new TargetRef(TargetCounterValue.REF, uri.uri());
		} else if (first instanceof CssToken.Func attr && attr.is("attr")) {
			final TokenStream attrParams = attr.argStream();
			final String ref = attrParams.ident();
			if (ref == null || attrParams.hasNext()) {
				throw new PropertyException("IDが必要です");
			}
			return new TargetRef(TargetCounterValue.ATTR, ref);
		} else {
			throw new PropertyException("IDが必要です");
		}
	}

	private static TargetCounterValue parsePageRef(TokenStream params) throws PropertyException {
		final TargetRef target = parseTargetRef(params);
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
		return new TargetCounterValue(target.type(), target.ref(), counter, numberStyleType, separator);
	}

	/** {@code target-counter(target, counter-name, counter-style?)} */
	private static TargetCounterValue parseTargetCounter(TokenStream params) throws PropertyException {
		final TargetRef target = parseTargetRef(params);
		if (!params.eatComma()) {
			throw new PropertyException("カンマが必要です");
		}
		final String counter = params.ident();
		if (counter == null) {
			throw new PropertyException("カウンタ名が必要です");
		}
		short numberStyleType = ListStyleTypeValue.DECIMAL;
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
		}
		return new TargetCounterValue(target.type(), target.ref(), counter, numberStyleType, null);
	}

	/** {@code target-counters(target, counter-name, separator, counter-style?)} */
	private static TargetCounterValue parseTargetCounters(TokenStream params) throws PropertyException {
		final TargetRef target = parseTargetRef(params);
		if (!params.eatComma()) {
			throw new PropertyException("カンマが必要です");
		}
		final String counter = params.ident();
		if (counter == null) {
			throw new PropertyException("カウンタ名が必要です");
		}
		if (!params.eatComma()) {
			throw new PropertyException("カンマが必要です");
		}
		final String separator = params.string();
		if (separator == null) {
			throw new PropertyException("区切り文字が必要です");
		}
		short numberStyleType = ListStyleTypeValue.DECIMAL;
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
		}
		return new TargetCounterValue(target.type(), target.ref(), counter, numberStyleType, separator);
	}

	/**
	 * {@code target-text(target, target-property?)}。v1では
	 * {@code target-property}は既定の{@code content}のみ対応
	 * (仕様上の{@code before}/{@code after}/{@code first-letter}は未対応、
	 * CSS-SUPPORT.md参照)。
	 */
	private static TargetTextValue parseTargetText(TokenStream params) throws PropertyException {
		final TargetRef target = parseTargetRef(params);
		if (params.hasNext()) {
			if (!params.eatComma()) {
				throw new PropertyException("カンマが必要です");
			}
			final String targetProperty = params.ident();
			if (!"content".equals(targetProperty)) {
				throw new PropertyException("target-textはcontentのみ対応です");
			}
		}
		return new TargetTextValue(target.type(), target.ref(), TargetTextValue.CONTENT);
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