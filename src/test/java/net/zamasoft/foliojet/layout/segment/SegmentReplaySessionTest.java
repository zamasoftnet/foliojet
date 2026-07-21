package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import junit.framework.TestCase;

/**
 * M6d-A1(2026-07-22新設)の値型契約を固定する単体テストです。
 * 挙動不変(既存クラスへの配線なし)——このテストは新パッケージ
 * 自身の契約だけを検証する。
 */
public class SegmentReplaySessionTest extends TestCase {
	private static List<Long> drain(final SegmentReplaySession session) {
		final List<Long> ordinals = new ArrayList<>();
		while (session.hasNext()) {
			ordinals.add(session.next().ordinal());
		}
		return ordinals;
	}

	/** 同一rangeを3回走査して同じordinal列になる。 */
	public void testSameRangeReplaysIdentically() {
		final SegmentId id = SegmentId.create();
		final SegmentRange range = new SegmentRange(new SegmentCursor(id, 0), new SegmentCursor(id, 5));

		final List<Long> first = drain(new SegmentReplaySession(range));
		final List<Long> second = drain(new SegmentReplaySession(range));
		final List<Long> third = drain(new SegmentReplaySession(range));

		assertEquals(List.of(0L, 1L, 2L, 3L, 4L), first);
		assertEquals(first, second);
		assertEquals(first, third);
	}

	/** 3セッションを交互に進めても互いに影響しない。 */
	public void testInterleavedSessionsAreIndependent() {
		final SegmentId id = SegmentId.create();
		final SegmentRange range = new SegmentRange(new SegmentCursor(id, 0), new SegmentCursor(id, 3));

		final SegmentReplaySession a = new SegmentReplaySession(range);
		final SegmentReplaySession b = new SegmentReplaySession(range);
		final SegmentReplaySession c = new SegmentReplaySession(range);

		// 交互に1歩ずつ進める
		assertEquals(0L, a.next().ordinal());
		assertEquals(0L, b.next().ordinal());
		assertEquals(0L, c.next().ordinal());
		assertEquals(1L, b.next().ordinal());
		assertEquals(1L, a.next().ordinal());
		assertEquals(1L, c.next().ordinal());
		assertEquals(2L, c.next().ordinal());
		assertEquals(2L, a.next().ordinal());
		assertEquals(2L, b.next().ordinal());

		assertFalse(a.hasNext());
		assertFalse(b.hasNext());
		assertFalse(c.hasNext());
	}

	/** 異なるSegmentId間のrangeを拒否する。 */
	public void testRejectsRangeAcrossDifferentSegmentIds() {
		final SegmentId id1 = SegmentId.create();
		final SegmentId id2 = SegmentId.create();
		try {
			new SegmentRange(new SegmentCursor(id1, 0), new SegmentCursor(id2, 5));
			fail("異なるSegmentId間のrangeは拒否されるはず");
		} catch (IllegalArgumentException expected) {
			// OK
		}
	}

	/** 逆転range(from > to)を拒否する。 */
	public void testRejectsReversedRange() {
		final SegmentId id = SegmentId.create();
		try {
			new SegmentRange(new SegmentCursor(id, 5), new SegmentCursor(id, 0));
			fail("逆転rangeは拒否されるはず");
		} catch (IllegalArgumentException expected) {
			// OK
		}
	}

	/** 範囲外cursor(負のordinal)を拒否する。 */
	public void testRejectsOutOfRangeCursor() {
		final SegmentId id = SegmentId.create();
		try {
			new SegmentCursor(id, -1);
			fail("負のordinalは拒否されるはず");
		} catch (IllegalArgumentException expected) {
			// OK
		}
	}

	/** SegmentRange.contains()がordinal境界を正しく判定する。 */
	public void testRangeContains() {
		final SegmentId id = SegmentId.create();
		final SegmentRange range = new SegmentRange(new SegmentCursor(id, 2), new SegmentCursor(id, 5));

		assertFalse(range.contains(new SegmentCursor(id, 1)));
		assertTrue(range.contains(new SegmentCursor(id, 2)));
		assertTrue(range.contains(new SegmentCursor(id, 4)));
		assertFalse(range.contains(new SegmentCursor(id, 5)));

		final SegmentId other = SegmentId.create();
		assertFalse("異なるSegmentIdのcursorは含まれないはず", range.contains(new SegmentCursor(other, 3)));
	}

	/** cursor/rangeのコピー後(等価な別インスタンス生成後)も等価である。 */
	public void testCopiesAreEqual() {
		final SegmentId id = SegmentId.create();
		final SegmentCursor c1 = new SegmentCursor(id, 3);
		final SegmentCursor c2 = new SegmentCursor(id, 3);
		assertEquals(c1, c2);
		assertEquals(c1.hashCode(), c2.hashCode());

		final SegmentRange r1 = new SegmentRange(new SegmentCursor(id, 0), new SegmentCursor(id, 3));
		final SegmentRange r2 = new SegmentRange(new SegmentCursor(id, 0), new SegmentCursor(id, 3));
		assertEquals(r1, r2);
		assertEquals(r1.hashCode(), r2.hashCode());
	}

	/** SegmentIdは生成順に異なる値を持つ(識別のみに使う不透明トークン)。 */
	public void testSegmentIdCreateIsUnique() {
		final SegmentId a = SegmentId.create();
		final SegmentId b = SegmentId.create();
		assertFalse(a.equals(b));
	}

	/** 空きrange(from==to)は正当で、即座にhasNext()==falseになる。 */
	public void testEmptyRangeHasNoElements() {
		final SegmentId id = SegmentId.create();
		final SegmentCursor point = new SegmentCursor(id, 3);
		final SegmentRange empty = new SegmentRange(point, point);
		assertEquals(0L, empty.length());
		assertFalse(new SegmentReplaySession(empty).hasNext());
	}

	/** 使い果たしたセッションでnext()を呼ぶとNoSuchElementExceptionになる。 */
	public void testNextAfterExhaustionThrows() {
		final SegmentId id = SegmentId.create();
		final SegmentRange range = new SegmentRange(new SegmentCursor(id, 0), new SegmentCursor(id, 1));
		final SegmentReplaySession session = new SegmentReplaySession(range);
		session.next();
		try {
			session.next();
			fail("使い果たした後のnext()は例外になるはず");
		} catch (NoSuchElementException expected) {
			// OK
		}
	}
}
