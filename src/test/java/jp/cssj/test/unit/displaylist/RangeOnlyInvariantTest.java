package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensus;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/** T4b: 固定manifestの所有終端・lease収支とrange/empty限定のbindを検査する。 */
public final class RangeOnlyInvariantTest extends TestCase {
	public void testFixedManifestOwnership() throws Exception {
		final var manifest = TwoPassDigestParityTest.fixedManifest();
		assertFalse("固定manifestが空", manifest.isEmpty());
		final List<String> failures = new ArrayList<>();
		long converted = 0, observed = 0;
		try (final TwoPassCensus census = ContinuationStats.beginTwoPassCensus()) {
			for (final var document : manifest.entrySet()) {
				ContinuationStats.reset();
				final List<RangeHandle> handles = Collections.synchronizedList(new ArrayList<>());
				try (final AutoCloseable observation = observe(RangeHandle.class, "sealObserver",
						(Consumer<RangeHandle>) handles::add)) {
					TwoPassDigestParityTest.transcode(document.getValue());
					assertTerminal(document.getKey(), handles);
					assertCensus(document.getKey(), census);
					TwoPassFlowSealTest.assertLeaseBalance(document.getKey());
					++converted;
					observed += handles.size();
				} catch (final Exception | AssertionError e) {
					failures.add(document.getKey() + ": " + e);
				}
				if ((converted + failures.size()) % 100 == 0) {
					System.err.println("[range invariant] documents=" + (converted + failures.size()) + "/" + manifest.size());
				}
			}
		} finally {
			ContinuationStats.reset();
		}
		assertTrue(String.join("\n", failures), failures.isEmpty());
		assertEquals("固定manifest全件", manifest.size(), converted);
		assertTrue("RangeHandle未観測", observed > 0);
	}

	static void assertCensus(final String doc, final TwoPassCensus census) {
		for (final TwoPassSealReject reject : TwoPassSealReject.values()) {
			assertEquals(doc + ": " + reject, 0, ContinuationStats.twoPassSealRejects(reject));
		}
		for (final var event : TwoPassCensusEvent.values()) {
			for (final var key : census.snapshot(event).keySet()) {
				assertTrue(doc + ": 未seal " + event + " " + key, key.sealAttempted());
				assertEquals(doc + ": 未知・不適格のseal " + key, "accepted", key.sealOutcome());
				assertNull(doc + ": barrier " + key, key.barrierReason());
			}
		}
		assertEquals(doc + ": range bind収支", ContinuationStats.RANGE_FIRST_BINDS.get(),
				total(census, TwoPassCensusEvent.BIND));
		assertEquals(doc + ": empty bind収支", ContinuationStats.TWO_PASS_EMPTY_BINDS.get(),
				total(census, TwoPassCensusEvent.EMPTY_BIND));
		assertEquals(doc + ": seal収支", ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get()
				+ ContinuationStats.TWO_PASS_EMPTY_SEALS.get(), total(census, TwoPassCensusEvent.SEAL));
	}

	private static long total(final TwoPassCensus census, final TwoPassCensusEvent event) {
		return census.snapshot(event).values().stream().mapToLong(Long::longValue).sum();
	}

