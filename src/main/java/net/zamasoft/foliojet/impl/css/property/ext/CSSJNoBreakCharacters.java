package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJNoBreakCharacters.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJNoBreakCharacters extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJNoBreakCharacters();

	public static CSSJBreakRuleValue get(CSSStyle style) {
		CSSJBreakRuleValue value = (CSSJBreakRuleValue) style.get(INFO);
		return value;
	}

	protected CSSJNoBreakCharacters() {
		super("-cssj-no-break-characters");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CSSJBreakRuleValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Str str) {
			final String head = str.value();
			final String tail;
			if (!tokens.hasNext()) {
				tail = "";
			} else {
				tail = tokens.string();
				if (tail == null || tokens.hasNext()) {
					throw new PropertyException();
				}
			}
			return new CSSJBreakRuleValue(head, tail);
		}
		if (lu instanceof CssToken.Ident ident && ident.is("none")) {
			return CSSJBreakRuleValue.NONE_VALUE;
		}
		throw new PropertyException();
	}

}