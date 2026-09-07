package jp.cssj.test.unit.displaylist;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.RetainedTextLimit;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.fragment.ScratchOwner;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/** BとCを同じスレッドで交互に駆動したときの、資源・会計・借用ログの境界です。 */
public final class ScratchOwnerTest extends TestCase {
	public void testHostCompletionCannotRetireBorrowedMainOrOtherScratchBody() throws Exception {
		try (final LayoutSource source = source(); final ScratchOwner owner = new ScratchOwner()) {
			final RangeHandle main = handle(source);
			final RangeHandle local;
			try (final var attachment = owner.attach()) {
				local = handle(source);
				try (final var nested = new ScratchReplayScope()) {
					local.completeScratchHost();
					main.completeScratchHost();
				}
				owner.reclaimCompleted();
				assertEquals("別scratchの計測では所有元の寿命は閉じない", RangeHandle.State.OPEN, local.state());
				local.completeScratchHost();
				main.completeScratchHost();
			}
			assertEquals("完了通知だけでは安全点前に破棄しない", RangeHandle.State.OPEN, local.state());
			owner.reclaimCompleted();
			assertEquals(RangeHandle.State.ABANDONED, local.state());
			assertEquals(RangeHandle.State.OPEN, main.state());
			assertEquals(0, owner.registeredResourceCount());
			main.abandon();
		}
	}

	public void testOneShotScopeStillReleasesNewResources() throws Exception {
		try (final LayoutSource source = source()) {
			final RangeHandle main = handle(source);
			final RangeHandle scratch;
			try (final ScratchReplayScope scope = new ScratchReplayScope()) {
				scratch = handle(source);
				source.retainFrom(0);
				assertEquals(ReplayIntent.MEASURE, ReplayIntent.current());
			}
			assertEquals(RangeHandle.State.ABANDONED, scratch.state());
			assertEquals(RangeHandle.State.OPEN, main.state());
			assertEquals(1, leaseCount(source));
			assertEquals(ReplayIntent.MAIN, ReplayIntent.current());
			main.abandon();
			assertEquals(0, leaseCount(source));
		}
	}

	public void testOwnerSurvivesMainBetweenAttachments() throws Exception {
		try (final LayoutSource source = source(); final ScratchOwner owner = new ScratchOwner()) {
			final RangeHandle first;
			try (final ScratchReplayScope scope = owner.attach()) {
				first = handle(source);
				source.retainFrom(0);
			}
			assertEquals(RangeHandle.State.OPEN, first.state());
			assertEquals(ReplayIntent.MAIN, ReplayIntent.current());
			final RangeHandle main = handle(source);
			final LayoutSource.RetentionLease mainLease = source.retainFrom(0);
			final RangeHandle second;
			try (final ScratchReplayScope scope = new ScratchReplayScope(owner)) {
				second = handle(source);
				try (final ScratchReplayScope nested = new ScratchReplayScope()) {
					handle(source);
				}
				assertEquals(RangeHandle.State.OPEN, first.state());
			}
			owner.release();
			owner.release();
			assertEquals(RangeHandle.State.ABANDONED, first.state());
			assertEquals(RangeHandle.State.ABANDONED, second.state());
			assertEquals(RangeHandle.State.OPEN, main.state());
			assertEquals("MAINのハンドルと単独リースだけが残る", 2, leaseCount(source));
			assertIllegalState(owner::attach);
			main.abandon();
			mainLease.close();
			assertEquals(0, leaseCount(source));
		}
	}

	public void testMeasurementAccountKeepsCumulativeBytesAcrossAttachments() {
		try (final RetainedTextLimit limit = new PDFUserAgent() { }.getRetainedTextLimit();
				final var main = limit.enter("main"); final var account = limit.measurementAccount("scratch")) {
			limit.add(20);
			try (final var attachment = account.attach()) {
				limit.add(100);
				limit.enter("child");
				limit.add(40);
			}
			assertEquals(20L, limit.getCurrentBytes());
			limit.add(30);
			try (final var attachment = account.attach()) {
				assertEquals(140L, limit.getCurrentBytes());
				limit.leave();
				limit.add(60);
				assertEquals(200L, limit.getCurrentBytes());
			}
			account.release();
			assertEquals(50L, limit.getCurrentBytes());
			assertEquals(200L, limit.getHighWater());
			assertIllegalState(account::attach);
		}
	}

