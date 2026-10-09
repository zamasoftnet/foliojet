package net.zamasoft.foliojet.layout.rescue;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Deque;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.TextShapeSink;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Unit tests for clipping, coordinates, and sliced decoration drawing in rescue-split fragments
 * ({@link VisualRescueBox}) (added 2026-07-25, increment 3).
 *
 * <p>
 * The source box is replaced by {@link FakeSource} (a test double that only records drawing arguments).
 * A fragment handles only two things: clip intersection and where to draw the source box from,
 * so this locks down its entire functionality. Slicing of borders and margins (no decoration at cut
 * edges, top decoration in the first fragment, bottom decoration in the last) is verified as a
 * geometric question: whether the source box's decoration bands fall inside or outside the clip.
 * </p>
 */
public class VisualRescueBoxTest extends TestCase {

	private static final double SOURCE_PAGE_EXTENT = 100;

	private static final double SOURCE_LINE_EXTENT = 60;

	/** The source box's top border band (an interval on the page axis). */
	private static final double TOP_DECORATION_END = 5;

	/** The source box's bottom border band (an interval on the page axis). */
	private static final double BOTTOM_DECORATION_START = 95;

	// ------------------------------------------------------------------
	// Test doubles.
	// ------------------------------------------------------------------

	/** A source box that only records drawing, outline, and text-extraction calls. */
	private static class FakeSource extends AbstractBox implements IFlowBox {
		private final BlockParams params = new BlockParams();
		private final Pos pos = new FlowPos();
		private final double width, height;

		int drawCount = 0;
		Shape drawClip = null;
		double drawX = Double.NaN, drawY = Double.NaN;
		int textShapeCount = 0;
		double textShapeX = Double.NaN, textShapeY = Double.NaN;

		boolean avoidBefore = false, avoidAfter = false;

		FakeSource(final double width, final double height) {
			this.width = width;
			this.height = height;
		}

		public BoxType getType() {
			return BoxType.REPLACED;
		}

		public Params getParams() {
			return this.params;
		}

		public Pos getPos() {
			return this.pos;
		}

		public double getWidth() {
			return this.width;
		}

		public double getHeight() {
			return this.height;
		}

		public double getInnerWidth() {
			return this.width;
		}

		public double getInnerHeight() {
			return this.height;
		}

		public void finishLayoutSelf(final IFramedBox containerBox) {
			TestCase.fail("断片は元ボックスをfinishLayoutし直さない");
		}

		public void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
			TestCase.fail("断片は元ボックスをfinishLayoutし直さない");
		}

