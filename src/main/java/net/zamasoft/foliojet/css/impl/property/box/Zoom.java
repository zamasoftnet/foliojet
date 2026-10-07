package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code zoom} (css-viewport-1 §4, added 2026-08-29).
 *
 * <p>
 * {@code <number [0,∞]> | <percentage [0,∞]> | normal | reset}. Not inherited;
 * defaults to 1. As specified, 0 equals 1 (old IE hid the element, but the current specification
 * defines {@code 0}=1).
 * </p>
 *
 * <p>
 * <b>Approximation</b>: the specified {@code zoom} affects layout (multiplying all computed
 * lengths of the element and its descendants and pushing surrounding content outward),
 * but this implementation scales during rendering. It scales drawing of the element and
 * its descendants about the <b>top-left of the element's border box</b>
 * ({@code AbstractBox.transform}; outside the author's {@code transform},
 * unaffected by {@code transform-origin}).
 * Surrounding layout is unchanged, so the enlarged part overlaps adjacent content.
 * Real sites mostly use {@code zoom:1} (IE's hasLayout trigger), which is identity and harmless.
 * </p>
 */
public class Zoom extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Zoom();

	public static double get(final CSSStyle style) {
		return ((RealValue) style.get(INFO)).getReal();
	}

	protected Zoom() {
		super("zoom");
	}

	public Value getDefault(final CSSStyle style) {
		return RealValue.ONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		double zoom;
		if (token instanceof CssToken.Num num) {
			zoom = num.value();
		} else if (token instanceof CssToken.Percent percent) {
			zoom = percent.value() / 100.0;
		} else if (token instanceof CssToken.Ident ident && (ident.is("normal") || ident.is("reset"))) {
			// reset (old WebKit: cancel ancestor zoom) is 1 because ancestors are not traversed.
			return RealValue.ONE;
		} else {
			throw new PropertyException();
		}
		if (zoom < 0) {
			throw new PropertyException();
		}
		if (zoom == 0) {
			zoom = 1;
		}
		return RealValue.create(zoom);
	}
}
