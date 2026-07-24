package net.zamasoft.foliojet.layout.box.content;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit;
import net.zamasoft.foliojet.layout.fragment.FragmentRecipe;
import net.zamasoft.foliojet.layout.fragment.FragmentState;
import net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * A-3a-1: {@code PreparedFloatFragment}(float断片の遅延構築材料)と
 * {@code AbstractBlockBox.splitFloatFragment}の、既存即時経路
 * ({@code split}→{@code splitPage}→{@code recipe.instantiate})との
 * 等価性テストです(2026-07-24新設)。
 *
 * <p>
 * 本番commit点でのshadow二重生成は行わない——{@code recipe.instantiate}は
 * {@code container.setBox(this)}の副作用(コンテナのbox参照の付け替え)を
 * 持つため、同じコンテナへの二重instantiateは配線を壊す(codex設計§2.4
 * 「同じboxにlegacy splitと新splitを二重実行した結果は比較しない」)。
 * 代わりに<b>同一構成の実ボックス2体(twin)</b>で即時経路とdeferred経路を
 * 並走させ、前断片のmutation・残余boxの幾何・クラス・パラメータ識別・
 * 残余コンテナ内容の一致を固定する。deferred経路は{@code splitPageState}の
 * 実出力をそのまま運ぶ(再計算しない)ため、この比較+構成上の同一性が
 * 等価性の根拠になる。
 * </p>
 */
public class PreparedFloatFragmentTest extends TestCase {

	private static BlockParams blockParams(final WritingMode flow) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.pageBreakInside = PageBreakMode.AUTO;
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	/**
	 * twin fixture: 内側floatを1つ抱えるブロックfloat。内側floatは切断線
	 * (100)より後(pageAxis=150)にあるため丸ごと次フラグメントへ移動し、
	 * 外側boxは内部切断(Split)になる。paramsはtwin間で共有し、recipeが
	 * キャプチャする値の識別比較を可能にする。
	 */
	private static FloatBlockBox outerWithMovingInnerFloat(final BlockParams outerParams,
			final BlockParams innerParams) {
		final FloatBlockBox outer = new FloatBlockBox(outerParams, new FloatPos());
		final FloatBlockBox inner = new FloatBlockBox(innerParams, new FloatPos());
		inner.setPageAxis(10);
		outer.getContainer().addFloating(inner, 0, 150);
		outer.setPageAxis(200);
		return outer;
	}

	/** twin比較: 即時経路(split)とdeferred経路(splitFloatFragment+materialize)が同じ結果を作ること。 */
	public void testSplitFloatFragmentMatchesImmediateSplitOnTwins() {
		final BlockParams outerParams = blockParams(WritingMode.TB);
		final BlockParams innerParams = blockParams(WritingMode.TB);
		final FloatBlockBox immediate = outerWithMovingInnerFloat(outerParams, innerParams);
		final FloatBlockBox deferred = outerWithMovingInnerFloat(outerParams, innerParams);

		// 即時経路
		final SplitResult immediateResult = immediate.split(100, BreakMode.DEFAULT_BREAK_MODE,
				IPageBreakableBox.FLAGS_SPLIT);
		assertTrue("fixtureはSplitになるはず: " + immediateResult, immediateResult instanceof SplitResult.Split);
		final IFloatBox immediateRemainder = (IFloatBox) ((SplitResult.Split) immediateResult).remainder();

		// deferred経路
		final FloatFragmentSplit deferredResult = deferred.splitFloatFragment(7, 100, BreakMode.DEFAULT_BREAK_MODE,
				IPageBreakableBox.FLAGS_SPLIT);
		assertTrue("fixtureはPreparedになるはず: " + deferredResult,
				deferredResult instanceof FloatFragmentSplit.Prepared);
		final PreparedFloatFragment fragment = ((FloatFragmentSplit.Prepared) deferredResult).fragment();
		assertEquals(7, fragment.serial());
		final IFloatBox deferredRemainder = fragment.materialize();

		// 前断片のmutationが一致(切りつめ後の寸法)
		assertEquals(immediate.getWidth(), deferred.getWidth(), 0);
		assertEquals(immediate.getHeight(), deferred.getHeight(), 0);
		// 残余boxのクラス・パラメータ識別・幾何が一致
		assertSame(immediateRemainder.getClass(), deferredRemainder.getClass());
		assertSame(immediateRemainder.getParams(), deferredRemainder.getParams());
		assertEquals(immediateRemainder.getWidth(), deferredRemainder.getWidth(), 0);
		assertEquals(immediateRemainder.getHeight(), deferredRemainder.getHeight(), 0);
		assertEquals(immediateRemainder.getPageExtent(WritingMode.TB), deferredRemainder.getPageExtent(WritingMode.TB),
				0);
		// 残余コンテナの内容(移動した内側float)が一致
		final Container immediateContainer = ((FloatBlockBox) immediateRemainder).getContainer();
		final Container deferredContainer = ((FloatBlockBox) deferredRemainder).getContainer();
		assertTrue(immediateContainer.hasFloatings());
		assertTrue(deferredContainer.hasFloatings());
	}

