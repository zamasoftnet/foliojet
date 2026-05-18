package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Opacity.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Opacity extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Opacity();

	public static float get(final CSSStyle style) {
		final RealValue real = (RealValue) style.get(INFO);
		return (float) real.getReal();
	}

	private Opacity() {
		super("opacity");
	}

	public Value getDefault(CSSStyle style) {
		return RealValue.ONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final CSSStyle parent = style.getParentStyle();
		if (parent == null) {
			return value;
		}
		final RealValue real = (RealValue) value;
		return RealValue.create(Opacity.get(parent) * real.getReal());
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_INTEGER: {
			float op = lu.getIntegerValue();
			if (op >= 0 && op <= 1) {
				return RealValue.create(op);
			}
			throw new PropertyException();
		}
		case LexicalUnit.SAC_REAL: {
			float op = lu.getFloatValue();
			if (op >= 0 && op <= 1) {
				return RealValue.create(op);
			}
			throw new PropertyException();
		}
		}
		throw new PropertyException();
	}

}