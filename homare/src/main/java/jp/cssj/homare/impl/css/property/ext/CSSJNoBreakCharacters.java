package jp.cssj.homare.impl.css.property.ext;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.ext.CSSJBreakRuleValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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