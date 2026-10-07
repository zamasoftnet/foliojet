package net.zamasoft.foliojet.ua;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Output that can receive multiple documents (EPUB spine items) as <b>independent units</b>
 * (2026-09-02).
 *
 * <p>
 * Each item is "exactly the output of converting a single document," laid out with its own
 * child UA. Children are independent, so layout can run in parallel, and sequential layout
 * produces bit-for-bit identical output. Results are released <b>in call order (spine order)</b>:
 * the first item streams as it is laid out; later items wait until it completes.
 * The full design is in {@code docs/epub-paged-svg-design.md}.
 * </p>
 *
 * <p>
 * Passing an EPUB to a UA that does not implement this streams all items sequentially
 * through one UA as before (PDF/image output).
 * </p>
 */
public interface MultiDocumentOutput extends UserAgent {
	/**
	 * Description of a document unit (spine item).
	 *
	 * @param index    position in the spine (1-based); excluded items also consume an index
	 * @param idref    OPF {@code itemref/@idref}
	 * @param uri      item path (absolute within the EPUB, e.g., {@code OEBPS/ch1.xhtml})
	 * @param included whether to lay out this item in this conversion
	 *                  (filtering with {@code input.epub.spine} can make some false)
	 */
	record DocumentUnit(int index, String idref, URI uri, boolean included) {
	}

	/** Table of contents entry. {@code uri} is the item path; {@code fragment} is the position within it. */
	record TocEntry(String label, URI uri, String fragment, List<TocEntry> children) {
	}

	/**
	 * Overall description. Supplied once before opening any item.
	 *
	 * @param units                    all items in spine order (including excluded items)
	 * @param pageProgressionDirection {@code ltr}/{@code rtl}/{@code default}
	 * @param metadata                 title, author, etc.
	 * @param toc                      table of contents (empty if absent)
	 */
	record DocumentSet(List<DocumentUnit> units, String pageProgressionDirection, Map<String, String> metadata,
			List<TocEntry> toc) {
	}

	/** Describes the whole output. Call exactly once, before {@link #openDocument}. */
	void describeDocuments(DocumentSet documents);

	/**
	 * Returns a child UA for laying out an item.
	 *
	 * <p>
	 * <b>Call order determines release order.</b> Call in spine order.
	 * The caller drives its own passes on the returned UA ({@code prepare} → layout → {@code finish})
	 * and may run it on another thread. {@code finish()} notifies the parent that the item is complete.
	 * </p>
	 */
	UserAgent openDocument(DocumentUnit unit);
}
