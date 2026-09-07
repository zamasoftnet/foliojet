package jp.cssj.test.unit.displaylist;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder.IncompleteTableResult;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder.IncompleteTableStatus;
import net.zamasoft.foliojet.layout.builder.impl.TextBuilder;
import net.zamasoft.foliojet.layout.fragment.ContainerCut;
import net.zamasoft.foliojet.layout.fragment.OpenShape;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/** B-2a: 直接組み立てた箱で親の契約を検査する。DirectSession の変換スレッドは使わない。 */
public final class IncompleteTableIntakeTest extends TestCase {
	public void testAcceptUnsplit() {
		final TableBox table = table(7, 20, 30);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertTrue(result.isAccepted());
		assertEquals(IncompleteTableStatus.UNSPLIT, result.status());
		assertSame(table, result.remainder());
		assertSame(table.getTableBody(0), result.body());
		assertSame(table, builder.lastTable());
		assertEquals(List.of(table), tables(builder.getFlowBox().getContainer()));
		assertEquals(50.0, builder.getPageAxis(), 0);
		assertEquals(0.0, builder.positiveMargin(), 0);
		assertFalse(builder.canBreakAfterTable());
		result.complete();
		builder.finish();
	}

	public void testInternalSplitAndAppendToParentRemainder() {
		final TableBox table = table(0, 40, 40, 40);
		final TableRowBox last = table.getTableBody(0).getTableRow(2);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(IncompleteTableStatus.SPLIT, result.status());
		assertNotSame(table, result.remainder());
		assertSame(result.remainder(), builder.lastTable());
		assertTrue(result.remainder().isIncomplete());
		assertSame(last, result.body().getTableRow(0));
		assertEquals(List.of(table), tables(builder.pages.get(0).getContainer()));
		assertEquals(List.of(result.remainder()), tables(builder.getFlowBox().getContainer()));
		assertEquals(80.0, table.getInnerHeight(), 0);
		assertEquals(40.0, result.remainder().getInnerHeight(), 0);
		assertIllegalState(table::complete);
		final TableBox remainder = result.remainder();
		result.body().addTableRow(row(table.getTableParams(), 15));
		assertEquals(40.0, remainder.getInnerHeight(), 0);
		assertEquals(IncompleteTableStatus.UNSPLIT, result.rowsAppended());
		assertEquals(55.0, remainder.getInnerHeight(), 0);
		assertEquals(55.0, builder.getPageAxis(), 0);
		assertEquals(55.0, builder.getFlowBox().getInnerHeight(), 0);
		assertEquals(1, builder.getFlowBox().getContainer().getFlowCount());
		assertEquals(80.0, table.getInnerHeight(), 0);
		result.complete();
	}

	public void testOneNotificationCountsEveryEmittedFragment() {
		final TableBox table = table(0, 40, 40, 40, 40, 40, 40, 40);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(IncompleteTableStatus.SPLIT, result.status());
		assertEquals(3, result.emittedFragments());
		assertEquals(3, builder.pages.size());
		result.body().addTableRow(row(table.getTableParams(), 5));
		assertEquals(IncompleteTableStatus.UNSPLIT, result.rowsAppended());
		assertEquals(0, result.emittedFragments());
		result.complete();
		assertEquals(0, result.emittedFragments());
	}

	public void testWholeTableMove() {
		final TableBox table = table(0, 40, 40);
		table.getTableBody(0).getInnerTableParams().pageBreakInside = PageBreakMode.AVOID;
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final FlowBlockBox preceding = parent(table.getTableParams());
		preceding.setPageAxis(60);
		builder.addBound(preceding);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(IncompleteTableStatus.MOVED, result.status());
		assertEquals("全体移動は断片の送出ではない", 0, result.emittedFragments());
		assertSame(table, result.remainder());
		assertSame(table, builder.lastTable());
		assertTrue(tables(builder.pages.get(0).getContainer()).isEmpty());
		assertEquals(List.of(table), tables(builder.getFlowBox().getContainer()));
		assertEquals(80.0, builder.getPageAxis(), 0);
		result.complete();
	}

	public void testUnsplitTableThatCannotBreakIsRetained() {
		final TableBox table = table(0, 20);
		table.setTableHeader(group(table.getTableParams(), 110));
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(IncompleteTableStatus.UNSPLITTABLE, result.status());
		assertSame(table, result.remainder());
		assertSame(table, builder.lastTable());
		assertEquals(130.0, builder.getPageAxis(), 0);
		assertEquals(IncompleteTableStatus.UNSPLITTABLE, result.complete());
	}

