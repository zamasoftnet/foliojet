package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_STRING_VALUE:
			String head = lu.getStringValue();
			String tail;
			lu = lu.getNextLexicalUnit();
			if (lu == null) {
				tail = "";
			} else if (lu.getLexicalUnitType() == LexicalUnit.SAC_STRING_VALUE) {
				tail = lu.getStringValue();
				lu = lu.getNextLexicalUnit();
				if (lu != null) {
					throw new PropertyException();
				}
			} else {
				throw new PropertyException();
			}
			return new CSSJBreakRuleValue(head, tail);

		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("none")) {
				return CSSJBreakRuleValue.NONE_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}