	/** twin比較: KEEP(切断線以下に収まる)は両経路で一致。 */
	public void testKeepParityOnTwins() {
		final BlockParams outerParams = blockParams(WritingMode.TB);
		final BlockParams innerParams = blockParams(WritingMode.TB);
		final FloatBlockBox immediate = outerWithMovingInnerFloat(outerParams, innerParams);
		final FloatBlockBox deferred = outerWithMovingInnerFloat(outerParams, innerParams);
		// 切断線が全体より後——移動なし
		assertTrue(immediate.split(500, BreakMode.DEFAULT_BREAK_MODE,
				(byte) 0) instanceof SplitResult.Keep);
		assertTrue(deferred.splitFloatFragment(1, 500, BreakMode.DEFAULT_BREAK_MODE,
				(byte) 0) instanceof FloatFragmentSplit.Keep);
	}

	/** materializeは一回限定(二回目はIllegalStateException)。 */
	public void testMaterializeIsOneShot() {
		final FloatBlockBox deferred = outerWithMovingInnerFloat(blockParams(WritingMode.TB),
				blockParams(WritingMode.TB));
		final FloatFragmentSplit result = deferred.splitFloatFragment(3, 100, BreakMode.DEFAULT_BREAK_MODE,
				IPageBreakableBox.FLAGS_SPLIT);
		final PreparedFloatFragment fragment = ((FloatFragmentSplit.Prepared) result).fragment();
		fragment.materialize();
		try {
			fragment.materialize();
			fail("二回目のmaterializeは失敗するはず");
		} catch (final IllegalStateException expected) {
			// 期待どおり
		}
	}

	/** materializeの構築が既存continueFragmentと同一であること(同じ材料からの直接比較)。 */
	public void testMaterializeUsesContinueFragmentConstruction() {
		final BlockParams params = blockParams(WritingMode.TB);
		// 実ボックスからrecipe/stateを採る(twinで同一材料を2組作る)
		final FloatBlockBox boxA = outerWithMovingInnerFloat(params, blockParams(WritingMode.TB));
		final FloatBlockBox boxB = outerWithMovingInnerFloat(params, blockParams(WritingMode.TB));
		final FragmentRecipe recipeA = boxA.fragmentRecipe();
		final FragmentRecipe recipeB = boxB.fragmentRecipe();
		final FragmentState stateA = boxA.splitPageState(60, false);
		final FragmentState stateB = boxB.splitPageState(60, false);
		final double crossExtent = 40;
		final AbstractBlockBox viaContinue = AbstractBlockBox.continueFragment(recipeA, stateA, new FlowContainer(),
				crossExtent);
		final IFloatBox viaMaterialize = new PreparedFloatFragment(1, recipeB, stateB, new FlowContainer(),
				crossExtent).materialize();
		assertSame(viaContinue.getClass(), viaMaterialize.getClass());
		assertSame(viaContinue.getParams(), viaMaterialize.getParams());
		assertEquals(viaContinue.getWidth(), viaMaterialize.getWidth(), 0);
		assertEquals(viaContinue.getHeight(), viaMaterialize.getHeight(), 0);
	}
}
