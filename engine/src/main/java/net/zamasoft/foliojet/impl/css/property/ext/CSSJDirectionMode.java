package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJDirectionMode.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJDirectionMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJDirectionMode();

	public static byte get(CSSStyle style) {
		Value value = style.get(INFO);
		return ((CSSJDirectionModeValue) value).getDirectionMode();
	}

	private CSSJDirectionMode() {
		super("-cssj-direction-mode");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CSSJDirectionModeValue.PHYSICAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			return InheritValue.INHERIT_VALUE;
		}
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("physical")) {
				return CSSJDirectionModeValue.PHYSICAL_VALUE;
			} else if (ident.equals("logical") || ident.equals("horizontal-tb")) {
				return CSSJDirectionModeValue.HORIZONTAL_TB_VALUE;
			} else if (ident.equals("vertical-rl")) {
				return CSSJDirectionModeValue.VERTICAL_RL_VALUE;
			}
			break;
		}
		throw new PropertyException();
	}
}