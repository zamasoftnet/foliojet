package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AutoPosition;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.Fiducial;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.segment.BoxKind;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * TwoPass T3c: 配置付き表のrecipe・範囲再生・排他所有の不変条件。
 * 全コーパスの件数はDualPathCensusCrossTabTestで観測し、ここでは対象文書の契約を固定する。
 */
public final class OpaqueTableRangeTest extends TestCase {
	private static final List<String> DOCUMENTS = List.of(
			"0070-table-layout/float-in-fixed.html",
			"0380-inline-block/inline-block-in-absolute.html");
	public void testT0Documents() throws Exception {
		for (final String doc : DOCUMENTS) {
			ContinuationStats.reset();

			final List<BoxRecipe.PlacedTable> tables = new ArrayList<>();
			final List<BoxRecipe.PlacedTable> replays = new ArrayList<>();
			try (final AutoCloseable append = observeTables(tables);
					final AutoCloseable replay = observeTableReplays(replays);
					final var census = ContinuationStats.beginTwoPassCensus()) {
				ContinuationStats.reset();
				final List<byte[]> range = TwoPassFlowSealTest.render(new File("files/unittest", doc));

				assertTrue(doc + ": 範囲再生未発火", ContinuationStats.RANGE_FIRST_BINDS.get() > 0);
				assertEquals(doc, 0, ContinuationStats.twoPassSealRejects(ContinuationStats.TwoPassSealReject.OPAQUE_RANGE));
				assertTableReplay(doc, tables, replays,
						doc.equals(DOCUMENTS.get(0)) ? BoxKind.FLOAT_BLOCK : BoxKind.INLINE_BLOCK);
			}
		}
	}

	public void testMinimalFixtures() throws Exception {
		for (final String name : List.of("float-in-fixed-cell", "inline-table", "absolute-table",
				"float-across-pages", "float-in-table", "absolute-in-table")) {
			final File file = TwoPassGridFlexRangeTest.fixtureFile("table-" + name);
			ContinuationStats.reset();
			final List<BoxRecipe.PlacedTable> tables = new ArrayList<>();
			final List<BoxRecipe.PlacedTable> replays = new ArrayList<>();
			try (final AutoCloseable append = observeTables(tables);
					final AutoCloseable replay = observeTableReplays(replays)) {
				ContinuationStats.reset();
				final List<byte[]> range = TwoPassFlowSealTest.render(file);
				TwoPassGridFlexRangeTest.assertGoldenPages("0500-twopass-range_" + file.getName().replace(".html", ""), range);
				final BoxKind placement = switch (name) {
				case "inline-table" -> BoxKind.INLINE_BLOCK;
				case "absolute-table", "absolute-in-table" -> BoxKind.ABSOLUTE;
				default -> BoxKind.FLOAT_BLOCK;
				};
				assertTableReplay(name, tables, replays, placement);

				assertEquals(name, 0, ContinuationStats.twoPassSealRejects(ContinuationStats.TwoPassSealReject.ABSOLUTE_RANGE));
				if (name.equals("float-across-pages")) {
					assertTrue(name, range.size() > 1);
					assertTrue(name + ": 2頁目に表の行がない",
							new String(range.get(1), StandardCharsets.UTF_8).contains("Text[\"TABLEROW\""));
					assertTrue(name + ": 表の行が複数頁に渡っていない", range.stream()
							.filter(page -> new String(page, StandardCharsets.UTF_8).contains("Text[\"TABLEROW\""))
							.count() > 1);
				}
			}
		}
	}

	private static void assertTableReplay(final String doc, final List<BoxRecipe.PlacedTable> tables,
			final List<BoxRecipe.PlacedTable> replays, final BoxKind placement) {
		assertTrue(doc + ": " + placement + "表の記録未発火",
				tables.stream().anyMatch(table -> table.placement().kind() == placement));
		assertTrue(doc + ": " + placement + "表自体の範囲再生未発火",
				replays.stream().anyMatch(table -> table.placement().kind() == placement
						&& tables.stream().anyMatch(recorded -> recorded == table)));
	}

	private static AutoCloseable observeTableReplays(final List<BoxRecipe.PlacedTable> replays) throws Exception {
		final Field field = BoxRecipeBoxFactory.class.getDeclaredField("placedTableReplayObserver");
		field.setAccessible(true);
		final Object saved = field.get(null);
		final Consumer<BoxRecipe.PlacedTable> observer = replays::add;
		field.set(null, observer);
		return () -> field.set(null, saved);
	}

