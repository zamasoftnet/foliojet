package jp.cssj.test.unit.fragment;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * LayoutSource(レイアウトソースプロトコルログ)のテストです(M6b v3)。
 * params/pos はログ機構に無関係のため null で代用します。
 */
public class LayoutSourceTest extends TestCase {
	private static LayoutSource.Event start() {
		return new LayoutSource.StartBlock(null, null);
	}

	public void testEventIdStableAcrossCompaction() {
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start()); // 開いたまま
		final long p1 = log.append(start());
		log.append(new LayoutSource.Chars(0, "aaa".toCharArray()));
		final long p1end = log.append(new LayoutSource.EndBlock());
		final long p2 = log.append(start());
		log.append(new LayoutSource.Chars(3, "bbb".toCharArray()));
		log.append(new LayoutSource.EndBlock());

		assertEquals(p1end, log.endOf(p1));

		// p2 より前を破棄(開いている body は残る)
		log.compact(p2);
		assertNotNull(log.get(body));
		assertNull(log.get(p1));
		assertNotNull(log.get(p2));
		// id は不変
		assertTrue(log.get(p2) instanceof LayoutSource.StartBlock);
		assertEquals(log.endOf(p2), p2 + 2);
	}

	public void testReplayRange() {
		final LayoutSource log = new LayoutSource();
		log.append(start());
		final long from = log.append(new LayoutSource.Chars(0, "xy".toCharArray()));
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
		log.append(new LayoutSource.Chars(0, "a".toCharArray()));
		assertEquals(-1, log.endOf(open));
	}

	public void testCompactKeepsNestedOpenStarts() {
		final LayoutSource log = new LayoutSource();
		final long html = log.append(start());
		final long body = log.append(start());
		final long p = log.append(start());
		log.append(new LayoutSource.EndBlock()); // /p
		final long div = log.append(start()); // 開いたまま
		final long tail = log.nextId();
		log.append(new LayoutSource.Chars(0, "t".toCharArray()));

		log.compact(tail);
		assertNotNull(log.get(html));
		assertNotNull(log.get(body));
		assertNotNull(log.get(div));
		assertNull(log.get(p));
		assertEquals(4, log.size());
	}
}
