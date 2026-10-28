package net.zamasoft.foliojet.css;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A structured {@code @page} rule (named pages N1a, 2026-07-31;
 * consult-codex-2026-07-31-named-pages.txt Q1). One entry in the ordered
 * rule sequence that replaces the previous four buckets (unnamed/first/left/right).
 * Specificity is CSS Page 3's (f,g,h)=(page name, :first/:blank/:nth(), :left/:right/:single).
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

	/**
	 * {@code :nth(An+B [of name])} (CSS GCPM 3 §3.1, 2026-10-28), all of which must match (empty if none).
	 *
	 * @param a  A
	 * @param b  B
	 * @param of the page group's name, or {@code null}
	 */
	public record Nth(int a, int b, String of) {
		/**
		 * Whether the page matches: an {@code of} counts the pages of a document whose page group has that name, a
		 * named rule those of its own group, else the pages of the output.
		 */
		boolean matches(final String ruleName, final PagePosition position) {
			final String group = this.of != null ? this.of : ruleName;
			final int index = group == null ? position.book()
					: group.equals(position.group()) ? position.inDocument() : this.of != null ? 0 : position.book();
			if (index < 1) {
				return false;
			}
			// An integer n >= 0 with A*n + B = index (as NthCondition does for elements)
			if (this.a == 0) {
				return index == this.b;
			}
			final long diff = (long) index - this.b;
			return diff % this.a == 0 && diff / this.a >= 0;
		}
	}

	final List<Nth> nths;

	PageRule(final String name, final byte pseudoMask, final Declaration declaration, final int order) {
		this(name, pseudoMask, List.of(), declaration, order);
	}

	PageRule(final String name, final byte pseudoMask, final List<Nth> nths, final Declaration declaration,
			final int order) {
		this.name = name;
		this.pseudoMask = pseudoMask;
		this.nths = nths;
		this.declaration = declaration;
		this.order = order;
	}

	/** Returns CSS Page 3 (f,g,h) specificity encoded as a single integer. */
	int specificity() {
		final int f = this.name != null ? 1 : 0;
		// Each component stays within its byte: many :nth() must not pass a page name (codex 2026-10-28)
		final int g = Math.min(255, Integer.bitCount(this.pseudoMask & (PSEUDO_FIRST | PSEUDO_BLANK)) + this.nths.size());
		final int h = Integer.bitCount(this.pseudoMask & (PSEUDO_LEFT | PSEUDO_RIGHT | PSEUDO_SINGLE));
		return (f << 16) | (g << 8) | h;
	}

	/**
	 * Determines whether this rule matches the page.
	 *
	 * @param pageName current page name (null=unnamed)
	 * @param pseudo   page pseudo-state bits
	 * @param position where the page is, for {@code :nth()}
	 */
	boolean matches(final String pageName, final byte pseudo, final PagePosition position) {
		if (this.name != null && !this.name.equals(pageName)) {
			return false;
		}
		if ((this.pseudoMask & ~pseudo) != 0) {
			return false;
		}
		for (final Nth nth : this.nths) {
			if (!nth.matches(this.name, position)) {
				return false;
			}
		}
		return true;
	}
}
