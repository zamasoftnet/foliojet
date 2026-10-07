package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code tab-size} (css-text-3 §3.2, added 2026-08-29).
 *
 * <p>
 * {@code <number [0,∞]> | <length [0,∞]>}. Inherited; defaults to 8.
 * A number is a multiple of the space character (U+0020) advance; a length is the tab width directly.
 * Tab positions are integer multiples of the tab width from the line start
 * ({@code TextBuilder.control}). The specification multiplies
 * "space advance + letter-spacing + word-spacing", but this implementation multiplies
 * only the space advance (an approximation).
 * </p>
 *
 * <p>
 * Before 2026-08-29, the width was fixed at 24 pt (default 12 pt font × two characters).
 * The default multiplier 8 is about 24 pt with the default Japanese space advance
 * (roughly 3 pt for a 12 pt font).
 * </p>
 */
public class TabSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TabSize();

	private static final RealValue DEFAULT = RealValue.create(8);

	/** Whether the value is a multiple of the space width ({@code <number>}). */
	public static boolean isMultiple(final CSSStyle style) {
		return style.get(INFO) instanceof RealValue;
	}

	/** The multiplier for a number, or the absolute length (pt) for a length. */
	public static double get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value instanceof RealValue real) {
			return real.getReal();
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	protected TabSize() {
		super("tab-size");
	}

	public Value getDefault(final CSSStyle style) {
		return DEFAULT;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (token instanceof CssToken.Num num) {
			if (num.value() < 0) {
				throw new PropertyException();
			}
			return RealValue.create(num.value());
		}
		final Value length = ValueUtils.toLength(ua, token);
		if (length == null || (length instanceof AbsoluteLengthValue abs && abs.getLength() < 0)) {
			throw new PropertyException();
		}
		return length;
	}
}
