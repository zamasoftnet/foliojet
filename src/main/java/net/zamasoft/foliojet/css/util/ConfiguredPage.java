package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * The page the I/O properties configure (2026-10-11): its margins as {@code output.page-margins} gives them, read once
 * here for the pages (PageSequence) and for the page area that media queries, viewport units and container size
 * estimates measure ({@link #contentArea}). The document's {@code @page} is not consulted here.
 *
 * @author MIYABE Tatsuhiko
 */
public final class ConfiguredPage {
	private ConfiguredPage() {
		// utility
	}

	/** Defaults if UA properties cannot be read (A4, 12.7 mm margins). */
	private static final double DEFAULT_WIDTH_PT = 210 / 25.4 * 72;
	private static final double DEFAULT_HEIGHT_PT = 297 / 25.4 * 72;
	private static final double DEFAULT_MARGIN_PT = 12.7 / 25.4 * 72;

	/**
	 * {@code output.page-margins} as top, right, bottom and left, from one to four lengths separated by white space as
	 * the CSS margin shorthand takes them; null when it is not set or not valid (reported with {@code warn}).
	 * PageSequence read three values as right, bottom and a fourth that is not there until 2026-10-11 (an exception).
	 */
	public static AbsoluteLengthValue[] margins(final UserAgent ua, final boolean warn) {
		final String s = property(ua, UAProps.OUTPUT_PAGE_MARGINS);
		if (s == null) {
			return null;
		}
		final String[] values = s.trim().split("\\s+");
		final AbsoluteLengthValue[] m = new AbsoluteLengthValue[values.length];
		boolean ok = values.length >= 1 && values.length <= 4 && !values[0].isEmpty();
		for (int i = 0; ok && i < values.length; ++i) {
			m[i] = ValueUtils.toAbsoluteLength(ua, false, values[i]);
			ok = m[i] != null;
		}
		if (!ok) {
			if (warn) {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_MARGINS.name, s);
			}
			return null;
		}
		final AbsoluteLengthValue top = m[0];
		final AbsoluteLengthValue right = m.length > 1 ? m[1] : top;
		final AbsoluteLengthValue bottom = m.length > 2 ? m[2] : top;
		final AbsoluteLengthValue left = m.length > 3 ? m[3] : right;
		return new AbsoluteLengthValue[] { top, right, bottom, left };
	}

	/**
	 * The page area (type area) width and height (pt) of the configured paper: output.page-width and
	 * output.page-height less the left and right, and the top and bottom, output.page-margins.
	 */
	public static double[] contentArea(final UserAgent ua) {
		final double width = length(ua, UAProps.OUTPUT_PAGE_WIDTH, DEFAULT_WIDTH_PT);
		final double height = length(ua, UAProps.OUTPUT_PAGE_HEIGHT, DEFAULT_HEIGHT_PT);
		final AbsoluteLengthValue[] m = margins(ua, false);
		final double top = m == null ? DEFAULT_MARGIN_PT : m[0].getLength();
		final double right = m == null ? DEFAULT_MARGIN_PT : m[1].getLength();
		final double bottom = m == null ? DEFAULT_MARGIN_PT : m[2].getLength();
		final double left = m == null ? DEFAULT_MARGIN_PT : m[3].getLength();
		return new double[] { Math.max(0, width - left - right), Math.max(0, height - top - bottom) };
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
