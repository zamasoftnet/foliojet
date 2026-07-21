package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;

/**
 * M6d-A3a(2026-07-22新設)の型契約を固定する単体テストです。
 * まだ未配線(既存の{@code LayoutSource}/{@code SourceReplayer}への
 * 変換アダプタはM6d-A3c以降)——この段階では型自身の構造(recipeと
 * 構造の分離、Barrierが理由を必須で持つこと等)だけを確認する。
 */
public class SegmentEventTest extends TestCase {
	public void testBeginBoxHoldsOnlyRecipeNotChildren() {
		final BoxRecipe recipe = new BoxRecipe(BoxKind.FLOW);
		final SegmentEvent.BeginBox begin = new SegmentEvent.BeginBox(recipe);
		assertEquals(recipe, begin.recipe());
	}

	public void testTextPreservesGeneratedContentOffsetConvention() {
		final SegmentEvent.Text generated = new SegmentEvent.Text(-1, "marker", false);
		final SegmentEvent.Text sourced = new SegmentEvent.Text(42, "abc", true);
		assertEquals(-1, generated.sourceOffset());
		assertEquals(42, sourced.sourceOffset());
		assertTrue(sourced.fixed());
		assertFalse(generated.fixed());
	}

	public void testBarrierAlwaysCarriesReasonAndKind() {
		final SegmentEvent.Barrier barrier = new SegmentEvent.Barrier(BoxKind.TABLE, BarrierReason.NOT_YET_SUPPORTED);
		assertEquals(BoxKind.TABLE, barrier.kind());
		assertEquals(BarrierReason.NOT_YET_SUPPORTED, barrier.reason());
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
		final BoxRecipe recipe = new BoxRecipe(BoxKind.INLINE);
		final ContainerNode node = new ContainerNode(recipe, children);

		assertEquals(recipe, node.recipe());
		assertEquals(children, node.children());
		// recipe自体は構造(children)の情報を一切持たない
		assertEquals(new BoxRecipe(BoxKind.INLINE), node.recipe());
	}

	/** 同値のrecipe/eventは記録場所によらず等価(record由来)。 */
	public void testValueEqualityAcrossConstructions() {
		assertEquals(new SegmentEvent.EndBox(), new SegmentEvent.EndBox());
		assertEquals(new BoxRecipe(BoxKind.MULTICOL), new BoxRecipe(BoxKind.MULTICOL));
		assertFalse(new BoxRecipe(BoxKind.MULTICOL).equals(new BoxRecipe(BoxKind.FLOW)));
	}
}
