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
 * Unit tests that lock down the type contracts for M6d-A3a (added 2026-07-22;
 * {@link BoxRecipe} was extended to a sealed interface after A3b). Not wired in yet
 * (conversion adapters for the existing {@code LayoutSource}/{@code SourceReplayer} come in M6d-A3c
 * and later). At this stage, only check the types' own structure (separation of recipe and structure,
 * mandatory reasons for Barriers, etc.).
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
		final SegmentEvent.Barrier withKind = new SegmentEvent.Barrier(java.util.Optional.of(BoxKind.TABLE_ROW),
				BarrierReason.NOT_YET_SUPPORTED);
		assertEquals(BoxKind.TABLE_ROW, withKind.kind().get());
		assertEquals(BarrierReason.NOT_YET_SUPPORTED, withKind.reason());

		// Empty because the equivalents of the old Opaque/Replaced have no kind information.
		final SegmentEvent.Barrier withoutKind = new SegmentEvent.Barrier(java.util.Optional.empty(),
				BarrierReason.NOT_YET_SUPPORTED);
		assertTrue(withoutKind.kind().isEmpty());
	}

	public void testReplacedHoldsRecipeNotLiveBox() {
		final ReplacedRecipe.Inline recipe = new ReplacedRecipe.Inline(
				ReplacedParamsTemplate.freeze(new ReplacedParams()),
				InlinePosTemplate.freeze(new InlinePos()));
		final SegmentEvent.Replaced replaced = new SegmentEvent.Replaced(recipe);
		assertEquals(ReplacedRecipe.GenerationKind.INLINE, replaced.recipe().generationKind());
	}

	/** {@code ReplacedRecipe} uses a distinct variant for each creation kind (different Pos types). */
	public void testReplacedRecipeVariantsCarryKindSpecificPosTemplates() {
		final ReplacedParamsTemplate params = ReplacedParamsTemplate.freeze(new ReplacedParams());
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
	 * A {@link ReplacedBoxImage} implementation (such as {@code BarcodeImage}, which writes a
	 * back-reference to the live box into itself) in {@code ReplacedParams} gets an independent copy
	 * stored on freeze, thanks to E-6 increment 3b-6 making freeze a total function through duplication.
	 * Another copy is supplied on each materialization (shared with neither the live instance
	 * nor other materializations).
	 */
	public void testReplacedParamsTemplateDuplicatesReplacedBoxImage() {
		final ReplacedParams params = new ReplacedParams();
		final StubReplacedBoxImage liveImage = new StubReplacedBoxImage();
		params.image = liveImage;
		final ReplacedParamsTemplate template = ReplacedParamsTemplate.freeze(params);
		final ReplacedParams m1 = template.materialize();
		final ReplacedParams m2 = template.materialize();
		assertNotSame(liveImage, m1.image);
		assertNotSame(liveImage, m2.image);
		assertNotSame(m1.image, m2.image);
		assertTrue(m1.image instanceof ReplacedBoxImage);
		assertSame(liveImage, params.image);
	}

	/** A minimal test stub implementing both {@link Image} and {@link ReplacedBoxImage}. */
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

		public Image duplicate() {
			return new StubReplacedBoxImage();
		}
	}

	/** Events with equal values are equal regardless of where they were recorded (inherited from record). */
	public void testValueEqualityAcrossConstructions() {
		assertEquals(new SegmentEvent.EndBox(), new SegmentEvent.EndBox());
		assertEquals(new BoxRecipe.Flow(null, null), new BoxRecipe.Flow(null, null));
		assertFalse(new BoxRecipe.Flow(null, null).equals(new BoxRecipe.Inline(null, null)));
	}

	/** BoxRecipe uses a distinct variant (Flow/Inline) for each BoxKind. */
	public void testBoxRecipeVariantsCarryKindSpecificTemplates() {
		final BoxRecipe.Flow flow = flowRecipe();
		final BoxRecipe.Inline inline = inlineRecipe();
		assertEquals(BoxKind.FLOW, flow.kind());
		assertEquals(BoxKind.INLINE, inline.kind());
		assertNotNull(flow.params().materialize());
		assertNotNull(inline.params().materialize());
	}
}
