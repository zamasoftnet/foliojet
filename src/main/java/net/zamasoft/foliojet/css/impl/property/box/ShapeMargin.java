package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BasicShapes;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code shape-margin} (css-shapes-1 §4.3, added 2026-08-29).
 *
 * <p>
 * {@code <length-percentage>} (nonnegative). Expands the {@code shape-outside} shape outward
 * by this distance. % is relative to the containing block's inline width
 * (resolved during layout). Defaults to 0; not inherited.
 * </p>
 */
public class ShapeMargin extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ShapeMargin();

	public static Length get(final CSSStyle style) {
		return BoxValueUtils.toLength(style.get(INFO));
	}

	protected ShapeMargin() {
		super("shape-margin");
	}

	public Value getDefault(final CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (value instanceof LengthValue length && !(value instanceof AbsoluteLengthValue)) {
			return length.toAbsoluteLength(style);
		}
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final QuantityValue q = BasicShapes.lengthOrPercentage(ua, tokens.next());
		if (tokens.hasNext() || q.isNegative()) {
			throw new PropertyException();
		}
		return q;
	}
}
