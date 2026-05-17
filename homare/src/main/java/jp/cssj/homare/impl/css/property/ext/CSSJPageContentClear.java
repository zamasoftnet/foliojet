package jp.cssj.homare.impl.css.property.ext;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.NoneValue;
import jp.cssj.homare.css.value.StringValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.ValueListValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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