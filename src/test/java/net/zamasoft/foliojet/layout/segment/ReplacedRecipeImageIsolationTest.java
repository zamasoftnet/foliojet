package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Added in E-6 increment 3b-3 and fully rewritten in 3b-6: unit tests that duplication-based freezing
 * of replaced elements referencing {@link ReplacedBoxImage} (an unshareable image with a back-reference),
 * through {@code ReplacedParamsTemplate} and {@link ReplacedRecipe#freeze}, isolates image state between
 * live and replay instances and between replay instances.
 *
 * <p>
 * The only current production implementation ({@code BarcodeImage}) has a no-op {@code setReplacedBox},
 * so a lack of isolation is invisible to golden comparisons (a latent defect). A stub that actually
 * stores the back-reference locks down the very structure that allows calculateSize on a replay box
 * to corrupt the live image state.
 * </p>
 */
public class ReplacedRecipeImageIsolationTest extends TestCase {

	/** A stub that actually stores the back-reference (unlike BarcodeImage's no-op). */
	private static final class RecordingReplacedBoxImage implements Image, ReplacedBoxImage {
		AbstractReplacedBox registeredBox;

		public double getWidth() {
			return 10;
		}

		public double getHeight() {
			return 5;
		}

		public void drawTo(GC gc) {
		}

		public String getAltString() {
			return null;
		}

		public void setReplacedBox(AbstractReplacedBox box, double width, double height) {
			this.registeredBox = box;
		}

		public Image duplicate() {
			return new RecordingReplacedBoxImage();
		}
	}

	/**
	 * A replaced element referencing {@link ReplacedBoxImage} freezes a duplicate image, then supplies
	 * another duplicate on each materialization. Thus calculateSize on replay boxes (scratch measurement
	 * or multiple replay boxes) neither writes a back-reference into the live image nor takes it over,
	 * and replay instances do not contend with each other either.
	 */
	public void testMaterializedReplayBoxesDoNotStealBackReference() {
		final RecordingReplacedBoxImage liveImage = new RecordingReplacedBoxImage();
		final ReplacedParams params = new ReplacedParams();
		params.image = liveImage;
		final InlineReplacedBox live = new InlineReplacedBox(params, new InlinePos());

		// Live layout (calculateSize) registers the back-reference.
		live.calculateSize(100, 100, 100, 100);
		assertSame(live, liveImage.registeredBox);

		// Recording-time freeze (3b-6: a total function even for ReplacedBoxImage).
		final ReplacedRecipe recipe = ReplacedRecipe.freeze(live).orElseThrow();
		assertTrue(recipe instanceof ReplacedRecipe.Inline);

		// materialize×2 → fresh replay boxes independent of each other.
		final AbstractReplacedBox replay1 = BoxRecipeBoxFactory.createReplaced(recipe);
		final AbstractReplacedBox replay2 = BoxRecipeBoxFactory.createReplaced(recipe);
		assertNotSame(live, replay1);
		assertNotSame(params, replay1.getReplacedParams());
		assertNotSame(liveImage, replay1.getReplacedParams().image);
		assertNotSame(replay1.getReplacedParams().image, replay2.getReplacedParams().image);
		assertTrue(replay1.getReplacedParams().image instanceof ReplacedBoxImage);
		assertSame(liveImage, params.image);

		// Replay-side calculateSize (equivalent to scratch measurement) registers with each instance's own copy,
		// leaving the live back-reference untouched: this is the latent defect being corrected.
		// Multiple replays (up to 20 probe attempts) do not contend either.
		replay1.calculateSize(100, 100, 100, 100);
		replay2.calculateSize(100, 100, 100, 100);
		assertSame(live, liveImage.registeredBox);
		assertSame(replay1, ((RecordingReplacedBoxImage) replay1.getReplacedParams().image).registeredBox);
		assertSame(replay2, ((RecordingReplacedBoxImage) replay2.getReplacedParams().image).registeredBox);

		// Replay uses params with equivalent values (isolation does not change content).
		assertEquals(params.lineHeight, replay1.getReplacedParams().lineHeight);
		assertEquals(params.size, replay1.getReplacedParams().size);
	}

	/** An ordinary (shareable) image remains shared as before (params are fresh). */
	public void testPlainImageKeepsSharedImage() {
		final ReplacedParams params = new ReplacedParams();
		params.image = new Image() {
			public double getWidth() {
				return 10;
			}

			public double getHeight() {
				return 5;
			}

			public void drawTo(GC gc) {
			}

			public String getAltString() {
				return null;
			}
		};
		final InlineReplacedBox live = new InlineReplacedBox(params, new InlinePos());

		final ReplacedRecipe recipe = ReplacedRecipe.freeze(live).orElseThrow();
		final AbstractReplacedBox replay = BoxRecipeBoxFactory.createReplaced(recipe);
		assertNotSame(live, replay);
		assertNotSame(params, replay.getReplacedParams());
		assertSame(params.image, replay.getReplacedParams().image);
	}

	/** Do not resolve a percentage min-width against an indefinite containing width to the sentinel's numeric size. */
	public void testCyclicPercentageMinimumFallsBackToZero() {
		final ReplacedParams params = new ReplacedParams();
		params.image = new RecordingReplacedBoxImage();
		params.size = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.AUTO);
		params.minSize = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.ABSOLUTE);
		params.maxSize = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.AUTO);
		final InlineReplacedBox box = new InlineReplacedBox(params, new InlinePos());

		box.calculateSize(LayoutUtils.NONE, LayoutUtils.NONE, LayoutUtils.NONE, LayoutUtils.NONE);

		assertEquals(10.0, box.getInnerWidth(), 0);
		assertEquals(5.0, box.getInnerHeight(), 0);
		assertTrue(LayoutUtils.isDrawable(box.getWidth()));
		assertTrue(LayoutUtils.isDrawable(box.getHeight()));
	}
}
