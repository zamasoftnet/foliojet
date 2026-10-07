package net.zamasoft.foliojet.ua;

/**
 * Formatting of JSON strings and numbers for export
 * (shared by image metrics tables and page-split SVG indexes/resource tables).
 *
 * <p>
 * Until 2026-10-04, {@code ImageMetricsJSON} and {@code PagedSVGResources} had copies of the
 * same rules; the latter wrote infinity as 9223372036854775807 through a {@code (long)} cast.
 * </p>
 */
public final class JsonText {
	private JsonText() {
	}

	/** Quoted string escaped according to JSON rules. */
	public static String quoted(final String value) {
		final StringBuilder out = new StringBuilder(value.length() + 2);
		out.append('"');
		for (int i = 0; i < value.length(); ++i) {
			final char c = value.charAt(i);
			switch (c) {
			case '"' -> out.append("\\\"");
			case '\\' -> out.append("\\\\");
			case '\b' -> out.append("\\b");
			case '\f' -> out.append("\\f");
			case '\n' -> out.append("\\n");
			case '\r' -> out.append("\\r");
			case '\t' -> out.append("\\t");
			default -> {
				if (c < 0x20) {
					out.append(String.format("\\u%04x", (int) c));
				} else {
					out.append(c);
				}
			}
			}
		}
		return out.append('"').toString();
	}

	/** Number. Writes integers as integers (1200 rather than 1200.0). */
	public static String number(final double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value)) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}
}
