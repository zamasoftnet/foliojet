package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Four-side shorthands {@code margin} / {@code padding} / {@code border-width} / {@code border-style} /
 * {@code border-color} / {@code inset}. Distributes one to four values to top, right, bottom, and left
 * (one: all sides; two: top/bottom and left/right; three: top, left/right, bottom).
 * The base class handles CSS-wide keywords via {@link #longhands()}.
 *
 * <p>
 * Until 2026-10-04, six classes duplicated this distribution logic, with unreachable {@code inherit} branches
 * and three styles of CSS-wide keyword handling (only border-color handled them itself).
 * Aligned the structure with the logical {@link LogicalBoxShorthand}.
 * Five or more values are invalid (previously the fifth and later values were silently ignored).
 * </p>
 *
 * <p>
 * {@code inset} (css-logical §4.4) expands to physical sides, not logical axes, as specified.
 * Without support, absolute centering via {@code inset:0; margin:auto}
 * (a common real-world centering idiom) was discarded entirely, pinning the element to its static position
 * (top-left; asahi.com video play icons moved to the top-left, 2026-08-27).
 * </p>
 */
public final class BoxSidesShorthand extends AbstractShorthandPropertyInfo {
	/** Parsing strategy for each value type. */
	@FunctionalInterface
	private interface Reader {
		Value read(UserAgent ua, CssToken token) throws PropertyException;
	}

	public static final ShorthandPropertyInfo MARGIN = new BoxSidesShorthand("margin", Margin.TOP, Margin.RIGHT,
			Margin.BOTTOM, Margin.LEFT, BoxValueUtils::toMarginWidth);
	public static final ShorthandPropertyInfo PADDING = new BoxSidesShorthand("padding", Padding.TOP, Padding.RIGHT,
			Padding.BOTTOM, Padding.LEFT, BoxValueUtils::toPositiveLength);
	public static final ShorthandPropertyInfo BORDER_WIDTH = new BoxSidesShorthand("border-width", BorderWidth.TOP,
			BorderWidth.RIGHT, BorderWidth.BOTTOM, BorderWidth.LEFT, BorderValueUtils::toBorderWidth);
	public static final ShorthandPropertyInfo BORDER_STYLE = new BoxSidesShorthand("border-style", BorderStyle.TOP,
			BorderStyle.RIGHT, BorderStyle.BOTTOM, BorderStyle.LEFT, (ua, token) -> BorderValueUtils.toBorderStyle(token));
	public static final ShorthandPropertyInfo BORDER_COLOR = new BoxSidesShorthand("border-color", BorderColor.TOP,
			BorderColor.RIGHT, BorderColor.BOTTOM, BorderColor.LEFT, BoxSidesShorthand::toBorderColor);
	public static final ShorthandPropertyInfo INSET = new BoxSidesShorthand("inset", Inset.TOP, Inset.RIGHT,
			Inset.BOTTOM, Inset.LEFT, BoxValueUtils::toMarginWidth);

	private final PrimitivePropertyInfo[] sides;
	private final Reader reader;

	private BoxSidesShorthand(final String name, final PrimitivePropertyInfo top, final PrimitivePropertyInfo right,
			final PrimitivePropertyInfo bottom, final PrimitivePropertyInfo left, final Reader reader) {
		super(name);
		this.sides = new PrimitivePropertyInfo[] { top, right, bottom, left };
		this.reader = reader;
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return this.sides.clone();
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		final Value[] values = new Value[4];
		int n = 0;
		do {
			final Value value = this.reader.read(ua, tokens.next());
			if (value == null) {
				throw new PropertyException();
			}
			values[n++] = value;
		} while (n < 4 && tokens.hasNext());
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		final Value top = values[0];
		final Value right = n > 1 ? values[1] : top;
		final Value bottom = n > 2 ? values[2] : top;
		final Value left = n > 3 ? values[3] : right;
		primitives.set(this.sides[0], top);
		primitives.set(this.sides[1], right);
		primitives.set(this.sides[2], bottom);
		primitives.set(this.sides[3], left);
	}

	private static Value toBorderColor(final UserAgent ua, final CssToken token) {
		if (ColorValueUtils.isTransparent(token)) {
			return KeywordValue.TRANSPARENT;
		}
		// currentcolor is the DEFAULT sentinel (2026-08-29).
		return ColorValueUtils.toColorOrCurrent(ua, token);
	}
}
