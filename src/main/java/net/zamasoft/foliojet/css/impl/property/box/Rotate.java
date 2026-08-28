package net.zamasoft.foliojet.css.impl.property.box;

import java.awt.geom.AffineTransform;
import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TransformValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 個別変換プロパティ{@code rotate}です(css-transforms-2 §7、
 * 2026-08-29新設)。
 *
 * <p>
 * {@code none | <angle> | [ x | y | z | <number>{3} ] && <angle>}。
 * 紙面に射影できるのはz軸回りだけなので、{@code x}/{@code y}、および
 * 軸ベクトルがz軸に沿わない{@code <number>{3}}は構文として受理して
 * 恒等にする(宣言を無効にすると併記の{@code translate}/{@code scale}
 * まで消えるわけではないが、作者の意図は「3D回転」であり近似しない
 * ——{@code transform}の{@code rotateX/Y}と同じ扱い)。軸ベクトルの
 * z成分が負なら角度の符号を反転する。
 * </p>
 */
public class Rotate extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Rotate();

	public static TransformValue get(final CSSStyle style) {
		return (TransformValue) style.get(INFO);
	}

	protected Rotate() {
		super("rotate");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (value == KeywordValue.NONE) {
			return TransformValue.IDENTITY_TRANSFORM_VALUE;
		}
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		if (tokens.peek() instanceof CssToken.Ident && ValueUtils.isNone(tokens.peek())) {
			tokens.next();
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.NONE;
		}
		// 角度と軸は順不同(&&)。角度は<angle>(単位なし0も可)、軸は
		// x|y|z か数値3つ
		Double angle = null;
		double zSign = 1;
		boolean axisSeen = false;
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token instanceof CssToken.Ident ident) {
				if (axisSeen) {
					throw new PropertyException();
				}
				axisSeen = true;
				final String axis = ident.lower();
				if (axis.equals("x") || axis.equals("y")) {
					zSign = 0;
				} else if (!axis.equals("z")) {
					throw new PropertyException();
				}
			} else if (token instanceof CssToken.Num && !axisSeen && tokens.hasNext()
					&& tokens.peek() instanceof CssToken.Num) {
				// <number>{3}
				final double x = Transform.toFloat(token);
				final double y = Transform.toFloat(tokens.next());
				final double z = Transform.toFloat(tokens.next());
				axisSeen = true;
				if (x != 0 || y != 0) {
					zSign = 0;
				} else if (z < 0) {
					zSign = -1;
				} else if (z == 0) {
					// 零ベクトルは回転しない(仕様: 恒等)
					zSign = 0;
				}
			} else if (angle == null) {
				angle = Transform.toAngle(token);
			} else {
				throw new PropertyException();
			}
		}
		if (angle == null) {
			throw new PropertyException();
		}
		return TransformValue.create(AffineTransform.getRotateInstance(angle * zSign));
	}
}
