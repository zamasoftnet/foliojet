package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.util.Locale;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontVariantNumericValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code font-variant-numeric}(CSS Fonts Level 3)です。 */
public final class FontVariantNumeric extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariantNumeric();

	public static FontVariantNumericValue get(final CSSStyle style) {
		return (FontVariantNumericValue) style.get(INFO);
	}

	private FontVariantNumeric() {
		super("font-variant-numeric");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return FontVariantNumericValue.NORMAL_VALUE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("normal")) {
			return FontVariantNumericValue.NORMAL_VALUE;
		}
		String figure = null;
		String spacing = null;
		String fraction = null;
		boolean ordinal = false;
		boolean slashedZero = false;
		while (tokens.hasNext()) {
			final String raw = tokens.ident();
			if (raw == null) {
				throw new PropertyException();
			}
			final String ident = raw.toLowerCase(Locale.ROOT);
			switch (ident) {
			case "lining-nums", "oldstyle-nums":
				if (figure != null) {
					throw new PropertyException();
				}
				figure = ident.equals("lining-nums") ? "lnum" : "onum";
				break;
			case "proportional-nums", "tabular-nums":
				if (spacing != null) {
					throw new PropertyException();
				}
				spacing = ident.equals("proportional-nums") ? "pnum" : "tnum";
				break;
			case "diagonal-fractions", "stacked-fractions":
				if (fraction != null) {
					throw new PropertyException();
				}
				fraction = ident.equals("diagonal-fractions") ? "frac" : "afrc";
				break;
			case "ordinal":
				if (ordinal) {
					throw new PropertyException();
				}
				ordinal = true;
				break;
			case "slashed-zero":
				if (slashedZero) {
					throw new PropertyException();
				}
				slashedZero = true;
				break;
			default:
				throw new PropertyException();
			}
		}
		if (figure == null && spacing == null && fraction == null && !ordinal && !slashedZero) {
			throw new PropertyException();
		}
		return FontVariantNumericValue.create(figure, spacing, fraction, ordinal, slashedZero);
	}
}
