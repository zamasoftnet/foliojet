package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.IntegerValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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