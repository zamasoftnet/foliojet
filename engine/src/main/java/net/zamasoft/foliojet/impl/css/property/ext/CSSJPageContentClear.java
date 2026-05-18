package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJPageContentClear.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJPageContentClear extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJPageContentClear();

	public static String[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_NONE) {
			return null;
		}
		ValueListValue valueList = (ValueListValue) value;
		Value[] values = valueList.getValues();
		String[] names = new String[values.length];
		for (int i = 0; i < names.length; ++i) {
			names[i] = ((StringValue) values[i]).getString();
		}
		return names;
	}

	private CSSJPageContentClear() {
		super("-cssj-page-content-clear");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			return InheritValue.INHERIT_VALUE;
		}
		final List<Value> list = new ArrayList<Value>();
		Value value;
		do {
			short luType = lu.getLexicalUnitType();
			switch (luType) {
			case LexicalUnit.SAC_IDENT:
				String ident = lu.getStringValue().toLowerCase();
				if (ident.equals("none")) {
					value = NoneValue.NONE_VALUE;
				} else {
					value = new StringValue(lu.getStringValue());
				}
				break;

			case LexicalUnit.SAC_STRING_VALUE:
				value = new StringValue(lu.getStringValue());
				break;

			default:
				throw new PropertyException();
			}
			list.add(value);
			lu = lu.getNextLexicalUnit();
		} while (lu != null);

		return new ValueListValue((Value[]) list.toArray(new Value[list.size()]));
	}
}