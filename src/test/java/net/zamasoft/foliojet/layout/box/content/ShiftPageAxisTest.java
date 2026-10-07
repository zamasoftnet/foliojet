package net.zamasoft.foliojet.layout.box.content;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Fiducial;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * Tests for the page-coordinate translation API ({@code FlowContainer.shiftPageAxis} and its underlying
 * {@code Floatings}/{@code Absolutes}) (2026-09-05, stage 1 of placing `float: top` on the current page
 * via translation). Locks down preservation of serial, line-axis position, {@code moveToNext}, and list
 * order; verifies that {@code keep} boxes, explicit offsets ({@code NONE}), and fixed boxes do not move;
 * and checks that static positions of absolutely positioned boxes move along the physical axis for
 * each writing direction.
 */
public class ShiftPageAxisTest extends TestCase {

	private static BlockParams blockParams(final WritingMode flow) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.pageBreakInside = PageBreakMode.AUTO;
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	private static FlowContainer container(final WritingMode flow) {
		final FlowContainer container = new FlowContainer();
		container.setBox(new FloatBlockBox(blockParams(flow), new FloatPos()));
		return container;
	}

	private static Set<IBox> keep(final IBox... boxes) {
		final Set<IBox> set = Collections.newSetFromMap(new IdentityHashMap<IBox, Boolean>());
		Collections.addAll(set, boxes);
		return set;
	}

	/** Normal flow preserves serial and box identity and moves only pageAxis. The keep box does not move. */
	public void testFlowsShiftKeepingSerialAndOrder() {
		final FlowContainer container = container(WritingMode.TB);
		final FlowBlockBox a = new FlowBlockBox(blockParams(WritingMode.TB), new FlowPos());
		final FlowBlockBox b = new FlowBlockBox(blockParams(WritingMode.TB), new FlowPos());
		container.addFlow(a, 10);
		container.addFlow(b, 30);
		final int serialA = container.flows.get(0).serial;
		final int serialB = container.flows.get(1).serial;

		container.shiftPageAxis(7.5, keep(b));

		assertSame(a, container.flows.get(0).box);
		assertEquals(17.5, container.flows.get(0).pageAxis, 0.0);
		assertEquals(serialA, container.flows.get(0).serial);
		assertSame("keepの箱は同じ要素のまま", b, container.flows.get(1).box);
		assertEquals(30.0, container.flows.get(1).pageAxis, 0.0);
		assertEquals(serialB, container.flows.get(1).serial);
	}

	/** Floats preserve serial, line-axis position, and moveToNext and move only pageAxis. */
	public void testFloatingsShiftKeepingMoveToNext() {
		final FlowContainer container = container(WritingMode.TB);
		final FloatBlockBox moved = new FloatBlockBox(blockParams(WritingMode.TB), new FloatPos());
		final FloatBlockBox kept = new FloatBlockBox(blockParams(WritingMode.TB), new FloatPos());
		container.addFloating(moved, 3, 20, true);
		container.addFloating(kept, 0, 0);
		final Floatings.Floating before = container.floatings.getFloating(0);
		final Floatings.Floating keptBefore = container.floatings.getFloating(1);

		container.shiftPageAxis(12, keep(kept));

		final Floatings.Floating after = container.floatings.getFloating(0);
		assertSame(moved, after.box);
		assertEquals(before.serial, after.serial);
		assertEquals(3.0, after.lineAxis, 0.0);
		assertEquals(32.0, after.pageAxis, 0.0);
		assertTrue("一回限りの移送状態を保つ", after.moveToNext);
		assertSame("keepの浮動体は同じインスタンス", keptBefore, container.floatings.getFloating(1));
	}

	/** Absolute static positions move on the physical axis for each writing direction; NONE and fixed do not move. */
	public void testAbsolutesShiftPerWritingMode() {
		for (final WritingMode flow : new WritingMode[] { WritingMode.TB, WritingMode.LR, WritingMode.RL }) {
			final FlowContainer container = container(flow);
			final AbsoluteBlockBox statik = new AbsoluteBlockBox(blockParams(flow), new AbsolutePos());
			// A box with an explicit offset (top in horizontal writing, left in vertical writing). addAbsolute replaces
			// the static position on that axis with NONE (Absolutes.addAbsolute).
			final AbsolutePos explicitPos = new AbsolutePos();
			explicitPos.location = flow == WritingMode.TB
					? Insets.create(0, 0, 0, 0, LengthType.ABSOLUTE, LengthType.AUTO, LengthType.AUTO, LengthType.AUTO)
					: Insets.create(0, 0, 0, 0, LengthType.AUTO, LengthType.AUTO, LengthType.AUTO, LengthType.ABSOLUTE);
			final AbsoluteBlockBox explicit = new AbsoluteBlockBox(blockParams(flow), explicitPos);
			final AbsolutePos fixedPos = new AbsolutePos();
			fixedPos.fiducial = Fiducial.ALL_PAGE;
			final AbsoluteBlockBox fixed = new AbsoluteBlockBox(blockParams(flow), fixedPos);
			container.addAbsolute(statik, 5, 6);
			container.addAbsolute(explicit, 5, 6);
			container.addAbsolute(fixed, 5, 6);

			container.shiftPageAxis(10, keep());

			final Absolutes.Absolute s = container.absolutes.getAbsolute(0);
			final Absolutes.Absolute e = container.absolutes.getAbsolute(1);
			final Absolutes.Absolute f = container.absolutes.getAbsolute(2);
			switch (flow) {
			case TB:
				assertEquals(flow + " y+dy", 16.0, s.y, 0.0);
				assertEquals(flow + " x不変", 5.0, s.x, 0.0);
				assertTrue(flow + " NONEは動かない", LayoutUtils.isNone(e.y));
				break;
			case LR:
				assertEquals(flow + " x+dy", 15.0, s.x, 0.0);
				assertEquals(flow + " y不変", 6.0, s.y, 0.0);
				assertTrue(flow + " NONEは動かない", LayoutUtils.isNone(e.x));
				break;
			case RL:
				assertEquals(flow + " x-dy", -5.0, s.x, 0.0);
				assertEquals(flow + " y不変", 6.0, s.y, 0.0);
				assertTrue(flow + " NONEは動かない", LayoutUtils.isNone(e.x));
				break;
			default:
				fail();
			}
			assertEquals(flow + " fixedは動かない", 5.0, f.x, 0.0);
			assertEquals(flow + " fixedは動かない", 6.0, f.y, 0.0);
		}
	}
}
