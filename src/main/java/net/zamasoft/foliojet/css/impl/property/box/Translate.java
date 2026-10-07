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
 * Individual transform property {@code translate} (css-transforms-2 §7,
 * added 2026-08-29).
 *
 * <p>
 * {@code none | <length-percentage> [ <length-percentage> <length>? ]?}.
 * Reads and discards the third component (z). Stores values in the same
 * {@link TransformValue} as {@code transform}; as with {@code translate()},
 * percentages travel as coefficients multiplied by the element's own dimensions.
 * Individual properties come first in the transform sequence
 * (translate→rotate→scale→transform, §7.3), so the matrix composed before
 * the percentages is identity, and the coefficients map directly to W→x and H→y.
 * Actual composition occurs in {@code BoxStyleMapper.setupParams}.
 * </p>
 */
public class Translate extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Translate();

	public static TransformValue get(final CSSStyle style) {
		return (TransformValue) style.get(INFO);
	}

	protected Translate() {
		super("translate");
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
		final double[] pct = new double[2];
		final double tx = Transform.lengthOrRatio(ua, first, pct, 0);
		double ty = 0;
		if (tokens.hasNext()) {
			ty = Transform.lengthOrRatio(ua, tokens.next(), pct, 1);
			if (tokens.hasNext()) {
				// z component. Cannot project onto paper, so only validate it as a length.
				Transform.toLength(ua, tokens.next());
			}
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return TransformValue.create(AffineTransform.getTranslateInstance(tx, ty), pct[0], pct[1]);
	}
}
