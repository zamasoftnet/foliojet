package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * M6d-A3c (added 2026-07-22): unit tests for the adapter that converts {@code LayoutSource.Event}
 * to {@link SegmentEvent}. Actual wiring into {@code LayoutSource} itself and {@code SourceReplayer}
 * (calls from the production path) is not done yet.
 */
public class LayoutSourceEventConverterTest extends TestCase {
	/**
	 * E-6 increment 3b-4: Start now holds a recipe frozen at recording time.
	 * {@code BoxRecipe.freeze} handles the freeze mapping from kind to variant/template (formerly
	 * conversion-time freezing in convertStart), so this test constructs Start through the same path
	 * as recording to verify conversion.
	 */
	private static LayoutSource.Start start(final LayoutSource.BoxKind kind,
			final net.zamasoft.foliojet.layout.box.params.Params params,
			final net.zamasoft.foliojet.layout.box.params.Pos pos) {
		return new LayoutSource.Start(BoxRecipe.freeze(kind, params, pos));
	}

	/**
	 * Event counts are one-to-one (preserve ordinal correspondence, consistent with M6d-A2).
	 * E-6 increment 3b-5: the full-List convert overload was removed; convert each event of the streaming
	 * view on the fly with the single-event overload (one event = one SegmentEvent).
	 */
	public void testEventCountIsPreserved1to1() {
		final LayoutSource log = new LayoutSource();
		log.append(start(LayoutSource.BoxKind.FLOW, new BlockParams(), new FlowPos()));
		log.append(new LayoutSource.Chars(0, "abc".toCharArray(), false));
		log.append(new LayoutSource.EndBlock());
		final LayoutSource.ReplaySlice slice = log.capture(0, 2);

		final List<SegmentEvent> converted = new ArrayList<>();
		slice.replay(event -> converted.add(LayoutSourceEventConverter.convert(event)));
		assertEquals(3, converted.size());
	}

	/** BoxKind.FLOW converts to BoxRecipe.Flow without losing content. */
	public void testFlowStartConvertsToBeginBoxWithFlowRecipe() {
		final BlockParams params = new BlockParams();
		params.orphans = 5;
		final FlowPos pos = new FlowPos();
		pos.align = net.zamasoft.foliojet.layout.box.params.Align.END;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(start(LayoutSource.BoxKind.FLOW, params, pos));

		assertTrue(converted instanceof SegmentEvent.BeginBox);
		final SegmentEvent.BeginBox begin = (SegmentEvent.BeginBox) converted;
		assertTrue(begin.recipe() instanceof BoxRecipe.Flow);
		final BoxRecipe.Flow flow = (BoxRecipe.Flow) begin.recipe();
		assertEquals(5, flow.params().materialize().orphans);
		assertEquals(net.zamasoft.foliojet.layout.box.params.Align.END, flow.pos().materialize().align);
	}

	/** BoxKind.INLINE converts to BoxRecipe.Inline without losing content. */
	public void testInlineStartConvertsToBeginBoxWithInlineRecipe() {
		final InlineParams params = new InlineParams();
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 2.0;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(start(LayoutSource.BoxKind.INLINE, params, pos));

		assertTrue(converted instanceof SegmentEvent.BeginBox);
		final BoxRecipe.Inline inline = (BoxRecipe.Inline) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(2.0, inline.pos().materialize().lineHeight);
	}

	/** MulticolumnBlockBox extends FlowBlockBox, so it converts to the same type as BoxKind.FLOW. */
	public void testMulticolStartConvertsToBeginBoxWithMulticolRecipe() {
		final BlockParams params = new BlockParams();
		params.widows = 4;
		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(start(LayoutSource.BoxKind.MULTICOL, params, new FlowPos()));

		assertTrue(((SegmentEvent.BeginBox) converted).recipe() instanceof BoxRecipe.Multicol);
		final BoxRecipe.Multicol multicol = (BoxRecipe.Multicol) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(4, multicol.params().materialize().widows);
	}

	/** OutsideMarkerBox/InlineBlockBox/InsideMarkerBox convert with BlockParams+InlinePos. */
	public void testBlockParamsWithInlinePosBoxKinds() {
		for (final LayoutSource.BoxKind kind : new LayoutSource.BoxKind[] { LayoutSource.BoxKind.MARKER,
				LayoutSource.BoxKind.INLINE_BLOCK, LayoutSource.BoxKind.INSIDE_MARKER }) {
			final SegmentEvent converted = LayoutSourceEventConverter
					.convert(start(kind, new BlockParams(), new InlinePos()));
			assertTrue("kind=" + kind, converted instanceof SegmentEvent.BeginBox);
		}
	}

