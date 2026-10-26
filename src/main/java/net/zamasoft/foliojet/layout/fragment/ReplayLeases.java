package net.zamasoft.foliojet.layout.fragment;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * The leases a resume session holds on the absorbed replay ranges of its continuation, per occurrence (2026-10-26,
 * design-pattern review: the PAGE and COLUMN sessions of RootBuilder each kept the same table).
 *
 * <p>
 * Absorbed ranges carry no boxes (no fallback), so they are protected from compaction until consumed (LayoutSource
 * clamps the watermark). Ownership is per occurrence ({@link Continuation.SourceRange} instance), so nested
 * continuations holding the same fromId do not interfere.
 * </p>
 */
public final class ReplayLeases {
	private final IdentityHashMap<Continuation.SourceRange, LayoutSource.RetentionLease> leases = new IdentityHashMap<>();

	/** Leases the ranges. */
	public void retain(final LayoutSource log, final List<Continuation.SourceRange> ranges) {
		for (final Continuation.SourceRange r : ranges) {
			this.leases.put(r, log.retainFrom(r.fromId()));
		}
	}

	/** Leases the prefix items of {@code frame} and of each child frame down its open tail. */
	public void retainChain(final LayoutSource log, final Continuation.ContinuationFrame frame) {
		for (Continuation.ContinuationFrame f = frame; f != null;) {
			this.retain(log, f.prefixItems());
			f = f.tail() instanceof Continuation.OpenTail.Child(final Continuation.ContinuationFrame child) ? child : null;
		}
	}

	/** Releases the lease of a consumed occurrence, if it holds one. */
	public void release(final Continuation.SourceRange occurrence) {
		final LayoutSource.RetentionLease lease = this.leases.remove(occurrence);
		if (lease != null) {
			lease.close();
		}
	}

	public boolean isEmpty() {
		return this.leases.isEmpty();
	}

	/**
	 * Releases what is left. Normal consumption has already released every lease; after an exception, leaving them
	 * behind would clamp all later compaction for good.
	 */
	public void releaseAll() {
		for (final LayoutSource.RetentionLease lease : this.leases.values()) {
			lease.close();
		}
		this.leases.clear();
	}
}
