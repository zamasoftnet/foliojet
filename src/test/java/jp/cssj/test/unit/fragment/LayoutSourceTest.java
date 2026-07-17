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
		return new LayoutSource.Start(LayoutSource.BoxKind.FLOW, null, null);
	}

	public void testEventIdStableAcrossCompaction() {
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start()); // 開いたまま
		final long p1 = log.append(start());
		log.append(new LayoutSource.Chars(0, "aaa".toCharArray(), false));
		final long p1end = log.append(new LayoutSource.EndBlock());
		final long p2 = log.append(start());
		log.append(new LayoutSource.Chars(3, "bbb".toCharArray(), false));
		log.append(new LayoutSource.EndBlock());

		assertEquals(p1end, log.endOf(p1));

		// p2 より前を破棄(開いている body は残る)
		log.compact(p2);
		assertNotNull(log.get(body));
		assertNull(log.get(p1));
		assertNotNull(log.get(p2));
		// id は不変
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
		// Opaque は EndBlock と対を成す開始イベント。対称に扱わないと
		// (1) endOf が Opaque の対の EndBlock を親の終端と誤認し、
		// (2) compact が祖先の Start を誤って pop して開チェーンが崩壊する
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start());
		final long div = log.append(start());
		log.append(new LayoutSource.Opaque()); // 絶対配置などの非対応ボックス
		log.append(new LayoutSource.Chars(0, "a".toCharArray(), false));
		log.append(new LayoutSource.EndBlock()); // /opaque
		log.append(new LayoutSource.Chars(1, "b".toCharArray(), false));
		final long divEnd = log.append(new LayoutSource.EndBlock()); // /div
		final long tail = log.nextId();
		log.append(new LayoutSource.Chars(2, "c".toCharArray(), false));

		// endOf: Opaque の対の EndBlock を div の終端と誤認しないこと
		assertEquals(divEnd, log.endOf(div));

		// compact: 破棄範囲内で完結する opaque 対が、開いている祖先
		// (body)の Start を pop してしまわないこと
		log.compact(tail);
		assertNotNull(log.get(body));
		assertEquals(-1, log.endOf(body));
	}

	public void testReplaySurvivesNestedCompaction() {
		// 再生の visitor 内で入れ子の改ページが compact を呼ぶケース
		// (再生した内容が新ページを溢れさせる)。backing list が
		// clear+addAll で作り直されても、再生中の範囲は完走しなければ
		// ならない(外部レビュー指摘: 数値 index の走査では silent skip する)
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
				// 水位 pin(fromId=ids[2])で clamp された compact を模擬
				log.compact(ids[2]);
			}
		});
		assertEquals(List.of(2, 3, 4, 5), seen);
	}

	public void testRetentionLeaseClampsCompaction() {
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[5];
		for (int i = 0; i < 5; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.RetentionLease lease = log.retainFrom(ids[1]);
		// リースが生きている間、fromId より前は破棄されない
		log.compact(ids[4]);
		assertNotNull(log.get(ids[1]));
		assertNotNull(log.get(ids[3]));
		assertNull(log.get(ids[0]));
		// 解放後は破棄できる
		lease.close();
		log.compact(ids[4]);
		assertNull(log.get(ids[1]));
		assertNotNull(log.get(ids[4]));
	}

	public void testRetentionLeaseIsRefCounted() {
		// 同じ fromId を複数の継続が独立に所有した場合、一方の解放で
		// もう一方の保持が消えてはならない(参照カウント)
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[3];
		for (int i = 0; i < 3; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.RetentionLease outer = log.retainFrom(ids[0]);
		final LayoutSource.RetentionLease inner = log.retainFrom(ids[0]);
		inner.close();
		inner.close(); // 冪等
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
		final long div = log.append(start()); // 開いたまま
		final long tail = log.nextId();
		log.append(new LayoutSource.Chars(0, "t".toCharArray(), false));

		log.compact(tail);
		assertNotNull(log.get(html));
		assertNotNull(log.get(body));
		assertNotNull(log.get(div));
		assertNull(log.get(p));
		assertEquals(4, log.size());
	}
}
