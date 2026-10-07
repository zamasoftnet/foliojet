package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A cache that carries Paged SVG font subsets to the next conversion in the same session
 * (2026-08-29).
 *
 * <p>
 * Subsets were rebuilt for every conversion and emitted only after all pages were written
 * (observed: fonts returned HTTP 500 when the first page appeared at 4.2 seconds;
 * all became available at 10.6 seconds; development log). Changing font size does not change
 * the character set, so identical input produces identical subsets.
 * Store the built bytes and <b>glyph order</b> (original font GID → subset GID order) here;
 * in the next conversion:
 * </p>
 * <ol>
 * <li>Reassign code points in the same order (matching the private-use codes in the previous page SVG).</li>
 * <li>Emit the previous bytes unchanged <b>before</b> the first page.</li>
 * </ol>
 * <p>
 * Only when a previously absent glyph appears does the subset "grow". Emit it again at the end
 * under a different URI with the version advanced by one ({@code font-0001-2.woff2}).
 * Pages before growth are complete with the previous version and remain correct.
 * The expanded version is a superset of the previous one; code points stay unchanged.
 * </p>
 *
 * <p>
 * Lifetime is the session ({@code DirectSession} owns it and passes it through
 * {@link net.zamasoft.foliojet.ua.UAContext} to the UA recreated for each conversion).
 * Unlike image dimensions ({@code ImageMetricsCache}), which reset per document,
 * this cache persists across documents. Even for a different document, this only fixes
 * code-point assignments; content depends on the glyphs actually used, so it is harmless.
 * Subsets are small (measured at 0.1 MB).
 * </p>
 */
public final class PagedSvgFontCarry {
	/**
	 * One subset per original font, orientation, and synthetic italic setting.
	 *
	 * <p>
	 * {@code document} is the document defining subset scope (the EPUB spine item path;
	 * empty for a standalone document). Subsets are per item (2026-09-02), so carryover is
	 * looked up per item too: even with the same font, glyph order differs between chapters.
	 * </p>
	 */
	public record Key(String document, String fontName, String mode, boolean oblique) {
	}

	/** Cached copy of one subset built previously. */
	public record Entry(int id, int version, int[] gids, byte[] bytes, String sha256) {
	}

	private final Map<Key, Entry> entries = new LinkedHashMap<>();
	/**
	 * Next number per document. <b>The numbering namespace is per document (EPUB item)</b>
	 * (2026-09-02): each item has its own {@code assets/fonts/}, so uniqueness is needed only
	 * within that directory. Global numbering would make an item's font numbers depend on
	 * execution order during parallel layout, making output nondeterministic.
	 */
	private final Map<String, Integer> nextIds = new java.util.HashMap<>();

	public synchronized Entry get(final Key key) {
		return this.entries.get(key);
	}

	public synchronized void put(final Key key, final Entry entry) {
		this.entries.put(key, entry);
		this.nextIds.merge(key.document(), entry.id() + 1, Math::max);
	}

	/** Issues a number unique across conversions within that document. */
	public synchronized int allocateId(final String document) {
		final int id = this.nextIds.getOrDefault(document, 1);
		this.nextIds.put(document, id + 1);
		return id;
	}

	public synchronized List<Entry> entries() {
		return new ArrayList<>(this.entries.values());
	}

	/** Only the cached entries for the document (EPUB item, or empty for a standalone document). */
	public synchronized List<Entry> entries(final String document) {
		final List<Entry> list = new ArrayList<>();
		for (final Map.Entry<Key, Entry> e : this.entries.entrySet()) {
			if (e.getKey().document().equals(document)) {
				list.add(e.getValue());
			}
		}
		return list;
	}

	public synchronized boolean isEmpty() {
		return this.entries.isEmpty();
	}

	public synchronized void clear() {
		this.entries.clear();
		this.nextIds.clear();
	}
}
