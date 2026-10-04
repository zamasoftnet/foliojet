package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 長さ計算のためのユーティリティです。
 * 
 * @author MIYABE Tatsuhiko
 */
public final class LengthUtils {
	private LengthUtils() {
		// unused
	}

	/**
	 * 単位換算します。
	 */
	public static double convert(UserAgent ua, double length, Unit fromUnit, Unit toUnit) {
		if (fromUnit == toUnit) {
			return length;
		}
		return length * inchesPer(ua, fromUnit) / inchesPer(ua, toUnit);
	}

	/**
	 * 1単位あたりのインチ数を返します。
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
