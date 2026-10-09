package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * min-block-size property (logical property). Maps to min-width or min-height
 * according to writing-mode (the opposite axis to min-inline-size).
 */
public final class MinBlockSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MinBlockSize();

	private MinBlockSize() {
		super("min-block-size");
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		// Intrinsic sizing keywords max-content/min-content/fit-content(L) (2026-08-29).
		final Value intrinsic = BoxValueUtils.toIntrinsicSize(ua, lu);
		if (intrinsic != null) {
			return intrinsic;
		}
		if (ValueUtils.isAuto(lu)) {
			// auto (the css-sizing-3 initial value) equals 0 in normal flow. Layout handles
			// the "automatic minimum size" of flex/grid items separately (2026-08-29), which this value keeps
			// (2026-10-09).
			return AbsoluteLengthValue.AUTO_MIN_SIZE;
		}
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
