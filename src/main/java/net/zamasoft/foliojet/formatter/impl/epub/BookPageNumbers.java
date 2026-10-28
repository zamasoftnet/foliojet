package net.zamasoft.foliojet.formatter.impl.epub;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Page numbers of the whole book for the spine items of an EPUB laid out as independent documents (Paged SVG) with
 * two or more passes (2026-10-21, a report from the paperback proofs: the table of contents had a number only on the
 * line pointing into its own item).
 *
 * <p>
 * Each item counts its pages from 1 in its first measuring pass. After every pass, all items wait here: an item's
 * first page continues the pages of the items before it (the {@code firstPage} of {@code index.json}), the page
 * numbers its targets recorded move by the same amount, and the targets an item looked up and did not find are
 * looked up in the other items. The next pass reads them as it reads a forward reference, so {@code counter(page)},
 * {@code counter(pages)} and a {@code target-counter()} into another item give the numbers of the book, as in PDF.
 * Each item's pages also take their sides in the book: only the book's first page is {@code :first}, and the sides
 * alternate across the items (2026-10-28). A single pass keeps the item's own numbering: nothing is known about the
 * other items until they are laid out.
 * </p>
 *
 * <p>
 * The page counts of an earlier conversion ({@code input.epub.page-counts}, {@link #seed}) stand in for that before the
 * first pass (2026-10-28, an e-book reader): a single pass, and the first pass of several, number the book from them.
 * They cover the whole spine, so the items not laid out keep their counts in the book: one chapter laid out alone is
 * numbered as in the whole book.
 * </p>
 *
 * <p>
 * Only the included items count ({@code input.epub.spine}), as in {@code index.json}. A page counter that the author
 * resets inside an item is not followed: the targets after the reset move with the item, and the next item starts
 * after the item's pages (PDF continues from the reset).
 * </p>
 */
final class BookPageNumbers {
	private final List<UserAgent> items;

	/** The item's document ({@code OEBPS/ch1.xhtml}), the target of a reference without a fragment. */
	private final List<URI> documents;

	/** The page counter before each item's first page in the pass just run. */
	private int[] offsets;

	/** The page count of each item in the pass just run. */
	private final int[] counts;

	/** The page count of the book in the pass just run. */
	private int pages = 0;

	private boolean shared = false;

	/** The pages of the items not laid out before each item, from the seed (0 without one). */
	private final int[] base;

	/** The pages of the items not laid out, from the seed. */
	private int notLaidOut = 0;

	/** The seed's count of each item, or {@code null} without a seed. */
	private int[] seeded = null;

	/** The one-based spine position of each item (for messages), or {@code null} without a seed. */
	private int[] positions = null;

	/** The copies of another item's targets that an item got at the last {@link #share()}. */
	private record Read(int item, URI uri, List<PageRef.Fragment> copies) {
	}

	private final List<Read> read = new ArrayList<>();

	BookPageNumbers(final List<UserAgent> items, final List<URI> documents) {
		this.items = items;
		this.documents = documents;
		this.offsets = new int[items.size()];
		this.counts = new int[items.size()];
		this.base = new int[items.size()];
	}

	/**
	 * Numbers the book from the page counts of an earlier conversion, before the first pass.
	 *
	 * @param spineCounts the page count of every spine item, in spine order
	 * @param included    whether each spine item is laid out (the items of this object, in order)
	 */
	void seed(final int[] spineCounts, final boolean[] included) {
		final int n = this.items.size();
		this.seeded = new int[n];
		this.positions = new int[n];
		int k = 0;
		int pages = 0;
		int skipped = 0;
		for (int i = 0; i < spineCounts.length; ++i) {
			if (included[i]) {
				this.offsets[k] = pages;
				this.base[k] = skipped;
				this.seeded[k] = spineCounts[i];
				this.positions[k] = i + 1;
				++k;
			} else {
				skipped += spineCounts[i];
			}
			pages += spineCounts[i];
		}
		assert k == n;
		this.notLaidOut = skipped;
		this.pages = pages;
		this.shared = true;
	}

	/** After a pass of every item: numbers the book for the next pass and shares the targets between the items. */
	void share() {
		final int n = this.items.size();
		final int[] next = new int[n];
		int pages = 0;
		for (int k = 0; k < n; ++k) {
			// The items not laid out keep the seed's pages before this one
			next[k] = this.base[k] + pages;
			this.counts[k] = this.items.get(k).getPassContext().getPageNumber();
			pages += this.counts[k];
		}
		pages += this.notLaidOut;
		for (int k = 0; k < n; ++k) {
			this.pageRef(k).shiftPages(next[k] - this.offsets[k], pages);
		}
		this.offsets = next;
		this.pages = pages;
		this.read.clear();
		for (int j = 0; j < n; ++j) {
			final PageRef pageRef = this.pageRef(j);
			for (final URI uri : pageRef.getMissing()) {
				if (!pageRef.currentFragments(uri).isEmpty()) {
					// Its own target, missed by a forward reference of the first pass. It stays the item's own even when
					// another item has the same key (two items with the same <base>)
					continue;
				}
				final List<PageRef.Fragment> found = this.find(uri, j);
				if (found != null) {
					this.read.add(new Read(j, uri, pageRef.importFragments(uri, found)));
				}
			}
		}
		this.shared = true;
	}

	/**
	 * The fragments of {@code uri} for item {@code j}: a target in another item, or the start of an item (an item does
	 * not record its own start). {@code null} if no item has it.
	 */
	private List<PageRef.Fragment> find(final URI uri, final int j) {
		for (int k = 0; k < this.items.size(); ++k) {
			if (k != j) {
				final List<PageRef.Fragment> fragments = this.pageRef(k).currentFragments(uri);
				if (!fragments.isEmpty()) {
					return fragments;
				}
			}
			if (uri.equals(this.documents.get(k))) {
				return List.of(PageRef.documentStart(uri, this.offsets[k] + 1, this.pages));
			}
		}
		return null;
	}

	/** At the start of a pass of item {@code k}, after {@code prepare}: its first page continues the book. */
	void begin(final int k) {
		if (!this.shared) {
			return;
		}
		final CounterScope scope = this.items.get(k).getPassContext().getCounterScope(0, true);
		scope.reset("page", this.offsets[k]);
		scope.reset("pages", this.pages);
		// Its pages take their sides in the book: only the book's first page is :first (2026-10-28, cti.li's reader)
		this.items.get(k).getPassContext().setPagesBefore(this.offsets[k]);
	}

	/**
	 * Whether the last pass gave the numbers it was laid out with: every item kept its page count, its own forward
	 * references converged, and what the last pass read from another item's targets (the counters it read, and the text
	 * if {@code target-text()} read it) did not change in that pass. Call after the last pass of every item.
	 */
	boolean converged() {
		for (int k = 0; k < this.items.size(); ++k) {
			if (this.items.get(k).getPassContext().getPageNumber() != this.counts[k]
					|| this.pageRef(k).isUnconverged()) {
				return false;
			}
		}
		for (final Read r : this.read) {
			final List<PageRef.Fragment> now = this.find(r.uri(), r.item());
			for (int i = 0; i < r.copies().size(); ++i) {
				final PageRef.Fragment copy = r.copies().get(i);
				if (copy.isStale() && (now == null || i >= now.size() || PageRef.readChanged(copy, now.get(i)))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The items whose page count differs from the seed, as {@code 3:28→29} (one-based spine positions; the first five and
	 * how many more), or {@code null} if none or without a seed. Call after the last pass of every item.
	 */
	String changedFromSeed() {
		if (this.seeded == null) {
			return null;
		}
		final StringBuilder changed = new StringBuilder();
		int count = 0;
		for (int k = 0; k < this.items.size(); ++k) {
			final int pages = this.items.get(k).getPassContext().getPageNumber();
			if (pages != this.seeded[k] && ++count <= 5) {
				if (changed.length() > 0) {
					changed.append(", ");
				}
				changed.append(this.positions[k]).append(':').append(this.seeded[k]).append('\u2192').append(pages);
			}
		}
		if (count > 5) {
			changed.append(", +").append(count - 5);
		}
		return count == 0 ? null : changed.toString();
	}

	private PageRef pageRef(final int k) {
		return this.items.get(k).getUAContext().getPageRef();
	}
}
