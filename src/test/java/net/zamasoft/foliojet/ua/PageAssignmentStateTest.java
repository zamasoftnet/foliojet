package net.zamasoft.foliojet.ua;

import junit.framework.TestCase;
import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;
import net.zamasoft.foliojet.ua.PageAssignmentState.Presence;
import net.zamasoft.foliojet.ua.PageAssignmentState.Resolution;
import net.zamasoft.foliojet.ua.PageAssignmentState.Snapshot;

/** Tests that lock down page boundaries, document order, the four policies, and deletion order. */
public class PageAssignmentStateTest extends TestCase {
	public void testFirstAndLastWithinPage() {
		final PageAssignmentState<String> state = new PageAssignmentState<String>();
		state.assign("h", "A", 10, false);
		state.assign("h", "B", 20, false);
		assertValue(state, Mode.FIRST, "A");
		assertValue(state, Mode.LAST, "B");
	}

	public void testOutOfOrderCallsStillRespectElementKey() {
		final PageAssignmentState<String> state = new PageAssignmentState<String>();
		state.assign("h", "later", 20, false);
		state.assign("h", "earlier", 5, true);
		assertValue(state, Mode.FIRST, "earlier");
		assertValue(state, Mode.START, "earlier");
		assertValue(state, Mode.LAST, "later");
	}

	public void testFirstFallsBackToEntryValueWhenNothingSetOnPage() {
		final PageAssignmentState<String> state = withEntry();
		for (final Mode mode : Mode.values()) {
			assertValue(state, mode, "entry");
		}
	}

	public void testFirstExceptIsSuppressedOnThePageWhereAssigned() {
		final PageAssignmentState<String> state = new PageAssignmentState<String>();
		state.assign("h", "page1", 1, false);
		assertPresence(state, Mode.FIRST_EXCEPT, Presence.SUPPRESSED);
		state.endPage();
		assertValue(state, Mode.FIRST_EXCEPT, "page1");
	}

	public void testUnsetNameIsAbsent() {
		final PageAssignmentState<String> state = new PageAssignmentState<String>();
		for (final Mode mode : Mode.values()) {
			assertPresence(state, mode, Presence.ABSENT);
		}
		assertEquals(new Snapshot<String>(null, null, null), state.snapshot("h"));
	}

	/**
	 * Cross the four policies with no assignment, page-start assignment, mid-page assignment, and multiple
	 * assignments.
	 */
	public void testModeMatrix() {
		for (int scenario = 0; scenario < 4; ++scenario) {
			for (final boolean entry : new boolean[] { false, true }) {
				final PageAssignmentState<String> state = entry ? withEntry() : new PageAssignmentState<String>();
				if (scenario != 0) {
					state.assign("h", "A", 10, scenario == 1);
				}
				if (scenario == 3) {
					// Even if last has beginsPage=true, START depends only on the facts about first.
					state.assign("h", "B", 20, true);
				}
				for (final Mode mode : Mode.values()) {
					if (scenario != 0 && mode == Mode.FIRST_EXCEPT) {
						assertPresence(state, mode, Presence.SUPPRESSED);
					} else if (scenario == 0 || (mode == Mode.START && scenario != 1)) {
						if (entry) {
							assertValue(state, mode, "entry");
						} else {
							assertPresence(state, mode, Presence.ABSENT);
						}
					} else {
						assertValue(state, mode, scenario == 3 && mode == Mode.LAST ? "B" : "A");
					}
				}
			}
		}
	}

	public void testSnapshotAndEndPageReleasePageCandidates() {
		final PageAssignmentState<String> state = withEntry();
		state.assign("h", "A", 10, true);
		state.assign("h", "B", 20, false);
		final Snapshot<String> snapshot = state.snapshot("h");
		assertEquals("entry", snapshot.entry().value());
		assertEquals("A", snapshot.first().value());
		assertEquals("B", snapshot.last().value());
		state.endPage();
		assertEquals(snapshot.last(), state.snapshot("h").entry());
		assertNull(state.snapshot("h").first());
		assertNull(state.snapshot("h").last());
		state.endPage();
		assertValue(state, Mode.FIRST, "B");
		assertEquals("A", snapshot.first().value());
	}

	/**
	 * For the same (name, order), the later call wins (pseudo-elements share order; EPUB numbering resets
	 * each chapter; immediate registration during build is repeated during draw).
	 * Discard intermediate orders because they are not candidates.
	 */
	public void testSameOrderIsReplacedByLaterCall() {
		final PageAssignmentState<String> state = new PageAssignmentState<String>();
		state.assign("h", "A", 10, false);
		state.assign("h", "B", 20, false);
		state.assign("h", "C", 30, false);
		state.assign("h", "A2", 10, true);
		assertValue(state, Mode.FIRST, "A2");
		assertValue(state, Mode.START, "A2");
		assertValue(state, Mode.LAST, "C");
		state.assign("h", "B2", 20, false);
		assertValue(state, Mode.FIRST, "A2");
		assertValue(state, Mode.LAST, "C");
		state.assign("h", "C2", 30, false);
		assertValue(state, Mode.LAST, "C2");
		// Pseudo-elements: do not fail even if all have order=-1.
		state.assign("h", "P1", -1, false);
		state.assign("h", "P2", -1, false);
		assertValue(state, Mode.FIRST, "P2");
	}

	public void testResetAndEmptyValueAreDistinct() {
		final PageAssignmentState<String> state = withEntry();
		state.assign("h", "", 10, false);
		assertValue(state, Mode.FIRST, "");
		state.reset();
		assertPresence(state, Mode.LAST, Presence.ABSENT);
		state.assign("h", "reused", 10, true);
		state.clear();
		assertEquals(new Snapshot<String>(null, null, null), state.snapshot("h"));
	}

	/**
	 * An assignment registered during build can later be marked as page-start when placement is finalized (R1b
	 * wiring target).
	 */
	public void testMarkBeginsPageUpgradesStart() {
		final PageAssignmentState<String> state = withEntry();
		state.assign("h", "A", 10, false);
		assertValue(state, Mode.START, "entry");
		state.markBeginsPage("h", 10);
		assertValue(state, Mode.START, "A");
		assertTrue(state.snapshot("h").first().beginsPage());
		// Ignore nonexistent orders and names.
		state.markBeginsPage("h", 99);
		state.markBeginsPage("nope", 10);
		assertValue(state, Mode.START, "A");
	}

	private static PageAssignmentState<String> withEntry() {
		final PageAssignmentState<String> state = new PageAssignmentState<String>();
		state.assign("h", "entry", 1, false);
		state.endPage();
		return state;
	}

	private static void assertValue(final PageAssignmentState<String> state, final Mode mode, final String value) {
		assertEquals(mode.toString(), new Resolution<String>(Presence.VALUE, value), state.resolve("h", mode));
	}

	private static void assertPresence(final PageAssignmentState<String> state, final Mode mode, final Presence presence) {
		assertEquals(mode.toString(), new Resolution<String>(presence, null), state.resolve("h", mode));
	}
}
