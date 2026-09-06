package jp.cssj.test.unit.displaylist;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.fragment.TableCutter;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/** B-1: 変換スレッドや入力文書に依存せず、未完表の箱と末尾会計の契約を検査する。 */
public final class IncompleteTableContractTest extends TestCase {
	public void testDefaultIsComplete() {
		final TableBox table = table(WritingMode.TB, 7);
		assertFalse(table.isIncomplete());
		assertEquals(120.0, table.getHeight(), 0);
		assertFalse(table.splitTableBox().isIncomplete());
	}

	public void testCompleteRestoresFrameOnce() {
		for (final WritingMode flow : WritingMode.values()) {
			final TableBox table = table(flow, 7);
			final AbsoluteRectFrame original = table.getFrame();
			final double originalExtent = table.getPageExtent(flow);
			table.markIncomplete();
			assertTrue(table.isIncomplete());
			assertEquals(0.0, table.getFrame().getFramePageEnd(flow), 0);
			assertEquals(table.getInnerPageExtent(flow) + original.getFramePageStart(flow),
					table.getPageExtent(flow), 0);
			// 退避したフレームも共有の style params も切断していない。
			assertTrue(original.getFramePageEnd(flow) > 0);
			assertSame(original.frame, table.getTableParams().frame);
			table.complete();
			assertFalse(table.isIncomplete());
			assertSame(original, table.getFrame());
			assertEquals(Double.doubleToLongBits(originalExtent), Double.doubleToLongBits(table.getPageExtent(flow)));
			assertIllegalState(table::complete);
		}
	}

	public void testOnlyFinalRemainderCanComplete() {
		for (final WritingMode flow : WritingMode.values()) {
			final TableBox table = table(flow, 7);
			final double end = table.getFrame().getFramePageEnd(flow);
			table.markIncomplete();
			final TableBox next = table.splitTableBox();
			final TableBox last = next.splitTableBox();
			assertTrue(table.isIncomplete());
			assertTrue(next.isIncomplete());
			assertTrue(last.isIncomplete());
			assertEquals(0, last.getTableBodyCount());
			assertEquals(0.0, last.getFrame().getFramePageStart(flow), 0);
			assertEquals(0.0, last.getFrame().getFramePageEnd(flow), 0);
			assertIllegalState(table::complete);
			assertIllegalState(next::complete);
			last.complete();
			assertEquals(end, last.getFrame().getFramePageEnd(flow), 0);
			assertEquals(0.0, last.getFrame().getFramePageStart(flow), 0);
			assertEquals(0.0, table.getFrame().getFramePageEnd(flow), 0);
			assertIllegalState(last::complete);
		}
	}

	public void testRepeatedHeaderKeepsStartFrame() {
		final TableBox table = table(WritingMode.TB, 7);
		final TableRowGroupBox header = group(table.getTableParams(), 10);
		table.setTableHeader(header);
		final double start = table.getFrame().getFrameTop();
		final double end = table.getFrame().getFrameBottom();
		table.markIncomplete();
		final TableBox next = table.splitTableBox();
		assertSame(header, next.getTableHeader());
		assertEquals(10.0, next.getInnerHeight(), 0);
		assertEquals(start, next.getFrame().getFrameTop(), 0);
		assertEquals(0.0, next.getFrame().getFrameBottom(), 0);
		next.complete();
		assertSame(header, next.getTableHeader());
		assertEquals(start, next.getFrame().getFrameTop(), 0);
		assertEquals(end, next.getFrame().getFrameBottom(), 0);
	}

	public void testForcedSplitKeepsIncompleteRemainder() {
		final TableBox table = table(WritingMode.TB, 7);
		table.setSize(200, 0);
		final TableRowGroupBox body = group(table.getTableParams(), 10, 20);
		table.addTableBody(body);
		table.markIncomplete();
		final BreakMode mode = new BreakMode.TableForceBreakMode(body.getTableRow(0), PageBreakMode.PAGE, 0, 0);
		final SplitResult result = table.split(10, mode, IPageBreakableBox.FLAGS_LAST);
		assertTrue(result instanceof SplitResult.Split);
		final TableBox next = (TableBox) ((SplitResult.Split) result).remainder();
		assertTrue(next.isIncomplete());
		assertEquals(10.0, table.getInnerHeight(), 0);
		assertEquals(20.0, next.getInnerHeight(), 0);
		next.complete();
		assertEquals(12.0, next.getFrame().getFrameBottom(), 0);
	}

	public void testParentDefersEndAccounting() {
		for (final double margin : new double[] { 7, -7 }) {
			final TableBox table = table(WritingMode.TB, margin);
			table.markIncomplete();
			final InspectingBuilder builder = new InspectingBuilder(table.getTableParams());
			builder.addBound(table);
			assertEquals(108.0, builder.getPageAxis(), 0);
			assertEquals(0.0, builder.positiveMargin(), 0);
			assertEquals(0.0, builder.negativeMargin(), 0);

			final TableBox closed = table(WritingMode.TB, margin);
			final InspectingBuilder closedBuilder = new InspectingBuilder(closed.getTableParams());
			closedBuilder.addBound(closed);
			assertEquals(closed.getHeight(), closedBuilder.getPageAxis(), 0);
			assertEquals(margin, closedBuilder.positiveMargin(), 0);
			assertEquals(margin, closedBuilder.negativeMargin(), 0);
		}
	}

