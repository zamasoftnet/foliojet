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
 * 個別変換プロパティ{@code scale}です(css-transforms-2 §7、
 * 2026-08-29新設)。
 *
 * <p>
 * {@code none | [ <number> | <percentage> ]{1,3}}。1値は等方、2値はx/y、
 * 3つ目(z)は読み捨てる。{@code 150%}は{@code 1.5}と同じ。
 * </p>
 */
public class Scale extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Scale();

	public static TransformValue get(final CSSStyle style) {
		return (TransformValue) style.get(INFO);
	}

	protected Scale() {
		super("scale");
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
		final CssToken first = tokens.next();
		if (first instanceof CssToken.Ident) {
			if (ValueUtils.isNone(first) && !tokens.hasNext()) {
				return KeywordValue.NONE;
			}
			throw new PropertyException();
		}
		final double sx = factor(first);
		double sy = sx;
		if (tokens.hasNext()) {
			sy = factor(tokens.next());
			if (tokens.hasNext()) {
				factor(tokens.next()); // z
			}
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return TransformValue.create(AffineTransform.getScaleInstance(sx, sy));
	}

	private static double factor(final CssToken token) throws PropertyException {
		if (token instanceof CssToken.Percent percent) {
			return percent.value() / 100.0;
		}
		return Transform.toFloat(token);
	}
}
