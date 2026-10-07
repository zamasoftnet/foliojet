package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * Resolves viewport units ({@code vw}/{@code vh}/{@code vmin}/{@code vmax}, plus
 * small/large/dynamic variants and vi/vb folded into the same values by {@link Unit#of})
 * to absolute lengths (2026-08-29).
 *
 * <p>
 * The initial containing block in paged media is the <b>page area</b> (the type area
 * remaining after {@code @page} margins are removed from the page box), so one unit
 * is 1% of that area. Since {@code @page} rules have not been aggregated at parse time,
 * uses UA properties ({@code output.page-width}/{@code output.page-height}/
 * {@code output.page-margins}, the same source as media-query width checks).
 * Values diverge if the document changes the type area with {@code @page { size; margin }}
 * (a documented approximation).
 * </p>
 *
 * <p>
 * Conversions of 50 real sites frequently encountered these inside calc(), e.g.
 * {@code min(192px, 100vh)} and {@code calc(100vw - 2rem)} (565 occurrences on 45 sites).
 * Since calc() leaves pass through {@code ValueUtils.toAbsoluteLength}, this class,
 * called from there, centralizes resolution.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ViewportUnits {
	private ViewportUnits() {
		// utility
	}

	/** Defaults if UA properties cannot be read (A4, 12.7 mm margins). */
	private static final double DEFAULT_WIDTH_PT = 210 / 25.4 * 72;
	private static final double DEFAULT_HEIGHT_PT = 297 / 25.4 * 72;
	private static final double DEFAULT_MARGIN_PT = 12.7 / 25.4 * 72;

	/**
	 * Resolves one unit.
	 *
	 * @param ua    UA (reads page-dimension properties)
	 * @param unit  one of VW/VH/VMIN/VMAX
	 * @param value multiplier for the unit
	 */
	public static AbsoluteLengthValue resolve(final UserAgent ua, final Unit unit, final double value) {
		final double[] area = contentArea(ua);
		final double base;
		switch (unit) {
		case VW:
			base = area[0];
			break;
		case VH:
			base = area[1];
			break;
		case VMIN:
			base = Math.min(area[0], area[1]);
			break;
		case VMAX:
			base = Math.max(area[0], area[1]);
			break;
		default:
			throw new IllegalArgumentException(unit.toString());
		}
		return AbsoluteLengthValue.create(ua, base * value / 100);
	}

	/** Type area (page area) width and height (pt). */
	static double[] contentArea(final UserAgent ua) {
		double width = length(ua, UAProps.OUTPUT_PAGE_WIDTH, DEFAULT_WIDTH_PT);
		double height = length(ua, UAProps.OUTPUT_PAGE_HEIGHT, DEFAULT_HEIGHT_PT);
		// Margins use 1–4 values (top right bottom left), like CSS margin
		double top = DEFAULT_MARGIN_PT, right = DEFAULT_MARGIN_PT, bottom = DEFAULT_MARGIN_PT,
				left = DEFAULT_MARGIN_PT;
		final String margins = property(ua, UAProps.OUTPUT_PAGE_MARGINS);
		if (margins != null) {
			final String[] values = margins.trim().split("[\s]+");
			final double[] m = new double[values.length];
			boolean ok = values.length >= 1 && values.length <= 4;
			for (int i = 0; ok && i < values.length; ++i) {
				final AbsoluteLengthValue l = ValueUtils.toAbsoluteLength(ua, false, values[i]);
				if (l == null) {
					ok = false;
				} else {
					m[i] = l.getLength();
				}
			}
			if (ok) {
				top = m[0];
				right = m.length > 1 ? m[1] : m[0];
				bottom = m.length > 2 ? m[2] : m[0];
				left = m.length > 3 ? m[3] : right;
			}
		}
		width = Math.max(0, width - left - right);
		height = Math.max(0, height - top - bottom);
		return new double[] { width, height };
	}

	private static double length(final UserAgent ua, final net.zamasoft.foliojet.ua.props.StringPropManager prop,
			final double fallback) {
		final String s = property(ua, prop);
		final AbsoluteLengthValue l = s == null ? null : ValueUtils.toAbsoluteLength(ua, false, s);
		return l == null ? fallback : l.getLength();
	}

	/**
	 * Reads a UA property. Falls back to the default when {@code getProperty} is not
	 * implemented, as in unit-test Proxy UAs.
	 */
	private static String property(final UserAgent ua, final net.zamasoft.foliojet.ua.props.StringPropManager prop) {
		try {
			return prop.getString(ua);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