	public void testCutReservationDefersEndMargin() {
		// 境界が末尾マージンに入った場合、完了表だけが末尾マージンを予約する。
		assertEquals(78.0, TableCutter.reserveNonBreakable(95, 100, 7, 10, 10, -1, -1), 0);
		assertEquals(88.0, TableCutter.reserveIncompleteNonBreakable(95, 7, -1), 0);
		assertEquals(78.0, TableCutter.reserveIncompleteNonBreakable(95, 7, 10), 0);
	}

	public void testParentKeepsUnsplitIncompleteTable() {
		// 収まる場合と、改ページを拒否された場合の両方で現在の表を保持する。
		for (final double limit : new double[] { 200, 50 }) {
			final TableBox table = table(WritingMode.TB, 7);
			table.markIncomplete();
			final UnsplitBuilder builder = new UnsplitBuilder(table.getTableParams(), limit);
			builder.addBound(table);
			assertSame(table, builder.lastTable());
			assertFalse(builder.canBreakAfterTable());
		}
	}

	public void testFullSourceReplayIsRejected() {
		final TableBox table = table(WritingMode.TB, 7);
		table.setSourceAnchor(42);
		assertTrue(table.isSourceReplayable());
		table.markIncomplete();
		assertFalse(table.isSourceReplayable());
		final TableBox next = table.splitTableBox();
		next.setSourceAnchor(42);
		assertFalse(table.isSourceReplayable());
		assertFalse(next.isSourceReplayable());
		next.complete();
		assertFalse(next.isSourceReplayable());
	}

	public void testRepeatedFooterCannotBecomeIncomplete() {
		final TableBox table = table(WritingMode.TB, 7);
		final TableRowGroupBox footer = group(table.getTableParams(), 10);
		table.setTableFooter(footer);
		final AbsoluteRectFrame frame = table.getFrame();
		assertIllegalState(table::markIncomplete);
		assertFalse(table.isIncomplete());
		assertSame(frame, table.getFrame());
		final TableBox open = table(WritingMode.TB, 7);
		open.markIncomplete();
		assertIllegalState(() -> open.setTableFooter(footer));
	}

	private static TableBox table(final WritingMode flow, final double bottomMargin) {
		final TableParams params = new TableParams();
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		params.flow = flow;
		final Border border = Border.create(Border.SOLID, 2, RGBColor.BLACK);
		final RectBorder borders = RectBorder.create(border, border, border, border,
				RectBorder.Radius.ZERO_RADIUS, RectBorder.Radius.ZERO_RADIUS,
				RectBorder.Radius.ZERO_RADIUS, RectBorder.Radius.ZERO_RADIUS);
		params.frame = RectFrame.create(Insets.create(3, 5, bottomMargin, 11, LengthType.ABSOLUTE,
				LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE), borders, null, null);
		params.borderCollapse = TableParams.BORDER_SEPARATE;
		params.borderSpacingH = 4;
		params.borderSpacingV = 6;
		final TableBox table = new TableBox(params, new FlowBlockBox(params, new FlowPos()));
		table.calculateFrame(200);
		table.setSize(200, 100);
		return table;
	}

	private static TableRowGroupBox group(final TableParams params, final double... sizes) {
		final TableRowGroupBox group = new TableRowGroupBox(new InnerTableParams(), new TableRowGroupPos());
		group.setTableParams(params);
		for (final double size : sizes) {
			final TableRowBox row = new TableRowBox(new InnerTableParams(), new TableRowPos());
			row.setTableParams(params);
			row.setLineSize(200);
			row.setPageSize(size);
			group.addTableRow(row);
		}
		return group;
	}

	private static void assertIllegalState(final Runnable action) {
		try {
			action.run();
			fail("IllegalStateException expected");
		} catch (final IllegalStateException expected) {
			// expected
		}
	}

	private static final class InspectingBuilder extends BlockBuilder {
		InspectingBuilder(final TableParams params) {
			super(null, parent(params));
		}

		private static FlowBlockBox parent(final TableParams tableParams) {
			final BlockParams params = new BlockParams();
			params.fontStyle = tableParams.fontStyle;
			params.flow = tableParams.flow;
			return new FlowBlockBox(params, new FlowPos());
		}

		double positiveMargin() {
			return this.poLastMargin;
		}

		double negativeMargin() {
			return this.neLastMargin;
		}
	}

	private static final class UnsplitBuilder extends BreakableBuilder {
		private final double limit;

		UnsplitBuilder(final TableParams params, final double limit) {
			super(null, InspectingBuilder.parent(params), MODE_AUTO);
			this.limit = limit;
		}

		@Override
		public double getPageLimit() {
			return this.limit;
		}

		@Override
		protected boolean pageBreak(final BreakMode mode, final byte flags) {
			return false;
		}

		TableBox lastTable() {
			return this.lastTableBox;
		}

		boolean canBreakAfterTable() {
			return this.interflowBreak;
		}
	}
}
