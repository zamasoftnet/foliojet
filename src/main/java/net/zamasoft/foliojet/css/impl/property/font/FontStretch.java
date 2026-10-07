package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-stretch} ({@code font-width} in css-fonts-4;
 * {@code font-stretch} is the legacy name) (added 2026-08-29).
 *
 * <p>
 * {@code normal | ultra-condensed | extra-condensed | condensed |
 * semi-condensed | semi-expanded | expanded | extra-expanded |
 * ultra-expanded | <percentage [0,∞]>}. Inherited; defaults to {@code normal} (100%).
 * Stores the value as a percentage ({@link PercentageValue}); {@link #getWidthClass}
 * rounds it to OpenType OS/2 {@code usWidthClass} (1..9)
 * (the mapping table in §2.3; percentages not in the table use the nearest class).
 * </p>
 *
 * <p>
 * <b>Effect</b>: carries the width class as {@code FontStyleImpl.widthClass}.
 * pdfg2d font selection ({@code PDFFontSourceManager.lookup}) chooses the face with the
 * nearest width class among italic/weight ties (narrower first for a request at or below
 * normal width, wider first otherwise; css-fonts-4 §5.2).
 * A face's width class comes from OS/2 {@code usWidthClass} during {@code <font-dir>} scanning
 * or from the {@code font-stretch} descriptor in {@code @font-face}.
 * Width synthesis (stretching/compressing glyphs) is not performed, so appearance is unchanged
 * if no face with a different width class exists.
 * </p>
 */
public class FontStretch extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontStretch();

	/** usWidthClass corresponding to {@code normal}. */
	public static final int NORMAL_WIDTH_CLASS = 5;

	/** Percentages corresponding to usWidthClass 1..9 (css-fonts-4 §2.3). */
	private static final double[] CLASS_PERCENTAGES = { 50, 62.5, 75, 87.5, 100, 112.5, 125, 150, 200 };

	private static final String[] KEYWORDS = { "ultra-condensed", "extra-condensed", "condensed", "semi-condensed",
			"normal", "semi-expanded", "expanded", "extra-expanded", "ultra-expanded" };

	public static PercentageValue get(final CSSStyle style) {
		return (PercentageValue) style.get(INFO);
	}

	/** Rounds the computed percentage to OS/2 usWidthClass (1..9). */
	public static int getWidthClass(final CSSStyle style) {
		return toWidthClass(get(style).getPercentage());
	}

	public static int toWidthClass(final double percentage) {
		int best = NORMAL_WIDTH_CLASS;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < CLASS_PERCENTAGES.length; ++i) {
			final double distance = Math.abs(CLASS_PERCENTAGES[i] - percentage);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i + 1;
			}
		}
		return best;
	}

	protected FontStretch() {
		super("font-stretch");
	}

	public Value getDefault(final CSSStyle style) {
		return PercentageValue.FULL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (token instanceof CssToken.Percent percent) {
			if (percent.value() < 0) {
				throw new PropertyException();
			}
			return PercentageValue.create(percent.value());
		}
		if (token instanceof CssToken.Ident ident) {
			for (int i = 0; i < KEYWORDS.length; ++i) {
				if (ident.is(KEYWORDS[i])) {
					return PercentageValue.create(CLASS_PERCENTAGES[i]);
				}
			}
		}
		throw new PropertyException();
	}
}