	public void testAppendCanSplitAgain() {
		final TableBox table = table(0, 40, 40);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		final TableRowGroupBox oldBody = result.body();
		oldBody.addTableRow(row(table.getTableParams(), 40));
		assertEquals(IncompleteTableStatus.SPLIT, result.rowsAppended());
		assertNotSame(oldBody, result.body());
		assertNotSame(table, result.remainder());
		assertEquals(40.0, builder.getPageAxis(), 0);
		result.body().addTableRow(row(table.getTableParams(), 5));
		result.rowsAppended();
		assertEquals(45.0, builder.getPageAxis(), 0);
		result.complete();
	}

	public void testCompleteRestoresMarginAndChecksOverflowOnce() {
		final TableBox table = table(30, 40, 40);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(0, builder.breakCalls);
		assertEquals(IncompleteTableStatus.SPLIT, result.complete());
		assertEquals(1, builder.breakCalls);
		assertEquals("完了通知中の分割も数える", 1, result.emittedFragments());
		assertFalse(result.remainder().isIncomplete());
		assertEquals(30.0, result.remainder().getFrame().margin.bottom, 0);
		assertEquals(30.0, builder.positiveMargin(), 0);
		assertEquals(30.0, builder.negativeMargin(), 0);
		assertEquals(70.0, builder.getPageAxis(), 0);
		assertTrue(builder.canBreakAfterTable());
		assertTrue(builder.canBreakBeforeTable());
		assertIllegalState(result::complete);
		assertEquals(1, builder.breakCalls);
		builder.finish();
	}

	public void testPositiveAndNegativeEndMargins() {
		for (final double margin : new double[] { 7, -7 }) {
			final TableBox table = table(margin, 20);
			final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
			final IncompleteTableResult result = builder.acceptIncompleteTable(table);
			assertEquals(20.0, builder.getPageAxis(), 0);
			assertEquals(IncompleteTableStatus.UNSPLIT, result.complete());
			assertEquals(20 + margin, builder.getPageAxis(), 0);
			assertEquals(20 + margin, builder.getFlowBox().getInnerHeight(), 0);
			assertEquals(20 + margin, ((FlowBlockBox) builder.getFlowBox()).getContentSize(), 0);
			assertEquals(margin, builder.positiveMargin(), 0);
			assertEquals(margin, builder.negativeMargin(), 0);
		}
	}

