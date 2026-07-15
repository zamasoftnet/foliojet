package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.WordBreakValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class WordBreak extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WordBreak();

	public static byte get(CSSStyle style) {
		return ((WordBreakValue) style.get(INFO)).getWordBreak();
	}

	protected WordBreak() {
		super("word-break");
	}

	public Value getDefault(CSSStyle style) {
		return WordBreakValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("normal")) {
				return WordBreakValue.NORMAL_VALUE;
			} else if (ident.equals("break-all")) {
				return WordBreakValue.BREAK_ALL_VALUE;
			} else if (ident.equals("keep-all")) {
				return WordBreakValue.KEEP_ALL_VALUE;
			}
		}
		throw new PropertyException();
	}

}