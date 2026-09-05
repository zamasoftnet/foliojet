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
 * 頁座標の平行移動 API({@code FlowContainer.shiftPageAxis} と、その下の
 * {@code Floatings}/{@code Absolutes})の試験(2026-09-05、`float: top` の現頁
 * 配置=translate の第1段)。serial・行軸位置・{@code moveToNext}・リスト順を
 * 保つこと、{@code keep} の箱と明示 offset({@code NONE})と fixed は動かない
 * こと、絶対配置の静的位置は書字方向ごとの物理軸で動くことを固定する。
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

	/** 通常フローは serial と箱を保ち pageAxis だけ動く。keep の箱は動かない。 */
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

	/** 浮動体は serial・行軸位置・moveToNext を保って pageAxis だけ動く。 */
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

	/** 絶対配置の静的位置は書字方向ごとの物理軸で動き、NONE と fixed は動かない。 */
	public void testAbsolutesShiftPerWritingMode() {
		for (final WritingMode flow : new WritingMode[] { WritingMode.TB, WritingMode.LR, WritingMode.RL }) {
			final FlowContainer container = container(flow);
			final AbsoluteBlockBox statik = new AbsoluteBlockBox(blockParams(flow), new AbsolutePos());
			// 明示 offset(横組みは top、縦組みは left)を持つ箱。addAbsolute がその軸の
			// 静的位置を NONE に置き換える(Absolutes.addAbsolute)
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
