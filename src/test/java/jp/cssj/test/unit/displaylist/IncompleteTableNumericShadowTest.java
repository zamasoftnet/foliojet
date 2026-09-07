package jp.cssj.test.unit.displaylist;

import static jp.cssj.test.unit.displaylist.IncompleteTableIntakeTest.addColumns;
import static jp.cssj.test.unit.displaylist.IncompleteTableIntakeTest.columns;
import static jp.cssj.test.unit.displaylist.IncompleteTableIntakeTest.group;
import static jp.cssj.test.unit.displaylist.IncompleteTableIntakeTest.parent;
import static jp.cssj.test.unit.displaylist.IncompleteTableIntakeTest.row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.IncompleteTablePlan;
import net.zamasoft.foliojet.layout.box.impl.IncompleteTablePlan.SplitKind;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BackgroundImage;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder.IncompleteTableResult;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder.IncompleteTableStatus;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RowLayoutEngine;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildPlanner;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildPlanner.RowEmissionExclusion;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildPlanner.RowEmissionFacts;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;

/**
 * B-2b-1: oracle は完成した実 TableBox と親の addBound。
 * 全残余は仮想計画同士、実箱は確定断片・完了時点で比較する。
 * 乱数試験の成功を、対象外の形まで含む一般的な同値証明とはしない。
 */
public final class IncompleteTableNumericShadowTest extends TestCase {
	public void testAutoSplitArithmeticWithoutHeader() throws Exception {
		arithmetic(false, 0);
	}

	public void testAutoSplitArithmeticWithHeaderAndColumns() throws Exception {
		arithmetic(false, 7.3);
	}

	public void testForcedSplitArithmeticWithoutHeader() throws Exception {
		arithmetic(true, 0);
	}

	public void testForcedSplitArithmeticWithHeader() throws Exception {
		arithmetic(true, 7.3);
	}

	private static void arithmetic(final boolean forced, final double header) throws Exception {
		for (int seed = 0; seed < 8; ++seed) {
			final double[] sizes = sizes(seed, 48);
			arithmetic(sizes, forced, header, 0);
		}
	}

	/**
	 * 配分済みh[]の数値契約だけを検査する。実セルbind・表指定高を持つラッパーは
	 * この合成箱にはなく、この試験の一致をGROUP_PAGE_SIZE解除の根拠にはしない。
	 */
	public void testDistributedGroupAndTableHeightsUseFinalRows() throws Exception {
		for (final boolean zero : new boolean[] { false, true }) {
			for (final boolean forced : new boolean[] { false, true }) {
				final double[] sizes = zero ? new double[48] : sizes(51, 48);
				assertTrue(RowLayoutEngine.distributeGroupSize(sizes, 720.1) > 0);
				if (zero) for (final double size : sizes) bits(720.1 / 48, size);
				final double[] groupRows = sizes.clone();
				final boolean[] auto = new boolean[sizes.length];
				Arrays.fill(auto, true);
				RowLayoutEngine.distributeTableSize(sizes, auto, 820.3);
				for (int i = 0; i < sizes.length; ++i) assertTrue(sizes[i] > groupRows[i]);
				arithmetic(sizes, forced, 7.3, 720.1);
				parentShadow(sizes, 7.3, 10.1, forced, true, 1.3, new double[] { 100, 80.5, 120.1 });
			}
		}
	}

	private static void arithmetic(final double[] sizes, final boolean forced, final double header,
			final double groupSize) throws Exception {
		TableBox oracle = table(sizes, sizes.length, header, 1.3, !forced, false);
		TableBox shadow = table(sizes, 12, header, 1.3, !forced, true);
		if (groupSize > 0) {
			oracle.getTableBody(0).getInnerTableParams().size = Length.create(groupSize, LengthType.ABSOLUTE);
			shadow.getTableBody(0).getInnerTableParams().size = Length.create(groupSize, LengthType.ABSOLUTE);
		}
		final var sharedHeader = shadow.getTableHeader();
		for (final int count : new int[] { 9, 7, 5 }) {
			final IncompleteTablePlan before = shadow.getIncompletePlan();
			bits(oracle.getTableBody(0).getPageSize(), before.groupSize());
			bits(oracle.getInnerHeight(), before.tableSize());
			final double limit = cutLimit(oracle, count);
			final TableBox oracleNext = split(oracle, limit, count, forced);
			final TableBox shadowNext = split(shadow, limit, count, forced);
			assertEquals(count, oracle.getTableBody(0).getTableRowCount());
			assertEquals(fragment(oracle), fragment(shadow));
			final IncompleteTablePlan.Cut cut = before.cut();
			assertEquals(forced ? SplitKind.FORCED : SplitKind.AUTO, cut.kind());
			assertEquals(before.start() + count, cut.cut());
			assertEquals(cut.cut(), before.visibleEnd());
			bits(oracle.getTableBody(0).getPageSize(), cut.groupKeep());
			bits(oracle.getInnerHeight(), cut.tableKeep());
			bits(oracleNext.getTableBody(0).getPageSize(), cut.groupNext());
			bits(oracleNext.getInnerHeight(), cut.tableNext());
			bits(cut.groupNext(), shadowNext.getIncompletePlan().groupSize());
			bits(cut.tableNext(), shadowNext.getIncompletePlan().tableSize());
			assertSame(sharedHeader, shadowNext.getTableHeader());
			assertEquals(openFrame(oracleNext), frame(shadowNext.getFrame()));
			assertTrue(shadowNext.getInnerHeight() < shadowNext.getIncompletePlan().tableSize());
			oracle = oracleNext;
			shadow = shadowNext;
			append(shadow, sizes, Math.min(sizes.length, shadow.getIncompletePlan().visibleEnd() + 10));
		}
		append(shadow, sizes, sizes.length);
		shadow.complete();
		assertEquals(fragment(oracle), fragment(shadow));
	}

	public void testGroupSplitReturnsPlannedHeightBeforeTableReadsIt() {
		final double[] sizes = new double[100];
		Arrays.fill(sizes, 10.1);
		final TableBox oracle = table(sizes, sizes.length, 0, 0, false, false);
		final TableBox shadow = table(sizes, 12, 0, 0, false, true);
		final var expected = oracle.getTableBody(0);
		final var actual = shadow.getTableBody(0);
		final var expectedNext = (net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox)
				((SplitResult.Split) expected.split(91.7, AutoBreakMode.withCapacity(100),
						IPageBreakableBox.FLAGS_FIRST)).remainder();
		actual.split(91.7, AutoBreakMode.withCapacity(100), IPageBreakableBox.FLAGS_FIRST);
		bits(expected.getPageSize(), actual.getPageSize());
		bits(expectedNext.getPageSize(), shadow.getIncompletePlan().cut().groupNext());
	}

	/** 行内分割しない高さでも、直交行のMOVEをグループはKEEPへ変える。 */
	public void testOrthogonalMoveAndPageFirstKeepArePreservedNumerically() throws Exception {
		for (final WritingMode flow : new WritingMode[] { WritingMode.RL, WritingMode.LR }) {
			for (final byte flags : new byte[] { 0, IPageBreakableBox.FLAGS_FIRST }) {
				final double[] sizes = { 10.1, 10.1, 10.1, 10.1 };
				final TableBox oracle = table(sizes, 4, 0, 0, true, false);
				final TableBox shadow = table(sizes, 2, 0, 0, true, true);
				for (final TableBox table : new TableBox[] { oracle, shadow }) {
					final TableRowBox row = table.getTableBody(0).getTableRow(1);
					orthogonalCell(row, table.getTableParams(), flow);
					assertTrue(row.split(5.1, AutoBreakMode.withCapacity(100), (byte) 0) instanceof SplitResult.Move);
					assertTrue(row.split(5.1, AutoBreakMode.withCapacity(100), IPageBreakableBox.FLAGS_FIRST)
							instanceof SplitResult.Keep);
					final double before = table.getTableBody(0).getPageSize();
					assertTrue(table.getTableBody(0).split(15.2, AutoBreakMode.withCapacity(100), flags)
							instanceof SplitResult.Keep);
					bits(before, table.getTableBody(0).getPageSize());
				}
				// KEEP/MOVEの試行が演算履歴を消費しない。全行を見せて完了後の寸法も比較する。
				final var expected = oracle.split(15.2, AutoBreakMode.withCapacity(100), flags);
				final var actual = shadow.split(15.2, AutoBreakMode.withCapacity(100), flags);
				assertEquals(expected.getClass(), actual.getClass());
				assertNull(shadow.getIncompletePlan().cut());
				append(shadow, sizes, sizes.length);
				shadow.complete();
				assertEquals(fragment(oracle), fragment(shadow));
				// 同じ残余を強制で複数回分割する(列木は完成経路でも分割しないので外す)。
				oracle.setTableColumnGroup(null);
				shadow.setTableColumnGroup(null);
				final TableBox expectedNext = split(oracle, 100, 1, true);
				final TableBox actualNext = split(shadow, 100, 1, true);
				assertEquals(fragment(oracle), fragment(shadow));
				final TableBox expectedTail = split(expectedNext, 100, 1, true);
				final TableBox actualTail = split(actualNext, 100, 1, true);
				assertEquals(fragment(expectedNext), fragment(actualNext));
				assertEquals(fragment(expectedTail), fragment(actualTail));
			}
		}
	}

