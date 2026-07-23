package net.zamasoft.foliojet.layout.segment;

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
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * M6d-A3c(2026-07-22新設)、{@code LayoutSource.Event}を
 * {@link SegmentEvent}へ変換するアダプタの単体テストです。
 * {@code LayoutSource}本体・{@code SourceReplayer}への実際の配線
 * (production経路からの呼び出し)はまだ行わない。
 */
public class LayoutSourceEventConverterTest extends TestCase {
	/** イベント数は1:1(ordinal対応を壊さない、M6d-A2との整合性)。 */
	public void testEventCountIsPreserved1to1() {
		final LayoutSource log = new LayoutSource();
		log.append(new LayoutSource.Start(LayoutSource.BoxKind.FLOW, new BlockParams(), new FlowPos()));
		log.append(new LayoutSource.Chars(0, "abc".toCharArray(), false));
		log.append(new LayoutSource.EndBlock());
		final LayoutSource.ReplaySlice slice = log.capture(0, 2);

		final List<SegmentEvent> converted = LayoutSourceEventConverter.convert(slice);
		assertEquals(3, converted.size());
	}

	/** BoxKind.FLOWは内容を失わずBoxRecipe.Flowへ変換される。 */
	public void testFlowStartConvertsToBeginBoxWithFlowRecipe() {
		final BlockParams params = new BlockParams();
		params.orphans = 5;
		final FlowPos pos = new FlowPos();
		pos.align = net.zamasoft.foliojet.layout.box.params.Align.END;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.FLOW, params, pos));

		assertTrue(converted instanceof SegmentEvent.BeginBox);
		final SegmentEvent.BeginBox begin = (SegmentEvent.BeginBox) converted;
		assertTrue(begin.recipe() instanceof BoxRecipe.Flow);
		final BoxRecipe.Flow flow = (BoxRecipe.Flow) begin.recipe();
		assertEquals(5, flow.params().materialize().orphans);
		assertEquals(net.zamasoft.foliojet.layout.box.params.Align.END, flow.pos().materialize().align);
	}

	/** BoxKind.INLINEは内容を失わずBoxRecipe.Inlineへ変換される。 */
	public void testInlineStartConvertsToBeginBoxWithInlineRecipe() {
		final InlineParams params = new InlineParams();
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 2.0;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.INLINE, params, pos));

		assertTrue(converted instanceof SegmentEvent.BeginBox);
		final BoxRecipe.Inline inline = (BoxRecipe.Inline) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(2.0, inline.pos().materialize().lineHeight);
	}

	/** MulticolumnBlockBoxはFlowBlockBoxを継承するため、BoxKind.FLOWと同じ型で変換される。 */
	public void testMulticolStartConvertsToBeginBoxWithMulticolRecipe() {
		final BlockParams params = new BlockParams();
		params.widows = 4;
		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.MULTICOL, params, new FlowPos()));

		assertTrue(((SegmentEvent.BeginBox) converted).recipe() instanceof BoxRecipe.Multicol);
		final BoxRecipe.Multicol multicol = (BoxRecipe.Multicol) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(4, multicol.params().materialize().widows);
	}

	/** OutsideMarkerBox/InlineBlockBox/InsideMarkerBoxはBlockParams+InlinePosで変換される。 */
	public void testBlockParamsWithInlinePosBoxKinds() {
		for (final LayoutSource.BoxKind kind : new LayoutSource.BoxKind[] { LayoutSource.BoxKind.MARKER,
				LayoutSource.BoxKind.INLINE_BLOCK, LayoutSource.BoxKind.INSIDE_MARKER }) {
			final SegmentEvent converted = LayoutSourceEventConverter
					.convert(new LayoutSource.Start(kind, new BlockParams(), new InlinePos()));
			assertTrue("kind=" + kind, converted instanceof SegmentEvent.BeginBox);
		}
	}

	/** FloatBlockBoxはBlockParams+FloatPosで変換される。 */
	public void testFloatBlockStartConvertsToBeginBoxWithFloatBlockRecipe() {
		final FloatPos pos = new FloatPos();
		pos.floating = FloatSide.END;
		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.FLOAT_BLOCK, new BlockParams(), pos));

		final BoxRecipe.FloatBlock floatBlock = (BoxRecipe.FloatBlock) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(FloatSide.END, floatBlock.pos().materialize().floating);
	}

	/**
	 * BoxKind.TABLEはTableParams+FlowPosで内容を失わずBoxRecipe.Tableへ
	 * 変換される(StyleBuilderの「box.getPos() instanceof FlowPosの
	 * 場合のみ記録」不変条件どおり)。
	 */
	public void testTableStartConvertsToBeginBoxWithTableRecipe() {
		final TableParams params = new TableParams();
		params.borderCollapse = TableParams.BORDER_COLLAPSE;
		final FlowPos pos = new FlowPos();
		pos.columnSpan = net.zamasoft.foliojet.layout.box.params.FlowPos.COLUMN_SPAN_ALL;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.TABLE, params, pos));

		assertTrue(converted instanceof SegmentEvent.BeginBox);
		final BoxRecipe.Table table = (BoxRecipe.Table) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(TableParams.BORDER_COLLAPSE, table.params().materialize().borderCollapse);
		assertEquals(net.zamasoft.foliojet.layout.box.params.FlowPos.COLUMN_SPAN_ALL,
				table.pos().materialize().columnSpan);
	}

	/** TableRowGroupBox/TableRowBox/TableColumnGroupBox/TableColumnBoxはInnerTableParamsで変換される。 */
	public void testInnerTableParamsBoxKinds() {
		final InnerTableParams tableRowGroupParams = new InnerTableParams();
		final SegmentEvent rowGroup = LayoutSourceEventConverter.convert(
				new LayoutSource.Start(LayoutSource.BoxKind.TABLE_ROW_GROUP, tableRowGroupParams, new TableRowGroupPos()));
		assertTrue(((SegmentEvent.BeginBox) rowGroup).recipe() instanceof BoxRecipe.TableRowGroup);

		final SegmentEvent row = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.TABLE_ROW, new InnerTableParams(), new TableRowPos()));
		assertTrue(((SegmentEvent.BeginBox) row).recipe() instanceof BoxRecipe.TableRow);

		final SegmentEvent columnGroup = LayoutSourceEventConverter.convert(new LayoutSource.Start(
				LayoutSource.BoxKind.TABLE_COLUMN_GROUP, new InnerTableParams(), new TableColumnPos()));
		assertTrue(((SegmentEvent.BeginBox) columnGroup).recipe() instanceof BoxRecipe.TableColumnGroup);

		final SegmentEvent column = LayoutSourceEventConverter.convert(
				new LayoutSource.Start(LayoutSource.BoxKind.TABLE_COLUMN, new InnerTableParams(), new TableColumnPos()));
		assertTrue(((SegmentEvent.BeginBox) column).recipe() instanceof BoxRecipe.TableColumn);
	}

	/** TableCellBoxは既存のBlockParamsを再利用し、TableCellPosで変換される。 */
	public void testTableCellStartConvertsToBeginBoxWithTableCellRecipe() {
		final BlockParams params = new BlockParams();
		params.orphans = 2;
		final TableCellPos pos = new TableCellPos();
		pos.colspan = 3;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.TABLE_CELL, params, pos));

		final BoxRecipe.TableCell cell = (BoxRecipe.TableCell) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(2, cell.params().materialize().orphans);
		assertEquals(3, cell.pos().materialize().colspan);
	}

	/** EndBlockはEndBoxへ、Charsは配列がStringへ変換される(charOffset/fixedは保持)。 */
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

	/** Opaqueは種別情報を持たないため、kindが空のBarrierへ変換される。 */
	public void testOpaqueConvertsToBarrierWithoutKind() {
		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Opaque());
		assertTrue(converted instanceof SegmentEvent.Barrier);
		assertTrue(((SegmentEvent.Barrier) converted).kind().isEmpty());
	}

	/** 通常フローの置換要素(FlowReplacedBox)は内容を失わずReplacedRecipe.Flowへ変換される。 */
	public void testFlowReplacedConvertsToReplacedWithFlowRecipe() {
		final ReplacedParams params = new ReplacedParams();
		params.lineHeight = 1.5;
		final FlowPos pos = new FlowPos();
		pos.align = net.zamasoft.foliojet.layout.box.params.Align.CENTER;
		final AbstractReplacedBox box = new FlowReplacedBox(params, pos);

		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Replaced(box));

		assertTrue(converted instanceof SegmentEvent.Replaced);
		final ReplacedRecipe recipe = ((SegmentEvent.Replaced) converted).recipe();
		assertTrue(recipe instanceof ReplacedRecipe.Flow);
		final ReplacedRecipe.Flow flow = (ReplacedRecipe.Flow) recipe;
		assertEquals(1.5, flow.params().materialize().lineHeight);
		assertEquals(net.zamasoft.foliojet.layout.box.params.Align.CENTER, flow.pos().materialize().align);
	}

	/** インラインの置換要素(InlineReplacedBox)は内容を失わずReplacedRecipe.Inlineへ変換される。 */
	public void testInlineReplacedConvertsToReplacedWithInlineRecipe() {
		final ReplacedParams params = new ReplacedParams();
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 3.0;
		final AbstractReplacedBox box = new InlineReplacedBox(params, pos);

		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Replaced(box));

		assertTrue(converted instanceof SegmentEvent.Replaced);
		final ReplacedRecipe.Inline inline = (ReplacedRecipe.Inline) ((SegmentEvent.Replaced) converted).recipe();
		assertEquals(3.0, inline.pos().materialize().lineHeight);
	}

	/**
	 * {@link ReplacedBoxImage}実装(live boxへのback-referenceを持つため
	 * 共有不可)を参照する置換要素は、fail closedでBarrierへ変換される
	 * (ReplacedRecipeは構築されない)。
	 */
	public void testReplacedBoxImageFailsClosedToBarrier() {
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
		// 通常のImageなら変換できることをまず確認(対照)
		final AbstractReplacedBox plain = new FlowReplacedBox(params, new FlowPos());
		assertTrue(LayoutSourceEventConverter.convert(new LayoutSource.Replaced(plain)) instanceof SegmentEvent.Replaced);

		final ReplacedParams unsafeParams = new ReplacedParams();
		unsafeParams.image = new StubReplacedBoxImage();
		final AbstractReplacedBox unsafe = new FlowReplacedBox(unsafeParams, new FlowPos());

		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Replaced(unsafe));
		assertTrue(converted instanceof SegmentEvent.Barrier);
		assertEquals(BarrierReason.NOT_YET_SUPPORTED, ((SegmentEvent.Barrier) converted).reason());
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
}
