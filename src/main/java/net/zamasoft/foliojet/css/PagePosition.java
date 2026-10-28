package net.zamasoft.foliojet.css;

/**
 * Where a page is, for {@code @page :nth()} (CSS GCPM 3 §3.1, 2026-10-28).
 *
 * @param book       the page's number in the output from 1: the book of an EPUB, continued across its items
 * @param inDocument the page's number in its document's page group from 1 (0 outside it)
 * @param group      the name of the document's page group (the {@code page} of its html or body) when the page is in
 *                   it, or {@code null}
 */
public record PagePosition(int book, int inDocument, String group) {
	/** No position known: no {@code :nth()} matches. */
	public static final PagePosition NONE = new PagePosition(0, 0, null);
}