	private static void orthogonalCell(final TableRowBox row, final TableParams table, final WritingMode flow) {
		final BlockParams params = new BlockParams();
		params.fontStyle = table.fontStyle;
		params.flow = flow;
		final TableCellBox cell = new TableCellBox(params, new TableCellPos(), new FlowContainer());
		cell.setWidth(20.1);
		cell.setHeight(row.getPageSize());
		row.addTableSourceCell(cell);
	}

	public void testAutomaticAndForcedTableSubtractionsAreDistinct() throws Exception {
		final double[] sizes = new double[100];
		Arrays.fill(sizes, 10.1);
		final double[] heights = new double[2];
		for (int i = 0; i < 2; ++i) {
			final TableBox oracle = table(sizes, sizes.length, 0, 0, false, false);
			final TableBox shadow = table(sizes, 12, 0, 0, false, true);
			split(oracle, 91.7, 9, i == 1);
			split(shadow, 91.7, 9, i == 1);
			assertEquals(fragment(oracle), fragment(shadow));
			heights[i] = shadow.getInnerHeight();
			assertTrue(Double.doubleToLongBits(shadow.getTableBody(0).getPageSize())
					!= Double.doubleToLongBits(heights[i]));
		}
		assertTrue(Double.doubleToLongBits(heights[0]) != Double.doubleToLongBits(heights[1]));
	}

	public void testCompletedTailStillUsesNumericPlanWhenItSplits() throws Exception {
		final double[] sizes = sizes(37, 40);
		final TableBox oracle = table(sizes, sizes.length, 7.3, 1.3, true, false);
		final TableBox shadow = table(sizes, 12, 7.3, 1.3, true, true);
		final TableBox expected = split(oracle, cutLimit(oracle, 9), 9, false);
		final TableBox actual = split(shadow, cutLimit(shadow, 9), 9, false);
		append(actual, sizes, sizes.length);
		actual.complete();
		final TableBox expectedNext = split(expected, cutLimit(expected, 7), 7, false);
		final TableBox actualNext = split(actual, cutLimit(actual, 7), 7, false);
		assertEquals(fragment(expected), fragment(actual));
		assertEquals(fragment(expectedNext), fragment(actualNext));
	}

	public void testForcedScanUsesCompletedPlacementRounding() {
		final double[] sizes = sizes(42, 100);
		final TableBox oracle = table(sizes, sizes.length, 7.3, 1.3, false, false);
		final TableBox shadow = table(sizes, 12, 7.3, 1.3, false, true);
		for (final double start : new double[] { 0, 10.1, Math.nextDown(10.1), Math.nextUp(10.1) }) {
			final double axis = start + oracle.getHeight();
			bits(axis - (oracle.getInnerHeight() + oracle.getFrame().getFrameBottom()),
					shadow.incompleteForceBreakStart(start));
		}
	}

	public void testParentSelectionAtHalfPointBoundaries() throws Exception {
		for (final double boundary : new double[] { 99.5, 100.5 }) {
			for (final double total : new double[] { Math.nextDown(boundary), boundary, Math.nextUp(boundary) }) {
				for (final double header : new double[] { 0, 7.3 }) {
					final double[] sizes = { 20.1, total - header - 20.1, 12.3, 34.7, 40.1, 40.1 };
					parentShadow(sizes, header, 0, false, false);
					parentShadow(sizes, header, 11.7, false, false);
				}
			}
		}
		assertEquals(0, LayoutUtils.compare(Math.nextDown(100.5), 100));
		assertTrue(LayoutUtils.compare(100.5, 100) > 0);
		assertTrue(LayoutUtils.compare(99.5, 100) < 0);
		assertEquals(0, LayoutUtils.compare(Math.nextUp(99.5), 100));
	}

	public void testParentRepeatedAutomaticSplits() throws Exception {
		parentShadow(sizes(17, 64), 7.3, 11.7, false, false);
	}

	public void testParentRepeatedForcedSplits() throws Exception {
		parentShadow(sizes(19, 40), 7.3, 10.1, true, false);
	}

	public void testMoveThenAppendAndComplete() throws Exception {
		assertTrue(parentShadow(new double[] { 40.1, 10.1 }, 0, 70.1, false, false)
				.contains(IncompleteTableStatus.MOVED));
	}

	public void testUnsplittableThenComplete() throws Exception {
		assertTrue(parentShadow(new double[] { 140.1, 0 }, 0, 0, false, false)
				.contains(IncompleteTableStatus.UNSPLITTABLE));
	}

	/** 実 Root.pageBreak の祖先寸法更新・切断・再開・drawPage を通します。 */
	public void testRootAncestorAccountingAtEmissionAndCompletion() throws Exception {
		parentShadow(sizes(29, 48), 7.3, 10.1, false, true);
		parentShadow(sizes(31, 40), 7.3, 10.1, true, true);
	}

	/** 前断片の箱を観測者が持っていても、描画後の本文行を所有し続けない。 */
	public void testDrawnFragmentReleasesBodyAndPreservesRemainder() throws Exception {
		final TableBox table = table(new double[] { 40, 40, 40, 40 }, 3, 7.3, 1.3, false, true);
		final List<List<Long>> pages = new ArrayList<>();
		final BreakableBuilder builder = host(table, true, pages, new ArrayList<>(), null);
		final IncompleteTableResult result = builder.acceptIncompleteTable(table);
		assertEquals(IncompleteTableStatus.SPLIT, result.status());
		assertFalse("解放より先に前頁を描いていない", pages.isEmpty());
		assertEquals("送出済みTableBoxが本文木を保持", 0, table.getTableBodyCount());
		assertSame(table.getTableHeader(), result.remainder().getTableHeader());
		assertEquals(1, result.body().getTableRowCount());
		final long drawnHeight = Double.doubleToLongBits(table.getInnerHeight());
		result.body().addTableRow(row(table.getTableParams(), 40));
		result.complete();
		assertEquals(2, result.body().getTableRowCount());
		assertEquals("描画済みの寸法会計が変わった", drawnHeight, Double.doubleToLongBits(table.getInnerHeight()));
		builder.endFlowBlock();
		builder.endFlowBlock();
		builder.finish();
	}

	public void testFramedRootAtHalfPointBoundariesAndChangingCapacity() throws Exception {
		for (final double boundary : new double[] { 99.5, 100.5 }) {
			for (final double total : new double[] { Math.nextDown(boundary), boundary, Math.nextUp(boundary) }) {
				// 始端はmargin 0.7pt + border-spacingの半分0.3pt。
				final double[] sizes = { 10.1, total - 7.3 - 1.0 - 10.1, 12.3, 34.7, 40.1, 40.1, 10.1 };
				parentShadow(sizes, 7.3, 0, false, true, 1.3, new double[] { 100, 80.5, 120.1 });
			}
		}
	}

