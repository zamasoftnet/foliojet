package net.zamasoft.foliojet.layout.constraint;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * {@link ExclusionSpace}の並び契約を固定する単体テストです(2026-07-23
 * 新設、排除域のConstraintSpace入力化P0)。まだ未配線
 * ({@code BlockBuilder}等の既存消費者はこの型を参照しない)——この
 * 段階では、{@code BlockBuilder.FLOAT_COMP}(pageEnd昇順の安定ソート、
 * 同値は追加順)と同じ並び契約を、この値型だけで再現できることだけを
 * 確認する。
 */
public class ExclusionSpaceTest extends TestCase {
	public ExclusionSpaceTest(String name) {
		super(name);
	}

	private static FloatExclusion exclusion(long order, double pageEnd) {
		return new FloatExclusion(order, FloatSide.START, new AxisSpan(0, pageEnd), new AxisSpan(0, 100));
	}

	public void testEmptyIsEmpty() {
		assertTrue(ExclusionSpace.EMPTY.isEmpty());
		assertEquals(0, ExclusionSpace.EMPTY.size());
		assertTrue(ExclusionSpace.EMPTY.ascendingByPageEnd().isEmpty());
		assertTrue(ExclusionSpace.EMPTY.descendingByPageEnd().isEmpty());
	}

	public void testPlusIsImmutable() {
		final ExclusionSpace before = ExclusionSpace.EMPTY;
		final ExclusionSpace after = before.plus(exclusion(0, 100));
		assertTrue(before.isEmpty());
		assertEquals(1, after.size());
	}

	public void testAscendingOrderForDistinctPageEnds() {
		// 挿入順はpageEnd順とは無関係に、常にpageEnd昇順で並ぶこと
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 300));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 200));

		final List<FloatExclusion> ascending = space.ascendingByPageEnd();
		assertEquals(3, ascending.size());
		assertEquals(100.0, ascending.get(0).pageSpan().end(), 0);
		assertEquals(200.0, ascending.get(1).pageSpan().end(), 0);
		assertEquals(300.0, ascending.get(2).pageSpan().end(), 0);
	}

	public void testTiesPreserveInsertionOrderAscending() {
		// BlockBuilder.FLOAT_COMPは安定ソート:
		// 同じpageEndの浮動体は追加順のまま。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 100));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 100));

		final List<FloatExclusion> ascending = space.ascendingByPageEnd();
		assertEquals(0, ascending.get(0).order());
		assertEquals(1, ascending.get(1).order());
		assertEquals(2, ascending.get(2).order());
	}

	public void testDescendingViewReversesEntireOrder() {
		// BlockBuilderの多くの消費者はfloatings.size()-1から0へ逆順走査する
		// ——同値のpageEndでは最後に追加されたものを先に見る。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 100));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 200));

		final List<FloatExclusion> descending = space.descendingByPageEnd();
		assertEquals(3, descending.size());
		assertEquals(2, descending.get(0).order());
		assertEquals(1, descending.get(1).order());
		assertEquals(0, descending.get(2).order());
	}

	public void testMixedDistinctAndTiedPageEnds() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 200));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 200));
		space = space.plus(exclusion(3, 50));

		final List<FloatExclusion> ascending = space.ascendingByPageEnd();
		assertEquals(List.of(3L, 1L, 0L, 2L), ascending.stream().map(FloatExclusion::order).toList());
	}
}
