package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;

/**
 * M6d-A3a(2026-07-22新設、A3b後に{@link BoxRecipe}をsealed interface
 * へ拡張)の型契約を固定する単体テストです。まだ未配線(既存の
 * {@code LayoutSource}/{@code SourceReplayer}への変換アダプタは
 * M6d-A3c以降)——この段階では型自身の構造(recipeと構造の分離、
 * Barrierが理由を必須で持つこと等)だけを確認する。
 */
public class SegmentEventTest extends TestCase {
	private static BoxRecipe.Flow flowRecipe() {
		return new BoxRecipe.Flow(BlockParamsTemplate.freeze(new BlockParams()),
				FlowPosTemplate.freeze(new FlowPos()));
	}

	private static BoxRecipe.Inline inlineRecipe() {
		return new BoxRecipe.Inline(InlineParamsTemplate.freeze(new InlineParams()),
				InlinePosTemplate.freeze(new InlinePos()));
	}

	public void testBeginBoxHoldsOnlyRecipeNotChildren() {
		final BoxRecipe recipe = flowRecipe();
		final SegmentEvent.BeginBox begin = new SegmentEvent.BeginBox(recipe);
		assertEquals(recipe, begin.recipe());
		assertEquals(BoxKind.FLOW, begin.recipe().kind());
	}

	public void testTextPreservesGeneratedContentOffsetConvention() {
		final SegmentEvent.Text generated = new SegmentEvent.Text(-1, "marker", false);
		final SegmentEvent.Text sourced = new SegmentEvent.Text(42, "abc", true);
		assertEquals(-1, generated.sourceOffset());
		assertEquals(42, sourced.sourceOffset());
		assertTrue(sourced.fixed());
		assertFalse(generated.fixed());
	}

	public void testBarrierAlwaysCarriesReason() {
		final SegmentEvent.Barrier withKind = new SegmentEvent.Barrier(java.util.Optional.of(BoxKind.TABLE),
				BarrierReason.NOT_YET_SUPPORTED);
		assertEquals(BoxKind.TABLE, withKind.kind().get());
		assertEquals(BarrierReason.NOT_YET_SUPPORTED, withKind.reason());

		// 旧Opaque/Replaced相当は種別情報を持たないため空
		final SegmentEvent.Barrier withoutKind = new SegmentEvent.Barrier(java.util.Optional.empty(),
				BarrierReason.NOT_YET_SUPPORTED);
		assertTrue(withoutKind.kind().isEmpty());
	}

	public void testReplacedHoldsRecipeNotLiveBox() {
		final ReplacedRecipe recipe = new ReplacedRecipe(ReplacedRecipe.GenerationKind.INLINE);
		final SegmentEvent.Replaced replaced = new SegmentEvent.Replaced(recipe);
		assertEquals(ReplacedRecipe.GenerationKind.INLINE, replaced.recipe().generationKind());
	}

	/** ContainerNodeはBoxRecipeと子範囲を分離して持つ(recipeに構造を混ぜない)。 */
	public void testContainerNodeSeparatesRecipeFromStructure() {
		final SegmentId id = SegmentId.create();
		final SegmentRange children = new SegmentRange(new SegmentCursor(id, 1), new SegmentCursor(id, 3));
		final BoxRecipe recipe = inlineRecipe();
		final ContainerNode node = new ContainerNode(recipe, children);

		assertSame(recipe, node.recipe());
		assertEquals(children, node.children());
		assertEquals(BoxKind.INLINE, node.recipe().kind());
	}

	/** 同値のイベントは記録場所によらず等価(record由来)。 */
	public void testValueEqualityAcrossConstructions() {
		assertEquals(new SegmentEvent.EndBox(), new SegmentEvent.EndBox());
		assertEquals(new BoxRecipe.Flow(null, null), new BoxRecipe.Flow(null, null));
		assertFalse(new BoxRecipe.Flow(null, null).equals(new BoxRecipe.Inline(null, null)));
	}

	/** BoxRecipeはBoxKindごとに異なるvariant(Flow/Inline)として表現される。 */
	public void testBoxRecipeVariantsCarryKindSpecificTemplates() {
		final BoxRecipe.Flow flow = flowRecipe();
		final BoxRecipe.Inline inline = inlineRecipe();
		assertEquals(BoxKind.FLOW, flow.kind());
		assertEquals(BoxKind.INLINE, inline.kind());
		assertNotNull(flow.params().materialize());
		assertNotNull(inline.params().materialize());
	}
}