	/** 上部はPlannerで除外、下部だけはcomplete後・ラッパー終端前の実Rootを比較する。 */
	public void testRootCaptionFallbackAndBottomCompletionOrder() throws Exception {
		for (final boolean forced : new boolean[] { false, true }) {
			for (final CaptionCase captions : new CaptionCase[] {
					new CaptionCase(40.1, 0), new CaptionCase(0, 20.3), new CaptionCase(40.1, 20.3) }) {
				parentShadow(sizes(63, 48), 7.3, captions.top() > 0 ? 70.1 : 10.1, forced, true,
						1.3, new double[] { 100, 80.5, 120.1 }, captions);
			}
		}
		// 上部captionの反例を残す。第2行までの外寸から0.5ptとその隣接doubleで、
		// Plannerの除外と完成経路への投入を検査する(未完経路の同値を主張しない)。
		final double[] sizes = { 10.1, 40.3, 12.3, 34.7, 40.1, 40.1 };
		final TableBox prefix = table(sizes, 2, 0, 0, false, false);
		final BreakableBuilder probe = host(prefix, true, new ArrayList<>(), new ArrayList<>(), null);
		addCaption(probe, prefix.getTableParams(), 20.1);
		final double prefixEnd = probe.getPageAxis() + prefix.getHeight();
		probe.endFlowBlock();
		probe.endFlowBlock();
		probe.finish();
		for (final double offset : new double[] { -0.5, 0.5 }) {
			final double boundary = prefixEnd + offset;
			bits(Math.abs(prefixEnd - boundary), 0.5);
			for (final double capacity : new double[] { Math.nextDown(boundary), boundary, Math.nextUp(boundary) }) {
				parentShadow(sizes, 0, 0, false, true, 0, new double[] { capacity, 80.5, 120.1 },
						new CaptionCase(20.1, 13.7));
			}
		}
	}

	private record CaptionCase(double top, double bottom) { }

	/** B-2b-5: captionと独立した、非ゼロ始点でのKEEP→MOVE反例。 */
	public void testDeferredEmissionAtHalfPointWithPrecedingContent() throws Exception {
		final double[] sizes = { 10.1, 10.1, 10.1, 10.1, 10.0, 12.3, 14.7, 10.1, 10.1 };
		final double prefix = 10.1 + 10.1 + 10.1 + 10.1 + 10.0;
		bits(0.5, (22.5 + prefix) - 72.4);
		assertEquals(0, LayoutUtils.compare(72.4 - 22.5, prefix));
		assertFalse(TableBuildPlanner.hasRowEmissionOverflow(prefix, 72.4 - 22.5));
		for (final boolean caption : new boolean[] { false, true }) {
			final List<Integer> notifications = deferredBoundaryShadow(sizes, 22.5,
					new double[] { 72.4, 80.5, 120.1 }, caption, 2);
			assertEquals("第5行は保留し、第6行で初回受理、最終行で完了", List.of(6, 9), notifications);
			for (final double offset : new double[] { -0.5, 0.5 }) {
				final double boundary = (22.5 + prefix) + offset;
				for (final double capacity : new double[] {
						Math.nextDown(boundary), boundary, Math.nextUp(boundary) }) {
					deferredBoundaryShadow(sizes, 22.5, new double[] { capacity, 80.5, 120.1 }, caption, 2);
				}
			}
		}
	}

	/** B-2b-6: 合計高は容量+0.5pt を超えるのに、切断走査の逐次減算では −0.4999… で KEEP になる反例。 */
	public void testDeferredEmissionSequentialSubtractionRounding() throws Exception {
		final double[] sizes = { 4.9, 13.2, 4.3, 12.3, 14.7, 10.1 };
		final double capacity = 44.4 - 22.5;
		assertTrue("合計高の比較は通る", TableBuildPlanner.hasRowEmissionOverflow(4.9 + 13.2 + 4.3, capacity));
		assertEquals("逐次減算は同値幅の中", 0, LayoutUtils.compare(((capacity - 4.9) - 13.2) - 4.3, 0));
		assertFalse("切断契約は第3行で保留", TableBuildPlanner.cutDetermined(new double[] { 4.9, 13.2, 4.3 }, (4.9 + 13.2) + 4.3, capacity));
		assertTrue("第4行が見えれば確定", TableBuildPlanner.cutDetermined(new double[] { 4.9, 13.2, 4.3, 12.3 }, ((4.9 + 13.2) + 4.3) + 12.3, capacity));
		for (final boolean caption : new boolean[] { false, true }) {
			final List<Integer> notifications = deferredBoundaryShadow(sizes, 22.5,
					new double[] { 44.4, 55.5, 120.1 }, caption, 2);
			assertEquals("第3行は保留し、第4行で初回受理、最終行で完了", List.of(4, 6), notifications);
		}
	}

	/** codex レビュー 2026-09-08 の反例: 微小行が同値幅の中で KEEP し続け、切断は後続行に依存する。 */
	public void testDeferredEmissionHoldsWhileTinyRowsKeepWithinThreshold() throws Exception {
		final double[] sizes = { 4.9, 0.5, 1.4, 0.3, 0.1, 0.2, 10, 10 };
		final double limit = 20 - 13.1;
		assertFalse("第3行で止まり KEEP が続く間は保留", TableBuildPlanner.cutDetermined(
				new double[] { 4.9, 0.5, 1.4 }, (4.9 + 0.5) + 1.4, limit));
		assertFalse("第6行まで KEEP が続いても保留(最終残 −0.4999…)", TableBuildPlanner.cutDetermined(
				new double[] { 4.9, 0.5, 1.4, 0.3, 0.1, 0.2 }, ((((4.9 + 0.5) + 1.4) + 0.3) + 0.1) + 0.2, limit));
		assertTrue("第7行が見えれば確定", TableBuildPlanner.cutDetermined(
				new double[] { 4.9, 0.5, 1.4, 0.3, 0.1, 0.2, 10 }, (((((4.9 + 0.5) + 1.4) + 0.3) + 0.1) + 0.2) + 10, limit));
		final List<Integer> notifications = deferredBoundaryShadow(sizes, 13.1, new double[] { 20, 20, 120.1 }, false, 2);
		assertEquals("第7行で初回受理、最終行で完了、完成側と同じ 2 ページ", List.of(7, 8), notifications);
	}

	/** 初回受理だけを修正しても通らない、追記通知のちょうど+0.5pt。 */
	public void testDeferredAppendAtHalfPointAndAdjacentDoubles() throws Exception {
		for (final double boundary : new double[] { 50, 51 }) {
			for (final double capacity : new double[] {
					Math.nextDown(boundary), boundary, Math.nextUp(boundary) }) {
				final List<Integer> notifications = deferredBoundaryShadow(
						new double[] { 20, 20, 20, 20, 10.5, 10, 10 }, 0,
						new double[] { 50, capacity, 120 }, false, 3);
				// B-2b-6: 逐次減算の残りがちょうど −0.5(容量 50.0)は実走査でも KEEP に
				// ならない(compare は ±0.5 を同値に含めない)ので第5行で受理できる。
				// 残りが −0.4999…(nextUp(50))と 51 系は第6行まで保留。
				assertEquals(List.of(3, capacity <= 50 ? 5 : 6, 7), notifications);
			}
		}
	}

	/** 同じ反例を実Planner・Pass B/C・セルbindへ通し、既定offとopt-inをD7で比較する。 */
	public void testRetainedHalfPointBoundaryWithPrecedingContent() throws Exception {
		for (final boolean caption : new boolean[] { false, true }) {
			for (final double capacity : new double[] { Math.nextDown(72.4), 72.4, Math.nextUp(72.4) }) {
				final StringBuilder html = new StringBuilder("<!doctype html><html><head><style>"
						+ "@page{size:200pt 120.1pt;margin:0}@page:first{size:200pt " + capacity + "pt}"
						+ "@page:left{size:200pt 80.5pt}body{margin:0;font:1pt/1 serif}"
						+ "table{width:140pt;border-collapse:separate;border-spacing:0;margin:0;border:0}"
						+ "td{padding:0;border:0;break-inside:avoid}"
						+ "caption{caption-side:bottom;height:13.7pt;margin:1.1pt 0 1.3pt}"
						+ "</style></head><body><div style='height:22.5pt;background:black'></div><table>");
				if (caption) html.append("<caption>caption</caption>");
				html.append("<tbody>");
				for (final double size : new double[] { 10.1, 10.1, 10.1, 10.1, 10.0, 12.3, 14.7, 10.1, 10.1 }) {
					html.append("<tr><td style='height:").append(size).append("pt'>row</td></tr>");
				}
				assertEquals("反例のD7は両経路とも2ページ", 2,
						emissionShadow(html.append("</tbody></table></body></html>").toString(), true, null));
			}
		}
	}

