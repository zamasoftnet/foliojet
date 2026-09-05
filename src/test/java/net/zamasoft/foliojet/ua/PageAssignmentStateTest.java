package net.zamasoft.foliojet.ua;

import junit.framework.TestCase;
import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;
import net.zamasoft.foliojet.ua.PageAssignmentState.Presence;
import net.zamasoft.foliojet.ua.PageAssignmentState.Resolution;
import net.zamasoft.foliojet.ua.PageAssignmentState.Snapshot;

/** 頁境界・文書順・四方針・削除の順序を固定する試験です。 */
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

	/** 代入なし・頁先頭・頁途中・複数代入を四方針と交差させます。 */
	public void testModeMatrix() {
		for (int scenario = 0; scenario < 4; ++scenario) {
			for (final boolean entry : new boolean[] { false, true }) {
				final PageAssignmentState<String> state = entry ? withEntry() : new PageAssignmentState<String>();
				if (scenario != 0) {
					state.assign("h", "A", 10, scenario == 1);
				}
				if (scenario == 3) {
					// last の beginsPage が真でも START は first の事実だけで決まる。
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

	/** assign→clear/clear→assign の文書順と呼び出し順の両方を交差させます。 */
	public void testAssignAndClearInEitherOrder() {
		for (final boolean clearLast : new boolean[] { false, true }) {
			for (final boolean reverse : new boolean[] { false, true }) {
				final PageAssignmentState<String> state = withEntry();
				for (int i = 0; i < 2; ++i) {
					final boolean last = reverse ? i == 0 : i == 1;
					final long order = last ? 20 : 10;
					if (last == clearLast) {
						state.clear("h", order, !last);
					} else {
						state.assign("h", "value", order, !last);
					}
				}
				if (clearLast) {
					assertValue(state, Mode.FIRST, "value");
					assertValue(state, Mode.START, "value");
					assertPresence(state, Mode.LAST, Presence.TOMBSTONE);
				} else {
					assertPresence(state, Mode.FIRST, Presence.TOMBSTONE);
					assertPresence(state, Mode.START, Presence.TOMBSTONE);
					assertValue(state, Mode.LAST, "value");
				}
				assertPresence(state, Mode.FIRST_EXCEPT, Presence.SUPPRESSED);
				state.endPage();
				for (final Mode mode : Mode.values()) {
					if (clearLast) {
						assertPresence(state, mode, Presence.TOMBSTONE);
					} else {
						assertValue(state, mode, "value");
					}
				}
			}
		}
	}

	public void testMidPageClearPreservesStartAndSuppressesFirstExcept() {
		final PageAssignmentState<String> state = withEntry();
		state.clear("h", 10, false);
		assertValue(state, Mode.START, "entry");
		assertPresence(state, Mode.FIRST, Presence.TOMBSTONE);
		assertPresence(state, Mode.FIRST_EXCEPT, Presence.SUPPRESSED);
	}

	public void testSnapshotAndEndPageReleasePageCandidates() {
		final PageAssignmentState<String> state = withEntry();
		state.assign("h", "A", 10, true);
		state.clear("h", 20, false);
		final Snapshot<String> snapshot = state.snapshot("h");
		assertEquals("entry", snapshot.entry().value());
		assertEquals("A", snapshot.first().value());
		assertTrue(snapshot.last().tombstone());
		state.endPage();
		assertEquals(snapshot.last(), state.snapshot("h").entry());
		assertNull(state.snapshot("h").first());
		assertNull(state.snapshot("h").last());
		state.endPage();
		assertPresence(state, Mode.FIRST, Presence.TOMBSTONE);
		assertEquals("A", snapshot.first().value());
	}

	/**
	 * 同じ (name, order) は後の呼び出しが勝つ(疑似要素は order を共有し、EPUB は章ごとに
	 * 採番が戻り、build 時の即時登録が draw 時にもう一度登録される)。中間 order は候補でないので捨てる。
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
		state.clear("h", 30, false);
		assertPresence(state, Mode.LAST, Presence.TOMBSTONE);
		// 疑似要素: 全部 order=-1 でも落ちない
		state.assign("h", "P1", -1, false);
		state.assign("h", "P2", -1, false);
		assertValue(state, Mode.FIRST, "P2");
	}

	public void testAssignmentAfterClearWithSameOrderWins() {
		final PageAssignmentState<String> state = new PageAssignmentState<String>();
		state.clear("h", 10, false);
		assertPresence(state, Mode.LAST, Presence.TOMBSTONE);
		state.assign("h", "value", 10, false);
		assertValue(state, Mode.LAST, "value");
		assertValue(state, Mode.FIRST, "value");
	}

	public void testResetAndEmptyValueAreDistinctFromTombstone() {
		final PageAssignmentState<String> state = withEntry();
		state.assign("h", "", 10, false);
		assertValue(state, Mode.FIRST, "");
		state.reset();
		assertPresence(state, Mode.LAST, Presence.ABSENT);
		state.assign("h", "reused", 10, true);
		state.clear();
		assertEquals(new Snapshot<String>(null, null, null), state.snapshot("h"));
	}

	public void testTombstoneCannotCarryValue() {
		try {
			new PageAssignmentState.Assignment<String>(1, "value", false, true);
			fail("値を持つ tombstone を受理しました");
		} catch (IllegalArgumentException expected) {
			// コンストラクタの契約。
		}
	}

	/** build 時に登録した代入へ、配置確定時に頁先頭の事実を後付けできる(R1b の配線先)。 */
	public void testMarkBeginsPageUpgradesStart() {
		final PageAssignmentState<String> state = withEntry();
		state.assign("h", "A", 10, false);
		assertValue(state, Mode.START, "entry");
		state.markBeginsPage("h", 10);
		assertValue(state, Mode.START, "A");
		assertTrue(state.snapshot("h").first().beginsPage());
		// 無い order・無い名前は無視
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