	/** FloatBlockBox converts with BlockParams+FloatPos. */
	public void testFloatBlockStartConvertsToBeginBoxWithFloatBlockRecipe() {
		final FloatPos pos = new FloatPos();
		pos.floating = FloatSide.END;
		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(start(LayoutSource.BoxKind.FLOAT_BLOCK, new BlockParams(), pos));

		final BoxRecipe.FloatBlock floatBlock = (BoxRecipe.FloatBlock) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(FloatSide.END, floatBlock.pos().materialize().floating);
	}

	/** TableRowGroupBox/TableRowBox/TableColumnGroupBox/TableColumnBox convert with InnerTableParams. */
	public void testInnerTableParamsBoxKinds() {
		final InnerTableParams tableRowGroupParams = new InnerTableParams();
		final SegmentEvent rowGroup = LayoutSourceEventConverter.convert(
				start(LayoutSource.BoxKind.TABLE_ROW_GROUP, tableRowGroupParams, new TableRowGroupPos()));
		assertTrue(((SegmentEvent.BeginBox) rowGroup).recipe() instanceof BoxRecipe.TableRowGroup);

		final SegmentEvent row = LayoutSourceEventConverter
				.convert(start(LayoutSource.BoxKind.TABLE_ROW, new InnerTableParams(), new TableRowPos()));
		assertTrue(((SegmentEvent.BeginBox) row).recipe() instanceof BoxRecipe.TableRow);

		final SegmentEvent columnGroup = LayoutSourceEventConverter.convert(start(
				LayoutSource.BoxKind.TABLE_COLUMN_GROUP, new InnerTableParams(), new TableColumnPos()));
		assertTrue(((SegmentEvent.BeginBox) columnGroup).recipe() instanceof BoxRecipe.TableColumnGroup);

		final SegmentEvent column = LayoutSourceEventConverter.convert(
				start(LayoutSource.BoxKind.TABLE_COLUMN, new InnerTableParams(), new TableColumnPos()));
		assertTrue(((SegmentEvent.BeginBox) column).recipe() instanceof BoxRecipe.TableColumn);
	}

	/** TableCellBox reuses the existing BlockParams and converts with TableCellPos. */
	public void testTableCellStartConvertsToBeginBoxWithTableCellRecipe() {
		final BlockParams params = new BlockParams();
		params.orphans = 2;
		final TableCellPos pos = new TableCellPos();
		pos.colspan = 3;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(start(LayoutSource.BoxKind.TABLE_CELL, params, pos));

		final BoxRecipe.TableCell cell = (BoxRecipe.TableCell) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(2, cell.params().materialize().orphans);
		assertEquals(3, cell.pos().materialize().colspan);
	}

	/** EndBlock converts to EndBox; Chars converts its array to a String (preserving charOffset/fixed). */
	public void testEndBlockAndCharsConvert() {
		assertTrue(LayoutSourceEventConverter.convert(new LayoutSource.EndBlock()) instanceof SegmentEvent.EndBox);

		final SegmentEvent text = LayoutSourceEventConverter
				.convert(new LayoutSource.Chars(7, "hello".toCharArray(), true));
		assertTrue(text instanceof SegmentEvent.Text);
		final SegmentEvent.Text t = (SegmentEvent.Text) text;
		assertEquals(7, t.sourceOffset());
		assertEquals("hello", t.text());
		assertTrue(t.fixed());
	}

