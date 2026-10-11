package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.ua.UserAgent;

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
 * {@code output.page-margins}, {@link ConfiguredPage#contentArea}); media queries measure the same area (2026-10-11).
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

	/**
	 * Resolves one unit.
	 *
	 * @param ua    UA (reads page-dimension properties)
	 * @param unit  one of VW/VH/VMIN/VMAX
	 * @param value multiplier for the unit
	 */
	public static AbsoluteLengthValue resolve(final UserAgent ua, final Unit unit, final double value) {
		final double[] area = ConfiguredPage.contentArea(ua);
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
}