	private static void assertTerminal(final String doc, final List<RangeHandle> handles) {
		final Set<LayoutSource> sources = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final RangeHandle handle : handles) {
			assertTrue(doc + ": OPEN [" + handle.fromId() + "," + handle.toId() + "]",
					switch (handle.state()) {
					case CONSUMED, SUBSUMED, ABANDONED -> true;
					case OPEN -> false;
					});
			sources.add(handle.source());
		}
		for (final LayoutSource source : sources) {
			assertEquals(doc + ": lease残存", 0L, ((Number) field(source, "retentionLeaseCount")).longValue());
			assertTrue(doc + ": lease台帳残存", ((Map<?, ?>) field(source, "retentionLeases")).isEmpty());
		}
	}

	public void testScratchTwiceThenMain() throws Exception {
		ScratchReplayScopeTest.checkDocument(new File("files/unittest/0070-table-layout/float-in-auto.html"));
	}

	/** compact後も、同一EventIdの本文をscratch二回→MAINへ独立に再生できる。 */
	public void testSlicedCellsScratchTwiceThenMain() throws Exception {
		final File document = EnduranceTest.generateManyRowsTable("t5a-sliced-scratch", 120);
		final AtomicBoolean compacted = new AtomicBoolean();
		try (final AutoCloseable observer = observe(net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder.class,
				"retentionObserver", (java.util.function.BiConsumer<String, LayoutSource>) (stage, source) -> {
			if (stage.equals("after-pass-b")) {
				assertTrue("セル本文のsliceが未発火", source.retentionSnapshot().slicedEvents() >= 120 * 3);
				assertTrue("Pass B後も表全体のログを保持", source.size() < 1024);
				compacted.set(true);
			}
		})) {
			ScratchReplayScopeTest.checkDocument(document);
		} finally {
			java.nio.file.Files.deleteIfExists(document.toPath());
		}
		assertTrue("sliceを保持したPass B後のcompactが未観測", compacted.get());
	}

	public void testTextSliceSurvivesCompactionAndRetainsPayloadBudget() throws Exception {
		try (final LayoutSource source = new LayoutSource(4)) {
			source.appendChars(0, new char[] { 'a', 'b' }, 0, 2, false);
			final RangeHandle handle = new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO,
					RangeHandle.ReplayMode.CHILDREN_ONLY, true);
			assertTrue(handle.hasTextSlice());
			assertEquals(0L, source.retentionSnapshot().leases());
			source.compact(source.nextId());
			assertEquals(0, source.size());
			assertEquals(1, source.retentionSnapshot().openRanges());
			assertEquals(1, source.retentionSnapshot().slicedEvents());
			final RangeHandle copy = new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO,
					RangeHandle.ReplayMode.CHILDREN_ONLY);
			assertTrue("本体回収後も元EventIdで同じsliceを共有", copy.hasTextSlice());
			copy.abandon();
			assertEquals(4L, ((Number) field(source, "liveInlineTextBytes")).longValue());
			final long spilled = source.appendChars(2, new char[] { 'c' }, 0, 1, false);
			assertTrue(((LayoutSource.Chars) source.get(spilled)).payload() instanceof LayoutSource.TextPayload.Spilled);
			try (final LayoutSource.ReplaySlice replay = source.capture(0, 0)) {
				assertNotNull(replay);
				handle.abandon();
				source.compact(source.nextId());
				replay.replay(event -> assertEquals("ab", new String(((LayoutSource.Chars) event).payload().freshChars())));
			}
			assertEquals(0, source.retentionSnapshot().slicedEvents());
			assertEquals(0L, ((Number) field(source, "liveInlineTextBytes")).longValue());
			final long inline = source.appendChars(3, new char[] { 'd' }, 0, 1, false);
			assertTrue(((LayoutSource.Chars) source.get(inline)).payload() instanceof LayoutSource.TextPayload.Inline);
		}
	}

	public void testSpilledSliceLivesUntilLastReplayCloses() throws Exception {
		try (final LayoutSource source = new LayoutSource(0)) {
			source.appendChars(0, new char[] { 'a' }, 0, 1, false);
			source.appendChars(1, new char[] { 'b' }, 0, 1, false);
			final RangeHandle handle = new RangeHandle(source, 0, 1, IntrinsicSizes.ZERO,
					RangeHandle.ReplayMode.CHILDREN_ONLY, true);
			final var spill = source.textSpillForTest();
			source.compact(source.nextId());
			try (final LayoutSource.ReplaySlice replay = source.capture(1, 1)) {
				assertNotNull(replay);
				assertEquals(1L, replay.fromId());
				assertEquals(1L, replay.toId());
				source.close();
				assertEquals(RangeHandle.State.ABANDONED, handle.state());
				assertFalse(spill.tempFilesDeletedForTest());
				replay.replay(event -> assertEquals("b", new String(((LayoutSource.Chars) event).payload().freshChars())));
			}
			assertTrue(spill.tempFilesDeletedForTest());
			assertEquals(0, source.retentionSnapshot().slicedRanges());
		}
	}

	/** 元ログとの列一致。EventIdは非ゼロ開始、生成文字とfixedも混ぜる。 */
	public void testTextSlicePreservesMultipleCharsAndOffsets() throws Exception {
		try (final LayoutSource source = new LayoutSource(6)) {
			source.appendChars(0, new char[] { 'p' }, 0, 1, false);
			final long from = source.appendChars(17, "_ab_".toCharArray(), 1, 2, false);
			source.appendChars(-1, new char[] { 'g' }, 0, 1, true);
			final long to = source.appendChars(42, new char[] { 'z' }, 0, 1, false);
			final List<LayoutSource.Event> original = new ArrayList<>();
			try (final var replay = source.capture(from, to)) { replay.replay(original::add); }
			final RangeHandle handle = new RangeHandle(source, from, to, IntrinsicSizes.ZERO,
					RangeHandle.ReplayMode.CHILDREN_ONLY, true);
			source.compact(source.nextId());
			assertEquals(0, source.size());
			for (int attempt = 0; attempt < 2; ++attempt) {
				final List<LayoutSource.Event> actual = new ArrayList<>();
				try (final var replay = source.capture(from, to)) {
					assertEquals(from, replay.fromId());
					assertEquals(to, replay.toId());
					replay.replay(actual::add);
				}
				assertEquals(original, actual);
				for (int i = 0; i < actual.size(); ++i) assertSame(original.get(i), actual.get(i));
			}
			try (final var replay = source.capture(from + 1, to)) {
				final List<LayoutSource.Event> tail = new ArrayList<>();
				handle.abandon();
				replay.replay(tail::add);
				assertEquals(original.subList(1, 3), tail);
				assertEquals(-1, ((LayoutSource.Chars) tail.get(0)).charOffset());
				assertTrue(((LayoutSource.Chars) tail.get(0)).fixed());
			}
			assertEquals(0L, ((Number) field(source, "liveInlineTextBytes")).longValue());
		}
	}

	/** sliceを先に解放する場合と、通常リースが本文の一部だけを守る場合の予算収支。 */
	public void testTextSliceReleasesBeforeOrAfterPartialCompaction() throws Exception {
		for (final boolean compactFirst : List.of(false, true)) {
			try (final LayoutSource source = new LayoutSource()) {
				for (int i = 0; i < 3; ++i) source.appendChars(i, new char[] { 'a' }, 0, 1, false);
				final RangeHandle handle = new RangeHandle(source, 0, 2, IntrinsicSizes.ZERO,
						RangeHandle.ReplayMode.CHILDREN_ONLY, true);
				// 重なった別範囲はリース。sliceの配列・予算を二重所有しない。
				final RangeHandle tail = new RangeHandle(source, 1, 2, IntrinsicSizes.ZERO,
						RangeHandle.ReplayMode.CHILDREN_ONLY, true);
				assertFalse(tail.hasTextSlice());
				if (compactFirst) source.compact(source.nextId());
				handle.abandon();
				assertEquals(compactFirst ? 4L : 6L, ((Number) field(source, "liveInlineTextBytes")).longValue());
				tail.abandon();
				source.compact(source.nextId());
				assertEquals(0L, ((Number) field(source, "liveInlineTextBytes")).longValue());
				assertEquals(0, source.retentionSnapshot().slicedRanges());
			}
		}
	}

	/** 表の前のログと、まだOPENな本文/再生のpinを越えて回収しない。 */
	public void testTableCompactionPreservesPrefixAndOpenLease() throws Exception {
		try (final LayoutSource source = new LayoutSource()) {
			for (int i = 0; i < 4; ++i) source.append(new LayoutSource.Chars(i, new char[] { 'x' }, false));
			try (final var lease = source.retainFrom(2)) {
				assertTrue(source.compactRetainedTable(1, 4, true));
				assertNotNull(source.get(0));
				assertNull(source.get(1));
				assertNotNull(source.get(2));
				assertNotNull(source.get(3));
				assertFalse(source.compactRetainedTable(1, 4, true));
			}
			assertTrue(source.compactRetainedTable(1, 4, true));
			assertNotNull(source.get(0));
			assertEquals(1, source.size());
		}
	}

	public void testStructuredCellKeepsLeaseAcrossNestedSeal() throws Exception {
		try (final LayoutSource source = new LayoutSource()) {
			source.append(new LayoutSource.AnonymousItemStart(0));
			source.append(new LayoutSource.Chars(0, new char[] { 'x' }, false));
			source.append(new LayoutSource.AnonymousItemEnd());
			final RangeHandle child = new RangeHandle(source, 1, 1, IntrinsicSizes.ZERO,
					RangeHandle.ReplayMode.ANONYMOUS_CHILDREN);
			final RangeHandle parent = new RangeHandle(source, 0, 2, IntrinsicSizes.ZERO,
					RangeHandle.ReplayMode.CHILDREN_ONLY, true);
			assertFalse("構造を含むセルはsliceにしない", parent.hasTextSlice());
			child.subsume();
			source.compact(source.nextId());
			assertEquals(3, source.size());
			assertEquals(2L, source.endOf(0));
			assertEquals(1L, source.retentionSnapshot().leases());
			parent.abandon();
			source.compact(source.nextId());
			assertEquals(0, source.size());
		}
	}

	/** 親TwoPass内の入れ子表は切り出さず、元source/範囲包含で吸収できる。 */
	public void testParentTableKeepsLeasesForNestedSeal() throws Exception {
		final List<RangeHandle> handles = new ArrayList<>();
		try (final AutoCloseable observer = observe(RangeHandle.class, "sealObserver", (Consumer<RangeHandle>) handle -> {
			assertFalse("親TwoPass内の本文をslice化", handle.hasTextSlice());
			handles.add(handle);
		})) {
			TwoPassFlowSealTest.render("<html><body><div style='float:left;width:auto'><table><tr><td>outer"
					+ "<table><tr><td>inner</td></tr></table></td></tr></table></div></body></html>");
		}
		assertTrue(handles.stream().anyMatch(handle -> handle.state() == RangeHandle.State.SUBSUMED));
		assertTerminal("nested table", handles);
	}

	public void testDetachedSubsumedAndAbandonedChildren() throws Exception {
		for (final String terminal : List.of("DETACHED", "SUBSUMED", "ABANDONED", "CONSUMED")) {
			try (final LayoutSource source = new LayoutSource()) {
				source.append(new LayoutSource.Chars(0, "x".toCharArray(), false));
				final LayoutStack context = (LayoutStack) Proxy.newProxyInstance(LayoutStack.class.getClassLoader(),
						new Class<?>[] { LayoutStack.class }, (proxy, method, args) -> {
							if (method.getName().equals("getPageContext")) return null;
							throw new AssertionError(method.getName());
						});
				final TwoPassBlockBuilder parent = new TwoPassBlockBuilder(context,
						new FlowBlockBox(blockParams(), new FlowPos()));
				final TwoPassBlockBuilder child = new TwoPassBlockBuilder(parent,
						new FloatBlockBox(blockParams(), new FloatPos()));
				child.getRootBox().setSourceAnchor(0);
				final Object kind = call(type("OwnershipLedger$Kind"), "valueOf", new Class<?>[] { String.class }, "STF");
				call(ledger(parent), "addChild", new Class<?>[] { TwoPassBlockBuilder.class, type("OwnershipLedger$Kind") }, child, kind);
				final RangeHandle handle;
				try (final ScratchReplayScope scope = terminal.equals("ABANDONED") ? new ScratchReplayScope() : null) {
					handle = new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO, RangeHandle.ReplayMode.CHILDREN_ONLY);
					call(child, "setBody", new Class<?>[] { type("TwoPassBlockBuilder$ReplayBody") },
							construct("TwoPassBlockBuilder$ReplayBody$SourceRangeBody",
									new Class<?>[] { RangeHandle.class, PageGenerator.class }, handle, null));
					assertEquals("SEALED", call(nodes(parent).get(0), "state").toString());
					assertTrue(collectOwnership(parent, source, 0, 0));
					if (terminal.equals("DETACHED")) {
						assertNotNull(child.detachDeferredBind());
					} else if (terminal.equals("SUBSUMED")) {
						try (final var parentLease = source.retainFrom(0)) {
							call(child, "subsumeIntoParentRange");
						}
					} else if (terminal.equals("CONSUMED")) {
						try {
							handle.bind(null, null);
							fail("不正なbind先を受理した");
						} catch (final NullPointerException expected) {
							// 失敗したbindも元のリースを消費する(成功時はfixture群で検証)。
						}
					}
				}
				assertEquals(terminal, call(nodes(parent).get(0), "state").toString());
				assertEquals(1, nodes(parent).size());
				assertNull(call(nodes(parent).get(0), "identity"));
				assertNull(call(nodes(parent).get(0), "parent"));
				assertNull(call(nodes(parent).get(0), "sourceRange"));
				assertNull(call(nodes(parent).get(0), "retainedPlan"));
				assertEquals(0L, ((Number) call(nodes(parent).get(0), "anchor")).longValue());
				assertFalse(terminal + "の子をledgerが拒否する", collectOwnership(parent, source, 0, 0));
				if (handle.state() == RangeHandle.State.OPEN) handle.abandon();
				assertEquals(terminal, call(nodes(parent).get(0), "state").toString());
			}
		}
	}


	/** 空本文もscratchでは温存し、持ち出した本文のMAINは一度だけ受け付ける。 */
	public void testEmptyBodyOwnership() throws Exception {
		ContinuationStats.reset();
		try {
			final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(null,
					new FlowBlockBox(blockParams(), new FlowPos()));
			assertNull("空の所有台帳を生成", field(builder, "ownershipLedger"));
			call(builder, "setBody", new Class<?>[] { type("TwoPassBlockBuilder$ReplayBody") },
					construct("TwoPassBlockBuilder$ReplayBody$Empty", new Class<?>[0]));
			final var body = builder.detachDeferredBind();
			assertNull(body.handle());
			assertEquals("DETACHED", builder.ownershipState());
			assertNull("空本文のseal/detachで台帳を生成", field(builder, "ownershipLedger"));
			body.measureInto(null);
			body.measureInto(null);
			assertEquals(0L, ContinuationStats.TWO_PASS_EMPTY_BINDS.get());
			body.bind(null);
			assertEquals(1L, ContinuationStats.TWO_PASS_EMPTY_BINDS.get());
			for (final boolean measure : List.of(false, true)) {
				try {
					if (measure) body.measureInto(null); else body.bind(null);
					fail("消費済み空本文を再生した");
				} catch (final IllegalStateException expected) {
					assertTrue(expected.getMessage().contains("空本文"));
				}
			}
		} finally {
			ContinuationStats.reset();
		}
	}

	public void testLeafRangeDetachHasNoBuilderBackReference() throws Exception {
		try (final LayoutSource source = new LayoutSource()) {
			source.appendChars(17, new char[] { 'x' }, 0, 1, true);
			final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(null,
					new FlowBlockBox(blockParams(), new FlowPos()));
			final RangeHandle handle = new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO,
					RangeHandle.ReplayMode.CHILDREN_ONLY, true);
			call(builder, "setBody", new Class<?>[] { type("TwoPassBlockBuilder$ReplayBody") },
					construct("TwoPassBlockBuilder$ReplayBody$SourceRangeBody",
							new Class<?>[] { RangeHandle.class, PageGenerator.class }, handle, null));
			assertNull(field(builder, "ownershipLedger"));
			final var detached = builder.detachDeferredBind();
			assertSame(handle, detached.handle());
			assertSame(handle.sizes(), detached.sizes());
			assertNull(field(handle, "ownerStateObserver"));
			assertNull(field(builder, "ownershipLedger"));
			assertNull("census無効時の診断タグ", field(detached, "censusTag"));
		}
	}

	public void testUnsealedBodyFailsWithContext() {
		final BlockParams params = new BlockParams();
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(null, new FlowBlockBox(params, new FlowPos()));
		try {
			builder.sealBodyForRangeBind();
			fail("ソースなし本文を受理した");
		} catch (final ContinuationInvariantViolationException expected) {
			for (final String detail : List.of("NO_SOURCE", "uri=", "EventId=[", "box kind=FlowBlockBox", "owner state=")) {
				assertTrue(expected.getMessage(), expected.getMessage().contains(detail));
			}
		}
	}

	/** 致命エラーの継続設定でも不変条件違反を成功扱いせず、残った本文を解放する。 */
	public void testFailedConversionReleasesHandlesAndRecovers() throws Exception {
		final var original = TwoPassDigestParityTest.fixedManifest().get("0070-table-layout/float-in-auto.html");
		assertNotNull(original);
		final var properties = new java.util.HashMap<>(original.properties());
		properties.put("processing.fail-on-fatal-error", "false");
		final var document = new TwoPassDigestParityTest.CorpusInput(original.source(), original.passCount(),
				original.mimeType(), Map.copyOf(properties));
		final List<RangeHandle> handles = new ArrayList<>();
		final AtomicBoolean injected = new AtomicBoolean();
		try (final AutoCloseable observation = observe(RangeHandle.class, "sealObserver",
				(Consumer<RangeHandle>) handle -> {
					handles.add(handle);
					if (ReplayIntent.current() == ReplayIntent.MAIN && injected.compareAndSet(false, true)) {
						throw new ContinuationInvariantViolationException("injected seal failure");
					}
				})) {
			try {
				TwoPassDigestParityTest.transcode(document);
				fail("不変条件違反を成功扱いにした");
			} catch (final jp.cssj.cti2.TranscoderException expected) {
				assertEquals(jp.cssj.cti2.TranscoderException.STATE_BROKEN, expected.getState());
				assertTrue("原因の不変条件違反を失った", hasInvariantCause(expected));
			}
		}
		assertTrue("失敗注入が未発火", injected.get());
		assertTerminal("failed conversion", handles);
		TwoPassDigestParityTest.transcode(original);
	}

	private static boolean hasInvariantCause(final Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof ContinuationInvariantViolationException) return true;
		}
		return false;
	}

	/** spine本文のseal失敗をEPUB/DirectSessionが通常のI/O失敗に潰さない。 */
	public void testEpubSealFailureKeepsCauseAndBrokenState() throws Exception {
		final var bytes = new java.io.ByteArrayOutputStream();
		try (final var zip = new java.util.zip.ZipOutputStream(bytes)) {
			epubEntry(zip, "mimetype", "application/epub+zip");
			epubEntry(zip, "META-INF/container.xml", """
					<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
					<rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles>
					</container>
					""");
			epubEntry(zip, "content.opf", """
					<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
					<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
					<dc:identifier id="uid">urn:t4b:seal</dc:identifier><dc:title>seal</dc:title><dc:language>en</dc:language>
					</metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
					<spine><itemref idref="chapter"/></spine></package>
					""");
			epubEntry(zip, "chapter.xhtml", """
					<html xmlns="http://www.w3.org/1999/xhtml"><head><title>seal</title>
					<style>body{font:10pt/14pt serif}.host{float:left;width:auto}</style></head>
					<body><div class="host">spine seal failure</div></body></html>
					""");
		}
		final var injected = new ContinuationInvariantViolationException("injected EPUB spine seal failure");
		injected.initCause(new IllegalStateException("seal cause"));
		final AtomicBoolean fired = new AtomicBoolean();
		final List<RangeHandle> handles = new ArrayList<>();
		try (final AutoCloseable observer = observe(RangeHandle.class, "sealObserver", (Consumer<RangeHandle>) handle -> {
			handles.add(handle);
			if (ReplayIntent.current() == ReplayIntent.MAIN && fired.compareAndSet(false, true)) throw injected;
		})) {
			try {
				transcodeEpub(bytes.toByteArray());
				fail("EPUBの不変条件違反を成功扱いにした");
			} catch (final jp.cssj.cti2.TranscoderException expected) {
				assertEquals(jp.cssj.cti2.TranscoderException.STATE_BROKEN, expected.getState());
				assertEquals(jp.cssj.cti2.helpers.CTIMessageCodes.FATAL_UNEXPECTED, expected.getCode());
				assertSame(injected, ContinuationInvariantViolationException.findIn(expected));
				assertEquals("seal cause", injected.getCause().getMessage());
			}
		}
		assertTrue("spine本文のsealへ到達していない", fired.get());
		assertTerminal("EPUB failure", handles);
		transcodeEpub(bytes.toByteArray());
	}

	private static void epubEntry(final java.util.zip.ZipOutputStream zip, final String name, final String content)
			throws Exception {
		zip.putNextEntry(new java.util.zip.ZipEntry(name));
		zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static void transcodeEpub(final byte[] bytes) throws Exception {
		// 同じ設定初期化を使い、ストリーム入力でDirectSessionのIOException境界も通す。
		Class.forName(TwoPassDigestParityTest.class.getName());
		try (final var session = new net.zamasoft.foliojet.driver.DirectDriver()
				.getSession(java.net.URI.create("copper:direct:"), null)) {
			session.setResults(new jp.cssj.cti2.results.SingleResult(new net.zamasoft.zstream.io.impl.StreamFragmentedOutput(
					java.io.OutputStream.nullOutputStream())));
			session.setSourceResolver(net.zamasoft.zstream.resolver.composite.CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("processing.fail-on-fatal-error", "false");
			jp.cssj.cti2.helpers.CTISessionHelper.transcodeStream(session, new java.io.ByteArrayInputStream(bytes),
					java.net.URI.create("file:/t4b-seal.epub"), "application/epub+zip", null);
		}
	}

	/** cleanup後の収支だけでなく、正常終了前に所有を使い切っていることを検査する。 */
	public void testSuccessfulConversionReleasesBeforeSourceClose() throws Exception {
		for (final String fixture : List.of("table-float-in-table", "grid-sealed-float", "cell-parent")) {
			final List<LayoutSource.RetentionSnapshot> snapshots = new ArrayList<>();
			try (final AutoCloseable observer = observe(LayoutSource.class, "beforeCloseObserver",
					(Consumer<LayoutSource>) source -> snapshots.add(source.retentionSnapshot()))) {
				TwoPassFlowSealTest.render(TwoPassGridFlexRangeTest.fixtureFile(fixture));
			}
			assertFalse(fixture + ": 清算前観測なし", snapshots.isEmpty());
			for (final var snapshot : snapshots) {
				assertEquals(fixture + ": 清算待ちの本文 " + snapshot, 0, snapshot.openRanges());
			}
		}
	}

	public void testSourceCloseObservationPrecedesAbandon() throws Exception {
		final List<LayoutSource.RetentionSnapshot> snapshots = new ArrayList<>();
		final RangeHandle handle;
		try (final AutoCloseable observer = observe(LayoutSource.class, "beforeCloseObserver",
				(Consumer<LayoutSource>) source -> snapshots.add(source.retentionSnapshot()));
				final LayoutSource source = new LayoutSource()) {
			source.append(new LayoutSource.Chars(0, new char[] { 'x' }, false));
			handle = new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO, RangeHandle.ReplayMode.CHILDREN_ONLY);
		}
		assertEquals(1, snapshots.size());
		assertEquals(1, snapshots.get(0).openRanges());
		assertEquals(1L, snapshots.get(0).leases());
		assertEquals(RangeHandle.State.ABANDONED, handle.state());
	}

	/** 実HTMLからの登録順・計画のidentity共有・吸収先の集合を独立に照合する。 */
	public void testHtmlRegistersAllOwnershipKindsInOrder() throws Exception {
		final AtomicBoolean observed = new AtomicBoolean();
		final List<TwoPassBlockBuilder> owners = new ArrayList<>();
		try (final AutoCloseable observer = observeOwnership(owner -> {
			final var element = owner.getRootBox().getParams().element;
			if (element == null || !"ledger-host".equals(element.id()) || !observed.compareAndSet(false, true)) return;
			owners.add(owner);
			final List<?> nodes = nodes(owner);
			final List<String> kinds = nodes.stream().map(node -> call(node, "kind").toString()).toList();
			assertEquals(List.of("STF", "INLINE_BLOCK", "TABLE", "INLINE_TABLE", "TABLE", "GRID", "FLEX",
					"PAGE_FLOAT", "MARGIN_NOTE", "FOOTNOTE", "ABSOLUTE"), kinds);
			assertSame("inline表と計測tokenは同一計画", call(nodes.get(2), "identity"), call(nodes.get(3), "identity"));
			final List<String> ids = new ArrayList<>();
			final List<TwoPassBlockBuilder> expectedChildren = new ArrayList<>();
			for (final Object node : nodes) {
				assertSame(owner, call(node, "parent"));
				final Object identity = call(node, "identity");
				final net.zamasoft.foliojet.layout.box.IBox box;
				if (identity instanceof TwoPassBlockBuilder child) {
					box = child.getRootBox();
					expectedChildren.add(child);
				} else if (identity instanceof net.zamasoft.foliojet.layout.builder.RetainedTable table) {
					box = table.getTableBox();
				} else if (identity instanceof net.zamasoft.foliojet.layout.builder.RetainedGrid grid) {
					box = grid.getGridBox();
				} else {
					box = ((net.zamasoft.foliojet.layout.builder.RetainedFlex) identity).getFlexBox();
				}
				ids.add(box.getParams().element.id());
				assertEquals(box.getSourceAnchor(), ((Number) call(node, "anchor")).longValue());
			}
			assertEquals(List.of("stf", "inline", "inline-table", "inline-table", "table", "grid", "flex",
					"page-float", "margin-note", "footnote", "absolute"), ids);
			final var source = owner.getPageContext().getPageGenerator().getLayoutSource();
			final long from = owner.getRootBox().getSourceAnchor() + 1;
			final long to = source.endOf(from - 1) - 1;
			final List<TwoPassBlockBuilder> children = new ArrayList<>();
			final List<Object> tables = new ArrayList<>();
			final List<RangeHandle> ranges = new ArrayList<>();
			final Set<Long> anchors = new java.util.HashSet<>();
			assertTrue((boolean) call(ledger(owner), "collectAbsorbable",
					new Class<?>[] { LayoutSource.class, long.class, long.class, List.class, List.class, List.class, Set.class, Set.class },
					source, from, to, children, tables, ranges, anchors, Collections.newSetFromMap(new IdentityHashMap<>())));
			assertEquals(expectedChildren, children);
			assertEquals(List.of(call(nodes.get(2), "identity"), call(nodes.get(4), "identity")), tables);
			assertEquals(2, ranges.size()); // GridとFlexそれぞれ1項目。
			assertTrue(ranges.get(0).fromId() < ranges.get(1).fromId());
			assertEquals(Set.of(((Number) call(nodes.get(10), "anchor")).longValue()), anchors);
		})) {
			TwoPassFlowSealTest.render(TwoPassGridFlexRangeTest.fixtureFile("ledger-owners"));
		}
		assertTrue("実HTMLの所有台帳を観測していない", observed.get());
		assertTrue("吸収後も子ノードを保持", nodes(owners.get(0)).isEmpty());
	}

	/** DirectSessionの別スレッドへ届くstatic volatileの観測口をfinallyで復元する。 */
	static AutoCloseable observe(final Class<?> owner, final String name, final Object observer) throws Exception {
		final Field hook = owner.getDeclaredField(name);
		assertTrue(name + ": volatileでない", java.lang.reflect.Modifier.isVolatile(hook.getModifiers()));
		assertTrue(name + ": staticでない", java.lang.reflect.Modifier.isStatic(hook.getModifiers()));
		hook.setAccessible(true);
		final Object saved = hook.get(null);
		hook.set(null, observer);
		return () -> hook.set(null, saved);
	}

	static AutoCloseable observeOwnership(final Consumer<TwoPassBlockBuilder> observer) throws Exception {
		return observe(Class.forName("net.zamasoft.foliojet.layout.builder.impl.OwnershipLedger"),
				"collectionObserver", observer);
	}

	static List<?> nodes(final TwoPassBlockBuilder owner) {
		return (List<?>) call(call(owner, "ownershipLedger"), "nodes");
	}

	static Object field(final Object owner, final String name) {
		try {
			final Field field = owner.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(owner);
		} catch (final ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static Class<?> type(final String name) {
		try {
			return Class.forName("net.zamasoft.foliojet.layout.builder.impl." + name);
		} catch (final ClassNotFoundException e) {
			throw new AssertionError(e);
		}
	}

	private static Object construct(final String name, final Class<?>[] types, final Object... args) throws Exception {
		final Constructor<?> constructor = type(name).getDeclaredConstructor(types);
		constructor.setAccessible(true);
		return constructor.newInstance(args);
	}

	static Object call(final Object target, final String name) {
		return call(target, name, new Class<?>[0]);
	}

	private static Object call(final Object target, final String name, final Class<?>[] types, final Object... args) {
		final Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
		try {
			final Method method = type.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method.invoke(target instanceof Class<?> ? null : target, args);
		} catch (final InvocationTargetException e) {
			if (e.getCause() instanceof Error error) throw error;
			if (e.getCause() instanceof RuntimeException runtime) throw runtime;
			throw new AssertionError(e.getCause());
		} catch (final ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static Object ledger(final TwoPassBlockBuilder owner) { return call(owner, "ownershipLedger"); }

	private static BlockParams blockParams() {
		final BlockParams params = new BlockParams();
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	private static boolean collectOwnership(final TwoPassBlockBuilder owner, final LayoutSource source,
			final long from, final long to) {
		return (boolean) call(ledger(owner), "collectAbsorbable",
				new Class<?>[] { LayoutSource.class, long.class, long.class, List.class, List.class, List.class, Set.class, Set.class },
				source, from, to, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
				new java.util.HashSet<>(), Collections.newSetFromMap(new IdentityHashMap<>()));
	}
}
