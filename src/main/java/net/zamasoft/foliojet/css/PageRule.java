package net.zamasoft.foliojet.css;

import java.util.EnumMap;
import java.util.Map;

/**
 * A structured {@code @page} rule (named pages N1a, 2026-07-31;
 * consult-codex-2026-07-31-named-pages.txt Q1). One entry in the ordered
 * rule sequence that replaces the previous four buckets (unnamed/first/left/right).
 * Specificity is CSS Page 3's (f,g,h)=(page name, :first/:blank, :left/:right/:single).
 *
 * @author MIYABE Tatsuhiko
 */
public final class PageRule {

	public static final byte PSEUDO_FIRST = 1;

	public static final byte PSEUDO_LEFT = 2;

	public static final byte PSEUDO_RIGHT = 4;

	/** Copper extension selecting pages that are not part of a spread. */
	public static final byte PSEUDO_SINGLE = 8;

	/**
	 * A page with no content created by a forced page break ({@code :blank}, css-page-3).
	 * Determined at page rendering time, after content is final, so it affects
	 * only margin boxes (2026-10-04, item ⑤ of TECH-20261003-004).
	 */
	public static final byte PSEUDO_BLANK = 16;

	/** Page name (null=unnamed; case-sensitive as a CSS identifier). */
	final String name;

	/** Required pseudo-page bits ({@link #PSEUDO_FIRST}, etc.). */
	final byte pseudoMask;

	/** Regular declarations (margins, etc.; null if none). */
	final Declaration declaration;

	/** Margin box declarations (empty if none). */
	final Map<MarginBoxName, Declaration> marginBoxes = new EnumMap<>(MarginBoxName.class);

	/** Source order (breaks ties in specificity). */
	final int order;

	PageRule(final String name, final byte pseudoMask, final Declaration declaration, final int order) {
		this.name = name;
		this.pseudoMask = pseudoMask;
		this.declaration = declaration;
		this.order = order;
	}

	/** Returns CSS Page 3 (f,g,h) specificity encoded as a single integer. */
	int specificity() {
		final int f = this.name != null ? 1 : 0;
		final int g = Integer.bitCount(this.pseudoMask & (PSEUDO_FIRST | PSEUDO_BLANK));
		final int h = Integer.bitCount(this.pseudoMask & (PSEUDO_LEFT | PSEUDO_RIGHT | PSEUDO_SINGLE));
		return (f << 16) | (g << 8) | h;
	}

	/**
	 * Determines whether this rule matches the page.
	 *
	 * @param pageName current page name (null=unnamed)
	 * @param pseudo   page pseudo-state bits
	 */
	boolean matches(final String pageName, final byte pseudo) {
		if (this.name != null && !this.name.equals(pageName)) {
			return false;
		}
		return (this.pseudoMask & ~pseudo) == 0;
	}
}