	public void testOwnerAttachesAccountingAndPreservesMainSuspension() {
		try (final RetainedTextLimit limit = new PDFUserAgent() { }.getRetainedTextLimit();
				final var main = limit.enter("main"); final ScratchOwner owner = new ScratchOwner(limit, "scratch")) {
			limit.add(20);
			try (final var suspended = limit.suspend()) {
				try (final ScratchReplayScope scope = owner.attach()) {
					limit.add(100);
					try (final ScratchReplayScope nested = owner.attach()) {
						limit.add(10);
					}
					limit.enter("未完の宿主");
				}
				limit.add(999);
				assertEquals(20L, limit.getCurrentBytes());
			}
			try (final var child = limit.enter("MAINの未完の子")) {
				limit.add(30);
				try (final ScratchReplayScope scope = owner.attach()) {
					assertEquals(110L, limit.getCurrentBytes());
					limit.add(40);
					owner.release();
					assertEquals(0L, limit.getCurrentBytes());
				}
				assertEquals(50L, limit.getCurrentBytes());
				limit.add(10);
				assertEquals(60L, limit.getCurrentBytes());
			}
			assertEquals(150L, limit.getHighWater());
		}
	}

	public void testIndependentOwnersRejectIndirectReentryWithoutChangingAccounts() {
		try (final RetainedTextLimit limit = new PDFUserAgent() { }.getRetainedTextLimit();
				final var main = limit.enter("main"); final ScratchOwner a = new ScratchOwner(limit, "A");
				final ScratchOwner b = new ScratchOwner(limit, "B")) {
			limit.add(17);
			try (final var first = a.attach()) {
				limit.add(31);
				try (final var second = b.attach()) {
					limit.add(53);
					assertIllegalState(a::attach);
					assertSame(b, ScratchReplayScope.currentOwner());
					assertEquals(ReplayIntent.MEASURE, ReplayIntent.current());
					assertEquals(53L, limit.getCurrentBytes());
					limit.add(7);
				}
				assertSame(a, ScratchReplayScope.currentOwner());
				assertEquals(31L, limit.getCurrentBytes());
			}
			assertNull(ScratchReplayScope.currentOwner());
			assertEquals(ReplayIntent.MAIN, ReplayIntent.current());
			assertEquals(17L, limit.getCurrentBytes());
			assertEquals(31L, a.currentBytes());
			assertEquals(60L, b.currentBytes());
		}
	}

	public void testOwnerReentryInsideLegacyMeasurementKeepsTemporaryAccount() {
		try (final RetainedTextLimit limit = new PDFUserAgent() { }.getRetainedTextLimit();
				final var main = limit.enter("main"); final ScratchOwner a = new ScratchOwner(limit, "A")) {
			limit.add(17);
			try (final var first = a.attach()) {
				limit.add(31);
				try (final var temporary = limit.measurement("temporary")) {
					limit.add(53);
					try (final var again = a.attach()) {
						assertSame(a, ScratchReplayScope.currentOwner());
						assertEquals(53L, limit.getCurrentBytes());
						limit.add(7);
					}
					assertEquals(60L, limit.getCurrentBytes());
					assertEquals(31L, a.currentBytes());
				}
				assertEquals(31L, limit.getCurrentBytes());
			}
			assertEquals(17L, limit.getCurrentBytes());
			assertEquals(60L, limit.getHighWater());
			assertNull(ScratchReplayScope.currentOwner());
			assertEquals(ReplayIntent.MAIN, ReplayIntent.current());
		}
	}

	public void testReleasedOuterAccountDoesNotLoseItsAttachment() {
		try (final RetainedTextLimit limit = new PDFUserAgent() { }.getRetainedTextLimit();
				final var main = limit.enter("main"); final var outer = limit.measurementAccount("outer");
				final var inner = limit.measurementAccount("inner")) {
			limit.add(20);
			try (final var first = outer.attach()) {
				limit.add(40);
				try (final var second = inner.attach(); final var temporary = limit.measurement("temporary")) {
					limit.add(80);
					outer.release();
					inner.release();
				}
				assertEquals(0L, limit.getCurrentBytes());
			}
			assertEquals(20L, limit.getCurrentBytes());
			assertEquals(80L, limit.getHighWater());
		}
	}

