package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.GeneratedValueUtils;
import jp.cssj.homare.css.value.ListStylePositionValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ListStylePosition.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ListStylePosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ListStylePosition();

	public static short get(CSSStyle style) {
		ListStylePositionValue value = (ListStylePositionValue) style.get(INFO);
		return value.getListStylePosition();
	}

	protected ListStylePosition() {
		super("list-style-position");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return ListStylePositionValue.OUTSIDE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_STRING_VALUE:
		case LexicalUnit.SAC_IDENT:
			break;
		default:
			throw new PropertyException();
		}
		final Value value = GeneratedValueUtils.toListStylePosition(lu.getStringValue());
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}