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
 * Individual transform property {@code rotate} (css-transforms-2 §7,
 * added 2026-08-29).
 *
 * <p>
 * {@code none | <angle> | [ x | y | z | <number>{3} ] && <angle>}.
 * Only rotation around the z axis can be projected onto paper, so {@code x}/{@code y}
 * and {@code <number>{3}} whose axis vector is not aligned with z are accepted syntactically
 * but treated as identity. Invalidating the declaration would not remove accompanying
 * {@code translate}/{@code scale}, but the author's intent is "3D rotation", so no approximation
 * is made, as with {@code rotateX/Y} in {@code transform}.
 * A negative z component of the axis vector reverses the angle's sign.
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
		// Angle and axis may appear in either order (&&). The angle is <angle> (unitless 0 allowed);
		// the axis is x|y|z or three numbers.
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
					// A zero vector does not rotate (identity per the specification).
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