	private static List<Integer> deferredBoundaryShadow(final double[] sizes, final double preceding,
			final double[] capacities, final boolean caption, final int pageCount) throws Exception {
		final TableBox oracle = table(sizes, sizes.length, 0, 0, false, false);
		final TableBox shadow = table(sizes, 0, 0, 0, false, true);
		final List<List<Long>> expectedPages = new ArrayList<>(), actualPages = new ArrayList<>();
		final List<String> expectedBreaks = new ArrayList<>(), actualBreaks = new ArrayList<>();
		final BreakableBuilder expected = boundaryHost(oracle, expectedPages, expectedBreaks, capacities, preceding);
		final BreakableBuilder actual = boundaryHost(shadow, actualPages, actualBreaks, capacities, preceding);
		expected.addBound(oracle);
		IncompleteTableResult handle = null;
		final List<Integer> notifications = new ArrayList<>();
		for (int i = 0; i < sizes.length; ++i) {
			if (handle == null) {
				append(shadow, sizes, i + 1);
				if (i == sizes.length - 1) {
					shadow.complete();
					actual.addBound(shadow);
				} else if (shadow.emissionCutDetermined(actual.getPageLimit() - actual.getPageAxis())) {
					handle = actual.acceptIncompleteTable(shadow);
					assertTrue(handle.isAccepted());
					notifications.add(i + 1);
				}
			} else {
				handle.body().addTableRow(row(shadow.getTableParams(), sizes[i]));
				if (i == sizes.length - 1 || handle.hasRowEmissionOverflow()) {
					if (i == sizes.length - 1) handle.complete();
					else handle.rowsAppended();
					notifications.add(i + 1);
				}
			}
		}
		bits(expected.getPageAxis(), actual.getPageAxis());
		if (caption) {
			addCaption(expected, oracle.getTableParams(), 13.7);
			addCaption(actual, shadow.getTableParams(), 13.7);
			bits(expected.getPageAxis(), actual.getPageAxis());
			if (capacities[0] == 72.4) bits(63.3, actual.getPageAxis());
		}
		assertEquals(expectedBreaks, actualBreaks);
		assertEquals(expectedPages, actualPages);
		assertEquals(container(expected.getFlowBox().getContainer()), container(actual.getFlowBox().getContainer()));
		for (int i = 0; i < expected.getFlowCount(); ++i) {
			bits(expected.getFlow(i).box.getInnerHeight(), actual.getFlow(i).box.getInnerHeight());
		}
		expected.endFlowBlock();
		actual.endFlowBlock();
		expected.endFlowBlock();
		actual.endFlowBlock();
		bits(expected.getPageAxis(), actual.getPageAxis());
		expected.finish();
		actual.finish();
		assertEquals("完成側のページ数", pageCount, expectedPages.size());
		assertEquals("未完側のページ数", pageCount, actualPages.size());
		assertEquals(expectedPages, actualPages);
		assertEquals(expectedBreaks, actualBreaks);
		return notifications;
	}

	private static BreakableBuilder boundaryHost(final TableBox table, final List<List<Long>> pages,
			final List<String> breaks, final double[] capacities, final double preceding) {
		final RootBuilder builder = new ShadowRoot(new Generator(table.getTableParams(), pages, capacities), breaks, true);
		builder.startFlowBlock(parent(table.getTableParams()));
		addPreceding(builder, table.getTableParams(), preceding);
		builder.startFlowBlock(parent(table.getTableParams())); // 先行内容の後に匿名ラッパーを開く。
		return builder;
	}

	public void testRowEmissionIsOptIn() throws Exception {
		for (final String property : new String[] { null, "false" }) {
			emissionShadow(emissionDocument(48, "", ""), false, RowEmissionExclusion.DISABLED,
					null, 1, 0, property);
		}
	}

	/** 実際の Retained Pass B/C・セル bind・フレーム描画を D7 の全 double 表現で比較する。 */
	public void testRetainedEmissionWithRealCellsAndChangingPages() throws Exception {
		emissionShadow(emissionDocument(48, "", ""), true, null);
		emissionShadow(emissionDocument(48, "", "<colgroup><col><col></colgroup>"), true, null);
		emissionShadow(emissionDocument(3, "", ""), false, null); // 受理前に全行完成
		emissionShadow(emissionDocument(48, "tr:nth-child(7n){break-after:page}", ""), true, null);
		emissionShadow(emissionDocument(48, "table{margin-bottom:-2pt}", ""), false,
				RowEmissionExclusion.NEGATIVE_END_MARGIN);
		emissionShadow(emissionDocument(48, "td{height:35pt}", ""), false, RowEmissionExclusion.ROW_SPLITTING);
		emissionShadow(emissionDocument(48, "tr{break-after:avoid}", ""), false, RowEmissionExclusion.ROW_SPLITTING);
		emissionShadow(emissionDocument(48, ".ancestor{min-height:2pt}", ""), false,
				RowEmissionExclusion.COMPLEX_ANCESTOR);
		emissionShadow(emissionDocument(48, "tr:nth-child(7n){break-after:page}", "<colgroup><col><col></colgroup>"),
				false, RowEmissionExclusion.FORCED_BREAK_WITH_COLUMNS);
		emissionShadow(emissionDocument(48, "table{border-collapse:collapse}", ""), false,
				RowEmissionExclusion.COLLAPSED_BORDERS);
		emissionShadow(emissionDocument(48, "tbody{height:720.1pt}", ""), false,
				RowEmissionExclusion.GROUP_PAGE_SIZE);
		emissionShadow(emissionDocument(48, "", "<caption>caption</caption>"), false, RowEmissionExclusion.CAPTION);
		emissionShadow(emissionDocument(48, "", "<caption style='caption-side:bottom'>caption</caption>"), true, null);
		emissionShadow(emissionDocument(48, "", "<tfoot><tr><td>F0</td><td>F1</td></tr></tfoot>"), false,
				RowEmissionExclusion.FOOTER);
		emissionShadow(emissionDocument(48, "", "").replace("<td>r0a", "<td rowspan='2'>r0a")
				.replace("<td>r1a</td>", ""), false, RowEmissionExclusion.ROWSPAN);
		emissionShadow(emissionDocument(48, "", "").replace("<table>",
				"<div style='float:left;width:10pt;height:25pt'>float</div><table>"), false,
				RowEmissionExclusion.FLOATING_HOST);
	}

	/** 左右で幅・高さが違う現ページに、後続セルの画像寸法を依存させない。 */
	public void testPageDependentReplacedCellsUseOrdinaryPath() throws Exception {
		for (final String css : new String[] { "max-height:5%", "max-width:5%", "height:5%", "width:5%",
				"min-height:5%", "min-width:5%", "max-height:calc(1pt + 3%)", "max-width:calc(1pt + 3%)" }) {
			emissionShadow(imageEmissionDocument(css), false, RowEmissionExclusion.PAGE_DEPENDENT_CELL_CONTENT);
		}
		// 固定画像の実セルは送出する。viewport単位も解析時に絶対長として凍結される。
		emissionShadow(imageEmissionDocument(""), true, null);
		for (final String unit : new String[] { "vh", "vw", "vmin", "vmax" }) {
			emissionShadow(imageEmissionDocument("max-height:0.5" + unit + ";max-width:0.5" + unit), true, null);
		}
	}

	private static String imageEmissionDocument(final String css) {
		final String image = Path.of("files/unittest/red.png").toAbsolutePath().toUri().toString();
		return emissionDocument(48, "td{height:auto}img{display:block;width:10pt;height:10pt;" + css + "}", "")
				.replace("@page:left{size:200pt 80.5pt}", "@page:right{size:200pt 100pt}@page:left{size:180pt 80.5pt}")
				.replaceAll("<td>r[0-9]+a</td>", "<td><div><img src='" + image + "'></div></td>");
	}

	/** auto高のセル内でもinline-blockの高さ/min-heightは現在頁まで参照し得る。 */
	public void testPageDependentInlineBlocksUseOrdinaryPath() throws Exception {
		for (final String css : new String[] { "height:5%", "min-height:5%", "max-height:5%",
				"height:calc(1pt + 3%)", "min-height:calc(1pt + 3%)", "max-height:calc(1pt + 3%)" }) {
			emissionShadow(inlineBlockEmissionDocument(css), false, RowEmissionExclusion.PAGE_DEPENDENT_CELL_CONTENT);
		}
		emissionShadow(inlineBlockEmissionDocument(""), true, null);
		// FlowBlockBoxの割合高さは直近の親を参照する別経路。
		emissionShadow(inlineBlockEmissionDocument("min-height:5%").replace("display:inline-block", "display:block"),
				true, null);
	}