	public void testOwnerPinsUnfinishedCaptureWhileDisconnected() throws Exception {
		try (final LayoutSource source = source(); final ScratchOwner owner = new ScratchOwner()) {
			// 別の所有者の接続中に取得しても、その所有者のcloseで解放されない。
			try (final ScratchReplayScope other = new ScratchReplayScope()) {
				owner.retainFrom(source, 0);
			}
			source.append(new LayoutSource.Chars(1, "y".toCharArray(), false));
			source.compact(source.nextId());
			assertFalse(source.compactRetainedTable(0, source.nextId(), true));
			assertEquals(2, source.size());
			try (final var capture = source.capture(0, 1)) {
				assertNotNull("切断中もTwoPass宿主のcapture範囲を保つ", capture);
			}
			owner.release();
			assertEquals(0, leaseCount(source));
			source.compact(source.nextId());
			assertEquals(0, source.size());
		}
	}

	public void testDiscardUnfinishedTableAndFloatsWithoutSealing() throws Exception {
		for (final boolean attached : List.of(false, true)) {
			final PDFUserAgent ua = new PDFUserAgent() { };
			try (final LayoutSource source = new LayoutSource(); final RetainedTextLimit limit = ua.getRetainedTextLimit();
					final var main = limit.enter("main"); final ScratchOwner owner = new ScratchOwner(limit, "scratch")) {
				limit.add(20);
				owner.retainFrom(source, 0);
				final DocumentBuilder doc;
				final TableBox table = table();
				final FloatBlockBox unfinished = new FloatBlockBox(blockParams(), new FloatPos());
				try (final ScratchReplayScope scope = owner.attach()) {
					doc = new DocumentBuilder(new MeasurePageGenerator(ua, blockParams(), 400, 600, source));
					start(source, doc, LayoutSource.BoxKind.FLOW, new FlowBlockBox(blockParams(), new FlowPos()));
					// 固定幅floatはBlockBuilder自身のRetainedTextLimit.Scopeを持つ。
					final BlockParams fixed = blockParams();
					fixed.size = Dimension.create(200, 0, LengthType.ABSOLUTE, LengthType.AUTO);
					start(source, doc, LayoutSource.BoxKind.FLOAT_BLOCK, new FloatBlockBox(fixed, new FloatPos()));
					start(source, doc, LayoutSource.BoxKind.TABLE, table);
					start(source, doc, LayoutSource.BoxKind.TABLE_ROW_GROUP,
							new TableRowGroupBox(new InnerTableParams(), new TableRowGroupPos()));
					start(source, doc, LayoutSource.BoxKind.TABLE_ROW,
							new TableRowBox(new InnerTableParams(), new TableRowPos()));
					start(source, doc, LayoutSource.BoxKind.TABLE_CELL, cell());
					start(source, doc, LayoutSource.BoxKind.FLOW, new FlowBlockBox(blockParams(), new FlowPos()));
					end(source, doc);
					end(source, doc); // 最初のセルだけseal済みにし、実ハンドルを残す。
					start(source, doc, LayoutSource.BoxKind.TABLE_CELL, cell());
					start(source, doc, LayoutSource.BoxKind.FLOAT_BLOCK, unfinished);
					start(source, doc, LayoutSource.BoxKind.FLOW, new FlowBlockBox(blockParams(), new FlowPos()));
					limit.add(80);
				}
				final List<RangeHandle> handles = openRanges(source);
				assertFalse("実セルのsealが未発火", handles.isEmpty());
				final List<?> scopes = List.copyOf((List<?>) field(owner, "scopes"));
				assertFalse("ビルダーの会計スコープが未登録", scopes.isEmpty());
				assertEquals(-1L, source.endOf(table.getSourceAnchor()));
				assertEquals(-1L, source.endOf(unfinished.getSourceAnchor()));
				final List<LayoutSource.Event> events = events(source);
				final long nextId = source.nextId();
				final Object watermark = field(source, "retainedTableWatermark");
				try (final var mainChild = limit.enter("MAINの未完の子")) {
					limit.add(5);
					if (attached) {
						try (final ScratchReplayScope scope = owner.attach()) {
							doc.discard();
						}
					} else {
						doc.discard();
					}
					assertEquals(25L, limit.getCurrentBytes());
					assertEquals(false, field(mainChild, "closed"));
				}
				for (final RangeHandle handle : handles) assertEquals(RangeHandle.State.ABANDONED, handle.state());
				for (final Object scope : scopes) assertEquals(true, field(scope, "closed"));
				assertEquals(0, leaseCount(source));
				assertEquals(nextId, source.nextId());
				assertEquals(events, events(source));
				assertEquals(watermark, field(source, "retainedTableWatermark"));
				assertTrue(((List<?>) field(doc, "builderStack")).isEmpty());
				assertTrue(((List<?>) field(doc, "boxStack")).isEmpty());
				assertTrue(((Map<?, ?>) field(doc, "translateScopeRoots")).isEmpty());
				assertIllegalState(doc::discard);
				assertIllegalState(doc::end);
				assertIllegalState(() -> doc.characters(0, new char[] { 'x' }, 0, 1, false));
				assertIllegalState(() -> doc.startBox(new FlowBlockBox(blockParams(), new FlowPos())));
				assertIllegalState(doc::endBox);
				assertIllegalState(() -> doc.preDispatch(DocumentBuilder.DispatchEvent.TEXT, null, nextId));
			}
		}
	}

