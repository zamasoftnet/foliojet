package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: UnicodeBidi.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class UnicodeBidi extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new UnicodeBidi();

	public static byte get(CSSStyle style) {
		UnicodeBidiValue value = (UnicodeBidiValue) style.get(INFO);
		return value.getUnicodeBidi();
	}

	private UnicodeBidi() {
		super("unicode-bidi");
	}

	public Value getDefault(CSSStyle style) {
		return UnicodeBidiValue.NORMAL_VALUE;
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
			if (ident.equals("normal")) {
				return UnicodeBidiValue.NORMAL_VALUE;
			} else if (ident.equals("embed")) {
				return UnicodeBidiValue.EMBED_VALUE;
			} else if (ident.equals("bidi-override")) {
				return UnicodeBidiValue.BIDI_OVERRIDE_VALUE;
			}
		default:
			throw new PropertyException();
		}
	}

}