	/** Opaque has no kind information, so it converts to a Barrier with an empty kind. */
	public void testOpaqueConvertsToBarrierWithoutKind() {
		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Opaque());
		assertTrue(converted instanceof SegmentEvent.Barrier);
		assertTrue(((SegmentEvent.Barrier) converted).kind().isEmpty());
	}

	/**
	 * A replaced element in normal flow (FlowReplacedBox) is frozen at recording time
	 * ({@code ReplacedRecipe.freeze}) into ReplacedRecipe.Flow without losing content;
	 * conversion wraps it directly in SegmentEvent.Replaced (E-6 increment 3b-3).
	 */
	public void testFlowReplacedConvertsToReplacedWithFlowRecipe() {
		final ReplacedParams params = new ReplacedParams();
		params.lineHeight = 1.5;
		final FlowPos pos = new FlowPos();
		pos.align = net.zamasoft.foliojet.layout.box.params.Align.CENTER;
		final AbstractReplacedBox box = new FlowReplacedBox(params, pos);

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Replaced(ReplacedRecipe.freeze(box).orElseThrow()));

		assertTrue(converted instanceof SegmentEvent.Replaced);
		final ReplacedRecipe recipe = ((SegmentEvent.Replaced) converted).recipe();
		assertTrue(recipe instanceof ReplacedRecipe.Flow);
		final ReplacedRecipe.Flow flow = (ReplacedRecipe.Flow) recipe;
		assertEquals(1.5, flow.params().materialize().lineHeight);
		assertEquals(net.zamasoft.foliojet.layout.box.params.Align.CENTER, flow.pos().materialize().align);
	}

	/** An inline replaced element (InlineReplacedBox) freezes into ReplacedRecipe.Inline without losing content. */
	public void testInlineReplacedConvertsToReplacedWithInlineRecipe() {
		final ReplacedParams params = new ReplacedParams();
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 3.0;
		final AbstractReplacedBox box = new InlineReplacedBox(params, pos);

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Replaced(ReplacedRecipe.freeze(box).orElseThrow()));

		assertTrue(converted instanceof SegmentEvent.Replaced);
		final ReplacedRecipe.Inline inline = (ReplacedRecipe.Inline) ((SegmentEvent.Replaced) converted).recipe();
		assertEquals(3.0, inline.pos().materialize().lineHeight);
	}

	/**
	 * Replaced elements referencing a {@link ReplacedBoxImage} implementation (unshareable because it
	 * holds a back-reference to a live box) can also freeze at recording time, thanks to E-6 increment
	 * 3b-6 making freeze a total function through duplication, and do not become Barriers
	 * (the live type {@code ReplacedLive} was removed). Images in frozen and materialized values are
	 * copies independent of the live image.
	 */
	public void testReplacedBoxImageFreezesWithDuplicatedImage() {
		// An ordinary Image still freezes with sharing intact, as before (control case).
		final ReplacedParams params = new ReplacedParams();
		params.image = new Image() {
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
		};
		final AbstractReplacedBox plain = new FlowReplacedBox(params, new FlowPos());
		final ReplacedRecipe plainRecipe = ReplacedRecipe.freeze(plain).orElseThrow();
		assertTrue(LayoutSourceEventConverter
				.convert(new LayoutSource.Replaced(plainRecipe)) instanceof SegmentEvent.Replaced);
		assertSame(params.image, ((ReplacedRecipe.Flow) plainRecipe).params().materialize().image);

		final StubReplacedBoxImage liveImage = new StubReplacedBoxImage();
		final ReplacedParams unsafeParams = new ReplacedParams();
		unsafeParams.image = liveImage;
		final AbstractReplacedBox unsafe = new FlowReplacedBox(unsafeParams, new FlowPos());

		// Recording-time freeze is a total function (3b-6): it succeeds even for ReplacedBoxImage,
		// and conversion yields Replaced rather than Barrier.
		final ReplacedRecipe recipe = ReplacedRecipe.freeze(unsafe).orElseThrow();
		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Replaced(recipe));
		assertTrue(converted instanceof SegmentEvent.Replaced);
		// Materialized images are independent of the live image and of each other.
		final ReplacedRecipe.Flow flow = (ReplacedRecipe.Flow) recipe;
		final Image m1 = flow.params().materialize().image;
		final Image m2 = flow.params().materialize().image;
		assertNotSame(liveImage, m1);
		assertNotSame(liveImage, m2);
		assertNotSame(m1, m2);
		assertTrue(m1 instanceof ReplacedBoxImage);
	}

	/** Opaque (the non-replayable marker) remains the sole source of Barrier after 3b-6 removed the live types. */
	public void testOpaqueRemainsSoleBarrierSource() {
		assertTrue(LayoutSourceEventConverter.convertsToBarrier(new LayoutSource.Opaque()));
		assertFalse(LayoutSourceEventConverter
				.convertsToBarrier(new LayoutSource.Chars(0, "x".toCharArray(), false)));
		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Opaque());
		assertEquals(BarrierReason.NOT_YET_SUPPORTED, ((SegmentEvent.Barrier) converted).reason());
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
}
