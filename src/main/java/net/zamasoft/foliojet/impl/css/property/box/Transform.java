package net.zamasoft.foliojet.impl.css.property.box;

import java.awt.geom.AffineTransform;
import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TransformValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * @author MIYABE Tatsuhiko
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
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == KeywordValue.NONE) {
			return TransformValue.IDENTITY_TRANSFORM_VALUE;
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		AffineTransform at = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident) {
				if (ValueUtils.isNone(lu)) {
					continue;
				}
				throw new PropertyException();
			}
			if (!(lu instanceof CssToken.Func func)) {
				throw new PropertyException();
			}
			final TokenStream params = func.argStream();
			if (func.is("matrix")) {
				double a = getFloatValue(params);
				double b = getFloatValue(params);
				double c = getFloatValue(params);
				double d = getFloatValue(params);
				double tx = getLengthValue(ua, params);
				double ty = getLengthValue(ua, params);
				AffineTransform t = new AffineTransform(a, b, c, d, tx, ty);
				if (at == null) {
					at = t;
				} else {
					at.concatenate(t);
				}
			} else if (func.is("rotate")) {
				double angle = getAngle(params);
				if (at == null) {
					at = AffineTransform.getRotateInstance(angle);
				} else {
					at.rotate(angle);
				}
			} else if (func.is("scale")) {
				double sx = getFloatValue(params);
				double sy;
				if (!params.hasNext()) {
					sy = sx;
				} else {
					sy = getFloatValue(params);
				}
				if (at == null) {
					at = AffineTransform.getScaleInstance(sx, sy);
				} else {
					at.scale(sx, sy);
				}
			} else if (func.is("scaleX")) {
				double sx = getFloatValue(params);
				if (at == null) {
					at = AffineTransform.getScaleInstance(sx, 1);
				} else {
					at.scale(sx, 1);
				}
			} else if (func.is("scaleY")) {
				double sy = getFloatValue(params);
				if (at == null) {
					at = AffineTransform.getScaleInstance(1, sy);
				} else {
					at.scale(1, sy);
				}
			} else if (func.is("skew")) {
				double shx = getAngle(params);
				double shy;
				if (!params.hasNext()) {
					shy = 0;
				} else {
					shy = getAngle(params);
				}
				if (at == null) {
					at = AffineTransform.getShearInstance(Math.tan(shx), Math.tan(shy));
				} else {
					at.shear(Math.tan(shx), Math.tan(shy));
				}
			} else if (func.is("skewX")) {
				double shx = getAngle(params);
				if (at == null) {
					at = AffineTransform.getShearInstance(Math.tan(shx), 0);
				} else {
					at.shear(Math.tan(shx), 0);
				}
			} else if (func.is("skewY")) {
				double shy = getAngle(params);
				if (at == null) {
					at = AffineTransform.getShearInstance(0, Math.tan(shy));
				} else {
					at.shear(0, Math.tan(shy));
				}
			} else if (func.is("translate")) {
				double tx = getLengthValue(ua, params);
				double ty;
				if (!params.hasNext()) {
					ty = 0;
				} else {
					ty = getLengthValue(ua, params);
				}
				if (at == null) {
					at = AffineTransform.getTranslateInstance(tx, ty);
				} else {
					at.translate(tx, ty);
				}
			} else if (func.is("translateX")) {
				double tx = getLengthValue(ua, params);
				if (at == null) {
					at = AffineTransform.getTranslateInstance(tx, 0);
				} else {
					at.translate(tx, 0);
				}
			} else if (func.is("translateY")) {
				double ty = getLengthValue(ua, params);
				if (at == null) {
					at = AffineTransform.getTranslateInstance(0, ty);
				} else {
					at.translate(0, ty);
				}
			} else {
				throw new PropertyException();
			}
		}
		if (at == null) {
			return KeywordValue.NONE;
		}
		return TransformValue.create(at);
	}

	private static CssToken nextParam(TokenStream params) throws PropertyException {
		params.eatComma();
		final CssToken token = params.next();
		if (token == null) {
			throw new PropertyException();
		}
		return token;
	}

	private double getAngle(TokenStream params) throws PropertyException {
		final CssToken token = nextParam(params);
		if (token instanceof CssToken.Dim dim && dim.unit() == net.zamasoft.foliojet.css.token.Unit.DEG) {
			return dim.value() * Math.PI / 180.0;
		}
		return toFloat(token);
	}

	private float getFloatValue(TokenStream params) throws PropertyException {
		return toFloat(nextParam(params));
	}

	private static float toFloat(CssToken token) throws PropertyException {
		if (token instanceof CssToken.Num num) {
			return (float) num.value();
		}
		throw new PropertyException();
	}

	private double getLengthValue(UserAgent ua, TokenStream params) throws PropertyException {
		final CssToken token = nextParam(params);
		AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, token);
		if (length == null) {
			if (token instanceof CssToken.Num num) {
				length = AbsoluteLengthValue.create(ua, num.value(), Unit.PX);
			} else {
				throw new PropertyException();
			}
		}
		return length.getLength();
	}

}