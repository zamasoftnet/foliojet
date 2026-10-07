package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves per-page assignments for each name in document order.
 * Retains only three candidate values: entry/first/last (O(1) storage per name).
 * <p>
 * For repeated registration of the same (name, order), <b>the later call wins</b>.
 * Pseudo-elements share a document-order key (`elementKey` is -1), EPUB numbering restarts
 * per chapter, and assignments registered immediately at build time are registered again when
 * placement is finalized. Duplicate registration therefore occurs on normal paths
 * (codex review 2026-09-05 R1a #1/#2/#3).
 * </p>
 */
public final class PageAssignmentState<T> {

	public enum Mode { FIRST, START, LAST, FIRST_EXCEPT }

	public enum Presence { ABSENT, VALUE, SUPPRESSED }

	/** order is stable document order; beginsPage is a fact determined when placement is finalized. */
	public record Assignment<T>(long order, T value, boolean beginsPage) {
	}

	/** Resolution result distinguishing unregistered, value, and suppressed states. */
	public record Resolution<T>(Presence presence, T value) {
	}

	/** The three candidates at call time. Does not copy the values themselves. */
	public record Snapshot<T>(Assignment<T> entry, Assignment<T> first, Assignment<T> last) {
	}

	private static final class Candidates<T> {
		Assignment<T> entry, first, last;
	}

	private final Map<String, Candidates<T>> names = new HashMap<String, Candidates<T>>();

	/** Registers a resolved value. */
	public void assign(final String name, final T value, final long order, final boolean beginsPage) {
		this.register(name, new Assignment<T>(order, Objects.requireNonNull(value), beginsPage));
	}

	private void register(final String name, final Assignment<T> assignment) {
		final Candidates<T> candidates = this.names.computeIfAbsent(Objects.requireNonNull(name),
				key -> new Candidates<T>());
		// A later call replaces the same order (discard intermediate orders that are not candidates)
		if (candidates.first == null || assignment.order() <= candidates.first.order()) {
			candidates.first = assignment;
		}
		if (candidates.last == null || assignment.order() >= candidates.last.order()) {
			candidates.last = assignment;
		}
	}

	/**
	 * Adds the fact "the assignment originates from the element at page start" to a registered assignment
	 * (the page builder supplies this when placement is finalized, for an assignment registered
	 * at build time; R1b). Does nothing if there is no matching per-page candidate (first/last).
	 *
	 * @param name  name
	 * @param order document order of the assignment source
	 */
	public void markBeginsPage(final String name, final long order) {
		final Candidates<T> candidates = this.names.get(name);
		if (candidates == null) {
			return;
		}
		if (candidates.first != null && candidates.first.order() == order && !candidates.first.beginsPage()) {
			candidates.first = new Assignment<T>(order, candidates.first.value(), true);
		}
		if (candidates.last != null && candidates.last.order() == order && !candidates.last.beginsPage()) {
			candidates.last = new Assignment<T>(order, candidates.last.value(), true);
		}
	}

	/** Resolves the current page's value with the specified policy. */
	public Resolution<T> resolve(final String name, final Mode mode) {
		Objects.requireNonNull(mode);
		final Snapshot<T> snapshot = this.snapshot(name);
		if (mode == Mode.FIRST_EXCEPT && snapshot.first() != null) {
			return new Resolution<T>(Presence.SUPPRESSED, null);
		}
		final Assignment<T> assignment = switch (mode) {
		case FIRST -> snapshot.first() != null ? snapshot.first() : snapshot.entry();
		case START -> snapshot.first() != null && snapshot.first().beginsPage()
				? snapshot.first() : snapshot.entry();
		case LAST -> snapshot.last() != null ? snapshot.last() : snapshot.entry();
		case FIRST_EXCEPT -> snapshot.entry();
		};
		if (assignment == null) {
			return new Resolution<T>(Presence.ABSENT, null);
		}
		return new Resolution<T>(Presence.VALUE, assignment.value());
	}

	/** Returns the three candidates for diagnostics or page snapshots. */
	public Snapshot<T> snapshot(final String name) {
		final Candidates<T> candidates = this.names.get(name);
		return candidates == null ? new Snapshot<T>(null, null, null)
				: new Snapshot<T>(candidates.entry, candidates.first, candidates.last);
	}

	/** Returns a copy of registered names for creating a read-only page snapshot. */
	public java.util.Set<String> names() {
		return java.util.Set.copyOf(this.names.keySet());
	}

	/** Carries the last assignment on the page to the next page and releases per-page candidates. */
	public void endPage() {
		for (final Candidates<T> candidates : this.names.values()) {
			if (candidates.last != null) {
				candidates.entry = candidates.last;
			}
			candidates.first = candidates.last = null;
		}
	}

	/** Initializes all names and page state. */
	public void reset() {
		this.names.clear();
	}

	/** Initializes all state. */
	public void clear() {
		this.reset();
	}
}
