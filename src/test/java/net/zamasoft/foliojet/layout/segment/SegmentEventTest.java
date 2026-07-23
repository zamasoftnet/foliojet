package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

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
		final ReplacedRecipe.Inline recipe = new ReplacedRecipe.Inline(
				ReplacedParamsTemplate.freeze(new ReplacedParams()).orElseThrow(),
				InlinePosTemplate.freeze(new InlinePos()));
		final SegmentEvent.Replaced replaced = new SegmentEvent.Replaced(recipe);
		assertEquals(ReplacedRecipe.GenerationKind.INLINE, replaced.recipe().generationKind());
	}

	/** {@code ReplacedRecipe}は生成種別ごとに異なるvariant(Pos型が違う)として表現される。 */
	public void testReplacedRecipeVariantsCarryKindSpecificPosTemplates() {
		final ReplacedParamsTemplate params = ReplacedParamsTemplate.freeze(new ReplacedParams()).orElseThrow();
		final ReplacedRecipe.Flow flow = new ReplacedRecipe.Flow(params, FlowPosTemplate.freeze(new FlowPos()));
		final ReplacedRecipe.Float floating = new ReplacedRecipe.Float(params,
				FloatPosTemplate.freeze(new net.zamasoft.foliojet.layout.box.params.FloatPos()));
		final ReplacedRecipe.Absolute absolute = new ReplacedRecipe.Absolute(params,
				AbsolutePosTemplate.freeze(new net.zamasoft.foliojet.layout.box.params.AbsolutePos()));
		assertEquals(ReplacedRecipe.GenerationKind.FLOW, flow.generationKind());
		assertEquals(ReplacedRecipe.GenerationKind.FLOAT, floating.generationKind());
		assertEquals(ReplacedRecipe.GenerationKind.ABSOLUTE, absolute.generationKind());
		assertNotNull(flow.pos().materialize());
		assertNotNull(floating.pos().materialize());
		assertNotNull(absolute.pos().materialize());
	}

	/**
	 * {@link ReplacedBoxImage}実装({@code BarcodeImage}等、live boxへの
	 * back-referenceを自身に書き込む)を持つ{@code ReplacedParams}は
	 * 共有不可なため、{@code freeze()}がfail closedで{@code
	 * Optional.empty()}を返す。
	 */
	public void testReplacedParamsTemplateFailsClosedForReplacedBoxImage() {
		final ReplacedParams params = new ReplacedParams();
		params.image = new StubReplacedBoxImage();
		assertTrue(ReplacedParamsTemplate.freeze(params).isEmpty());
	}

	/** {@link Image}かつ{@link ReplacedBoxImage}を両方実装する最小のテスト用スタブ。 */
	private static final class StubReplacedBoxImage implements Image, ReplacedBoxImage {
		public double getWidth() {
			return 0;
		}

		public double getHeight() {
			return 0;
		}

		public void drawTo(GC gc) {
		}

		public String getAltString() {
			return null;
		}

		public void setReplacedBox(AbstractReplacedBox box, double width, double height) {
		}
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
