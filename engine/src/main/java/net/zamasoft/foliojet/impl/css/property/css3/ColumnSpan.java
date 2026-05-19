package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.ColumnSpanValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnSpan.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnSpan extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new ColumnSpan();

	public static byte get(CSSStyle style) {
		ColumnSpanValue value = (ColumnSpanValue) style.get(INFO);
		return value.getColumnSpan();
	}

	protected ColumnSpan() {
		super("-cssj-column-span");
	}

	public Value getDefault(CSSStyle style) {
		return ColumnSpanValue.SINGLE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_INTEGER:
			if (lu.getIntegerValue() == 1) {
				return ColumnSpanValue.SINGLE_VALUE;
			}
			throw new PropertyException();

		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("all")) {
				return ColumnSpanValue.ALL_VALUE;
			}
			throw new PropertyException();

		default:
			throw new PropertyException();
		}
	}

}