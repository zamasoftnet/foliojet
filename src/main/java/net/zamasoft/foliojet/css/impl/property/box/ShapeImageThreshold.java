package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code shape-image-threshold} (css-shapes-1 §4.2, added 2026-08-29).
 *
 * <p>
 * {@code <number>}. Opacity threshold for extracting a shape from a
 * {@code shape-outside: url()} image (pixels <b>greater than</b> this value form the shape).
 * Clamps out-of-range values to 0..1 at the computed-value stage, as specified.
 * Defaults to 0; not inherited.
 * </p>
 */
public class ShapeImageThreshold extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ShapeImageThreshold();

	public static double get(final CSSStyle style) {
		return ((RealValue) style.get(INFO)).getReal();
	}

	protected ShapeImageThreshold() {
		super("shape-image-threshold");
	}

	public Value getDefault(final CSSStyle style) {
		return RealValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final RealValue real = ValueUtils.toReal(tokens.next());
		if (real == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		final double clamped = Math.max(0, Math.min(1, real.getReal()));
		return clamped == real.getReal() ? real : RealValue.create(clamped);
	}
}
