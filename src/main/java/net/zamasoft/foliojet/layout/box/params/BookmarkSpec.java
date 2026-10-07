package net.zamasoft.foliojet.layout.box.params;

/**
 * Computed values of CSS {@code bookmark-level} and {@code bookmark-label} (css-gcpm-3)
 * (2026-10-04, TECH-20261003-004 ⑤). If both retain their defaults, they are not attached to the box
 * ({@code Params.bookmark == null}); bookmarks (PDF outline) are created from h1–h6 levels and
 * heading text as before.
 *
 * @param level bookmark level. 0 means no bookmark ({@code none}); -1 follows the document's
 *              heading level (h1–h6) ({@code auto})
 * @param label components of the bookmark text. A {@code null} entry means the element's text
 *              ({@code content()}). A {@code null} array means only the element's text
 */
public record BookmarkSpec(int level, String[] label) implements java.io.Serializable {
	/** Value that delegates the level to the document's heading. */
	public static final int LEVEL_AUTO = -1;

	/** Assembles the bookmark text. */
	public String title(final String elementText) {
		if (this.label == null) {
			return elementText;
		}
		final StringBuilder buff = new StringBuilder();
		for (final String part : this.label) {
			buff.append(part == null ? (elementText == null ? "" : elementText) : part);
		}
		return buff.length() == 0 ? null : buff.toString();
	}
}
