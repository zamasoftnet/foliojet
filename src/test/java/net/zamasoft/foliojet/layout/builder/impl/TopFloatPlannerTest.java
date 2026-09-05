package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * {@code RootBuilder.planTopFloats} の純粋な FIFO-prefix 計画の試験(2026-09-05、
 * translate の第1段)。頁先頭({@code atPageStart=true})は「stackEnd==0 の先頭
 * 1 件は容量に関係なく採る→以後は収まる間だけ」、現頁への平行移動
 * ({@code atPageStart=false})は「先頭が収まらなければ空」で、どちらも途中を
 * 飛ばさない。
 */
public class TopFloatPlannerTest extends TestCase {

	private final Map<FloatBlockBox, Double> extents = new IdentityHashMap<>();

	private FloatBlockBox floatOf(final double extent) {
		final BlockParams params = new BlockParams();
		params.flow = WritingMode.TB;
		params.pageBreakInside = PageBreakMode.AUTO;
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		final FloatBlockBox box = new FloatBlockBox(params, new FloatPos());
		this.extents.put(box, extent);
		return box;
	}

	private RootBuilder.TopFloatPlan plan(final Deque<FloatBlockBox> queue, final double stackEnd,
			final double maxArea, final boolean atPageStart) {
		return RootBuilder.planTopFloats(queue, this.extents::get, stackEnd, maxArea, atPageStart);
	}

	public void testPageStartTakesFirstEvenIfTooLarge() {
		final Deque<FloatBlockBox> queue = new ArrayDeque<>();
		final FloatBlockBox big = this.floatOf(500);
		final FloatBlockBox next = this.floatOf(10);
		queue.add(big);
		queue.add(next);
		final RootBuilder.TopFloatPlan plan = this.plan(queue, 0, 300, true);
		assertEquals(1, plan.boxes.size());
		assertSame("収まらなくても先頭 1 件は採る(前進保証)", big, plan.boxes.get(0));
		assertEquals(500.0, plan.dy, 0.0);
		assertEquals("queue は変更しない", 2, queue.size());
	}

	public void testPageStartFifoStopsAtFirstNonFitting() {
		final Deque<FloatBlockBox> queue = new ArrayDeque<>();
		queue.add(this.floatOf(100));
		queue.add(this.floatOf(150));
		queue.add(this.floatOf(10)); // 途中を飛ばして採らない
		final RootBuilder.TopFloatPlan plan = this.plan(queue, 0, 200, true);
		assertEquals(1, plan.boxes.size());
		assertEquals(100.0, plan.dy, 0.0);
	}

	public void testMidPageNeverTakesNonFittingHead() {
		final Deque<FloatBlockBox> queue = new ArrayDeque<>();
		queue.add(this.floatOf(120));
		queue.add(this.floatOf(10));
		assertTrue(this.plan(queue, 0, 100, false).boxes.isEmpty());
		final RootBuilder.TopFloatPlan plan = this.plan(queue, 0, 130, false);
		assertEquals(2, plan.boxes.size());
		assertEquals(130.0, plan.dy, 0.0);
	}

	public void testStackEndCountsAgainstCapacity() {
		final Deque<FloatBlockBox> queue = new ArrayDeque<>();
		queue.add(this.floatOf(60));
		assertTrue("既配置 50 + 60 > 100", this.plan(queue, 50, 100, false).boxes.isEmpty());
		assertEquals(1, this.plan(queue, 40, 100, false).boxes.size());
	}
}