	/** compact前のappendを観測し、ログ全体にOpaqueがないことを確かめる。 */
	private static AutoCloseable observeTables(final List<BoxRecipe.PlacedTable> tables) throws Exception {
		final Field field = LayoutSource.class.getDeclaredField("appendObserver");
		field.setAccessible(true);
		final Object saved = field.get(null);
		final Consumer<LayoutSource.Event> observer = event -> {
			assertFalse("主ログにOpaqueが残っている", event instanceof LayoutSource.Opaque);
			if (event instanceof LayoutSource.Start start && start.recipe() instanceof BoxRecipe.PlacedTable table) {
				tables.add(table);
			}
		};
		field.set(null, observer);
		return () -> field.set(null, saved);
	}

	/** TABLEの1対構造、配置索引、exact照合、params aliasと未知subclassのfail closed。 */
	public void testRecipeAndSourceContract() throws Exception {
		final Method freeze = Class.forName("net.zamasoft.foliojet.css.style.RecordingLayoutSink")
				.getDeclaredMethod("boxRecipe", INonReplacedBox.class);
		freeze.setAccessible(true);
		for (final BoxKind placement : List.of(BoxKind.FLOAT_BLOCK, BoxKind.INLINE_BLOCK, BoxKind.ABSOLUTE)) {
			final TableParams params = tableParams();
			params.borderSpacingH = 7;
			params.borderSpacingV = 9;
			params.opacity = 0.6f;
			final AbstractBlockBox block = switch (placement) {
			case FLOAT_BLOCK -> new FloatBlockBox(params, new FloatPos());
			case INLINE_BLOCK -> new InlineBlockBox(params, new InlinePos());
			case ABSOLUTE -> new AbsoluteBlockBox(params, new AbsolutePos());
			default -> throw new AssertionError(placement);
			};
			switch (block.getPos()) {
			case FloatPos pos -> { pos.floating = FloatSide.END; pos.clear = ClearMode.BOTH; }
			case InlinePos pos -> { pos.lineHeight = 23; pos.offset = Offset.HALF_OFFSET; }
			case AbsolutePos pos -> {
				pos.location = Insets.create(3, 5, 7, 11, LengthType.ABSOLUTE, LengthType.ABSOLUTE,
						LengthType.ABSOLUTE, LengthType.ABSOLUTE);
				pos.autoPosition = AutoPosition.INLINE;
				pos.fiducial = Fiducial.ALL_PAGE;
			}
			default -> throw new AssertionError(placement);
			}
			final TableBox original = new TableBox(params, block);
			final BoxRecipe.PlacedTable recipe = (BoxRecipe.PlacedTable) freeze.invoke(null, original);
			// freeze後に元のparams/posを変更しても再構築値は変わらない。
			params.borderSpacingH = 70;
			params.borderSpacingV = 90;
			params.opacity = 1;
			switch (block.getPos()) {
			case FloatPos pos -> { pos.floating = FloatSide.START; pos.clear = ClearMode.NONE; }
			case InlinePos pos -> { pos.lineHeight = 1; pos.offset = null; }
			case AbsolutePos pos -> {
				pos.location = Insets.AUTO_INSETS;
				pos.autoPosition = AutoPosition.BLOCK;
				pos.fiducial = Fiducial.CONTEXT;
			}
			default -> throw new AssertionError(placement);
			}
			assertEquals(BoxKind.TABLE, recipe.kind());
			assertEquals(placement, recipe.placement().kind());
			final TableBox copy = (TableBox) BoxRecipeBoxFactory.create(recipe);
			assertEquals(block.getClass(), copy.getBlockBox().getClass());
			assertSame(copy.getTableParams(), copy.getBlockBox().getParams());
			assertNotSame(params, copy.getTableParams());
			assertNotSame(block.getPos(), copy.getBlockBox().getPos());
			assertEquals(7, copy.getTableParams().borderSpacingH, 0);
			assertEquals(9, copy.getTableParams().borderSpacingV, 0);
			assertEquals(0.6f, copy.getTableParams().opacity, 0);
			switch (copy.getBlockBox().getPos()) {
			case FloatPos pos -> { assertEquals(FloatSide.END, pos.floating); assertEquals(ClearMode.BOTH, pos.clear); }
			case InlinePos pos -> { assertEquals(23, pos.lineHeight, 0); assertSame(Offset.HALF_OFFSET, pos.offset); }
			case AbsolutePos pos -> {
				assertEquals(3, pos.location.getTop(), 0);
				assertEquals(5, pos.location.getRight(), 0);
				assertEquals(7, pos.location.getBottom(), 0);
				assertEquals(11, pos.location.getLeft(), 0);
				assertEquals(LengthType.ABSOLUTE, pos.location.getTopType());
				assertEquals(AutoPosition.INLINE, pos.autoPosition);
				assertEquals(Fiducial.ALL_PAGE, pos.fiducial);
			}
			default -> throw new AssertionError(placement);
			}
			assertUnsupported(freeze, new TableBox(params, block) { });
			assertUnsupported(freeze, new TableBox(params, new FloatBlockBox(params, new FloatPos()) { }));
			assertUnsupported(freeze, new TableBox(tableParams(), block));
			try (final LayoutSource source = new LayoutSource()) {
				source.append(new LayoutSource.Chars(0, "before".toCharArray(), false));
				final long start = source.append(new LayoutSource.Start(recipe));
				source.append(new LayoutSource.Chars(6, "cell".toCharArray(), false));
				final long end = source.append(new LayoutSource.EndBlock());
				assertEquals(end, source.endOf(start));
				assertFalse("表の子だけでは表ビルダーが開かれない",
						net.zamasoft.foliojet.layout.SourceReplayer.canReplayChildren(source, start, params.flow));
				final SegmentEvent converted = LayoutSourceEventConverter.convert(source.get(start));
				assertTrue(converted instanceof SegmentEvent.BeginBox);
				assertSame(recipe, ((SegmentEvent.BeginBox) converted).recipe());
				try (final var lease = source.retainFrom(start)) {
					source.compact(source.nextId());
					assertEquals(end, source.endOf(start));
					assertFalse(source.containsOpaque(start, end));
					assertTrue(source.containsTable(start, end));
					assertEquals(placement == BoxKind.FLOAT_BLOCK, source.containsFloat(start, end));
					assertEquals(placement == BoxKind.ABSOLUTE, source.containsAbsolute(start, end));
					assertEquals(placement != BoxKind.ABSOLUTE, source.absoluteStartsExactly(start, end, Set.of()));
					assertEquals(placement == BoxKind.ABSOLUTE, source.absoluteStartsExactly(start, end, Set.of(start)));
					assertFalse(source.absoluteStartsExactly(start, end, Set.of(start, end)));
				}
			}
		}
	}