	private static String inlineBlockEmissionDocument(final String css) {
		return emissionDocument(48, "td{height:auto}.sized{display:inline-block;width:8pt;height:1pt;"
				+ "background:red;" + css + "}", "")
				.replace("@page:left{size:200pt 80.5pt}", "@page:right{size:200pt 100pt}@page:left{size:180pt 80.5pt}")
				.replaceAll("<td>r[0-9]+a</td>", "<td><div><span class='sized'></span></div></td>");
	}

	public void testPageDependentNestedTablesUseOrdinaryPath() throws Exception {
		for (final String display : new String[] { "inline-table", "table" }) {
			for (final String css : new String[] { "height:5%", "min-height:5%" }) {
				final String html = inlineBlockEmissionDocument(css)
						.replace("display:inline-block", "display:" + display)
						.replace("<table>", "<table id='outer-shadow'>")
						.replace("<span class='sized'></span>", "<span class='sized'>"
								+ "<span style='display:table-row'><span style='display:table-cell'>x</span></span></span>");
				emissionShadow(html, false, RowEmissionExclusion.PAGE_DEPENDENT_CELL_CONTENT, "outer-shadow");
			}
		}
	}

	/** 同じ入力位置で32回を超えて送出しても、実行した行の消費で指紋が進む。 */
	public void testLongEmissionMakesProgressAndMatchesOrdinaryPath() throws Exception {
		final long alarms = net.zamasoft.foliojet.layout.fragment.ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		assertTrue("停滞検出の閾値を超える実断片が未観測",
				emissionShadow(emissionDocument(640, "", ""), true, null)
						> net.zamasoft.foliojet.layout.fragment.ContinuationStats.STALLED_AUTO_BREAK_LIMIT);
		assertEquals(alarms, net.zamasoft.foliojet.layout.fragment.ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
	}

	public void testPermanentEmissionFixtureMatchesOrdinaryPath() throws Exception {
		emissionShadow(Files.readString(Path.of("files/unittest/0240-table/row-streaming-emit.html")), true, null);
	}

	public void testPermanentCaptionAndGroupHeightFixturesMatchOrdinaryPath() throws Exception {
		emissionShadow(Files.readString(Path.of("files/unittest/0240-table/row-streaming-caption.html")),
				true, RowEmissionExclusion.CAPTION, null, 3, 1);
		emissionShadow(Files.readString(Path.of("files/unittest/0240-table/row-streaming-group-height.html")),
				false, RowEmissionExclusion.GROUP_PAGE_SIZE, null, 2);
	}

	public void testCaptionSidesAndGroupGrowthWithAutomaticAndForcedBreaks() throws Exception {
		for (final boolean forced : new boolean[] { false, true }) {
			for (final String caption : new String[] { "<caption>top</caption>",
					"<caption style='caption-side:bottom'>bottom</caption>",
					"<caption>top</caption><caption style='caption-side:bottom'>bottom</caption>" }) {
				emissionShadow(emissionDocument(48, "tbody{height:720.1pt}"
						+ "caption{margin:1.1pt 0 1.3pt}"
						+ (forced ? "tr:nth-child(7n){break-after:page}" : ""), caption), false,
						RowEmissionExclusion.GROUP_PAGE_SIZE);
				// グループ高と独立に、上部の除外・下部だけの実送出を検査する。
				final boolean bottomOnly = caption.startsWith("<caption style=");
				emissionShadow(emissionDocument(48, "caption{margin:1.1pt 0 1.3pt}"
						+ (forced ? "tr:nth-child(7n){break-after:page}" : ""), caption), bottomOnly,
						bottomOnly ? null : RowEmissionExclusion.CAPTION);
			}
		}
	}

	public void testSpecifiedAndMinMaxGroupSizesRemainExcluded() throws Exception {
		for (final String css : new String[] { "height:0", "height:480.1pt", "height:50%", "height:calc(50% + 1pt)",
				"min-height:1pt", "max-height:900pt", "min-height:5%", "max-height:50%" }) {
			emissionShadow(emissionDocument(48, "tbody{" + css + "}", ""), false,
					RowEmissionExclusion.GROUP_PAGE_SIZE);
		}
		// ABSOLUTEも行内分割を要する指定は解除しない。
		emissionShadow(emissionDocument(48, "tbody{height:2400pt}", ""), false, RowEmissionExclusion.ROW_SPLITTING);
		emissionShadow(emissionDocument(48, "td{writing-mode:vertical-rl}", ""), false,
				RowEmissionExclusion.ORTHOGONAL_CELL);
		for (final String css : new String[] { "height:900pt", "min-height:1pt", "max-height:900pt" }) {
			emissionShadow(emissionDocument(48, "tbody{height:720.1pt}table{height:820.3pt}.ancestor{" + css + "}",
					""), false, RowEmissionExclusion.COMPLEX_ANCESTOR);
		}
	}

	/** 容量超過前の強制改頁でも、直前の未送出木を必ず採取する。 */
	public void testForcedEmissionAlwaysHasPreNotificationSample() throws Exception {
		final int[] before = { -1 }, forced = { 0 };
		final Path input = Files.createTempFile("b2c-forced-retention-", ".html");
		try {
			Files.writeString(input, emissionDocument(48, "tr:nth-child(3n){break-after:page}", ""));
			try (final AutoCloseable observer = RangeOnlyInvariantTest.observe(RetainedTableBuilder.class,
					"retentionPlanObserver", (BiConsumer<String, RetainedTableBuilder>) (stage, table) -> {
				if (ReplayIntent.current() != ReplayIntent.MAIN) return;
				if (stage.equals("before-row-emission")) before[0] = table.rowRetention().pendingRows();
				if (stage.equals("after-row-emission") || stage.equals("after-row-completion")) {
					assertEquals("同じ追記行で親へ通知する前の採取がない",
							table.rowRetention().pendingRows(), before[0]);
					if (stage.equals("after-row-emission")) ++forced[0];
					before[0] = -1;
				}
			})) {
				TwoPassDigestParityTest.transcode(new TwoPassDigestParityTest.CorpusInput(input.toString(), 1,
						"text/html", Map.of("input.include", "**", "processing.fail-on-fatal-error", "true",
								"processing.table-row-emission", "true")));
			}
			assertTrue("初回受理後の強制送出が未観測", forced[0] > 1);
		} finally {
			Files.deleteIfExists(input);
		}
	}

	private static String emissionDocument(final int rows, final String extraCss, final String columns) {
		final StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:200pt 100pt;margin:0}@page:left{size:200pt 80.5pt}"
				+ "body{margin:0;font:6pt/1 serif}.ancestor{border:0.1pt solid black}"
				+ "table{width:140pt;border-collapse:separate;border-spacing:0;border:0.2pt solid black;"
				+ "margin:0.7pt 0.9pt 1.3pt 1.1pt}td,th{padding:0;border:0.1pt solid black}"
				+ "td{height:9.1pt}" + extraCss + "</style></head><body><div class='ancestor'><table>" + columns
				+ "<thead><tr><th>H0</th><th>H1</th></tr></thead><tbody>");
		for (int i = 0; i < rows; ++i) html.append("<tr><td>r").append(i).append("a</td><td>r")
				.append(i).append("b</td></tr>");
		return html.append("</tbody></table><p>after table</p></div></body></html>").toString();
	}

	private static int emissionShadow(final String html, final boolean shouldEmit,
			final RowEmissionExclusion exclusion) throws Exception {
		return emissionShadow(html, shouldEmit, exclusion, null);
	}

	private static int emissionShadow(final String html, final boolean shouldEmit,
			final RowEmissionExclusion exclusion, final String outerId) throws Exception {
		return emissionShadow(html, shouldEmit, exclusion, outerId, 1);
	}

	private static int emissionShadow(final String html, final boolean shouldEmit,
			final RowEmissionExclusion exclusion, final String outerId, final int tables) throws Exception {
		return emissionShadow(html, shouldEmit, exclusion, outerId, tables, shouldEmit ? tables : 0);
	}

	private static int emissionShadow(final String html, final boolean shouldEmit,
			final RowEmissionExclusion exclusion, final String outerId, final int tables,
			final int streamingTables) throws Exception {
		return emissionShadow(html, shouldEmit, exclusion, outerId, tables, streamingTables, "true");
	}

	private static int emissionShadow(final String html, final boolean shouldEmit,
			final RowEmissionExclusion exclusion, final String outerId, final int tables,
			final int streamingTables, final String emissionProperty) throws Exception {
		final Path input = Files.createTempFile("b2b2-shadow-", ".html");
		try {
			Files.writeString(input, html, StandardCharsets.UTF_8);
			final List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
			final List<List<Long>> expectedRows = new ArrayList<>(), actualRows = new ArrayList<>();
			final List<List<Long>> expectedRoot = new ArrayList<>(), actualRoot = new ArrayList<>();
			final int[] emissions = { 0 }, completions = { 0 }, ordinaryEnds = { 0 };
			final boolean[] sawExclusion = { false };
			final Map<RetainedTableBuilder, Long> anchors = new IdentityHashMap<>();
			for (final boolean ordinary : new boolean[] { true, false }) {
				final List<String> pages = ordinary ? expected : actual;
				try (final AutoCloseable plans = RangeOnlyInvariantTest.observe(RetainedTableBuilder.class,
						"retentionPlanObserver", (BiConsumer<String, RetainedTableBuilder>) (stage, table) -> {
					if (ReplayIntent.current() != ReplayIntent.MAIN) return;
					if (outerId != null) {
						if (stage.equals("after-pass-b")) {
							final var element = table.getTableBox().getTableParams().element;
							if (element == null || element.atts() == null
									|| !outerId.equals(element.atts().getValue("id"))) return;
						} else if (!anchors.containsKey(table)) return;
					}
					if (stage.equals("after-top-captions") || stage.equals("after-table-end")) {
						final RootBuilder root = ((LayoutStack) RangeOnlyInvariantTest.field(table, "layoutStack"))
								.getPageContext();
						final List<Long> state = new ArrayList<>();
						state.add((long) pages.size());
						state.add(Double.doubleToLongBits(root.getPageAxis()));
						for (int i = 0; i < root.getFlowCount(); ++i) {
							state.add(Double.doubleToLongBits(root.getFlow(i).box.getInnerHeight()));
						}
						(ordinary ? expectedRoot : actualRoot).add(state);
					}
					if (stage.equals("after-pass-b")) {
						anchors.put(table, table.getSourceAnchor());
						final List<Long> rows = new ArrayList<>();
						for (final double[] group : RowRetentionReport.rowSizes(table)) {
							rows.add((long) group.length);
							for (final double size : group) rows.add(Double.doubleToLongBits(size));
						}
						(ordinary ? expectedRows : actualRows).add(rows);
						if (!ordinary && exclusion != null && table.rowEmissionExclusions().contains(exclusion)) {
							sawExclusion[0] = true;
						}
						if (ordinary) {
							assertEquals("oracleは既定offの実Plannerで完成経路へ",
									EnumSet.of(RowEmissionExclusion.DISABLED), table.rowEmissionExclusions());
						}
					} else if (stage.equals("after-row-emission") || stage.equals("after-row-completion")) {
						assertFalse(ordinary);
						emissions[0] += table.rowEmittedFragments();
						if (stage.equals("after-row-completion")) ++completions[0];
						assertNull(table.getTableBox());
						assertEquals(anchors.get(table).longValue(), table.getSourceAnchor());
						assertTrue(((List<?>) RangeOnlyInvariantTest.field(table, "bodyGroups")).isEmpty());
						assertNull(RangeOnlyInvariantTest.field(table, "firstRowBox"));
						assertNull(RangeOnlyInvariantTest.field(table, "upperRow"));
						assertNull(RangeOnlyInvariantTest.field(table, "columnGroupBox"));
						assertEquals(1, ((List<?>) RangeOnlyInvariantTest.field(table, "rowGroups")).stream()
								.filter(group -> group == null).count());
						assertTrue(((Map<?, ?>) RangeOnlyInvariantTest.field(table, "rowGroupToRows")).isEmpty());
						assertEquals(0, table.rowRetention().boundRows());
					} else if (!ordinary && stage.equals("after-table-end") && table.getTableBox() != null) {
						++ordinaryEnds[0];
					}
				}); final AutoCloseable drawing = DisplayListDumper.observePages((drawer, page) -> pages.add(digest(drawer)))) {
					final Map<String, String> properties = new HashMap<>(Map.of(
							"input.include", "**", "processing.fail-on-fatal-error", "true"));
					if (!ordinary && emissionProperty != null) {
						properties.put("processing.table-row-emission", emissionProperty);
					}
					TwoPassDigestParityTest.transcode(new TwoPassDigestParityTest.CorpusInput(input.toString(), 1,
							"text/html", properties));
				}
			}
			assertFalse("描画が観測されていない", expected.isEmpty());
			assertEquals("D7のページ数", expected.size(), actual.size());
			for (int i = 0; i < expected.size(); ++i) {
				assertEquals("D7の全byte: page=" + (i + 1), expected.get(i), actual.get(i));
			}
			assertEquals("Pass Bの最終h[]", expectedRows, actualRows);
			assertEquals("上部caption後・下部captionとラッパー終端後の実Root", expectedRoot, actualRoot);
			assertEquals("実断片の送出有無", shouldEmit, emissions[0] > 0);
			assertEquals("送出した表の完了回数", streamingTables, completions[0]);
			assertEquals("完成経路で終えた表数", tables - streamingTables, ordinaryEnds[0]);
			if (exclusion != null) assertTrue("除外理由が観測されていない: " + exclusion, sawExclusion[0]);
			return actual.size();
		} finally {
			Files.deleteIfExists(input);
		}
	}

	private static String digest(final Drawer drawer) {
		try {
			// D7 の serializer 自体を oracle にする。座標を丸める golden dump では代用しない。
			final Class<?> type = Class.forName(TwoPassDigestParityTest.class.getName() + "$DigestSerializer");
			final var constructor = type.getDeclaredConstructor(Map.class);
			constructor.setAccessible(true);
			final var page = type.getDeclaredMethod("page", Drawer.class);
			page.setAccessible(true);
			return new String((byte[]) page.invoke(constructor.newInstance(new IdentityHashMap<>()), drawer),
					StandardCharsets.UTF_8);
		} catch (final ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	public void testNegativeMarginIsExcludedBeforeFirstEmission() {
		final TableBox table = table(new double[] { 40, 40, 40, 40 }, 4, 0, -80, false, false);
		final var facts = facts(false, false, false, false, false, false, false, 4, 1);
		assertEquals(EnumSet.of(RowEmissionExclusion.NEGATIVE_END_MARGIN),
				TableBuildPlanner.rowEmissionExclusionsAfterPassB(table, facts));
		// 完成表なら100ptに収まる。3行で送出してから取り消す形には進まない。
		assertTrue(table.getHeight() < 100);
	}

	public void testAdditionalStageOneExclusions() {
		final TableBox table = table(new double[] { 10.1 }, 1, 0, 0, false, false);
		assertTrue(TableBuildPlanner.rowEmissionExclusionsAfterPassB(table,
				facts(false, false, false, false, false, false, false, 1, 1)).isEmpty());
		assertEquals(EnumSet.of(RowEmissionExclusion.ROW_SPLITTING, RowEmissionExclusion.ORTHOGONAL_CELL,
				RowEmissionExclusion.PAGE_SIDE_EFFECTS, RowEmissionExclusion.COMPLEX_ANCESTOR,
				RowEmissionExclusion.FORCED_BREAK_WITH_COLUMNS, RowEmissionExclusion.EMPTY_TABLE),
				TableBuildPlanner.rowEmissionExclusionsAfterPassB(table,
						facts(true, true, true, true, true, true, false, 0, 0)));
		assertEquals(EnumSet.of(RowEmissionExclusion.EMPTY_TABLE),
				TableBuildPlanner.rowEmissionExclusionsAfterPassB(table,
						facts(false, false, false, false, false, false, false, 0, 1)));
		assertEquals(EnumSet.of(RowEmissionExclusion.EMPTY_TABLE),
				TableBuildPlanner.rowEmissionExclusionsAfterPassB(table,
						facts(false, false, false, false, false, false, false, 1, 0)));
	}

	public void testForcedColumnsAreRejectedBeforeMutatingTheFragment() throws Exception {
		final double[] sizes = sizes(23, 24);
		final TableBox table = table(sizes, 12, 7.3, 1.3, true, true);
		final List<Long> before = fragment(table);
		try {
			split(table, 100, 7, true);
			fail("Forced breaks with a column tree are outside stage one");
		} catch (final IllegalStateException expected) {
			assertEquals(before, fragment(table));
			assertNull(table.getIncompletePlan().cut());
		}
	}

	private static RowEmissionFacts facts(final boolean rowSplit, final boolean orthogonal,
			final boolean effects, final boolean ancestor, final boolean forced, final boolean columns,
			final boolean rowspan, final int rows, final int cols) {
		return new RowEmissionFacts(true, true, true, true, 1, rows, cols, rowspan, false, false, false,
				rowSplit, orthogonal, effects, ancestor, forced, columns);
	}

	private static List<IncompleteTableStatus> parentShadow(final double[] sizes, final double header,
			final double preceding, final boolean forced, final boolean root) throws Exception {
		return parentShadow(sizes, header, preceding, forced, root, 0, null);
	}

	private static List<IncompleteTableStatus> parentShadow(final double[] sizes, final double header,
			final double preceding, final boolean forced, final boolean root, final double margin,
			final double[] capacities) throws Exception {
		return parentShadow(sizes, header, preceding, forced, root, margin, capacities, null);
	}

	private static List<IncompleteTableStatus> parentShadow(final double[] sizes, final double header,
			final double preceding, final boolean forced, final boolean root, final double margin,
			final double[] capacities, final CaptionCase captions) throws Exception {
		final TableBox oracle = table(sizes, sizes.length, header, margin, !forced, false);
		final boolean topCaption = captions != null && captions.top() > 0;
		final var captionExclusions = TableBuildPlanner.rowEmissionExclusionsAfterPassB(oracle,
				new RowEmissionFacts(true, true, true, true, 1, sizes.length, 1, false, false,
						topCaption, false, false, false, false, false, forced, !forced));
		assertEquals(topCaption ? EnumSet.of(RowEmissionExclusion.CAPTION)
				: EnumSet.noneOf(RowEmissionExclusion.class), captionExclusions);
		// 上部captionの0.5pt反例も残す。除外された形は未完表を作らず完成経路へ渡す。
		final boolean emit = captionExclusions.isEmpty();
		final TableBox shadow = table(sizes, emit ? 1 : sizes.length, header, margin, !forced, emit);
		markForcedRows(oracle, 0, forced);
		markForcedRows(shadow, 0, forced);
		final List<List<Long>> expectedPages = new ArrayList<>();
		final List<List<Long>> actualPages = new ArrayList<>();
		final List<String> expectedBreaks = new ArrayList<>();
		final List<String> actualBreaks = new ArrayList<>();
		final BreakableBuilder expected = host(oracle, root, expectedPages, expectedBreaks, capacities);
		final BreakableBuilder actual = host(shadow, root, actualPages, actualBreaks, capacities);
		addPreceding(expected, oracle.getTableParams(), preceding);
		addPreceding(actual, shadow.getTableParams(), preceding);
		if (captions != null) {
			final int before = expectedBreaks.size();
			addCaption(expected, oracle.getTableParams(), captions.top());
			addCaption(actual, shadow.getTableParams(), captions.top());
			bits(expected.getPageAxis(), actual.getPageAxis());
			assertEquals(expectedBreaks, actualBreaks);
			if (preceding == 70.1 && captions.top() > 0) {
				assertTrue("上部キャプションでの改頁が未観測", expectedBreaks.size() > before);
			}
		}
		expected.addBound(oracle);
		final List<IncompleteTableStatus> statuses = new ArrayList<>();
		if (emit) {
			// 実装(RetainedTableBuilder)と同じ切断契約で受理・追記を通知する(B-2b-6):
			// 可視行だけで切断が確定するまで受理せず、追記通知も確定するまで保留する。
			IncompleteTableResult handle = null;
			for (int i = 0; i < sizes.length; ++i) {
				if (i > 0) {
					final TableRowBox row = row(shadow.getTableParams(), sizes[i]);
					if (forced && i % 7 == 6) {
						row.getTableRowPos().pageBreakAfter = PageBreakMode.PAGE;
					}
					if (handle == null) {
						final var body = shadow.getTableBody(0);
						final int count = body.getTableRowCount();
						final double size = body.getPageSize();
						body.addTableRow(row);
						shadow.updateIncompleteBody(body, count, size);
					} else {
						handle.body().addTableRow(row);
					}
				}
				if (handle == null) {
					if (i == sizes.length - 1) {
						shadow.complete();
						actual.addBound(shadow);
					} else if (shadow.emissionCutDetermined(actual.getPageLimit() - actual.getPageAxis())) {
						handle = actual.acceptIncompleteTable(shadow);
						assertTrue(handle.isAccepted());
						statuses.add(handle.status());
					}
				} else if (i == sizes.length - 1) {
					statuses.add(handle.complete());
				} else if (handle.hasRowEmissionOverflow()) {
					statuses.add(handle.rowsAppended());
				}
			}
		} else {
			actual.addBound(shadow);
		}
		bits(expected.getPageAxis(), actual.getPageAxis());
		assertEquals(expectedBreaks, actualBreaks);
		assertEquals(expectedPages, actualPages);
		assertEquals(container(expected.getFlowBox().getContainer()), container(actual.getFlowBox().getContainer()));
		if (captions != null) {
			addCaption(expected, oracle.getTableParams(), captions.bottom());
			addCaption(actual, shadow.getTableParams(), captions.bottom());
			bits(expected.getPageAxis(), actual.getPageAxis());
			assertEquals(expectedBreaks, actualBreaks);
			assertEquals(expectedPages, actualPages);
		}
		if (root) {
			assertEquals(expected.getFlowCount(), actual.getFlowCount());
			for (int i = 0; i < expected.getFlowCount(); ++i) {
				bits(expected.getFlow(i).box.getInnerHeight(), actual.getFlow(i).box.getInnerHeight());
			}
			expected.endFlowBlock();
			actual.endFlowBlock();
			if (captions != null) {
				// 匿名ラッパーを閉じてから後続本文を配置する。
				addPreceding(expected, oracle.getTableParams(), 12.3);
				addPreceding(actual, shadow.getTableParams(), 12.3);
				bits(expected.getPageAxis(), actual.getPageAxis());
			}
			expected.endFlowBlock();
			actual.endFlowBlock();
		}
		expected.finish();
		actual.finish();
		assertEquals(expectedPages, actualPages);
		assertEquals(expectedBreaks, actualBreaks);
		return statuses;
	}

	private static void addCaption(final BreakableBuilder builder, final TableParams params, final double size) {
		if (size == 0) return;
		final BlockParams captionParams = new BlockParams();
		captionParams.fontStyle = params.fontStyle;
		// 高さだけの空箱はpaintsBeyondPageがfalseで、自動改頁の根拠にならない。
		// 描画を持つcaptionにし、上部captionで改頁するケースを実際に通す。
		final Background background = Background.create(
				new net.zamasoft.foliojet.css.value.ColorValue(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(0, 0, 0)),
				(BackgroundImage) null, Background.BORDER_BOX);
		captionParams.frame = RectFrame.create(Insets.create(1.1, 0, 1.3, 0,
				LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE),
				null, background, null);
		final FlowBlockBox caption = new FlowBlockBox(captionParams, new TableCaptionPos());
		builder.startFlowBlock(caption);
		addPreceding(builder, params, size);
		builder.endFlowBlock();
	}

	private static BreakableBuilder host(final TableBox table, final boolean root,
			final List<List<Long>> pages, final List<String> breaks, final double[] capacities) {
		if (!root) {
			return new ShadowBuilder(table.getTableParams(), pages, breaks);
		}
		final RootBuilder builder = new ShadowRoot(new Generator(table.getTableParams(), pages, capacities), breaks,
				capacities != null);
		builder.startFlowBlock(parent(table.getTableParams()));
		builder.startFlowBlock(parent(table.getTableParams()));
		return builder;
	}

	private static void addPreceding(final BreakableBuilder builder, final TableParams params, final double size) {
		if (size == 0) {
			return;
		}
		final FlowBlockBox block = parent(params);
		block.setPageAxis(size);
		builder.addBound(block);
	}

	private static void markForcedRows(final TableBox table, final int start, final boolean forced) {
		if (!forced) {
			return;
		}
		for (int i = 0; i < table.getTableBody(0).getTableRowCount(); ++i) {
			if ((start + i) % 7 == 6) {
				table.getTableBody(0).getTableRow(i).getTableRowPos().pageBreakAfter = PageBreakMode.PAGE;
			}
		}
	}

	private static TableBox table(final double[] sizes, final int visible, final double header,
			final double bottomMargin, final boolean withColumns, final boolean incomplete) {
		final TableParams params = IncompleteTableIntakeTest.table(0, 1).getTableParams();
		// 演算試験は端数の始端・左右フレームと終端を持つ。境界選択試験は枠なし。
		final boolean framed = bottomMargin > 0;
		params.borderSpacingV = framed ? 0.6 : 0;
		params.borderSpacingH = framed ? 0.4 : 0;
		params.frame = RectFrame.create(Insets.create(framed ? 0.7 : 0, framed ? 0.9 : 0,
				bottomMargin, framed ? 1.1 : 0, LengthType.ABSOLUTE,
				LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE), null, null, null);
		final TableBox table = new TableBox(params, parent(params));
		table.calculateFrame(200);
		table.setSize(200, 0);
		if (header > 0) {
			table.setTableHeader(group(params, header));
		}
		table.addTableBody(group(params, Arrays.copyOf(sizes, visible)));
		if (withColumns) {
			addColumns(table);
		}
		if (incomplete) {
			table.markIncomplete();
			table.setIncompletePlan(new IncompleteTablePlan(sizes, header));
		}
		return table;
	}

	private static double[] sizes(final int seed, final int count) {
		final Random random = new Random(0xB2B10000L + seed);
		final double[] sizes = new double[count];
		for (int i = 0; i < count; ++i) {
			sizes[i] = i % 3 == 0 ? 10.1 : 8.1 + random.nextInt(40) / 10.0;
		}
		return sizes;
	}

	private static void append(final TableBox table, final double[] sizes, final int end) {
		final var body = table.getTableBody(0);
		final int count = body.getTableRowCount();
		final double size = body.getPageSize();
		for (int i = table.getIncompletePlan().visibleEnd(); i < end; ++i) {
			body.addTableRow(row(table.getTableParams(), sizes[i]));
		}
		if (body.getTableRowCount() != count) {
			table.updateIncompleteBody(body, count, size);
		}
	}

	private static double cutLimit(final TableBox table, final int count) {
		double limit = table.getFrame().getFrameTop();
		if (table.getTableHeader() != null) {
			limit += table.getTableHeader().getPageSize();
		}
		for (int i = 0; i < count; ++i) {
			limit += table.getTableBody(0).getTableRow(i).getPageSize();
		}
		return limit + 0.75;
	}

	private static TableBox split(final TableBox table, final double limit, final int count, final boolean forced) {
		final BreakMode mode = forced ? new TableForceBreakMode(table.getTableBody(0).getTableRow(count - 1),
				PageBreakMode.PAGE, 0, count - 1) : AutoBreakMode.withCapacity(100);
		final SplitResult result = table.split(limit, mode, IPageBreakableBox.FLAGS_FIRST);
		assertTrue(result instanceof SplitResult.Split);
		return (TableBox) ((SplitResult.Split) result).remainder();
	}

	private static List<Long> fragment(final TableBox table) throws Exception {
		final List<Long> values = new ArrayList<>();
		values.add(Double.doubleToLongBits(table.getInnerHeight()));
		values.add(Double.doubleToLongBits(table.getInnerWidth()));
		values.addAll(frame(table.getFrame()));
		values.add(Double.doubleToLongBits(table.getTableBody(0).getPageSize()));
		values.add(Double.doubleToLongBits(table.getTableBody(0).getLineSize()));
		values.add((long) table.getTableBody(0).getTableRowCount());
		for (int i = 0; i < table.getTableBody(0).getTableRowCount(); ++i) {
			final TableRowBox row = table.getTableBody(0).getTableRow(i);
			values.add(Double.doubleToLongBits(row.getPageSize()));
			values.add(Double.doubleToLongBits(row.getLineSize()));
			values.add((long) row.getCellCount());
			for (int j = 0; j < row.getCellCount(); ++j) {
				final TableCellBox cell = row.getCell(j).getCellBox();
				values.add(Double.doubleToLongBits(cell.getWidth()));
				values.add(Double.doubleToLongBits(cell.getHeight()));
			}
		}
		final var columns = columns(table);
		if (columns != null) {
			values.add(Double.doubleToLongBits(columns.getPageSize()));
			values.add(Double.doubleToLongBits(columns.getLineSize()));
			columns.eachColumn((column, col, span) -> {
				values.add(Double.doubleToLongBits(column.getPageSize()));
				values.add(Double.doubleToLongBits(column.getLineSize()));
			});
		}
		return values;
	}

	private static List<Long> frame(final AbsoluteRectFrame frame) {
		final List<Long> values = new ArrayList<>();
		for (final double value : new double[] { frame.getFrameTop(), frame.getFrameBottom(), frame.getFrameLeft(),
				frame.getFrameRight(), frame.margin.top, frame.margin.bottom, frame.margin.left, frame.margin.right,
				frame.padding.top, frame.padding.bottom, frame.padding.left, frame.padding.right }) {
			values.add(Double.doubleToLongBits(value));
		}
		return values;
	}

	private static List<Long> openFrame(final TableBox table) {
		return frame(net.zamasoft.foliojet.layout.fragment.TableCutter.incompleteFrame(false, false, table.getFrame()));
	}

	private static List<Long> container(final Container container) {
		final List<Long> values = new ArrayList<>();
		container.eachFlowBox(box -> {
			try {
				if (box instanceof TableBox table) {
					values.addAll(fragment(table));
				} else if (box instanceof AbstractContainerBox block) {
					values.add(Double.doubleToLongBits(block.getInnerHeight()));
					values.addAll(container(block.getContainer()));
				}
			} catch (final Exception e) {
				throw new AssertionError(e);
			}
		});
		return values;
	}

	private static void bits(final double expected, final double actual) {
		assertEquals("expected=" + expected + ", actual=" + actual,
				Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
	}

	private static String breakKind(final BreakMode mode, final double capacity) {
		if (mode instanceof TableForceBreakMode force) {
			return "forced:" + force.breakType;
		}
		assertTrue(mode instanceof AutoBreakMode);
		bits(capacity, ((AutoBreakMode) mode).fragmentCapacity);
		return "auto";
	}

	private static final class ShadowBuilder extends IncompleteTableIntakeTest.PagingBuilder {
		private final List<List<Long>> pages;
		private final List<String> breaks;

		ShadowBuilder(final TableParams params, final List<List<Long>> pages, final List<String> breaks) {
			super(params, 100); // 成長する親の内寸を容量として使わない。
			this.pages = pages;
			this.breaks = breaks;
		}

		@Override
		protected boolean pageBreak(final BreakMode mode, final byte flags) {
			final var previous = this.getRootBox();
			final String kind = breakKind(mode, this.getPageLimit());
			final boolean result = super.pageBreak(mode, flags);
			if (result) {
				this.breaks.add(kind);
				this.pages.add(container(previous.getContainer()));
			}
			return result;
		}
	}

	private static final class ShadowRoot extends RootBuilder {
		private final List<String> breaks;
		private final boolean variableCapacity;

		ShadowRoot(final PageGenerator generator, final List<String> breaks, final boolean variableCapacity) {
			super(generator, MODE_PAGE_BREAK);
			this.breaks = breaks;
			this.variableCapacity = variableCapacity;
		}

		@Override
		public double getPageLimit() {
			return this.variableCapacity ? super.getPageLimit() : 100;
		}

		@Override
		protected boolean pageBreak(final BreakMode mode, final byte flags) {
			final String kind = breakKind(mode, this.getPageLimit());
			final boolean result = super.pageBreak(mode, flags);
			if (result) {
				this.breaks.add(kind);
			}
			return result;
		}
	}

	/** drawPage 内で即座に採取し、rowsAppended が戻った後の補正では通らないようにする。 */
	private static final class Generator implements PageGenerator {
		private final UserAgent ua = new PDFUserAgent() { };
		private final TableParams params;
		private final List<List<Long>> pages;
		private final double[] capacities;
		private int page;

		Generator(final TableParams params, final List<List<Long>> pages, final double[] capacities) {
			this.params = params;
			this.pages = pages;
			this.capacities = capacities == null ? new double[] { 100 } : capacities;
		}

		public UserAgent getUserAgent() { return this.ua; }
		public PageBreakMode getPageSide() { return PageBreakMode.AUTO; }

		public PageBox nextPage() {
			final BlockParams params = new BlockParams();
			params.fontStyle = this.params.fontStyle;
			params.size = Dimension.create(200, this.capacities[this.page++ % this.capacities.length],
					LengthType.ABSOLUTE, LengthType.ABSOLUTE);
			return new PageBox(params, this.ua);
		}

		public boolean drawPage(final PageBox page, final boolean lastPage, final boolean forced) {
			this.pages.add(container(page.getContainer()));
			return true;
		}
	}
}
