package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.VisibilityValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Visibility.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Visibility extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Visibility();

	public static byte get(CSSStyle style) {
		VisibilityValue value = (VisibilityValue) style.get(INFO);
		return value.getVisibility();
	}

	protected Visibility() {
		super("visibility");
	}

	public Value getDefault(CSSStyle style) {
		return VisibilityValue.VISIBLE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("visible")) {
				return VisibilityValue.VISIBLE_VALUE;
			} else if (ident.equals("hidden")) {
				return VisibilityValue.HIDDEN_VALUE;
			} else if (ident.equals("collapse")) {
				return VisibilityValue.COLLAPSE_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}
}