	public void testUnsplitFractionalRowsPreserveAssemblyBits() {
		final TableBox table = table(0.7, 10.1);
		final TableRowGroupBox header = group(table.getTableParams(), 3.3);
		// 完成経路と同じ header -> body の初期組付け順。
		table.setSize(200, 0);
		table.setTableHeader(header);
		table.setSize(200, header.getHeight() + table.getTableBody(0).getHeight());
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 10000);
		builder.setPageAxis(0.3);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		double plannedBody = 10.1;
		for (int i = 1; i < 100; ++i) {
			plannedBody += 10.1;
			result.body().addTableRow(row(table.getTableParams(), 10.1));
			result.rowsAppended();
			assertBits(header.getHeight() + plannedBody, table.getInnerHeight());
			assertBits(0.3 + (header.getHeight() + plannedBody), builder.getPageAxis());
		}
		result.complete();
		assertBits(0.3 + ((header.getHeight() + plannedBody) + 0.7), builder.getPageAxis());
	}

	public void testFinalAppendUsesRestoredNegativeMarginBeforeBreaking() {
		final TableBox table = table(-30, 40, 40);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		result.body().addTableRow(row(table.getTableParams(), 40));
		assertEquals(IncompleteTableStatus.UNSPLIT, result.complete());
		assertSame(table, result.remainder());
		assertEquals(3, result.body().getTableRowCount());
		assertEquals(120.0, table.getInnerHeight(), 0);
		assertEquals(90.0, builder.getPageAxis(), 0);
		assertEquals(-30.0, builder.negativeMargin(), 0);
		assertEquals(0, builder.breakCalls);
		assertTrue(builder.pages.isEmpty());
		assertEquals(List.of(table), tables(builder.getFlowBox().getContainer()));

		final TableBox completed = table(-30, 40, 40, 40);
		completed.complete();
		final PagingBuilder reference = new PagingBuilder(completed.getTableParams(), 100);
		reference.addBound(completed);
		assertBits(reference.getPageAxis(), builder.getPageAxis());
		assertBits(reference.getFlowBox().getInnerHeight(), builder.getFlowBox().getInnerHeight());
		assertEquals(0, reference.breakCalls);
		assertIllegalState(result::complete);
		assertEquals(0, builder.breakCalls);
		builder.finish();
	}

	public void testFinalAppendCanBeNotifiedBeforeCompleteWhenNoBreakChanges() {
		for (final double margin : new double[] { 7, -7 }) {
			for (final boolean notify : new boolean[] { false, true }) {
				final TableBox table = table(margin, 20);
				final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
				final IncompleteTableResult result = builder.acceptIncompleteTable(table);
				result.body().addTableRow(row(table.getTableParams(), 10));
				result.body().addTableRow(row(table.getTableParams(), 20));
				if (notify) {
					assertEquals(IncompleteTableStatus.UNSPLIT, result.rowsAppended());
				}
				assertEquals(IncompleteTableStatus.UNSPLIT, result.complete());
				assertBits(50, table.getInnerHeight());
				assertBits(50 + margin, builder.getPageAxis());
				assertBits(50 + margin, builder.getFlowBox().getInnerHeight());
				assertEquals(margin, builder.positiveMargin(), 0);
				assertEquals(margin, builder.negativeMargin(), 0);
				assertEquals(0, builder.breakCalls);
				builder.finish();
			}
		}
	}

	public void testColumnsFollowAppendAndCompletion() throws Exception {
		final TableBox table = table(7, 20);
		final TableRowGroupBox header = group(table.getTableParams(), 3.3);
		table.setSize(200, 0);
		table.setTableHeader(header);
		table.setSize(200, header.getHeight() + table.getTableBody(0).getHeight());
		addColumns(table);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		result.body().addTableRow(row(table.getTableParams(), 20.2));
		result.rowsAppended();
		assertBits(3.3 + (20 + 20.2), table.getInnerHeight());
		assertColumnSizes(table, table.getInnerHeight());
		result.body().addTableRow(row(table.getTableParams(), 10.1));
		result.complete();
		assertBits(3.3 + ((20 + 20.2) + 10.1), table.getInnerHeight());
		assertColumnSizes(table, table.getInnerHeight());
		assertBits(table.getInnerHeight() + 7, table.getHeight());
		builder.finish();
	}

	public void testSplitRemainderColumnsFollowAppendAndCompletion() throws Exception {
		final TableBox table = table(7, 40, 40);
		addColumns(table);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		result.body().addTableRow(row(table.getTableParams(), 40));
		assertEquals(IncompleteTableStatus.SPLIT, result.rowsAppended());
		final TableBox remainder = result.remainder();
		assertNotSame(columns(table), columns(remainder));
		assertColumnSizes(table, 80);
		assertColumnSizes(remainder, 40);
		result.body().addTableRow(row(table.getTableParams(), 15));
		result.rowsAppended();
		assertColumnSizes(remainder, 55);
		// 古い列高を注入し、追記を伴わない完了でも現在の列木を同期することを検査する。
		columns(remainder).eachColumn((column, col, span) -> column.setPageSize(40));
		result.complete();
		assertColumnSizes(remainder, 55);
		assertColumnSizes(table, 80);
		assertEquals(62.0, remainder.getHeight(), 0);
		builder.finish();
	}

	public void testForcedBreakOnFormerLastRowAfterAppend() {
		final TableBox table = table(0, 20);
		table.getTableBody(0).getTableRow(0).getTableRowPos().pageBreakAfter = PageBreakMode.PAGE;
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(IncompleteTableStatus.UNSPLIT, result.status());
		result.body().addTableRow(row(table.getTableParams(), 20));
		assertEquals(IncompleteTableStatus.SPLIT, result.rowsAppended());
		assertEquals(1, builder.breakCalls);
		assertEquals(1, result.body().getTableRowCount());
		result.complete();
	}

	public void testInvalidOperations() {
		final TableBox table = table(0, 20);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertIllegalState(() -> builder.acceptIncompleteTable(table));
		assertIllegalState(result::rowsAppended);
		assertIllegalState(builder::finish);
		result.body().addTableRow(row(table.getTableParams(), 10));
		result.rowsAppended();
		assertIllegalState(result::rowsAppended);
		result.complete();
		assertIllegalState(result::rowsAppended);
		assertIllegalState(result::complete);
		builder.finish();
	}

	public void testCompleteRejectsChangedAppendedRowSize() {
		final TableBox table = table(0, 20);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		final TableRowBox appended = row(table.getTableParams(), 10);
		result.body().addTableRow(appended);
		appended.setPageSize(11);
		assertIllegalState(result::complete);
		assertTrue(table.isIncomplete());
		assertEquals(20.0, builder.getPageAxis(), 0);
		appended.setPageSize(10);
		result.complete();
		assertEquals(30.0, builder.getPageAxis(), 0);
		builder.finish();
	}

	public void testMissingRemainderIsAnError() {
		final TableBox table = table(0, 40, 40, 40);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		builder.loseRemainder = true;
		assertIllegalState(() -> builder.acceptIncompleteTable(table));
		assertIllegalState(builder::finish);
	}

	public void testUnsupportedHostsDoNotAcceptOrPlace() {
		for (int condition = 0; condition < 6; ++condition) {
			final TableBox table = table(7, 20);
			final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
			switch (condition) {
			case 0:
				// DocumentBuilder の continuous もこのモードを選ぶ。
				builder.setMode(BreakableBuilder.MODE_NO_BREAK);
				break;
			case 1:
				builder.setMode(BreakableBuilder.MODE_AUTO);
				break;
			case 2:
				builder.disableBreakAtDepth();
				break;
			case 3:
				builder.startRestyle();
				break;
			case 4:
				table.getTableParams().flow = WritingMode.RL;
				break;
			case 5:
				builder.getFlowBox().getBlockParams().flow = WritingMode.RL;
				break;
			default:
				throw new AssertionError();
			}
			final IncompleteTableResult result = builder.acceptIncompleteTable(table);
			assertFalse(result.isAccepted());
			assertEquals(IncompleteTableStatus.UNSUPPORTED, result.status());
			assertNull(result.remainder());
			assertEquals(0.0, builder.getPageAxis(), 0);
			assertEquals(0, builder.getFlowBox().getContainer().getFlowCount());
			assertTrue(table.isIncomplete());
			assertIllegalState(result::rowsAppended);
			assertIllegalState(result::complete);
		}
	}

	public void testTextSessionIsRejectedWithoutAbortingIt() throws Exception {
		final TableBox table = table(0, 20);
		final PagingBuilder builder = new PagingBuilder(table.getTableParams(), 100);
		// 非公開セッションを直接置き、受理が再生・abort を起こさないことだけを検査する。
		final var field = BlockBuilder.class.getDeclaredField("textSession");
		field.setAccessible(true);
		final var constructor = field.getType().getDeclaredConstructor(BlockBuilder.class, TextBuilder.class,
				BlockParams.class, double.class, double.class);
		constructor.setAccessible(true);
		final Object session = constructor.newInstance(builder, null, builder.getFlowBox().getBlockParams(), 200, 0);
		field.set(builder, session);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(IncompleteTableStatus.UNSUPPORTED, result.status());
		assertSame(session, field.get(builder));
		assertEquals(0.0, builder.getPageAxis(), 0);
		assertEquals(0, builder.getFlowBox().getContainer().getFlowCount());
	}

	static TableBox table(final double bottomMargin, final double... sizes) {
		final TableParams params = new TableParams();
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		params.borderCollapse = TableParams.BORDER_SEPARATE;
		params.frame = RectFrame.create(Insets.create(0, 0, bottomMargin, 0, LengthType.ABSOLUTE,
				LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE), null, null, null);
		final TableBox table = new TableBox(params, parent(params));
		table.calculateFrame(200);
		table.setSize(200, 0);
		table.addTableBody(group(params, sizes));
		table.markIncomplete();
		return table;
	}

	static TableRowGroupBox group(final TableParams params, final double... sizes) {
		final TableRowGroupBox group = new TableRowGroupBox(new InnerTableParams(), new TableRowGroupPos());
		group.setTableParams(params);
		for (final double size : sizes) {
			group.addTableRow(row(params, size));
		}
		return group;
	}

	static void addColumns(final TableBox table) {
		final TableColumnGroupBox root = new TableColumnGroupBox(new InnerTableParams(), new TableColumnPos());
		root.setTableParams(table.getTableParams());
		final TableColumnGroupBox group = new TableColumnGroupBox(new InnerTableParams(), new TableColumnPos());
		group.setTableParams(table.getTableParams());
		final TableColumnBox first = new TableColumnBox(new InnerTableParams(), new TableColumnPos());
		first.setTableParams(table.getTableParams());
		group.addTableColumn(first);
		root.addTableColumn(group);
		final TableColumnBox second = new TableColumnBox(new InnerTableParams(), new TableColumnPos());
		second.setTableParams(table.getTableParams());
		root.addTableColumn(second);
		root.eachColumn((column, col, span) -> {
			column.setLineSize(100);
			column.setPageSize(table.getInnerHeight());
		});
		table.setTableColumnGroup(root);
	}

	static TableColumnGroupBox columns(final TableBox table) throws Exception {
		final var field = TableBox.class.getDeclaredField("columnGroupBox");
		field.setAccessible(true);
		return (TableColumnGroupBox) field.get(table);
	}

	private static void assertColumnSizes(final TableBox table, final double pageSize) throws Exception {
		// eachColumn は実列・列グループを訪問し、走査用の匿名根は含めない。
		final int[] count = { 0 };
		columns(table).eachColumn((column, col, span) -> {
			assertBits(pageSize, column.getPageSize());
			assertBits(100, column.getLineSize());
			++count[0];
		});
		assertEquals(3, count[0]);
	}

	static TableRowBox row(final TableParams params, final double size) {
		final TableRowBox row = new TableRowBox(new InnerTableParams(), new TableRowPos());
		row.setTableParams(params);
		row.setLineSize(200);
		row.setPageSize(size);
		return row;
	}

	static FlowBlockBox parent(final TableParams tableParams) {
		final BlockParams params = new BlockParams();
		params.fontStyle = tableParams.fontStyle;
		return new FlowBlockBox(params, new FlowPos());
	}

	static List<TableBox> tables(final Container container) {
		final List<TableBox> tables = new ArrayList<>();
		container.eachFlowBox(box -> {
			if (box instanceof TableBox table) {
				tables.add(table);
			}
		});
		return tables;
	}

	private static void assertBits(final double expected, final double actual) {
		assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
	}

	private static void assertIllegalState(final Runnable action) {
		try {
			action.run();
			fail("IllegalStateException expected");
		} catch (final IllegalStateException expected) {
			// expected
		}
	}

	/** ページ出力だけを省く宿主。切断・残余再配置は実際の FlowContainer / TableBox を使う。 */
	static class PagingBuilder extends BreakableBuilder {
		private final TableParams params;
		private final double limit;
		private final List<FlowBlockBox> pages = new ArrayList<>();
		private int breakCalls;
		private boolean loseRemainder;

		PagingBuilder(final TableParams params, final double limit) {
			super(null, parent(params), MODE_PAGE_BREAK);
			this.params = params;
			this.limit = limit;
		}

		@Override
		public double getPageLimit() {
			return this.limit;
		}

		@Override
		protected boolean pageBreak(final BreakMode mode, final byte flags) {
			++this.breakCalls;
			final FlowBlockBox previous = (FlowBlockBox) this.getRootBox();
			final Container tail = ((ContainerCut.Plain) previous.getContainer()
					.splitPageAxis(this.limit, mode, flags, null)).container();
			if (tail == null || tail == previous.getContainer()) {
				return false;
			}
			this.pages.add(previous);
			this.beginBreak();
			this.contextFlow = new Flow(parent(this.params), 0, 0);
			this.resetFragmentCursor(0, 0);
			if (this.loseRemainder) {
				// 残余の再配置漏れを注入し、親のハンドルが黙って成功しないことを検査する。
				return true;
			}
			this.beginRestyling();
			try {
				tail.restyle(this, OpenShape.CLOSED, false);
			} finally {
				this.endRestyling();
			}
			return true;
		}

		TableBox lastTable() {
			return this.lastTableBox;
		}

		double positiveMargin() {
			return this.poLastMargin;
		}

		double negativeMargin() {
			return this.neLastMargin;
		}

		boolean canBreakAfterTable() {
			return this.interflowBreak;
		}

		boolean canBreakBeforeTable() {
			return this.canBreakBefore;
		}

		void setMode(final byte mode) {
			this.mode = mode;
		}

		void disableBreakAtDepth() {
			this.breakDepth = 0;
		}

		void startRestyle() {
			this.beginRestyling();
		}
	}
}