		public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
				final AffineTransform transform, final double contextX, final double contextY, final double x,
				final double y, final Deque<DrawStep> worklist) {
			++this.drawCount;
			this.drawClip = clip;
			this.drawX = x;
			this.drawY = y;
			this.drawArtifact = drawer != null && drawer.isArtifact();
			this.drawVisitor = visitor;
		}

		public void pushGetTextSteps(final StringBuilder textBuff, final Deque<GetTextStep> worklist) {
			textBuff.append("SOURCE");
		}

		boolean drawArtifact;

		Visitor drawVisitor;

		public void pushTextShapeSteps(final PageBox pageBox, final TextShapeSink sink, final AffineTransform transform,
				final double x, final double y, final Deque<TextShapeStep> worklist) {
			++this.textShapeCount;
			this.textShapeX = x;
			this.textShapeY = y;
		}

		public boolean avoidBreakBefore() {
			return this.avoidBefore;
		}

		public boolean avoidBreakAfter() {
			return this.avoidAfter;
		}
	}

	/** A source box for floats. */
	private static final class FakeFloatSource extends FakeSource implements IFloatBox {
		private final FloatPos floatPos = new FloatPos();

		FakeFloatSource(final double width, final double height) {
			super(width, height);
		}

		public FloatPos getFloatPos() {
			return this.floatPos;
		}

		public Pos getPos() {
			return this.floatPos;
		}
	}

	private static FakeSource source(final WritingMode progression) {
		return progression.isVertical() ? new FakeSource(SOURCE_PAGE_EXTENT, SOURCE_LINE_EXTENT)
				: new FakeSource(SOURCE_LINE_EXTENT, SOURCE_PAGE_EXTENT);
	}

	private static VisualRescueFlowBox fragment(final FakeSource src, final WritingMode progression,
			final double offset, final double sliceExtent) {
		return new VisualRescueFlowBox(src, progression, SOURCE_PAGE_EXTENT, offset, sliceExtent);
	}

	/**
	 * Returns the physical interval (Y for horizontal writing, X for vertical writing) occupied by the
	 * source box's page-axis interval {@code [from, to)} at its actual drawing position.
	 */
	private static double[] physicalBand(final VisualRescueBox box, final double sourceX, final double sourceY,
			final double from, final double to) {
		return switch (box.getProgression()) {
		// For a positive page-axis direction (TB/LR), add the start directly.
		case TB -> new double[] { sourceY + from, sourceY + to };
		case LR -> new double[] { sourceX + from, sourceX + to };
		// Only RL has a negative direction (right to left).
		case RL -> {
			final double right = sourceX + box.getSourcePageExtent();
			yield new double[] { right - to, right - from };
		}
		};
	}

	/** Returns the clip rectangle's page-axis interval. */
	private static double[] clipBand(final VisualRescueBox box, final Rectangle2D clip) {
		return box.getProgression().isVertical() ? new double[] { clip.getMinX(), clip.getMaxX() }
				: new double[] { clip.getMinY(), clip.getMaxY() };
	}

	private static boolean contains(final double[] outer, final double[] inner) {
		return outer[0] <= inner[0] && inner[1] <= outer[1];
	}

	private static boolean disjoint(final double[] a, final double[] b) {
		return a[1] <= b[0] || b[1] <= a[0];
	}

	// ------------------------------------------------------------------
	// Dimensions.
	// ------------------------------------------------------------------

	/** A fragment changes only the occupied page-axis extent. The line axis stays the same as the source box. */
	public void testOnlyPageExtentDiffersFromSource() {
		for (final WritingMode progression : WritingMode.values()) {
			final FakeSource src = source(progression);
			final VisualRescueBox box = fragment(src, progression, 20, 30);
			assertEquals(progression.name(), SOURCE_LINE_EXTENT, box.getLineExtent(progression), 0);
			assertEquals(progression.name(), 30.0, box.getPageExtent(progression), 0);
			assertEquals(progression.name(), SOURCE_LINE_EXTENT, src.getLineExtent(progression), 0);
			assertEquals(progression.name(), SOURCE_PAGE_EXTENT, src.getPageExtent(progression), 0);
		}
	}

	/** A fragment identifies itself as an independent type (it does not impersonate an existing type). */
	public void testTypeIsRescue() {
		final FakeSource src = source(WritingMode.TB);
		assertEquals(BoxType.RESCUE, fragment(src, WritingMode.TB, 0, 40).getType());
		assertEquals(BoxType.REPLACED, src.getType());
	}

	/** Returns the source box's Params and Pos as-is (without copying or modifying them). */
	public void testParamsAndPosAreShared() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 0, 40);
		assertSame(src.getParams(), box.getParams());
		assertSame(src.getPos(), box.getPos());
	}

	/** Rescue fragments are excluded from recipe replay (they have no SourceAnchor). */
	public void testSourceAnchorStaysUnset() {
		assertEquals(-1L, fragment(source(WritingMode.TB), WritingMode.TB, 0, 40).getSourceAnchor());
	}

	/** First/last fragment detection. */
	public void testFirstAndLastFragmentFlags() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox head = fragment(src, WritingMode.TB, 0, 40);
		final VisualRescueBox middle = fragment(src, WritingMode.TB, 40, 40);
		final VisualRescueBox tail = fragment(src, WritingMode.TB, 80, 20);
		assertTrue(head.isFirstFragment());
		assertFalse(head.isLastFragment());
		assertFalse(head.isContinuation());
		assertFalse(middle.isFirstFragment());
		assertFalse(middle.isLastFragment());
		assertTrue(middle.isContinuation());
		assertFalse(tail.isFirstFragment());
		assertTrue(tail.isLastFragment());
		assertTrue(tail.isContinuation());
	}

	// ------------------------------------------------------------------
	// Coordinates (TB / RL / LR).
	// ------------------------------------------------------------------

	/** Horizontal writing: sourceY = fragmentY - offset; X stays unchanged. */
	public void testHorizontalCoordinates() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 17, 200);
		assertEquals(1, src.drawCount);
		assertEquals(17.0, src.drawX, 0);
		assertEquals(200.0 - 40.0, src.drawY, 0);
	}

	/**
	 * Vertical writing: sourceX = fragmentX - (sourcePageExtent - offset - sliceExtent);
	 * Y stays unchanged. RL and LR use the same internal convention (page axis runs right to left).
	 */
	public void testVerticalCoordinates() {
		for (final WritingMode progression : new WritingMode[] { WritingMode.RL, WritingMode.LR }) {
			final FakeSource src = source(progression);
			final VisualRescueBox box = fragment(src, progression, 40, 30);
			box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 300, 17);
			assertEquals(progression.name(), 1, src.drawCount);
			// RL has a negative direction, so shift left by the unconsumed remainder = 100 - 40 - 30 = 30;
			// LR has a positive direction, so shift left by the consumed extent = offset = 40.
			final double expectedX = progression == WritingMode.RL ? 300.0 - 30.0 : 300.0 - 40.0;
			assertEquals(progression.name(), expectedX, src.drawX, 0);
			assertEquals(progression.name(), 17.0, src.drawY, 0);
		}
	}

	/** The first fragment is drawn from the same position as the source box (no displacement). */
	public void testFirstFragmentDrawsSourceAtTheFragmentOrigin() {
		for (final WritingMode progression : WritingMode.values()) {
			final FakeSource src = source(progression);
			// First fragment (offset=0, with a remainder still present).
			final VisualRescueBox box = fragment(src, progression, 0, 40);
			box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 50, 60);
			// For a positive direction (TB/LR), the first fragment at offset=0 starts at the fragment origin itself.
			// Only RL has a negative direction, so shift left by the unconsumed remainder = 100 - 0 - 40 = 60.
			assertEquals(progression.name(), progression == WritingMode.RL ? 50.0 - 60.0 : 50.0, src.drawX, 0);
			assertEquals(progression.name(), 60.0, src.drawY, 0);
		}
	}

	/** The last fragment is drawn from a position where the source box's end matches the fragment's end. */
	public void testLastFragmentAlignsTheSourceEnd() {
		for (final WritingMode progression : WritingMode.values()) {
			final FakeSource src = source(progression);
			final VisualRescueBox box = fragment(src, progression, 80, 20);
			box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 50, 60);
			switch (progression) {
			case TB -> assertEquals(progression.name(), 60.0 - 80.0, src.drawY, 0);
			// RL: no remainder, so the source box's left edge matches the fragment's left edge.
			case RL -> assertEquals(progression.name(), 50.0, src.drawX, 0);
			// LR: shift left by the consumed extent so the source box's right edge matches the fragment's right edge.
			// (Starts at 50 - 80 = -30, and -30 + 100 = 70 = 50 + 20.)
			case LR -> assertEquals(progression.name(), 50.0 - 80.0, src.drawX, 0);
			}
		}
	}

	// ------------------------------------------------------------------
	// Artifact marking (2026-07-25, increment 5).
	// ------------------------------------------------------------------

	/**
	 * Draw the first fragment as real content (real Visitor, non-artifact).
	 */
	public void testFirstFragmentDrawsAsRealContent() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 0, 30);
		final Visitor visitor = new net.zamasoft.foliojet.layout.visitor.VisitorWrapper(null);
		box.draw(null, new Drawer(0), visitor, null, new AffineTransform(), 0, 0, 0, 0);
		assertFalse("先頭断片はartifactではない", src.drawArtifact);
		assertSame("先頭断片は実Visitorで描く", visitor, src.drawVisitor);
	}

	/**
	 * Draw continuation fragments ({@code offset > 0}) as artifacts, passing a Visitor with no side effects
	 * (recommendation §3: do not emit links, forms, page references, string-set, or bookmarks twice).
	 */
	public void testContinuationFragmentDrawsAsArtifact() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		final Visitor visitor = new net.zamasoft.foliojet.layout.visitor.VisitorWrapper(null);
		box.draw(null, new Drawer(0), visitor, null, new AffineTransform(), 0, 0, 0, 0);
		assertTrue("継続断片はartifact", src.drawArtifact);
		assertSame("継続断片は副作用のないVisitorで描く",
				net.zamasoft.foliojet.layout.visitor.ArtifactVisitor.INSTANCE, src.drawVisitor);
	}

	// ------------------------------------------------------------------
	// Clipping.
	// ------------------------------------------------------------------

	/** With no existing clip, use the fragment rectangle itself. */
	public void testClipWithoutExistingClipIsTheFragmentRect() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 10, 200);
		final Rectangle2D clip = (Rectangle2D) src.drawClip;
		assertEquals(10.0, clip.getX(), 0);
		assertEquals(200.0, clip.getY(), 0);
		assertEquals(SOURCE_LINE_EXTENT, clip.getWidth(), 0);
		assertEquals(30.0, clip.getHeight(), 0);
	}

	/** Intersect with an existing clip (the same approach as AbstractContainerBox.clip()). */
	public void testClipIntersectsTheExistingClip() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		// The fragment is (10,200)-(70,230). The existing clip trims the left, right, and bottom.
		final Rectangle2D.Double outer = new Rectangle2D.Double(30, 100, 100, 120);
		box.draw(null, new Drawer(0), null, outer, new AffineTransform(), 0, 0, 10, 200);
		final Rectangle2D clip = (Rectangle2D) src.drawClip;
		final Rectangle2D expected = new Rectangle2D.Double(10, 200, SOURCE_LINE_EXTENT, 30)
				.createIntersection(outer);
		assertEquals(expected.getX(), clip.getX(), 0);
		assertEquals(expected.getY(), clip.getY(), 0);
		assertEquals(expected.getWidth(), clip.getWidth(), 0);
		assertEquals(expected.getHeight(), clip.getHeight(), 0);
		assertEquals(30.0, clip.getX(), 0);
		assertEquals(200.0, clip.getY(), 0);
		assertEquals(40.0, clip.getWidth(), 0);
		assertEquals(20.0, clip.getHeight(), 0);
	}

	/** In vertical writing too, the clip is the fragment's physical rectangle (width is sliceExtent). */
	public void testVerticalClipIsTheFragmentRect() {
		final FakeSource src = source(WritingMode.RL);
		final VisualRescueBox box = fragment(src, WritingMode.RL, 40, 30);
		box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 300, 17);
		final Rectangle2D clip = (Rectangle2D) src.drawClip;
		assertEquals(300.0, clip.getX(), 0);
		assertEquals(17.0, clip.getY(), 0);
		assertEquals(30.0, clip.getWidth(), 0);
		assertEquals(SOURCE_LINE_EXTENT, clip.getHeight(), 0);
	}

	/** A clip shape that cannot be intersected throws ClassCastException, as in the existing implementation. */
	public void testNonRectangularClipIsRejectedLikeTheExistingClipConvention() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 0, 30);
		try {
			box.clip(new Ellipse2D.Double(0, 0, 10, 10), 0, 0);
			fail("矩形以外のクリップはAbstractContainerBox.clip()と同様に扱えない");
		} catch (final ClassCastException expected) {
			// As expected (follows the existing approach).
		}
	}

	// ------------------------------------------------------------------
	// Slicing borders and margins.
	// ------------------------------------------------------------------

	/**
	 * Only the first fragment includes top decoration, only the last includes bottom decoration,
	 * and intermediate fragments include neither (CSS box-decoration-break: slice).
	 * No new lines appear at cut edges: a fragment only draws the original geometry as-is and clips it.
	 */
	public void testDecorationIsSlicedAcrossFragments() {
		for (final WritingMode progression : WritingMode.values()) {
			final double[][] intervals = { { 0, 40 }, { 40, 40 }, { 80, 20 } };
			for (int i = 0; i < intervals.length; ++i) {
				final FakeSource src = source(progression);
				final VisualRescueBox box = fragment(src, progression, intervals[i][0], intervals[i][1]);
				box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 500, 400);
				final double[] clip = clipBand(box, (Rectangle2D) src.drawClip);
				final double[] top = physicalBand(box, src.drawX, src.drawY, 0, TOP_DECORATION_END);
				final double[] bottom = physicalBand(box, src.drawX, src.drawY, BOTTOM_DECORATION_START,
						SOURCE_PAGE_EXTENT);
				final String at = progression + " fragment#" + i;
				if (i == 0) {
					assertTrue(at + ": 先頭断片は上枠線を含む", contains(clip, top));
					assertTrue(at + ": 先頭断片は下枠線を含まない", disjoint(clip, bottom));
				} else if (i == intervals.length - 1) {
					assertTrue(at + ": 最終断片は上枠線を含まない", disjoint(clip, top));
					assertTrue(at + ": 最終断片は下枠線を含む", contains(clip, bottom));
				} else {
					assertTrue(at + ": 中間断片は上枠線を含まない", disjoint(clip, top));
					assertTrue(at + ": 中間断片は下枠線を含まない", disjoint(clip, bottom));
				}
			}
		}
	}

	/** Arranged fragments exactly cover the source box's page axis (no overlaps or gaps). */
	public void testFragmentsTileTheSourceExactly() {
		for (final WritingMode progression : WritingMode.values()) {
			final double[][] intervals = { { 0, 40 }, { 40, 40 }, { 80, 20 } };
			double covered = 0;
			for (final double[] interval : intervals) {
				final FakeSource src = source(progression);
				final VisualRescueBox box = fragment(src, progression, interval[0], interval[1]);
				// Fragments are placed consecutively along the page axis (only RL has a negative direction).
				final double fragmentX = switch (progression) {
				case TB -> 500;
				case RL -> 500 - covered - interval[1];
				case LR -> 500 + covered;
				};
				final double fragmentY = progression.isVertical() ? 400 : 400 + covered;
				box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, fragmentX, fragmentY);
				// Every fragment places the source box at the same position (= visual continuity).
				switch (progression) {
				case TB -> assertEquals(progression.name(), 400.0, src.drawY, 0);
				case RL -> assertEquals(progression.name(), 500 - SOURCE_PAGE_EXTENT, src.drawX, 0);
				case LR -> assertEquals(progression.name(), 500.0, src.drawX, 0);
				}
				covered += interval[1];
			}
			assertEquals(SOURCE_PAGE_EXTENT, covered, 0);
		}
	}

	// ------------------------------------------------------------------
	// Semantics.
	// ------------------------------------------------------------------

	/** Only the first fragment returns text, once (prevents duplicate extraction and speech output). */
	public void testOnlyTheFirstFragmentYieldsText() {
		final FakeSource src = source(WritingMode.TB);
		final StringBuilder head = new StringBuilder();
		fragment(src, WritingMode.TB, 0, 40).getText(head);
		assertEquals("SOURCE", head.toString());

		final StringBuilder middle = new StringBuilder();
		fragment(src, WritingMode.TB, 40, 40).getText(middle);
		assertEquals("", middle.toString());

		final StringBuilder tail = new StringBuilder();
		fragment(src, WritingMode.TB, 80, 20).getText(tail);
		assertEquals("", tail.toString());
	}

	/** Outlines are visual, so all fragments delegate (shifting only the coordinates). */
	public void testTextShapeIsDelegatedWithShiftedOrigin() {
		final FakeSource src = source(WritingMode.TB);
		fragment(src, WritingMode.TB, 40, 30).textShapeQuiet(null, new GeneralPath(), new AffineTransform(), 10, 200);
		assertEquals(1, src.textShapeCount);
		assertEquals(10.0, src.textShapeX, 0);
		assertEquals(160.0, src.textShapeY, 0);
	}

	/** A fragment does not lay out the source box again. */
	public void testFinishLayoutDoesNotTouchTheSource() {
		final FakeSource src = source(WritingMode.TB);
		// FakeSource.finishLayout calls fail(), so the test fails if it is called.
		fragment(src, WritingMode.TB, 40, 30).finishLayout(null);
	}

	// ------------------------------------------------------------------
	// Adapters.
	// ------------------------------------------------------------------

	/** Only edge fragments inherit the source box's page-break prohibitions. */
	public void testAvoidBreakOnlyAppliesToTheOuterEdges() {
		final FakeSource src = source(WritingMode.TB);
		src.avoidBefore = true;
		src.avoidAfter = true;
		final VisualRescueFlowBox head = fragment(src, WritingMode.TB, 0, 40);
		final VisualRescueFlowBox middle = fragment(src, WritingMode.TB, 40, 40);
		final VisualRescueFlowBox tail = fragment(src, WritingMode.TB, 80, 20);
		assertTrue(head.avoidBreakBefore());
		assertFalse(head.avoidBreakAfter());
		assertFalse(middle.avoidBreakBefore());
		assertFalse(middle.avoidBreakAfter());
		assertFalse(tail.avoidBreakBefore());
		assertTrue(tail.avoidBreakAfter());
	}

	/** The float adapter returns positioning parameters as-is. */
	public void testFloatAdapterSharesTheFloatPos() {
		final FakeFloatSource src = new FakeFloatSource(SOURCE_LINE_EXTENT, SOURCE_PAGE_EXTENT);
		final VisualRescueFloatBox box = new VisualRescueFloatBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, 40, 30);
		assertSame(src.getFloatPos(), box.getFloatPos());
		assertEquals(BoxType.RESCUE, box.getType());
	}

	/** The factory selects an adapter according to the source box type. */
	public void testFactorySelectsTheAdapter() {
		final RescueDecision.Slice slice = new RescueDecision.Slice(40, 30, 70, false, false);
		final VisualRescueBox flow = VisualRescueBox.of(source(WritingMode.TB), WritingMode.TB, SOURCE_PAGE_EXTENT,
				slice);
		assertTrue(flow instanceof VisualRescueFlowBox);
		final VisualRescueBox floating = VisualRescueBox.of(
				new FakeFloatSource(SOURCE_LINE_EXTENT, SOURCE_PAGE_EXTENT), WritingMode.TB, SOURCE_PAGE_EXTENT,
				slice);
		assertTrue(floating instanceof VisualRescueFloatBox);
	}

	// ------------------------------------------------------------------
	// Invariants.
	// ------------------------------------------------------------------

	/** Do not create nested fragments (represent intervals only with offset/sliceExtent). */
	public void testNestedFragmentsAreRejected() {
		final VisualRescueBox box = fragment(source(WritingMode.TB), WritingMode.TB, 0, 40);
		try {
			new VisualRescueBox(box, WritingMode.TB, 40, 0, 20);
			fail("断片の断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
	}

	/** Cannot create a fragment that extends beyond the source box. */
	public void testFragmentOutsideTheSourceIsRejected() {
		final FakeSource src = source(WritingMode.TB);
		try {
			new VisualRescueBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, 80, 40);
			fail("元ボックスをはみ出す断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
	}

	/** Cannot create a nonpositive fragment or a negative offset. */
	public void testDegenerateIntervalsAreRejected() {
		final FakeSource src = source(WritingMode.TB);
		try {
			new VisualRescueBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, 0, 0);
			fail("寸法0の断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
		try {
			new VisualRescueBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, -1, 10);
			fail("負のoffsetは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
	}
}
