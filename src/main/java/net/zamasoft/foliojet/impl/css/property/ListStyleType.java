package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ListStyleType.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ListStyleType extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ListStyleType();

	public static short get(CSSStyle style) {
		ListStyleTypeValue value = (ListStyleTypeValue) style.get(INFO);
		return value.getListStyleType();
	}

	protected ListStyleType() {
		super("list-style-type");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return ListStyleTypeValue.DISC_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
		case LexicalUnit.SAC_STRING_VALUE:
			break;
		default:
			throw new PropertyException();
		}
		final Value value = GeneratedValueUtils.toListStyleType(lu.getStringValue());
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}