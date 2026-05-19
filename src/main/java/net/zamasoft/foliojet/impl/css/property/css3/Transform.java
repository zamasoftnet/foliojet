package net.zamasoft.foliojet.impl.css.property.css3;

import java.awt.geom.AffineTransform;
import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TransformValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Transform.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Transform extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new Transform();

	public static AffineTransform get(CSSStyle style) {
		TransformValue value = (TransformValue) style.get(INFO);
		return value.getTransform();
	}

	protected Transform() {
		super("-cssj-transform");
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == NoneValue.NONE_VALUE) {
			return TransformValue.IDENTITY_TRANSFORM_VALUE;
		}
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		AffineTransform at = null;
		do {
			switch (lu.getLexicalUnitType()) {
			case LexicalUnit.SAC_IDENT:
				if (ValueUtils.isNone(lu)) {
					break;
				}
				throw new PropertyException();
			case LexicalUnit.SAC_FUNCTION:
				String func = lu.getFunctionName();
				if (func.equalsIgnoreCase("matrix")) {
					LexicalUnit params = lu.getParameters();
					double a = getFloatValue(params);
					params = params.getNextLexicalUnit();
					double b = getFloatValue(params);
					params = params.getNextLexicalUnit();
					double c = getFloatValue(params);
					params = params.getNextLexicalUnit();
					double d = getFloatValue(params);
					params = params.getNextLexicalUnit();
					double tx = getLengthValue(ua, params);
					params = params.getNextLexicalUnit();
					double ty = getLengthValue(ua, params);
					AffineTransform t = new AffineTransform(a, b, c, d, tx, ty);
					if (at == null) {
						at = t;
					} else {
						at.concatenate(t);
					}

				} else if (func.equalsIgnoreCase("rotate")) {
					LexicalUnit params = lu.getParameters();
					double angle = getAngle(params);
					if (at == null) {
						at = AffineTransform.getRotateInstance(angle);
					} else {
						at.rotate(angle);
					}
				} else if (func.equalsIgnoreCase("scale")) {
					LexicalUnit params = lu.getParameters();
					double sx = getFloatValue(params);
					params = params.getNextLexicalUnit();
					double sy;
					if (params == null) {
						sy = sx;
					} else {
						sy = getFloatValue(params);
					}
					if (at == null) {
						at = AffineTransform.getScaleInstance(sx, sy);
					} else {
						at.scale(sx, sy);
					}
				} else if (func.equalsIgnoreCase("scaleX")) {
					LexicalUnit params = lu.getParameters();
					double sx = getFloatValue(params);
					if (at == null) {
						at = AffineTransform.getScaleInstance(sx, 1);
					} else {
						at.scale(sx, 1);
					}
				} else if (func.equalsIgnoreCase("scaleY")) {
					LexicalUnit params = lu.getParameters();
					double sy = getFloatValue(params);
					if (at == null) {
						at = AffineTransform.getScaleInstance(1, sy);
					} else {
						at.scale(1, sy);
					}
				} else if (func.equalsIgnoreCase("skew")) {
					LexicalUnit params = lu.getParameters();
					double shx = getAngle(params);
					params = params.getNextLexicalUnit();
					double shy;
					if (params == null) {
						shy = 0;
					} else {
						shy = getAngle(params);
					}
					if (at == null) {
						at = AffineTransform.getShearInstance(Math.tan(shx), Math.tan(shy));
					} else {
						at.shear(Math.tan(shx), Math.tan(shy));
					}
				} else if (func.equalsIgnoreCase("skewX")) {
					LexicalUnit params = lu.getParameters();
					double shx = getAngle(params);
					if (at == null) {
						at = AffineTransform.getShearInstance(Math.tan(shx), 0);
					} else {
						at.shear(Math.tan(shx), 0);
					}
				} else if (func.equalsIgnoreCase("skewY")) {
					LexicalUnit params = lu.getParameters();
					double shy = getAngle(params);
					if (at == null) {
						at = AffineTransform.getShearInstance(0, Math.tan(shy));
					} else {
						at.shear(0, Math.tan(shy));
					}
				} else if (func.equalsIgnoreCase("translate")) {
					LexicalUnit params = lu.getParameters();
					double tx = getLengthValue(ua, params);
					params = params.getNextLexicalUnit();
					double ty;
					if (params == null) {
						ty = 0;
					} else {
						ty = getLengthValue(ua, params);
					}
					if (at == null) {
						at = AffineTransform.getTranslateInstance(tx, ty);
					} else {
						at.translate(tx, ty);
					}
				} else if (func.equalsIgnoreCase("translateX")) {
					LexicalUnit params = lu.getParameters();
					double tx = getLengthValue(ua, params);
					if (at == null) {
						at = AffineTransform.getTranslateInstance(tx, 0);
					} else {
						at.translate(tx, 0);
					}
				} else if (func.equalsIgnoreCase("translateY")) {
					LexicalUnit params = lu.getParameters();
					double ty = getLengthValue(ua, params);
					if (at == null) {
						at = AffineTransform.getTranslateInstance(0, ty);
					} else {
						at.translate(0, ty);
					}
				} else {
					throw new PropertyException();
				}
				break;
			default:
				throw new PropertyException();
			}
			lu = lu.getNextLexicalUnit();

		} while (lu != null);
		if (at == null) {
			return NoneValue.NONE_VALUE;
		}
		return TransformValue.create(at);
	}

	private double getAngle(LexicalUnit lu) throws PropertyException {
		if (lu == null) {
			throw new PropertyException();
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
			lu = lu.getNextLexicalUnit();
			if (lu == null) {
				throw new PropertyException();
			}
		}
		double angle;
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_DEGREE) {
			angle = (lu.getFloatValue() * Math.PI / 180.0);
		} else {
			angle = getFloatValue(lu);
		}
		return angle;
	}

	private float getFloatValue(LexicalUnit lu) throws PropertyException {
		if (lu == null) {
			throw new PropertyException();
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
			lu = lu.getNextLexicalUnit();
			if (lu == null) {
				throw new PropertyException();
			}
		}
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_REAL:
			return lu.getFloatValue();
		case LexicalUnit.SAC_INTEGER:
			return lu.getIntegerValue();
		default:
			throw new PropertyException();
		}
	}

	private double getLengthValue(UserAgent ua, LexicalUnit lu) throws PropertyException {
		if (lu == null) {
			throw new PropertyException();
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
			lu = lu.getNextLexicalUnit();
			if (lu == null) {
				throw new PropertyException();
			}
		}
		AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, lu);
		if (length == null) {
			switch (lu.getLexicalUnitType()) {
			case LexicalUnit.SAC_REAL:
				length = AbsoluteLengthValue.create(ua, lu.getFloatValue(), AbsoluteLengthValue.UNIT_PX);
				break;
			case LexicalUnit.SAC_INTEGER:
				length = AbsoluteLengthValue.create(ua, lu.getIntegerValue(), AbsoluteLengthValue.UNIT_PX);
				break;
			default:
				throw new PropertyException();
			}
		}
		return length.getLength();
	}

}