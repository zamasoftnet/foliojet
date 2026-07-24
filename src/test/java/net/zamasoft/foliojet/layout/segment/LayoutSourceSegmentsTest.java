package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * M6d-A2(2026-07-22新設)の座標変換アダプタの契約を固定する単体
 * テストです。挙動不変(既存の{@code LayoutSource}・
 * {@code SourceReplayer}への配線なし)。
 */
public class LayoutSourceSegmentsTest extends TestCase {
	private static LayoutSource.Event start() {
		return new LayoutSource.Start(LayoutSource.BoxKind.FLOW, null, null);
	}

	/** 同一sourceに対しては常に同じSegmentIdが返る。 */
	public void testIdForIsStablePerSource() {
		final LayoutSource log = new LayoutSource();
		final SegmentId id1 = LayoutSourceSegments.idFor(log);
		final SegmentId id2 = LayoutSourceSegments.idFor(log);
		assertEquals(id1, id2);
	}

	/** 異なるsourceには異なるSegmentIdが割り当てられる。 */
	public void testIdForDiffersAcrossSources() {
		final LayoutSource log1 = new LayoutSource();
		final LayoutSource log2 = new LayoutSource();
		assertFalse(LayoutSourceSegments.idFor(log1).equals(LayoutSourceSegments.idFor(log2)));
	}

	/** rangeOfは両端inclusiveから半開への変換を正しく行う。 */
	public void testRangeOfConvertsInclusiveToHalfOpen() {
		final LayoutSource log = new LayoutSource();
		final SegmentRange range = LayoutSourceSegments.rangeOf(log, 3, 7);
		assertEquals(3L, range.from().ordinal());
		assertEquals(8L, range.toExclusive().ordinal());
		assertEquals(5L, range.length());
	}

	/** captureで得たReplaySliceの内容が、直接LayoutSource.replayした結果と一致する。 */
	public void testCaptureMatchesDirectReplay() {
		final LayoutSource log = new LayoutSource();
		log.append(start());
		final long from = log.append(new LayoutSource.Chars(0, "xy".toCharArray(), false));
		final long to = log.append(new LayoutSource.EndBlock());
		log.append(start());

		final SegmentRange range = LayoutSourceSegments.rangeOf(log, from, to);
		final LayoutSource.ReplaySlice slice = LayoutSourceSegments.capture(log, range);
		assertNotNull(slice);

		final List<LayoutSource.Event> viaSegment = new ArrayList<>();
		slice.replay(viaSegment::add);

		final List<LayoutSource.Event> viaDirect = new ArrayList<>();
		log.replay(from, to, viaDirect::add);

		assertEquals(viaDirect, viaSegment);
		assertEquals(2, viaSegment.size());
	}

	/** captureはSegmentReplaySessionで辿ったcursor位置とも整合する。 */
	public void testCaptureRangeMatchesReplaySessionWalk() {
		final LayoutSource log = new LayoutSource();
		log.append(start());
		final long from = log.append(new LayoutSource.Chars(0, "abc".toCharArray(), false));
		log.append(new LayoutSource.Chars(3, "def".toCharArray(), false));
		final long to = log.append(new LayoutSource.EndBlock());

		final SegmentRange range = LayoutSourceSegments.rangeOf(log, from, to);
		final SegmentReplaySession session = new SegmentReplaySession(range);
		final List<Long> ordinals = new ArrayList<>();
		while (session.hasNext()) {
			ordinals.add(session.next().ordinal());
		}
		assertEquals(List.of(from, from + 1, from + 2), ordinals);

		final LayoutSource.ReplaySlice slice = LayoutSourceSegments.capture(log, range);
		final List<LayoutSource.Event> seen = new ArrayList<>();
		slice.replay(seen::add);
		assertEquals(3, seen.size());
	}

	/** 退化区間(toId == fromId - 1相当)はnullを返す。 */
	public void testEmptyRangeCaptureReturnsNull() {
		final LayoutSource log = new LayoutSource();
		log.append(start());
		final SegmentRange range = LayoutSourceSegments.rangeOf(log, 5, 4);
		assertEquals(0L, range.length());
		assertNull(LayoutSourceSegments.capture(log, range));
	}

	/** 異なるsourceのrangeでcaptureすると例外になる。 */
	public void testCaptureRejectsForeignRange() {
		final LayoutSource log1 = new LayoutSource();
		log1.append(start());
		log1.append(new LayoutSource.EndBlock());
		final LayoutSource log2 = new LayoutSource();
		log2.append(start());
		log2.append(new LayoutSource.EndBlock());

		final SegmentRange rangeFromLog1 = LayoutSourceSegments.rangeOf(log1, 0, 1);
		try {
			LayoutSourceSegments.capture(log2, rangeFromLog1);
			fail("異なるsourceのrangeは拒否されるはず");
		} catch (IllegalArgumentException expected) {
			// OK
		}
	}

	/** compaction後も、生きているリース範囲に対するcursor変換は安定する。 */
	public void testCursorStableAcrossCompaction() {
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start());
		log.append(start());
		log.append(new LayoutSource.Chars(0, "z".toCharArray(), false));
		final long p1end = log.append(new LayoutSource.EndBlock());
		final long p2 = log.append(start());

		final SegmentCursor beforeCompact = LayoutSourceSegments.cursorOf(log, p2);
		log.compact(p2);
		final SegmentCursor afterCompact = LayoutSourceSegments.cursorOf(log, p2);

		assertEquals(beforeCompact, afterCompact);
		assertNotNull(log.get(body));
	}
}
