package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * Unit tests for text payload spilling in {@link LayoutSource} (E-6 increment 3b-2, added 2026-07-24).
 * Locks down deterministic byte budgeting (inline if retained inline bytes plus the new payload fit
 * the budget, otherwise spill), fresh decoding of Spilled, unchanged findCharsAt behavior (heap metadata
 * for utf16Length), budget release by compact, and idempotent temporary-file deletion by close.
 */
public class LayoutSourceTextSpillTest extends TestCase {
	/** Retain inline within budget; spill new appends after exceeding it (event boundaries stay unchanged). */
	public void testBudgetSpillDecision() throws Exception {
		try (LayoutSource log = new LayoutSource(8)) {
			// 4 chars = 8 bytes → exactly within budget (inline).
			final long first = log.appendChars(0, "abcd".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, first) instanceof LayoutSource.TextPayload.Inline);
			assertNull("予算内ではspillストアは生成されないはず", log.textSpillForTest());

			// Another 4 chars → 8 + 8 > 8, so spill.
			final long second = log.appendChars(4, "efgh".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, second) instanceof LayoutSource.TextPayload.Spilled);
			assertNotNull(log.textSpillForTest());

			// 1 Chars = 1 payload (no splitting or merging).
			assertEquals(4, payloadOf(log, first).utf16Length());
			assertEquals(4, payloadOf(log, second).utf16Length());

			// Decoding is always fresh (equal content, a different instance each time).
			final LayoutSource.TextPayload spilled = payloadOf(log, second);
			final char[] a = spilled.freshChars();
			final char[] b = spilled.freshChars();
			assertEquals("efgh", new String(a));
			assertEquals("efgh", new String(b));
			assertNotSame(a, b);
			final LayoutSource.TextPayload inline = payloadOf(log, first);
			assertNotSame(inline.freshChars(), inline.freshChars());

		}
	}

	/** A single event larger than the budget spills (even with zero retained inline bytes). */
	public void testOversizeSingleEventSpills() throws Exception {
		try (LayoutSource log = new LayoutSource(8)) {
			final long id = log.appendChars(0, "abcdefgh".toCharArray(), 0, 8, false);
			assertTrue(payloadOf(log, id) instanceof LayoutSource.TextPayload.Spilled);
			assertEquals("abcdefgh", new String(payloadOf(log, id).freshChars()));
		}
	}

	/** compact releases inline bytes from budget accounting, so subsequent appends become inline again. */
	public void testCompactReleasesInlineBudget() throws Exception {
		try (LayoutSource log = new LayoutSource(8)) {
			log.appendChars(0, "abcd".toCharArray(), 0, 4, false);
			final long spilledId = log.appendChars(4, "efgh".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, spilledId) instanceof LayoutSource.TextPayload.Spilled);

			// Discard all events → inline accounting returns to 0.
			log.compact(log.nextId());
			assertEquals(0, log.size());

			// With budget available again, retain inline (Spilled records remain in the store,
			// but are deleted along with the temporary file on close; this is not a leak).
			final long third = log.appendChars(8, "ijkl".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, third) instanceof LayoutSource.TextPayload.Inline);
		}
	}

	/** replay (streaming view) correctly restores spilled events. */
	public void testReplayDecodesSpilledEvents() throws Exception {
		try (LayoutSource log = new LayoutSource(0)) {
			// Zero budget → every append spills.
			final long from = log.appendChars(0, "hello".toCharArray(), 0, 5, false);
			final long to = log.appendChars(5, "world".toCharArray(), 0, 5, true);
			assertTrue(payloadOf(log, from) instanceof LayoutSource.TextPayload.Spilled);
			assertTrue(payloadOf(log, to) instanceof LayoutSource.TextPayload.Spilled);

			final List<String> texts = new ArrayList<>();
			log.replay(from, to, event -> {
				final LayoutSource.Chars chars = (LayoutSource.Chars) event;
				texts.add(new String(chars.payload().freshChars()));
			});
			assertEquals(List.of("hello", "world"), texts);
		}
	}

	/** close deletes the temporary file and is idempotent. */
	public void testCloseDeletesTempFilesIdempotently() throws Exception {
		final LayoutSource log = new LayoutSource(0);
		log.appendChars(0, "x".toCharArray(), 0, 1, false);
		final TextSpill spill = log.textSpillForTest();
		assertNotNull(spill);
		assertTrue(spill.dataFileForTest().exists());
		assertTrue(spill.indexFileForTest().exists());
		log.close();
		assertTrue("close後に一時ファイルが残っています", spill.tempFilesDeletedForTest());
		// Idempotent.
		log.close();
		assertTrue(spill.tempFilesDeletedForTest());
	}

	/** If spilling was unnecessary, close (idempotent) does nothing. */
	public void testCloseWithoutSpillIsNoop() throws Exception {
		final LayoutSource log = new LayoutSource();
		log.appendChars(0, "abc".toCharArray(), 0, 3, false);
		assertNull(log.textSpillForTest());
		log.close();
		log.close();
	}

	private static LayoutSource.TextPayload payloadOf(final LayoutSource log, final long id) {
		return ((LayoutSource.Chars) log.get(id)).payload();
	}
}
