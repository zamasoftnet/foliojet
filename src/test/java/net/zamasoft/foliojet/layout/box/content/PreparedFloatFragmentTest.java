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
 * A-3a-1: tests that {@code PreparedFloatFragment} (materials for deferred construction of a float fragment)
 * and {@code AbstractBlockBox.splitFloatFragment} are equivalent to the existing immediate path
 * ({@code split}→{@code splitPage}→{@code recipe.instantiate}) (added 2026-07-24).
 *
 * <p>
 * Do not create a second shadow instance at the production commit point: {@code recipe.instantiate} has
 * the side effect of {@code container.setBox(this)} (reassigning the container's box reference), so
 * instantiating twice with the same container breaks the wiring (codex design §2.4:
 * "Do not compare results of running both legacy split and the new split on the same box").
 * Instead, run the immediate and deferred paths on <b>two real boxes with identical configurations
 * (twins)</b>, and lock down equality of the previous fragment's mutations, remainder box geometry,
 * class, parameter identity, and remainder container contents. The deferred path carries the actual
 * output of {@code splitPageState} unchanged (without recomputation), so this comparison plus structural
 * identity provides the basis for equivalence.
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
	 * Twin fixture: a block float containing one inner float. The inner float is beyond the cut line
	 * (100), at pageAxis=150, so it moves whole to the next fragment, while the outer box splits internally
	 * (Split). The twins share params, allowing identity comparisons of the values captured by the recipe.
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

	/**
	 * Twin comparison: immediate (split) and deferred (splitFloatFragment+materialize) paths produce the same
	 * result.
	 */
	public void testSplitFloatFragmentMatchesImmediateSplitOnTwins() {
		final BlockParams outerParams = blockParams(WritingMode.TB);
		final BlockParams innerParams = blockParams(WritingMode.TB);
		final FloatBlockBox immediate = outerWithMovingInnerFloat(outerParams, innerParams);
		final FloatBlockBox deferred = outerWithMovingInnerFloat(outerParams, innerParams);

		// Immediate path.
		final SplitResult immediateResult = immediate.split(100, BreakMode.DEFAULT_BREAK_MODE,
				IPageBreakableBox.FLAGS_SPLIT);
		assertTrue("fixtureはSplitになるはず: " + immediateResult, immediateResult instanceof SplitResult.Split);
		final IFloatBox immediateRemainder = (IFloatBox) ((SplitResult.Split) immediateResult).remainder();

		// Deferred path.
		final FloatFragmentSplit deferredResult = deferred.splitFloatFragment(7, 100, BreakMode.DEFAULT_BREAK_MODE,
				IPageBreakableBox.FLAGS_SPLIT);
		assertTrue("fixtureはPreparedになるはず: " + deferredResult,
				deferredResult instanceof FloatFragmentSplit.Prepared);
		final PreparedFloatFragment fragment = ((FloatFragmentSplit.Prepared) deferredResult).fragment();
		assertEquals(7, fragment.serial());
		final IFloatBox deferredRemainder = fragment.materialize();

		// Mutations of the previous fragment match (dimensions after truncation).
		assertEquals(immediate.getWidth(), deferred.getWidth(), 0);
		assertEquals(immediate.getHeight(), deferred.getHeight(), 0);
		// The remainder boxes match in class, parameter identity, and geometry.
		assertSame(immediateRemainder.getClass(), deferredRemainder.getClass());
		assertSame(immediateRemainder.getParams(), deferredRemainder.getParams());
		assertEquals(immediateRemainder.getWidth(), deferredRemainder.getWidth(), 0);
		assertEquals(immediateRemainder.getHeight(), deferredRemainder.getHeight(), 0);
		assertEquals(immediateRemainder.getPageExtent(WritingMode.TB), deferredRemainder.getPageExtent(WritingMode.TB),
				0);
		// Remainder container contents (the moved inner float) match.
		final Container immediateContainer = ((FloatBlockBox) immediateRemainder).getContainer();
		final Container deferredContainer = ((FloatBlockBox) deferredRemainder).getContainer();
		assertTrue(immediateContainer.hasFloatings());
		assertTrue(deferredContainer.hasFloatings());
	}

	/** Twin comparison: KEEP (fits at or before the cut line) matches on both paths. */
	public void testKeepParityOnTwins() {
		final BlockParams outerParams = blockParams(WritingMode.TB);
		final BlockParams innerParams = blockParams(WritingMode.TB);
		final FloatBlockBox immediate = outerWithMovingInnerFloat(outerParams, innerParams);
		final FloatBlockBox deferred = outerWithMovingInnerFloat(outerParams, innerParams);
		// The cut line is beyond the entire box: nothing moves.
		assertTrue(immediate.split(500, BreakMode.DEFAULT_BREAK_MODE,
				(byte) 0) instanceof SplitResult.Keep);
		assertTrue(deferred.splitFloatFragment(1, 500, BreakMode.DEFAULT_BREAK_MODE,
				(byte) 0) instanceof FloatFragmentSplit.Keep);
	}

	/** materialize is one-shot (the second call throws IllegalStateException). */
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
			// As expected.
		}
	}

	/**
	 * materialize constructs the same result as the existing continueFragment (direct comparison with the same
	 * materials).
	 */
	public void testMaterializeUsesContinueFragmentConstruction() {
		final BlockParams params = blockParams(WritingMode.TB);
		// Obtain recipe/state from real boxes (use twins to create two identical sets of materials).
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
