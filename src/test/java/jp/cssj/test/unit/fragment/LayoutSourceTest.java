package jp.cssj.test.unit.fragment;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * Tests for LayoutSource (layout-source protocol log) (M6b v3).
 * Recipe contents are irrelevant to the logging mechanism, so use a minimal recipe with default values
 * (Start began retaining a recipe frozen at recording time in E-6 increment 3b-4).
 */
public class LayoutSourceTest extends TestCase {
	private static LayoutSource.Event start() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Flow(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event tableStart() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Table(
				net.zamasoft.foliojet.layout.segment.TableParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event captionStart() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Caption(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.TableCaptionPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableCaptionPos())));
	}

	/**
	 * Protocol tests for caption recipes C1/C2
	 * (the handling table by range in consult-codex-2026-08-01-caption-recipe.txt Q1).
	 */
	public void testCaptionContextCompleteRange() {
		final LayoutSource log = new LayoutSource();
		final long block = log.append(start()); // BLOCK
		final long table = log.append(tableStart()); // TABLE
		final long caption = log.append(captionStart()); // CAPTION
		log.append(new LayoutSource.Chars(0, "c".toCharArray(), false));
		final long captionEnd = log.append(new LayoutSource.EndBlock());
		final long tableEnd = log.append(new LayoutSource.EndBlock());
		final long blockEnd = log.append(new LayoutSource.EndBlock());

		// containsCaption: detect containment
		assertTrue(log.containsCaption(block, blockEnd));
		assertTrue(log.containsCaption(caption, caption));
		assertFalse(log.containsCaption(block, table));

		// TABLE→CAPTION / BLOCK→TABLE→CAPTION: eligible
		assertTrue(log.isContextCompleteRange(table, tableEnd));
		assertTrue(log.isContextCompleteRange(block, blockEnd));
		// CAPTION-only range: ineligible (direct cause of G-1)
		assertFalse(log.isContextCompleteRange(caption, captionEnd));
		// tableStart+1..tableEnd-1 (starts with CAPTION, no TABLE Start): ineligible
		assertFalse(log.isContextCompleteRange(table + 1, tableEnd - 1));
		// Range cutting through CAPTION (ends while it remains open): ineligible
		assertFalse(log.isContextCompleteRange(table, caption));
		// Range ending just before CAPTION Start: eligible (contains no caption—
		// ineligible is actually correct because an open TABLE remains).
		assertFalse(log.isContextCompleteRange(block, table));
	}

	public void testEventIdStableAcrossCompaction() {
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start()); // Left open
		final long p1 = log.append(start());
		log.append(new LayoutSource.Chars(0, "aaa".toCharArray(), false));
		final long p1end = log.append(new LayoutSource.EndBlock());
		final long p2 = log.append(start());
		log.append(new LayoutSource.Chars(3, "bbb".toCharArray(), false));
		log.append(new LayoutSource.EndBlock());

		assertEquals(p1end, log.endOf(p1));

		// Discard before p2 (keep the open body).
		log.compact(p2);
		assertNotNull(log.get(body));
		assertNull(log.get(p1));
		assertNotNull(log.get(p2));
		// IDs remain unchanged.
		assertTrue(log.get(p2) instanceof LayoutSource.Start);
		assertEquals(log.endOf(p2), p2 + 2);
	}

	public void testReplayRange() {
		final LayoutSource log = new LayoutSource();
		log.append(start());
		final long from = log.append(new LayoutSource.Chars(0, "xy".toCharArray(), false));
		final long to = log.append(new LayoutSource.EndBlock());
		log.append(start());

		final List<LayoutSource.Event> seen = new ArrayList<LayoutSource.Event>();
		log.replay(from, to, seen::add);
		assertEquals(2, seen.size());
		assertTrue(seen.get(0) instanceof LayoutSource.Chars);
		assertTrue(seen.get(1) instanceof LayoutSource.EndBlock);
	}

	public void testOpenSubtreeNotClosed() {
		final LayoutSource log = new LayoutSource();
		final long open = log.append(start());
		log.append(new LayoutSource.Chars(0, "a".toCharArray(), false));
		assertEquals(-1, log.endOf(open));
	}

	public void testOpaquePairsWithEndBlock() {
		// Opaque is a start event paired with EndBlock. Without symmetric handling,
		// (1) endOf mistakes the EndBlock paired with Opaque for the parent's end, and
		// (2) compact incorrectly pops an ancestor's Start, destroying the open chain.
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start());
		final long div = log.append(start());
		log.append(new LayoutSource.Opaque()); // Unsupported boxes such as absolutely positioned boxes
		log.append(new LayoutSource.Chars(0, "a".toCharArray(), false));
		log.append(new LayoutSource.EndBlock()); // /opaque
		log.append(new LayoutSource.Chars(1, "b".toCharArray(), false));
		final long divEnd = log.append(new LayoutSource.EndBlock()); // /div
		final long tail = log.nextId();
		log.append(new LayoutSource.Chars(2, "c".toCharArray(), false));

		// endOf: do not mistake the EndBlock paired with Opaque for the end of div.
		assertEquals(divEnd, log.endOf(div));

		// compact: an opaque pair completed within the discarded range must not pop
		// the Start of an open ancestor (body).
		log.compact(tail);
		assertNotNull(log.get(body));
		assertEquals(-1, log.endOf(body));
	}

	public void testReplaySurvivesNestedCompaction() {
		// A nested page break calls compact inside the replay visitor
		// (the replayed content overflows the new page). Even if the backing list
		// is rebuilt with clear+addAll, the range being replayed must complete
		// (external review: scanning by numeric index silently skips entries).
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[7];
		for (int i = 0; i < 7; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final List<Integer> seen = new ArrayList<Integer>();
		log.replay(ids[2], ids[5], event -> {
			final int off = ((LayoutSource.Chars) event).charOffset();
			seen.add(off);
			if (off == 2) {
				// Simulate compact clamped by a watermark pin (fromId=ids[2]).
				log.compact(ids[2]);
			}
		});
		assertEquals(List.of(2, 3, 4, 5), seen);
	}

	public void testReplaySurvivesNestedCompactionBeyondRange() {
		// E-6 increment 3a: even if the watermark of nested compact during replay points beyond
		// the entire replay range, the slice's own lease clamps it at fromId,
		// so the streaming scan completes (replaces the isolation guarantee
		// previously provided by copying the entire range).
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[7];
		for (int i = 0; i < 7; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final List<Integer> seen = new ArrayList<Integer>();
		log.replay(ids[2], ids[5], event -> {
			final int off = ((LayoutSource.Chars) event).charOffset();
			seen.add(off);
			if (off == 3) {
				// A watermark beyond the end of the range (without a lease, unread entries 4 and 5 disappear).
				log.compact(ids[6]);
			}
		});
		assertEquals(List.of(2, 3, 4, 5), seen);
		// The lease is released when replay finishes; the next compact can discard normally.
		// (Leaving it behind would clamp forever, leaking retained entries.)
		log.compact(ids[6]);
		assertNull(log.get(ids[2]));
		assertNotNull(log.get(ids[6]));
	}

	public void testReplaySliceIsConsumeOnce() {
		final LayoutSource log = new LayoutSource();
		final long from = log.append(new LayoutSource.Chars(0, "a".toCharArray(), false));
		final long to = log.append(new LayoutSource.Chars(1, "b".toCharArray(), false));
		final LayoutSource.ReplaySlice slice = log.capture(from, to);
		slice.replay(event -> {
		});
		try {
			slice.replay(event -> {
			});
			fail("ReplaySlice は consume-once のはず");
		} catch (IllegalStateException expected) {
			// OK
		}
	}

	public void testAbandonedReplaySliceReleasesLeaseOnClose() {
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[3];
		for (int i = 0; i < 3; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.ReplaySlice slice = log.capture(ids[0], ids[1]);
		// During capture, the lease clamps compact.
		log.compact(ids[2]);
		assertNotNull(log.get(ids[0]));
		// Abandoning capture (close is idempotent) releases the lease and permits discarding.
		slice.close();
		slice.close();
		log.compact(ids[2]);
		assertNull(log.get(ids[0]));
		assertNotNull(log.get(ids[2]));
	}

	public void testRetentionLeaseClampsCompaction() {
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[5];
		for (int i = 0; i < 5; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.RetentionLease lease = log.retainFrom(ids[1]);
		// While the lease is active, entries before fromId are not discarded.
		log.compact(ids[4]);
		assertNotNull(log.get(ids[1]));
		assertNotNull(log.get(ids[3]));
		assertNull(log.get(ids[0]));
		// After release, entries can be discarded.
		lease.close();
		log.compact(ids[4]);
		assertNull(log.get(ids[1]));
		assertNotNull(log.get(ids[4]));
	}

	public void testRetentionLeaseIsRefCounted() {
		// When multiple continuations independently own the same fromId, releasing one must not
		// remove the retention of the other (reference counting).
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[3];
		for (int i = 0; i < 3; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.RetentionLease outer = log.retainFrom(ids[0]);
		final LayoutSource.RetentionLease inner = log.retainFrom(ids[0]);
		inner.close();
		inner.close(); // Idempotent
		log.compact(ids[2]);
		assertNotNull(log.get(ids[0]));
		outer.close();
		log.compact(ids[2]);
		assertNull(log.get(ids[0]));
	}

	public void testCompactKeepsNestedOpenStarts() {
		final LayoutSource log = new LayoutSource();
		final long html = log.append(start());
		final long body = log.append(start());
		final long p = log.append(start());
		log.append(new LayoutSource.EndBlock()); // /p
		final long div = log.append(start()); // Left open
		final long tail = log.nextId();
		log.append(new LayoutSource.Chars(0, "t".toCharArray(), false));

		log.compact(tail);
		assertNotNull(log.get(html));
		assertNotNull(log.get(body));
		assertNotNull(log.get(div));
		assertNull(log.get(p));
		assertEquals(4, log.size());
	}
	/**
	 * Property test for equivalence between RangeSummary (O(log k) contains* via a sparse index,
	 * 2026-08-01) and a linear scan. Since the test constructs the event sequence itself,
	 * it records the correct category ledger during construction and compares all predicates using
	 * random range queries (fixed seed). After compact, check again for ranges entirely at or beyond
	 * the watermark (all entries survive).
	 * Only the Replaced(FLOAT) branch is excluded because it is difficult to construct here:
	 * tier1/golden tests using float-image documents in the real corpus provide effective coverage.
	 */
	public void testRangeSummaryMatchesLinearScan() {
		final java.util.Random random = new java.util.Random(20260801L);
		final LayoutSource log = new LayoutSource();
		final int kinds = 9;
		final java.util.List<long[]> truth = new java.util.ArrayList<>(); // {id, kindOrdinal}
		final int OPAQUE = 0, CAPTION = 1, TABLE = 2, MULTICOL = 3, GRID = 4, ABSOLUTE = 5, FLOATB = 6, VFLOW = 7,
				FLEX = 8;
		final java.util.ArrayDeque<Long> open = new java.util.ArrayDeque<>();
		for (int i = 0; i < 400; ++i) {
			final int roll = random.nextInt(13);
			final long id;
			switch (roll) {
			case 0 -> {
				id = log.append(new LayoutSource.Opaque());
				truth.add(new long[] { id, OPAQUE });
				open.push(id);
			}
			case 1 -> {
				id = log.append(caption());
				truth.add(new long[] { id, CAPTION });
				open.push(id);
			}
			case 2 -> {
				id = log.append(table());
				truth.add(new long[] { id, TABLE });
				open.push(id);
			}
			case 3 -> {
				id = log.append(multicol());
				truth.add(new long[] { id, MULTICOL });
				open.push(id);
			}
			case 4 -> {
				id = log.append(grid());
				truth.add(new long[] { id, GRID });
				open.push(id);
			}
			case 5 -> {
				id = log.append(absolute());
				truth.add(new long[] { id, ABSOLUTE });
				open.push(id);
			}
			case 6 -> {
				id = log.append(floatBlock());
				truth.add(new long[] { id, FLOATB });
				open.push(id);
			}
			case 7 -> {
				id = log.append(verticalStart());
				truth.add(new long[] { id, VFLOW });
				open.push(id);
			}
			case 12 -> {
				id = log.append(flex());
				truth.add(new long[] { id, FLEX });
				open.push(id);
			}
			case 8, 9 -> {
				id = log.append(start()); // FLOW with horizontal flow
				open.push(id);
			}
			case 10 -> {
				if (open.isEmpty()) {
					id = log.append(start());
					open.push(id);
				} else {
					open.pop();
					id = log.append(new LayoutSource.EndBlock());
				}
			}
			default -> id = log.append(new LayoutSource.Chars(i, "x".toCharArray(), false));
			}
		}
		final long maxId = log.nextId() - 1;
		verifyAgainstTruth(log, truth, 0, maxId, random);
		// After compact: check again for ranges entirely at or beyond the watermark.
		final long watermark = maxId / 2;
		log.compact(watermark);
		verifyAgainstTruth(log, truth, watermark, maxId, random);
	}

	private static void verifyAgainstTruth(final LayoutSource log, final java.util.List<long[]> truth,
			final long minFrom, final long maxId, final java.util.Random random) {
		final int OPAQUE = 0, CAPTION = 1, TABLE = 2, MULTICOL = 3, GRID = 4, ABSOLUTE = 5, FLOATB = 6, VFLOW = 7,
				FLEX = 8;
		final net.zamasoft.foliojet.layout.box.params.WritingMode horizontal = net.zamasoft.foliojet.layout.box.params.WritingMode.TB;
		for (int q = 0; q < 500; ++q) {
			final long from = minFrom + (long) (random.nextDouble() * (maxId - minFrom + 1));
			final long to = from + (long) (random.nextDouble() * (maxId - from + 1));
			final boolean[] expect = new boolean[9];
			for (final long[] t : truth) {
				if (t[0] >= from && t[0] <= to) {
					expect[(int) t[1]] = true;
				}
			}
			final String at = " [" + from + "," + to + "]";
			assertEquals("opaque" + at, expect[OPAQUE], log.containsOpaque(from, to));
			assertEquals("caption" + at, expect[CAPTION], log.containsCaption(from, to));
			assertEquals("table" + at, expect[TABLE], log.containsTable(from, to));
			assertEquals("multicol" + at, expect[MULTICOL], log.containsMulticol(from, to));
			assertEquals("grid" + at, expect[GRID], log.containsGrid(from, to));
			assertEquals("flex" + at, expect[FLEX], log.containsFlex(from, to));
			assertEquals("absolute" + at, expect[ABSOLUTE], log.containsAbsolute(from, to));
			assertEquals("float" + at, expect[FLOATB], log.containsFloat(from, to));
			// For a horizontal root, mixedFlow means a vertical-flow start exists.
			assertEquals("mixedFlow" + at, expect[VFLOW], log.containsMixedFlow(from, to, horizontal));
		}
	}

	private static LayoutSource.Event caption() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Caption(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.TableCaptionPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableCaptionPos())));
	}

	private static LayoutSource.Event table() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Table(
				net.zamasoft.foliojet.layout.segment.TableParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event multicol() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Multicol(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event grid() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Grid(
				net.zamasoft.foliojet.layout.segment.GridParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.GridParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event flex() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Flex(
				net.zamasoft.foliojet.layout.segment.FlexParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlexParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event absolute() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Absolute(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.AbsolutePosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.AbsolutePos())));
	}

	private static LayoutSource.Event floatBlock() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.FloatBlock(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.FloatPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FloatPos())));
	}

	private static LayoutSource.Event verticalStart() {
		final net.zamasoft.foliojet.layout.box.params.BlockParams params = new net.zamasoft.foliojet.layout.box.params.BlockParams();
		params.flow = net.zamasoft.foliojet.layout.box.params.WritingMode.RL;
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Flow(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate.freeze(params),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

}
