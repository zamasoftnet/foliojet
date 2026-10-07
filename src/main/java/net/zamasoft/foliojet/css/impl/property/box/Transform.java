package net.zamasoft.foliojet.css.impl.property.box;

import java.awt.geom.AffineTransform;
import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.CalcValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AngleValue;
import net.zamasoft.foliojet.css.value.RealValue;
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

	/** Percentage component of {@code translate()} (multiplied by the element width). */
	public static double getTxRatio(CSSStyle style) {
		return ((TransformValue) style.get(INFO)).getTxRatio();
	}

	/** Percentage component of {@code translate()} (multiplied by the element height). */
	public static double getTyRatio(CSSStyle style) {
		return ((TransformValue) style.get(INFO)).getTyRatio();
	}

	/** Cross component (height→x). {@link TransformValue#getTxRatioH()} */
	public static double getTxRatioH(CSSStyle style) {
		return ((TransformValue) style.get(INFO)).getTxRatioH();
	}

	/** Cross component (width→y). {@link TransformValue#getTyRatioW()} */
	public static double getTyRatioW(CSSStyle style) {
		return ((TransformValue) style.get(INFO)).getTyRatioW();
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
		// Percentage components of translate(). Cannot fold them into the matrix because they need element dimensions
		// (see the TransformValue Javadoc). Even after rotation/scaling, decompose percentages
		// linearly and add to the coefficient vector (2026-08-29): if the accumulated matrix A precedes
		// a percentage translation T(v), A·T(v)·B = A·B + A_lin·v,
		// so accumulate coefficients px·A_lin·e1 and py·A_lin·e2 for v=(px·W, py·H).
		// ratio[0]=W→x, ratio[1]=H→y, ratio[2]=H→x, ratio[3]=W→y
		final double[] ratio = new double[4];
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
			} else if (func.is("matrix3d")) {
				// Use only the 2D components (a b / c d / tx ty) of the 4x4 matrix. Ignore z.
				final double[] m = new double[16];
				for (int i = 0; i < 16; ++i) {
					m[i] = getFloatValue(params);
				}
				AffineTransform t = new AffineTransform(m[0], m[1], m[4], m[5], m[12], m[13]);
				if (at == null) {
					at = t;
				} else {
					at.concatenate(t);
				}
			} else if (func.is("rotate") || func.is("rotateZ")) {
				double angle = getAngle(params);
				if (at == null) {
					at = AffineTransform.getRotateInstance(angle);
				} else {
					at.rotate(angle);
				}
			} else if (func.is("scale") || func.is("scale3d")) {
				double sx = getFloatValue(params);
				double sy;
				if (!params.hasNext()) {
					sy = sx;
				} else {
					sy = getFloatValue(params);
					if (params.hasNext()) {
						getFloatValue(params); // z of scale3d
					}
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
			} else if (func.is("translate") || func.is("translate3d")) {
				final double[] pct = new double[2];
				double tx = getLengthOrRatio(ua, params, pct, 0);
				double ty;
				if (!params.hasNext()) {
					ty = 0;
				} else {
					ty = getLengthOrRatio(ua, params, pct, 1);
					if (params.hasNext()) {
						getLengthValue(ua, params); // z of translate3d
					}
				}
				accumulateRatio(at, pct, ratio);
				if (at == null) {
					at = AffineTransform.getTranslateInstance(tx, ty);
				} else {
					at.translate(tx, ty);
				}
			} else if (func.is("translateX")) {
				final double[] pct = new double[2];
				double tx = getLengthOrRatio(ua, params, pct, 0);
				accumulateRatio(at, pct, ratio);
				if (at == null) {
					at = AffineTransform.getTranslateInstance(tx, 0);
				} else {
					at.translate(tx, 0);
				}
			} else if (func.is("translateY")) {
				final double[] pct = new double[2];
				double ty = getLengthOrRatio(ua, params, pct, 1);
				accumulateRatio(at, pct, ratio);
				if (at == null) {
					at = AffineTransform.getTranslateInstance(0, ty);
				} else {
					at.translate(0, ty);
				}
			} else if (func.is("translateZ") || func.is("perspective") || func.is("rotateX")
					|| func.is("rotateY") || func.is("rotate3d") || func.is("scaleZ")) {
				// 3D transforms cannot be projected onto paper. Most are GPU compositing hints
				// (translateZ(0), etc.), so ignore only this function to preserve
				// the others (2026-08-29). rotateX/Y are true 3D rotations
				// and are not approximated.
				while (params.hasNext()) {
					params.next();
				}
			} else {
				throw new PropertyException();
			}
		}
		if (ratio[0] != 0 || ratio[1] != 0 || ratio[2] != 0 || ratio[3] != 0) {
			return TransformValue.create(at == null ? new AffineTransform() : at, ratio[0], ratio[1], ratio[2],
					ratio[3]);
		}
		if (at == null) {
			return KeywordValue.NONE;
		}
		return TransformValue.create(at);
	}

	/**
	 * Maps a percentage translation (px·W, py·H) through the linear part of the matrix
	 * accumulated so far and adds it to the coefficient vector. {@code ratio} is
	 * [W→x, H→y, H→x, W→y].
	 */
	private static void accumulateRatio(final AffineTransform prefix, final double[] pct, final double[] ratio) {
		if (pct[0] == 0 && pct[1] == 0) {
			return;
		}
		final double m00 = prefix == null ? 1 : prefix.getScaleX();
		final double m10 = prefix == null ? 0 : prefix.getShearY();
		final double m01 = prefix == null ? 0 : prefix.getShearX();
		final double m11 = prefix == null ? 1 : prefix.getScaleY();
		// W component: px·A_lin·e1 = px·(m00, m10)
		ratio[0] += pct[0] * m00;
		ratio[3] += pct[0] * m10;
		// H component: py·A_lin·e2 = py·(m01, m11)
		ratio[2] += pct[1] * m01;
		ratio[1] += pct[1] * m11;
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
		return toAngle(nextParam(params));
	}

	/**
	 * Converts {@code <angle>} to radians. Accepts the four units deg/grad/rad/turn
	 * (css-values-4) and unitless 0 (or a number for legacy syntax compatibility).
	 * Shared with the individual {@code rotate} property (2026-08-29).
	 */
	static double toAngle(final CssToken token) throws PropertyException {
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case DEG:
				return dim.value() * Math.PI / 180.0;
			case GRAD:
				return dim.value() * Math.PI / 200.0;
			case RAD:
				return dim.value();
			default:
				if ("turn".equalsIgnoreCase(dim.unitText())) {
					return dim.value() * Math.PI * 2.0;
				}
				throw new PropertyException();
			}
		}
		Value calc = CalcValueUtils.toCalc(null, token);
		if (calc instanceof AngleValue angle) {
			return angle.getRadians();
		}
		return toFloat(token);
	}

	private float getFloatValue(TokenStream params) throws PropertyException {
		return toFloat(nextParam(params));
	}

	static float toFloat(CssToken token) throws PropertyException {
		if (token instanceof CssToken.Num num) {
			return (float) num.value();
		}
		Value calc = CalcValueUtils.toCalc(null, token);
		if (calc instanceof RealValue real) {
			return (float) real.getReal();
		}
		throw new PropertyException();
	}

	/**
	 * Translation amount. <b>Does not resolve percentages here</b>; accumulates them in {@code ratio}.
	 * They are relative to the element's own border box, whose dimensions are unavailable during parsing.
	 */
	private double getLengthOrRatio(UserAgent ua, TokenStream params, double[] ratio, int axis)
			throws PropertyException {
		return lengthOrRatio(ua, nextParam(params), ratio, axis);
	}

	/**
	 * Splits one {@code <length-percentage>} token into an absolute length (the return value)
	 * and a percentage (added to {@code ratio[axis]}). Shared with the individual
	 * {@code translate} property (2026-08-29).
	 */
	static double lengthOrRatio(UserAgent ua, CssToken token, double[] ratio, int axis) throws PropertyException {
		if (token instanceof CssToken.Percent percent) {
			ratio[axis] += percent.value() / 100.0;
			return 0;
		}
		// **Accept calc(%±length) by decomposing it** (2026-08-19). Real sites move
		// list markers, etc. outward by their own element width using translateX(calc(-100% - 0.5em))
		// (shower-demo). Previously, PropertyException invalidated the entire transform
		// declaration, leaving markers overlapping the body text.
		final net.zamasoft.foliojet.css.value.Value calc = net.zamasoft.foliojet.css.util.CalcValueUtils.toCalc(ua, token);
		if (calc != null) {
			if (calc instanceof net.zamasoft.foliojet.css.value.PercentageValue percent) {
				ratio[axis] += percent.getRatio();
				return 0;
			}
			if (calc instanceof AbsoluteLengthValue length) {
				return length.getLength();
			}
			if (calc instanceof net.zamasoft.foliojet.css.value.CalcLengthValue mixed) {
				ratio[axis] += mixed.getRatio();
				return mixed.getAbsolute();
			}
			if (calc instanceof net.zamasoft.foliojet.css.value.CalcFontRelativeValue fontRel) {
				// Approximate font-relative components using the default font size (see this method's Javadoc).
				ratio[axis] += fontRel.getRatio();
				return fontRel.approximateAbsolute(ua);
			}
		}
		return toLength(ua, token);
	}

	private double getLengthValue(UserAgent ua, TokenStream params) throws PropertyException {
		return toLength(ua, nextParam(params));
	}

	static double toLength(UserAgent ua, CssToken token) throws PropertyException {
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
