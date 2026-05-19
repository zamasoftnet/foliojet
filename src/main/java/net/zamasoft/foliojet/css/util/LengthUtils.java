package net.zamasoft.foliojet.css.util;

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
 * @version $Id: LengthUtils.java 1554 2018-04-26 03:34:02Z miyabe $
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
	 * 
	 * @param ua
	 * @param length
	 * @param fromUnit
	 * @param toUnit
	 * @return
	 */
	public static double convert(UserAgent ua, double length, short fromUnit, short toUnit) {
		switch (toUnit) {
		case LengthValue.UNIT_IN:
			switch (fromUnit) {
			case LengthValue.UNIT_IN:
				return length;

			case LengthValue.UNIT_CM:
				return length / 2.54;

			case LengthValue.UNIT_MM:
				return length / 25.4;

			case LengthValue.UNIT_PT:
				return length / 72.0;

			case LengthValue.UNIT_PC:
				return length / 6.0;

			case LengthValue.UNIT_PX:
				return length / ua.getPixelsPerInch();

			default:
				throw new IllegalArgumentException();
			}

		case LengthValue.UNIT_CM:
			switch (fromUnit) {
			case LengthValue.UNIT_IN:
				return length * 2.54;

			case LengthValue.UNIT_CM:
				return length;

			case LengthValue.UNIT_MM:
				return length / 10.0;

			case LengthValue.UNIT_PT:
				return length * 2.54 / 72.0;

			case LengthValue.UNIT_PC:
				return length * 2.54 / 6.0;

			case LengthValue.UNIT_PX:
				return length * 2.54 / ua.getPixelsPerInch();

			default:
				throw new IllegalArgumentException();
			}

		case LengthValue.UNIT_MM:
			switch (fromUnit) {
			case LengthValue.UNIT_IN:
				return length * 25.4;

			case LengthValue.UNIT_CM:
				return length * 10.0;

			case LengthValue.UNIT_MM:
				return length;

			case LengthValue.UNIT_PT:
				return length * 25.4 / 72.0;

			case LengthValue.UNIT_PC:
				return length * 25.4 / 6.0;

			case LengthValue.UNIT_PX:
				return length * 25.4 / ua.getPixelsPerInch();

			default:
				throw new IllegalArgumentException();
			}

		case LengthValue.UNIT_PT:
			switch (fromUnit) {
			case LengthValue.UNIT_IN:
				return length * 72.0;

			case LengthValue.UNIT_CM:
				return length * 72.0 / 2.54;

			case LengthValue.UNIT_MM:
				return length * 72.0 / 25.4;

			case LengthValue.UNIT_PT:
				return length;

			case LengthValue.UNIT_PC:
				return length * 12.0;

			case LengthValue.UNIT_PX:
				return length * 72.0 / ua.getPixelsPerInch();

			default:
				throw new IllegalArgumentException();
			}

		case LengthValue.UNIT_PC:
			switch (fromUnit) {
			case LengthValue.UNIT_IN:
				return length * 6.0;

			case LengthValue.UNIT_CM:
				return length * 6.0 / 2.54;

			case LengthValue.UNIT_MM:
				return length * 6.0 / 25.4;

			case LengthValue.UNIT_PT:
				return length / 6.0;

			case LengthValue.UNIT_PC:
				return length;

			case LengthValue.UNIT_PX:
				return length * 6.0 / ua.getPixelsPerInch();

			default:
				throw new IllegalArgumentException();
			}

		case LengthValue.UNIT_PX:
			switch (fromUnit) {
			case LengthValue.UNIT_IN:
				return length * ua.getPixelsPerInch();

			case LengthValue.UNIT_CM:
				return length * ua.getPixelsPerInch() / 2.54;

			case LengthValue.UNIT_MM:
				return length * ua.getPixelsPerInch() / 25.4;

			case LengthValue.UNIT_PT:
				return length * ua.getPixelsPerInch() / 72.0;

			case LengthValue.UNIT_PC:
				return length * ua.getPixelsPerInch() / 6.0;

			case LengthValue.UNIT_PX:
				return length;

			default:
				throw new IllegalArgumentException();
			}

		default:
			throw new IllegalArgumentException();
		}
	}

	public static AbsoluteLengthValue parseLength(UserAgent ua, String s)
			throws NumberFormatException, IllegalArgumentException {
		s = s.toLowerCase().trim();
		double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
		if (s.endsWith("mm")) {
			return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_MM);
		} else if (s.endsWith("cm")) {
			return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_CM);
		} else if (s.endsWith("pt")) {
			return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_PT);
		} else if (s.endsWith("px")) {
			return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_PX);
		} else if (s.endsWith("pc")) {
			return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_PT);
		} else if (s.endsWith("in")) {
			return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_IN);
		}
		throw new IllegalStateException();
	}
}