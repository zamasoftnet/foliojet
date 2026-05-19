package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.UnicodeRangeValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.UnicodeRange;
import net.zamasoft.pdfg2d.gc.font.UnicodeRangeList;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSUnicodeRange.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSUnicodeRange extends AbstractPrimitivePropertyInfo {
	public static final AbstractPrimitivePropertyInfo INFO = new CSSUnicodeRange();

	public static UnicodeRangeList get(CSSStyle style) {
		return ((UnicodeRangeValue) style.get(INFO)).asUnicodeRangeList();
	}

	protected CSSUnicodeRange() {
		super("unicode-range");
	}

	public Value getDefault(CSSStyle style) {
		return UnicodeRangeValue.EMPTY;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		List<UnicodeRange> list = new ArrayList<UnicodeRange>();
		do {
			if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
				lu = lu.getNextLexicalUnit();
				if (lu == null) {
					break;
				}
			}
			if (lu.getLexicalUnitType() != LexicalUnit.SAC_UNICODERANGE) {
				throw new PropertyException();
			}
			String str = lu.getStringValue();
			UnicodeRange unicodeRange;
			try {
				unicodeRange = UnicodeRange.parseRange(str);
			} catch (NumberFormatException e) {
				throw new PropertyException();
			}
			list.add(unicodeRange);
			lu = lu.getNextLexicalUnit();
		} while (lu != null);
		return new UnicodeRangeValue((UnicodeRange[]) list.toArray(new UnicodeRange[list.size()]));
	}

}
