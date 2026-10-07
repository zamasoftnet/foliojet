package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.OverflowValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * Axis-specific primitives for overflow-x / overflow-y. The
 * {@code overflow} shorthand is expanded to both axes by
 * {@link net.zamasoft.foliojet.css.impl.property.shorthand.OverflowShorthand}.
 *
 * @author MIYABE Tatsuhiko
 */
public class Overflow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_X = new Overflow("overflow-x");
	public static final PrimitivePropertyInfo INFO_Y = new Overflow("overflow-y");

	/**
	 * Logical-axis {@code overflow-block} / {@code overflow-inline} (css-overflow-3,
	 * 2026-08-30).
	 *
	 * <p>
	 * For print, {@code hidden}/{@code scroll}/{@code auto} all mean "clip".
	 * Since {@link #get(CSSStyle)} ultimately merges the axes,
	 * <b>the mapping to physical axes via writing-mode is not considered</b>.
	 * The simplified rule clips if any of the four values is non-visible
	 * (as with the physical version, clipping cannot be enabled for only one axis).
	 * </p>
	 */
	public static final PrimitivePropertyInfo INFO_BLOCK = new Overflow("overflow-block");

	/** @see #INFO_BLOCK */
	public static final PrimitivePropertyInfo INFO_INLINE = new Overflow("overflow-inline");

	/**
	 * Collapses both axes into a single rendering mode.
	 *
	 * <p>
	 * Under CSS Overflow 3 computation rules, if one axis is non-visible, visible on the other
	 * computes to auto (clipping and non-clipping axes cannot be mixed).
	 * For print, hidden/scroll/auto all clip and are treated alike,
	 * so returns visible only when both axes are visible; otherwise returns
	 * the non-visible axis's mode.
	 * </p>
	 */
	public static OverflowMode get(CSSStyle style) {
		final OverflowMode x = strongest(((OverflowValue) style.get(INFO_X)).getOverflow(),
				((OverflowValue) style.get(INFO_INLINE)).getOverflow());
		final OverflowMode y = strongest(((OverflowValue) style.get(INFO_Y)).getOverflow(),
				((OverflowValue) style.get(INFO_BLOCK)).getOverflow());
		if (x == y) {
			return x;
		}
		if (x == OverflowMode.VISIBLE) {
			return y;
		}
		if (y == OverflowMode.VISIBLE) {
			return x;
		}
		// If both axes are non-visible but have different types, they all clip during rendering
		// and are equivalent. Prefer the stronger mode (hidden).
		return (x == OverflowMode.HIDDEN || y == OverflowMode.HIDDEN) ? OverflowMode.HIDDEN : x;
	}

	/**
	 * Chooses the clipping mode from the physical and logical axes (2026-08-30).
	 * For print, everything except visible clips, so the non-visible value wins.
	 */
	private static OverflowMode strongest(final OverflowMode physical, final OverflowMode logical) {
		if (physical == OverflowMode.VISIBLE) {
			return logical;
		}
		if (logical == OverflowMode.VISIBLE) {
			return physical;
		}
		return (physical == OverflowMode.HIDDEN || logical == OverflowMode.HIDDEN) ? OverflowMode.HIDDEN : physical;
	}

	private Overflow(String name) {
		super(name);
	}

	public Value getDefault(CSSStyle style) {
		return OverflowValue.VISIBLE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = toValue(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	/**
	 * Converts one overflow keyword to a value. Returns null if it does not match.
	 */
	public static Value toValue(CssToken lu) {
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			switch (ident) {
			case "visible":
				return OverflowValue.VISIBLE_VALUE;
			case "hidden":
			// clip (CSS Overflow 3) clips without scrolling. For print,
			// it is equivalent to hidden.
			case "clip":
				return OverflowValue.HIDDEN_VALUE;
			case "scroll":
				return OverflowValue.SCROLL_VALUE;
			case "auto":
				return OverflowValue.AUTO_VALUE;
			}
		}
		return null;
	}

}
