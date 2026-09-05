package net.zamasoft.foliojet.layout.builder;

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
 * builder 側の頁座標を持つ値({@code LayoutContext.Flow}/{@code Floating})の
 * 平行移動の試験(2026-09-05、translate の第1段)。{@code frameHead}・
 * {@code lineClamp}・行軸範囲を保つことを固定する。
 */
public class LayoutContextShiftTest extends TestCase {

	private static BlockParams blockParams() {
		final BlockParams params = new BlockParams();
		params.flow = WritingMode.TB;
		params.pageBreakInside = PageBreakMode.AUTO;
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	public void testFlowShiftKeepsFrameHeadAndLineClamp() {
		final FloatBlockBox box = new FloatBlockBox(blockParams(), new FloatPos());
		final LayoutContext.Flow flow = new LayoutContext.Flow(box, 4, 40, 2.5);
		final LineClampState clamp = flow.lineClamp;
		final LayoutContext.Flow shifted = flow.shiftedPageAxis(15);
		assertSame(box, shifted.box);
		assertEquals(4.0, shifted.lineAxis, 0.0);
		assertEquals(55.0, shifted.pageAxis, 0.0);
		assertEquals(2.5, shifted.frameHead, 0.0);
		assertSame("line-clamp の可変状態は同じ参照を引き継ぐ", clamp, shifted.lineClamp);
	}

	public void testFloatingShiftKeepsLineRange() {
		final FloatBlockBox box = new FloatBlockBox(blockParams(), new FloatPos());
		final LayoutContext.Floating floating = new LayoutContext.Floating(box, 3, 20, WritingMode.TB);
		final LayoutContext.Floating shifted = floating.shiftedPageAxis(8);
		assertSame(box, shifted.box);
		assertEquals(floating.lineStart, shifted.lineStart, 0.0);
		assertEquals(floating.lineEnd, shifted.lineEnd, 0.0);
		assertEquals(floating.pageStart + 8, shifted.pageStart, 0.0);
		assertEquals(floating.pageEnd + 8, shifted.pageEnd, 0.0);
	}
}
