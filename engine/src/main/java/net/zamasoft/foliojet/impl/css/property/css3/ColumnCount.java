package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnCount.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnCount extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ColumnCount();

	public static int get(CSSStyle style) {
		IntegerValue value = (IntegerValue) style.get(INFO);
		return value.getInteger();
	}

	private ColumnCount() {
		super("-cssj-column-count");
	}

	public Value getDefault(CSSStyle style) {
		return IntegerValue.ONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return IntegerValue.ZERO;
		}
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_INTEGER: {
			final int a = lu.getIntegerValue();
			if (a >= 1) {
				return IntegerValue.create(a);
			}
		}
		default:
			throw new PropertyException();
		}
	}

}