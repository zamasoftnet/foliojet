package net.zamasoft.foliojet.css;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.zamasoft.foliojet.css.selector.SimpleSelector;

/**
 * The children of one element, shared by their {@link CSSElement}s (2026-10-11). Counts their element types for
 * {@code :nth-of-type()}, remembers for the general sibling combinator ({@code ~}) the nearest sibling matching a
 * selector, and keeps the chain of preceding siblings bounded. Until then {@code :nth-child()} and
 * {@code :nth-of-type()} walked the chain back to the first child, {@code ~} walked it until a sibling matched, and
 * every closed sibling stayed: on a flat document of 40,000 paragraphs {@code .zz ~ p} took 128 seconds instead of 7.
 */
final class Siblings {
	/**
	 * How many preceding siblings each element keeps reachable, at least (twice as many at most). Sibling
	 * combinators do not look further back than that, except through what a {@link Memo} holds.
	 */
	static final int KEPT = 1024;

	private record Type(String uri, String lName) {
	}

	private final Map<Type, int[]> types = new HashMap<Type, int[]>();

	/** The memos of {@code ~}, by the selector the siblings are matched against. */
	private Map<SimpleSelector, Memo> memos;

	/** The one-based position of a new child among the children of its type. */
	int nextTypeIndex(final String uri, final String lName) {
		final int[] count = this.types.computeIfAbsent(new Type(uri, lName), k -> new int[1]);
		return ++count[0];
	}

	/**
	 * Cuts the chain behind {@code last}, the newest child, once every {@link #KEPT} children: what stays reachable
	 * from it is between {@link #KEPT} and twice as many siblings.
	 */
	static void keepBounded(final CSSElement last) {
		if (last.childIndex % KEPT != 0) {
			return;
		}
		CSSElement e = last;
		for (int i = 0; i < KEPT && e != null; ++i) {
			e = e.precedingElement;
		}
		if (e != null) {
			e.precedingElement = null;
		}
	}

	/**
	 * For one selector, matched with ancestors of one length: the nearest sibling at or before {@link #scanned}
	 * that matches it ({@link #match}, null if none). A later query walks back only to {@link #scanned}: a sibling
	 * that matched once still matches, as long as the selector cannot change its answer while the document goes on
	 * ({@link #cacheable}: it has no {@code :has()}).
	 */
	static final class Memo {
		final int depth;

		final boolean cacheable;

		CSSElement scanned, match;

		/** The memo of the same selector for another length of ancestors. */
		Memo next;

		Memo(final int depth, final boolean cacheable, final CSSElement scanned, final CSSElement match) {
			this.depth = depth;
			this.cacheable = cacheable;
			this.scanned = scanned;
			this.match = match;
		}
	}

	Memo memo(final SimpleSelector selector, final int depth) {
		if (this.memos != null) {
			for (Memo m = this.memos.get(selector); m != null; m = m.next) {
				if (m.depth == depth) {
					return m;
				}
			}
		}
		return null;
	}

	void addMemo(final SimpleSelector selector, final Memo memo) {
		if (this.memos == null) {
			this.memos = new IdentityHashMap<SimpleSelector, Memo>();
		}
		memo.next = this.memos.put(selector, memo);
	}
}
