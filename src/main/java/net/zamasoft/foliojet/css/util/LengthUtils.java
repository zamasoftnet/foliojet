package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.util.NumberUtils;

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
	 * パーセント値を絶対長さに変換します。
	 * 
	 * @param percentage
	 * @param srcLength
	 * @return
	 */
	public static double toAbsoluteLength(PercentageValue percentage, double srcLength) {
		double ratio = percentage.getRatio();
		return srcLength * ratio;
	}

	/**
	 * 比率を絶対長さに変換します。
	 * 
	 * @param real
	 * @param srcLength
	 * @return
	 */
	public static double toAbsoluteLength(RealValue real, double srcLength) {
		double ratio = real.getReal();
		return srcLength * ratio;
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

	public static AbsoluteLengthValue parseLength(UserAgent ua, String s)
			throws NumberFormatException, IllegalArgumentException {
		s = s.toLowerCase().trim();
		if (s.endsWith("q")) {
			double len = NumberUtils.parseDouble(s.substring(0, s.length() - 1));
			return AbsoluteLengthValue.create(ua, len, Unit.Q);
		}
		double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
		if (s.endsWith("mm")) {
			return AbsoluteLengthValue.create(ua, len, Unit.MM);
		} else if (s.endsWith("cm")) {
			return AbsoluteLengthValue.create(ua, len, Unit.CM);
		} else if (s.endsWith("pt")) {
			return AbsoluteLengthValue.create(ua, len, Unit.PT);
		} else if (s.endsWith("px")) {
			return AbsoluteLengthValue.create(ua, len, Unit.PX);
		} else if (s.endsWith("pc")) {
			return AbsoluteLengthValue.create(ua, len, Unit.PC);
		} else if (s.endsWith("in")) {
			return AbsoluteLengthValue.create(ua, len, Unit.IN);
		}
		throw new IllegalStateException();
	}
}