	public void testDiscardGridFlexAndReplayOnlyWithRealText() throws Exception {
		try (final var fonts = new net.zamasoft.pdfg2d.pdf.font.FontManagerImpl(
				net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager.getDefaultFontSourceManager())) {
			for (final String kind : List.of("grid", "flex", "replay")) {
				final PDFUserAgent ua = new PDFUserAgent() { };
				try (final LayoutSource source = new LayoutSource(); final RetainedTextLimit limit = ua.getRetainedTextLimit();
						final var main = limit.enter("main"); final ScratchOwner owner = new ScratchOwner(limit, kind)) {
					limit.add(17);
					final DocumentBuilder doc;
					final Object replayBody;
					try (final var attached = owner.attach()) {
						final BlockParams root = textParams(new BlockParams(), fonts);
						doc = new DocumentBuilder(new MeasurePageGenerator(ua, root, 400, 600, source));
						start(source, doc, LayoutSource.BoxKind.FLOW, new FlowBlockBox(root, new FlowPos()));
						if (kind.equals("replay")) {
							final var box = new FloatBlockBox(textParams(new BlockParams(), fonts), new FloatPos());
							doc.startReplayOnlyEvent(new net.zamasoft.foliojet.layout.segment.SegmentEvent.BeginBox(
									BoxRecipe.freeze(LayoutSource.BoxKind.FLOAT_BLOCK, box)), 0);
							doc.startBox(box);
							doc.finishReplayOnlyEvent();
							final List<?> stack = (List<?>) field(doc, "builderStack");
							// ContainerBuilderEntry は protected なので reflection で辿る
							final Object entry = stack.get(stack.size() - 1);
							final Object builder = field(entry, "builder");
							assertTrue(builder instanceof net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder);
							replayBody = field(builder, "body");
							assertEquals("ReplayOnly", replayBody.getClass().getSimpleName());
							final String text = "real replay text";
							doc.startReplayOnlyEvent(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Text(0, text, false), 1);
							doc.characters(0, text.toCharArray(), 0, text.length(), false);
							doc.finishReplayOnlyEvent();
							assertTrue(((List<?>) field(replayBody, "events")).stream()
									.anyMatch(event -> event instanceof net.zamasoft.foliojet.layout.segment.SegmentEvent.Text));
						} else {
							replayBody = null;
							if (kind.equals("grid")) {
								start(source, doc, LayoutSource.BoxKind.GRID, new net.zamasoft.foliojet.layout.box.impl.GridBox(
										textParams(new net.zamasoft.foliojet.layout.box.params.GridParams(), fonts), new FlowPos()));
							} else {
								start(source, doc, LayoutSource.BoxKind.FLEX, new net.zamasoft.foliojet.layout.box.impl.FlexBox(
										textParams(new net.zamasoft.foliojet.layout.box.params.FlexParams(), fonts), new FlowPos()));
							}
							assertTrue("実coordinatorが開いた", ((List<?>) field(doc, "builderStack")).stream().anyMatch(builder ->
									builder.getClass().getSimpleName().equals(kind.equals("grid") ? "GridBuilder" : "FlexBuilder")));
							for (int item = 0; item < 2; ++item) {
								start(source, doc, LayoutSource.BoxKind.FLOW,
										new FlowBlockBox(textParams(new BlockParams(), fonts), new FlowPos()));
								final String text = "real item text";
								source.append(new LayoutSource.Chars(item * text.length(), text.toCharArray(), false));
								doc.characters(item * text.length(), text.toCharArray(), 0, text.length(), false);
								if (item == 0) end(source, doc); // 完了項目と未完項目を同居させる。
							}
						}
						doc.finishReplayKeepText();
						final List<?> stack = (List<?>) field(doc, "builderStack");
						final var builder = (net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder)
								field(stack.get(stack.size() - 1), "builder");
						assertTrue(kind + ": 実文字が固有寸法の計測へ届いた", builder.getIntrinsicSizes().maxContent() > 0);
						// flushTextはshaperを流すだけ。項目の録画・固有寸法計測では
						// TextBuilderが行へ文字を配置しないので、組版済み文字会計はまだ0。
						assertEquals(kind + ": 未配置の文字は組版済み文字会計に含めない", 0L, owner.currentBytes());
					}
					final long end = source.nextId();
					final List<LayoutSource.Event> before = events(source);
					final List<RangeHandle> ranges = openRanges(source);
					if (!kind.equals("replay")) assertFalse(kind + ": 完了項目の範囲を実際に保持", ranges.isEmpty());
					doc.discard();
					assertEquals(17L, limit.getCurrentBytes());
					assertEquals(0L, owner.currentBytes());
					assertEquals(0, owner.registeredResourceCount());
					assertEquals(0, leaseCount(source));
					for (final RangeHandle range : ranges) assertEquals(RangeHandle.State.ABANDONED, range.state());
					assertEquals(end, source.nextId());
					assertEquals("discardは正常Endを追記しない", before, events(source));
					assertTrue(((List<?>) field(doc, "builderStack")).isEmpty());
					assertTrue(((List<?>) field(doc, "boxStack")).isEmpty());
					assertTrue(((Map<?, ?>) field(doc, "translateScopeRoots")).isEmpty());
					if (replayBody != null) {
						assertEquals(false, field(replayBody, "closed"));
						assertEquals(false, field(replayBody, "consumed"));
					}
					assertIllegalState(doc::end);
					assertIllegalState(() -> doc.characters(0, new char[] { 'x' }, 0, 1, false));
				}
			}
		}
	}