	private static void assertUnsupported(final Method freeze, final INonReplacedBox box) throws Exception {
		try {
			freeze.invoke(null, box);
			fail("未知の箱・配置を受理した");
		} catch (final java.lang.reflect.InvocationTargetException expected) {
			assertTrue(expected.getCause() instanceof net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException);
		}
	}

	private static TableParams tableParams() {
		final TableParams params = new TableParams();
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	/** ownership ledgerの検証核を、正常な計画から条件を1つずつ変えて検査する。 */
	public void testCollectAbsorbableTableRejectsWrongAnchorAndAttachedAbsolute() throws Exception {
		for (final String condition : List.of("unattached", "wrong-table-anchor", "wrong-block-anchor", "attached-absolute")) {
			final TableParams params = tableParams();
			final AbsoluteBlockBox block = new AbsoluteBlockBox(params, new AbsolutePos());
			final TableBox table = new TableBox(params, block);
			final RetainedTableBuilder retained = new RetainedTableBuilder(null, table);
			try (final LayoutSource source = new LayoutSource()) {
				final long start = source.append(new LayoutSource.Start(freezeBox(table)));
				source.append(new LayoutSource.Chars(0, "cell".toCharArray(), false));
				final long end = source.append(new LayoutSource.EndBlock());
				// anchorは付与後不変なので、負例ごとに新しい箱へ一度だけ付ける。
				table.setSourceAnchor(condition.equals("wrong-table-anchor") ? end : start);
				block.setSourceAnchor(condition.equals("wrong-block-anchor") ? end : start);
				if (condition.equals("attached-absolute")) {
					assertTrue("正常な未係留表を拒否", collectTable(retained, source, start, end));
					final TwoPassBlockBuilder detached = new TwoPassBlockBuilder(null, block);
					detached.startReplayOnly(null);
					detached.finishReplayOnly(-1, true);
					block.prepareBind(detached);
					assertFalse(block.isUnattachedForParentRange());
				}
				assertEquals(condition, condition.equals("unattached"), collectTable(retained, source, start, end));
			}
		}
	}

	public void testCollectAbsorbableTableRejectsTerminalCells() throws Exception {
		for (final RangeHandle.State terminal : List.of(RangeHandle.State.CONSUMED,
				RangeHandle.State.SUBSUMED, RangeHandle.State.ABANDONED)) {
			final TableParams params = tableParams();
			final TableBox table = new TableBox(params, new FloatBlockBox(params, new FloatPos()));
			final TableCellBox cellBox = new TableCellBox(tableParams(), new TableCellPos(), new FlowContainer());
			final RetainedTableBuilder retained = new RetainedTableBuilder(null, table);
			try (final LayoutSource source = new LayoutSource()) {
				final long start = source.append(new LayoutSource.Start(freezeBox(table)));
				table.setSourceAnchor(start);
				cellBox.setSourceAnchor(source.append(new LayoutSource.Start(freezeBox(cellBox))));
				final long content = source.append(new LayoutSource.Chars(0, "cell".toCharArray(), false));
				source.append(new LayoutSource.EndBlock());
				final long end = source.append(new LayoutSource.EndBlock());
				final RangeHandle handle = new RangeHandle(source, content, content, IntrinsicSizes.ZERO,
						RangeHandle.ReplayMode.CHILDREN_ONLY);
				try {
					addSealedCell(retained, cellBox, handle);
					assertTrue(terminal + ": OPENなセルを拒否", collectTable(retained, source, start, end));
					if (terminal == RangeHandle.State.CONSUMED) {
						try {
							handle.bind(null, null);
							fail("不正なbind先を受理した");
						} catch (final NullPointerException expected) {
							// 失敗したbindもリースを消費する。成功時はHTML fixtureで検査する。
						}
					} else if (terminal == RangeHandle.State.SUBSUMED) {
						try (final var parentLease = source.retainFrom(start)) {
							handle.subsume();
						}
					} else {
						handle.abandon();
					}
					assertEquals(terminal, handle.state());
					assertFalse(terminal + ": 終端済みセルを受理", collectTable(retained, source, start, end));
				} finally {
					if (handle.state() == RangeHandle.State.OPEN) handle.abandon();
				}
			}
		}
	}

	private static BoxRecipe freezeBox(final INonReplacedBox box) throws Exception {
		final Method freeze = Class.forName("net.zamasoft.foliojet.css.style.RecordingLayoutSink")
				.getDeclaredMethod("boxRecipe", INonReplacedBox.class);
		freeze.setAccessible(true);
		return (BoxRecipe) freeze.invoke(null, box);
	}

	private static boolean collectTable(final RetainedTableBuilder retained, final LayoutSource source,
			final long from, final long to) throws Exception {
		final Method collect = TwoPassBlockBuilder.class.getDeclaredMethod("collectAbsorbableTable",
				RetainedTableBuilder.class, LayoutSource.class, long.class, long.class,
				List.class, List.class, List.class, Set.class, Set.class);
		collect.setAccessible(true);
		return (boolean) collect.invoke(null, retained, source, from, to,
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
	}

	/** 行計画とseal済み本文だけを組み立て、終端遷移は実際のRangeHandleを通す。 */
	@SuppressWarnings("unchecked")
	private static void addSealedCell(final RetainedTableBuilder retained, final TableCellBox cellBox,
			final RangeHandle handle) throws Exception {
		final String impl = "net.zamasoft.foliojet.layout.builder.impl.";
		final LayoutStack context = (LayoutStack) Proxy.newProxyInstance(LayoutStack.class.getClassLoader(),
				new Class<?>[] { LayoutStack.class }, (proxy, method, args) -> {
					if (method.getName().equals("getPageContext")) return null;
					throw new AssertionError(method.getName());
				});
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(context, cellBox);
		final Class<?> rangeBody = Class.forName(impl + "TwoPassBlockBuilder$ReplayBody$SourceRangeBody");
		final var rangeConstructor = rangeBody.getDeclaredConstructor(RangeHandle.class, PageGenerator.class);
		rangeConstructor.setAccessible(true);
		final Method setBody = TwoPassBlockBuilder.class.getDeclaredMethod("setBody",
				Class.forName(impl + "TwoPassBlockBuilder$ReplayBody"));
		setBody.setAccessible(true);
		setBody.invoke(builder, rangeConstructor.newInstance(handle, null));
		final Class<?> cellType = Class.forName(impl + "CellContent");
		final var cellConstructor = cellType.getDeclaredConstructor(TwoPassBlockBuilder.class);
		cellConstructor.setAccessible(true);
		final Object cell = cellConstructor.newInstance(builder);
		final Method seal = cellType.getDeclaredMethod("sealForRangeBind");
		seal.setAccessible(true);
		seal.invoke(cell);
		final TableRowGroupBox group = new TableRowGroupBox(new InnerTableParams(), new TableRowGroupPos());
		final TableRowBox row = new TableRowBox(new InnerTableParams(), new TableRowPos());
		((List<TableRowGroupBox>) tableField(retained, "rowGroups")).add(group);
		((Map<TableRowGroupBox, List<TableRowBox>>) tableField(retained, "rowGroupToRows")).put(group, List.of(row));
		((Map<TableRowBox, ArrayList<Object>>) tableField(retained, "rowToCells")).put(row, new ArrayList<>(List.of(cell)));
	}

	private static Object tableField(final RetainedTableBuilder retained, final String name) throws Exception {
		final Field field = RetainedTableBuilder.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(retained);
	}
}
