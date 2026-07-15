package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;
import java.util.ArrayList;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.lang.LanguageProfile;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.QuotesValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class Quotes extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Quotes();

	public static Value[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	protected Quotes() {
		super("quotes");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		LanguageProfile lang = LanguageProfileBundle.getLanguageProfile(style.getCSSElement().lang);
		return lang.getQuotes();
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.peek() instanceof CssToken.Ident) {// none
			if (ValueUtils.isNone(tokens.next())) {
				return KeywordValue.NONE;
			}
			throw new PropertyException();
		}

		ArrayList<QuotesValue> values = new ArrayList<QuotesValue>();

		while (tokens.hasNext()) {// <string> <string> ...
			final String open = tokens.string();
			if (open == null) {
				throw new PropertyException();
			}
			final String close = tokens.string();
			if (close == null) {
				throw new PropertyException();
			}
			values.add(new QuotesValue(open, close));
		}
		if (values.isEmpty()) {
			return KeywordValue.NONE;
		}
		final ValueListValue fvalues = new ValueListValue((Value[]) values.toArray(new Value[values.size()]));
		return fvalues;
	}

}