	public void testDiscardReleasesLaidOutTextAccounting() throws Exception {
		try (final var fonts = new net.zamasoft.pdfg2d.pdf.font.FontManagerImpl(
				net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager.getDefaultFontSourceManager())) {
			final PDFUserAgent ua = new PDFUserAgent() { };
			try (final LayoutSource source = new LayoutSource(); final RetainedTextLimit limit = ua.getRetainedTextLimit();
					final var main = limit.enter("main"); final ScratchOwner owner = new ScratchOwner(limit, "laid-out")) {
				limit.add(17);
				final DocumentBuilder doc;
				final String text = "scratch";
				try (final var attached = owner.attach()) {
					final BlockParams root = textParams(new BlockParams(), fonts);
					doc = new DocumentBuilder(new MeasurePageGenerator(ua, root, 400, 600, source));
					start(source, doc, LayoutSource.BoxKind.FLOW, new FlowBlockBox(root, new FlowPos()));
					start(source, doc, LayoutSource.BoxKind.FLOW,
							new FlowBlockBox(textParams(new BlockParams(), fonts), new FlowPos()));
					source.append(new LayoutSource.Chars(0, text.toCharArray(), false));
					doc.characters(0, text.toCharArray(), 0, text.length(), false);
					end(source, doc); // 段落を閉じ、TextBuilderが文字を行へ配置してから観測する。
					assertEquals("実文字が所有者の会計へ一度届く", 2L * text.length(), owner.currentBytes());
				}
				assertEquals(17L, limit.getCurrentBytes());
				assertEquals("切断中も組版済み文字を保持", 2L * text.length(), owner.currentBytes());
				final List<LayoutSource.Event> before = events(source);
				doc.discard(); // 外側のflowは未完のまま破棄する。
				assertEquals(17L, limit.getCurrentBytes());
				assertEquals(0L, owner.currentBytes());
				assertEquals(0, owner.registeredResourceCount());
				assertEquals("discardは正常Endを追記しない", before, events(source));
			}
		}
	}

