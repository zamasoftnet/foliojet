package net.zamasoft.foliojet.ua.props;

/**
 * How to write text in single SVG output (B-1, user request on 2026-08-29).
 *
 * <p>
 * Page-split SVG already uses {@code <text>} plus WOFF2 subsets.
 * This makes the same mechanism selectable for self-contained SVG.
 * </p>
 */
public enum SvgTextMode implements PropCode {
	/**
	 * Converts glyphs to outlines (paths) (default).
	 *
	 * <p>
	 * Appearance is the same wherever opened, but text is shapes and cannot be selected.
	 * </p>
	 */
	OUTLINE,

	/**
	 * Retains {@code <text>} and embeds subset WOFF2 in the SVG.
	 *
	 * <p>
	 * To make each SVG self-contained, fonts and images are embedded as {@code data:}.
	 * Unlike page-split SVG, resources cannot be shared, so many pages increase total size.
	 * </p>
	 */
	KEEP;
}
