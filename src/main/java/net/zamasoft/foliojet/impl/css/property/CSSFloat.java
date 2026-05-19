package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSFloat.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSFloat extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSFloat();

	public static byte get(CSSStyle style) {
		CSSFloatValue value = (CSSFloatValue) style.get(INFO);
		return value.getFloat();
	}

	private CSSFloat() {
		super("float");
	}

	public Value getDefault(CSSStyle style) {
		return CSSFloatValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {

		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("none")) {
				return CSSFloatValue.NONE_VALUE;
			} else if (ident.equals("left")) {
				return CSSFloatValue.LEFT_VALUE;
			} else if (ident.equals("right")) {
				return CSSFloatValue.RIGHT_VALUE;
			} else if (ident.equals("start")) {
				return CSSFloatValue.START_VALUE;
			} else if (ident.equals("end")) {
				return CSSFloatValue.END_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}