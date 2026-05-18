package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.RectValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Clip.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Clip extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Clip();

	public static RectValue get(CSSStyle style) {
		Value value = style.get(INFO);
		return value.getValueType() == Value.TYPE_AUTO ? null : (RectValue) value;
	}

	private Clip() {
		super("clip");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return AutoValue.AUTO_VALUE;
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_RECT_FUNCTION) {
			LengthValue top, left, bottom, right;
			lu = lu.getParameters();

			if (ValueUtils.isAuto(lu)) {
				top = AbsoluteLengthValue.ZERO;
			} else {
				top = ValueUtils.toLength(ua, lu);
			}
			if (top == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			lu = lu.getNextLexicalUnit().getNextLexicalUnit();
			if (ValueUtils.isAuto(lu)) {
				left = AbsoluteLengthValue.ZERO;
			} else {
				left = ValueUtils.toLength(ua, lu);
			}
			if (left == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			lu = lu.getNextLexicalUnit().getNextLexicalUnit();
			if (ValueUtils.isAuto(lu)) {
				bottom = AbsoluteLengthValue.ZERO;
			} else {
				bottom = ValueUtils.toLength(ua, lu);
			}
			if (bottom == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			lu = lu.getNextLexicalUnit().getNextLexicalUnit();
			if (ValueUtils.isAuto(lu)) {
				right = AbsoluteLengthValue.ZERO;
			} else {
				right = ValueUtils.toLength(ua, lu);
			}
			if (right == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			final RectValue rect = new RectValue(top, left, bottom, right);
			return rect;
		}
		throw new PropertyException(String.valueOf(lu));
	}

}