	public void testFailedTerminationStillReleasesTextSlice() throws Exception {
		final PDFUserAgent ua = new PDFUserAgent() { };
		try (final LayoutSource source = source(); final ScratchOwner owner = new ScratchOwner(ua.getRetainedTextLimit(), "slice")) {
			final DocumentBuilder doc;
			final RangeHandle range;
			try (final var attached = owner.attach()) {
				doc = new DocumentBuilder(new MeasurePageGenerator(ua, blockParams(), 400, 600, source));
				range = new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO, RangeHandle.ReplayMode.CHILDREN_ONLY, true);
			}
			assertTrue(range.hasTextSlice());
			source.compact(source.nextId());
			assertEquals(1, source.retentionSnapshot().slicedEvents());
			assertTrue(source.retainedInlineTextBytes() > 0);
			final IllegalStateException failure = new IllegalStateException("termination");
			range.observeOwnerState(state -> { throw failure; });
			try {
				doc.discard();
				fail("終了通知の例外を伝える");
			} catch (final IllegalStateException expected) {
				assertSame(failure, expected);
			}
			assertEquals(RangeHandle.State.ABANDONED, range.state());
			assertFalse(range.hasTextSlice());
			assertEquals(0, source.retentionSnapshot().slicedEvents());
			assertEquals(0L, source.retainedInlineTextBytes());
			assertTrue(openRanges(source).isEmpty());
			assertEquals(0, leaseCount(source));
			assertEquals(0, owner.registeredResourceCount());
		}
	}

	private static <T extends BlockParams> T textParams(final T params, final net.zamasoft.pdfg2d.gc.font.FontManager fonts) {
		params.fontStyle = blockParams().fontStyle;
		params.fontManager = fonts;
		params.lineHeight = 14;
		params.lineBreakRules = new net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules() {
			public boolean atomic(final char before, final char after) { return false; }
			public boolean canSeparate(final char before, final char after) { return true; }
		};
		return params;
	}

	public void testDiscardRejectsProductionGenerator() {
		final DocumentBuilder doc = new DocumentBuilder(mainGenerator(new PDFUserAgent() { }, null));
		assertIllegalState(doc::discard);
	}

	public void testDiscardCollectsCleanupFailures() throws Exception {
		final PDFUserAgent ua = new PDFUserAgent() { };
		try (final LayoutSource source = source(); final RetainedTextLimit limit = ua.getRetainedTextLimit();
				final ScratchOwner owner = new ScratchOwner(limit, "scratch")) {
			final DocumentBuilder doc;
			final RangeHandle first, second, last;
			final IllegalStateException firstFailure = new IllegalStateException("first");
			final IllegalArgumentException secondFailure = new IllegalArgumentException("second");
			try (final ScratchReplayScope scope = owner.attach()) {
				doc = new DocumentBuilder(new MeasurePageGenerator(ua, blockParams(), 400, 600, source));
				doc.startBox(new FlowBlockBox(blockParams(), new FlowPos()));
				first = handle(source);
				second = handle(source);
				last = handle(source);
				limit.enter("未完の子");
				limit.add(80);
			}
			first.observeOwnerState(state -> { throw firstFailure; });
			second.observeOwnerState(state -> { throw secondFailure; });
			try {
				doc.discard();
				fail("解放時の例外を捨てた");
			} catch (final IllegalStateException expected) {
				assertSame(firstFailure, expected);
				assertEquals(1, expected.getSuppressed().length);
				assertSame(secondFailure, expected.getSuppressed()[0]);
			}
			assertEquals(RangeHandle.State.ABANDONED, last.state());
			assertEquals(0, leaseCount(source));
			assertTrue(openRanges(source).isEmpty());
			assertEquals(0L, limit.getCurrentBytes());
			assertTrue(((List<?>) field(doc, "builderStack")).isEmpty());
			assertTrue(((List<?>) field(doc, "boxStack")).isEmpty());
			assertIllegalState(doc::discard);
		}
	}

	public void testRetainedTableNeverCompactsBorrowedMeasureLog() throws Exception {
		// 意図と生成器を別々に検査し、MAINの対照では本当にcompactされることを確かめる。
		for (int mode = 0; mode < 3; ++mode) {
			final PDFUserAgent ua = new PDFUserAgent() { };
			try (final LayoutSource source = source()) {
				final PageGenerator generator = mode == 1
						? new MeasurePageGenerator(ua, blockParams(), 400, 600, source) : mainGenerator(ua, source);
				final TableBox table = table();
				table.setSourceAnchor(0);
				final RetainedTableBuilder builder = new RetainedTableBuilder(
						new RootBuilder(generator, BreakableBuilder.MODE_NO_BREAK), table);
				final Method compact = RetainedTableBuilder.class.getDeclaredMethod("compactCellText", boolean.class);
				compact.setAccessible(true);
				try (final ReplayIntent.Scope intent = (mode == 0 ? ReplayIntent.MEASURE : ReplayIntent.MAIN).enter()) {
					compact.invoke(builder, true);
				}
				assertEquals(1L, source.nextId());
				if (mode < 2) {
					assertNotNull(source.get(0));
					assertEquals(-1L, field(source, "retainedTableWatermark"));
				} else {
					assertNull("MAINの対照でもcompactが未発火", source.get(0));
				}
			}
		}
	}

	private static LayoutSource source() {
		final LayoutSource source = new LayoutSource();
		source.append(new LayoutSource.Chars(0, "x".toCharArray(), false));
		return source;
	}

	private static RangeHandle handle(final LayoutSource source) {
		return new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO, RangeHandle.ReplayMode.CHILDREN_ONLY);
	}

	private static BlockParams blockParams() {
		final BlockParams params = new BlockParams();
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		params.lineHeight = 14;
		return params;
	}

	private static TableBox table() {
		final TableParams params = new TableParams();
		params.fontStyle = blockParams().fontStyle;
		return new TableBox(params, new FlowBlockBox(params, new FlowPos()));
	}

	private static TableCellBox cell() {
		return new TableCellBox(blockParams(), new TableCellPos(), new FlowContainer());
	}

	private static void start(final LayoutSource source, final DocumentBuilder doc, final LayoutSource.BoxKind kind,
			final INonReplacedBox box) {
		box.setSourceAnchor(source.append(new LayoutSource.Start(BoxRecipe.freeze(kind, box))));
		doc.startBox(box);
	}

	private static void end(final LayoutSource source, final DocumentBuilder doc) {
		source.append(new LayoutSource.EndBlock());
		doc.endBox();
	}

	private static PageGenerator mainGenerator(final UserAgent ua, final LayoutSource source) {
		return new PageGenerator() {
			public UserAgent getUserAgent() { return ua; }
			public PageBreakMode getPageSide() { return PageBreakMode.AUTO; }
			public LayoutSource getLayoutSource() { return source; }
			public PageBox nextPage() {
				final BlockParams params = blockParams();
				params.size = Dimension.create(400, 600, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
				return new PageBox(params, ua);
			}
			public boolean drawPage(final PageBox page, final boolean lastPage, final boolean forced) { return true; }
		};
	}

	private static Object field(final Object target, final String name) throws Exception {
		final Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static int leaseCount(final LayoutSource source) throws Exception {
		final Map<?, ?> leases = (Map<?, ?>) field(source, "retentionLeases");
		return leases.values().stream().mapToInt(value -> (Integer) value).sum();
	}

	@SuppressWarnings("unchecked")
	private static List<RangeHandle> openRanges(final LayoutSource source) throws Exception {
		return List.copyOf((Set<RangeHandle>) field(source, "openRanges"));
	}

	private static List<LayoutSource.Event> events(final LayoutSource source) {
		final List<LayoutSource.Event> events = new ArrayList<>();
		for (long id = 0; id < source.nextId(); ++id) events.add(source.get(id));
		return events;
	}

	private static void assertIllegalState(final Runnable action) {
		try {
			action.run();
			fail("IllegalStateException expected");
		} catch (final IllegalStateException expected) {
			// 所有・入力の終端違反は黙って受理しない。
		}
	}
}
