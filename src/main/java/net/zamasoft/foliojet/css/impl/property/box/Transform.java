package net.zamasoft.foliojet.css.impl.property.box;

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

	/** {@code translate()}の割合成分(要素の幅に掛ける)。 */
	public static double getTxRatio(CSSStyle style) {
		return ((TransformValue) style.get(INFO)).getTxRatio();
	}

	/** {@code translate()}の割合成分(要素の高さに掛ける)。 */
	public static double getTyRatio(CSSStyle style) {
		return ((TransformValue) style.get(INFO)).getTyRatio();
	}

	/** 交差成分(高さ→x)。{@link TransformValue#getTxRatioH()} */
	public static double getTxRatioH(CSSStyle style) {
		return ((TransformValue) style.get(INFO)).getTxRatioH();
	}

	/** 交差成分(幅→y)。{@link TransformValue#getTyRatioW()} */
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
		// translate()の割合成分。要素の寸法が要るので行列へ畳めない
		// (TransformValueのjavadoc参照)。割合が回転・拡大の後ろに来ても
		// 線形分解して係数ベクトルへ足し込む(2026-08-29): 割合つき平行移動
		// T(v)の前に合成済みの行列Aがあるとき、A·T(v)·B = A·B + A_lin·v
		// なので、v=(px·W, py·H)の係数 px·A_lin·e1 と py·A_lin·e2 を積む
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
				// 4x4のうち2D成分(a b / c d / tx ty)だけを使う。zは無視
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
						getFloatValue(params); // scale3dのz
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
						getLengthValue(ua, params); // translate3dのz
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
				// 3D変換は紙面に射影できない。GPU合成のヒント(translateZ(0)等)
				// として書かれることが大半なので、他の関数を活かすために
				// この関数だけを無視する(2026-08-29)。rotateX/Yは真の3D回転
				// なので近似しない
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
	 * 割合つき平行移動 (px·W, py·H) を、ここまでの合成行列の線形部で
	 * 写して係数ベクトルへ足します。{@code ratio}は
	 * [W→x, H→y, H→x, W→y]。
	 */
	private static void accumulateRatio(final AffineTransform prefix, final double[] pct, final double[] ratio) {
		if (pct[0] == 0 && pct[1] == 0) {
			return;
		}
		final double m00 = prefix == null ? 1 : prefix.getScaleX();
		final double m10 = prefix == null ? 0 : prefix.getShearY();
		final double m01 = prefix == null ? 0 : prefix.getShearX();
		final double m11 = prefix == null ? 1 : prefix.getScaleY();
		// W成分: px·A_lin·e1 = px·(m00, m10)
		ratio[0] += pct[0] * m00;
		ratio[3] += pct[0] * m10;
		// H成分: py·A_lin·e2 = py·(m01, m11)
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

	/**
	 * 平行移動の量。<b>割合はここでは解かず</b>{@code ratio}へ積む
	 * ——その要素自身の境界箱が基準なので、解析時には寸法が無い。
	 */
	private double getLengthOrRatio(UserAgent ua, TokenStream params, double[] ratio, int axis)
			throws PropertyException {
		final CssToken token = nextParam(params);
		if (token instanceof CssToken.Percent percent) {
			ratio[axis] += percent.value() / 100.0;
			return 0;
		}
		// **calc(%±長さ)を分解して受ける**(2026-08-19)。実物のWebは
		// リストマーカー等を translateX(calc(-100% - 0.5em)) で自要素幅ぶん
		// 外へ出す(shower-demo)。従来はPropertyExceptionでtransform指定
		// 全体が無効になり、マーカーが本文の上に残って重なっていた
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
				// フォント相対成分は既定フォント寸法で近似(同メソッドjavadoc)
				ratio[axis] += fontRel.getRatio();
				return fontRel.approximateAbsolute(ua);
			}
		}
		return toLength(ua, token);
	}

	private double getLengthValue(UserAgent ua, TokenStream params) throws PropertyException {
		return toLength(ua, nextParam(params));
	}

	private double toLength(UserAgent ua, CssToken token) throws PropertyException {
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