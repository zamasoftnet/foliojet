package net.zamasoft.foliojet.impl.css.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.WordWrapValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class WordWrap extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WordWrap();

	public static byte get(CSSStyle style) {
		return ((WordWrapValue) style.get(INFO)).getWordWrap();
	}

	protected WordWrap() {
		super("-cssj-word-wrap");
	}

	public Value getDefault(CSSStyle style) {
		return WordWrapValue.NORMAL_VALUE;
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
				return WordWrapValue.NORMAL_VALUE;
			} else if (ident.equals("break-word")) {
				return WordWrapValue.BREAK_WORD_VALUE;
			}
		}
		throw new PropertyException();
	}

}