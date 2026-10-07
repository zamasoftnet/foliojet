package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Utilities for length calculations.
 *
 * @author MIYABE Tatsuhiko
 */
public final class LengthUtils {
	private LengthUtils() {
		// unused
	}

	/**
	 * Converts units.
	 */
	public static double convert(UserAgent ua, double length, Unit fromUnit, Unit toUnit) {
		if (fromUnit == toUnit) {
			return length;
		}
		return length * inchesPer(ua, fromUnit) / inchesPer(ua, toUnit);
	}

	/**
	 * Returns inches per unit.
	 */
	private static double inchesPer(UserAgent ua, Unit unit) {
		switch (unit) {
		case IN:
			return 1;
		case CM:
			return 1 / 2.54;
		case MM:
			return 1 / 25.4;
		case Q:
			return 1 / (25.4 * 4);
		case PT:
			return 1 / 72.0;
		case PC:
			return 1 / 6.0;
		case PX:
			return 1 / ua.getPixelsPerInch();
		default:
			throw new IllegalArgumentException(unit.toString());
		}
	}
}
