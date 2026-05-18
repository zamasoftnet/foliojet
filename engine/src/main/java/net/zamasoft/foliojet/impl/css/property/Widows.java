package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Widows.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Widows extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Widows();

	public static int get(CSSStyle style) {
		IntegerValue value = (IntegerValue) style.get(INFO);
		return value.getInteger();
	}

	private Widows() {
		super("widows");
	}

	public Value getDefault(CSSStyle style) {
		return IntegerValue.TWO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_INTEGER: {
			final int a = lu.getIntegerValue();
			return IntegerValue.create(a);
		}

		default:
			throw new PropertyException();
		}
	}

}