package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.WhiteSpaceValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WhiteSpace.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class WhiteSpace extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WhiteSpace();

	public static byte get(CSSStyle style) {
		return ((WhiteSpaceValue) style.get(INFO)).getWhiteSpace();
	}

	protected WhiteSpace() {
		super("white-space");
	}

	public Value getDefault(CSSStyle style) {
		return WhiteSpaceValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
			String ident = lu.getStringValue().toLowerCase();
			final Value value;
			if (ident.equals("normal")) {
				value = WhiteSpaceValue.NORMAL_VALUE;
			} else if (ident.equals("pre")) {
				value = WhiteSpaceValue.PRE_VALUE;
			} else if (ident.equals("nowrap")) {
				value = WhiteSpaceValue.NOWRAP_VALUE;
			} else if (ident.equals("pre-wrap")) {
				value = WhiteSpaceValue.PRE_WRAP_VALUE;
			} else if (ident.equals("pre-line")) {
				value = WhiteSpaceValue.PRE_LINE_VALUE;
			} else {
				throw new PropertyException();
			}
			return value;

		}
		throw new PropertyException();
	}

}