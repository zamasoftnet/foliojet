package net.zamasoft.foliojet.ua;

/**
 * 書き出す JSON の文字列と数の書き方です(画像寸法表とページ分割SVGの索引・資源表で共有する)。
 *
 * <p>
 * 2026-10-04 まで {@code ImageMetricsJSON} と {@code PagedSVGResources} に同じ規則の写しがあり、
 * 後者は無限大を {@code (long)} で 9223372036854775807 と書いていた。
 * </p>
 */
public final class JsonText {
	private JsonText() {
	}

	/** 引用符で囲み、JSON の規則で逃がした文字列です。 */
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

	/** 数です。整数は整数のまま書く(1200.0 ではなく 1200)。 */
	public static String number(final double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value)) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}
}
