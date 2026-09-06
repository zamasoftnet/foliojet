package jp.cssj.test.unit.displaylist;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.RangeHandle.State;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.visitor.ArtifactVisitor;

/** scratchの非消費・一時リース回収と、ハンドル単位の状態遷移を固定します。 */
public final class ScratchReplayScopeTest extends TestCase {
	private static RangeHandle handle(final LayoutSource source) {
		return new RangeHandle(source, 0, 0, IntrinsicSizes.ZERO, RangeHandle.ReplayMode.CHILDREN_ONLY);
	}

	private static LayoutSource source() {
		final LayoutSource source = new LayoutSource();
		source.append(new LayoutSource.Chars(0, "x".toCharArray(), false));
		return source;
	}

	public void testTerminalTransitionsAreOnceOnly() throws Exception {
		for (final State terminal : List.of(State.SUBSUMED, State.ABANDONED, State.CONSUMED)) {
			try (final LayoutSource source = source()) {
				final RangeHandle handle = handle(source);
				assertEquals(State.OPEN, handle.state());
				switch (terminal) {
				case SUBSUMED -> handle.subsume();
				case ABANDONED -> handle.abandon();
				case CONSUMED -> {
					// bind先の欠陥で失敗しても、元の使用権は消費して閉じる。
					try {
						handle.bind(null, null);
						fail("不正なbind先を受理した");
					} catch (final NullPointerException expected) {
						// 成功時のCONSUMEDは下の実セル再生で検証する。
					}
				}
				default -> throw new AssertionError(terminal);
				}
				assertEquals(terminal, handle.state());
				assertEquals(0, leaseCount(source));
				assertIllegalState(handle::subsume);
				assertIllegalState(handle::abandon);
				assertIllegalState(() -> handle.bind(null, null));
				assertIllegalState(() -> handle.measure(null, null));
			}
		}
	}

	public void testBalanceDetectsUnterminatedMainHandle() throws Exception {
		ContinuationStats.reset();
		try (final LayoutSource source = source()) {
			final RangeHandle handle = handle(source);
			boolean detected = false;
			try {
				TwoPassFlowSealTest.assertLeaseBalance("open main handle");
			} catch (final junit.framework.AssertionFailedError expected) {
				detected = true;
			} finally {
				handle.abandon();
			}
			assertTrue("MAINハンドルの終端忘れを検出しなかった", detected);
			TwoPassFlowSealTest.assertLeaseBalance("abandoned main handle");
			assertEquals(0, leaseCount(source));
		}
	}

	public void testFailedMeasurementKeepsOriginalLeaseOpen() throws Exception {
		try (final LayoutSource source = source()) {
			final RangeHandle handle = handle(source);
			try {
				handle.measure(null, null);
				fail("不正な計測先を受理した");
			} catch (final NullPointerException expected) {
				assertEquals(State.OPEN, handle.state());
				assertEquals(1, leaseCount(source));
			} finally {
				handle.abandon();
			}
			assertEquals(ReplayIntent.MAIN, ReplayIntent.current());
			assertEquals(0, leaseCount(source));
		}
	}

	public void testScratchScopeOwnsOnlyNewLeasesAndRestoresNestedIntent() throws Exception {
		try (final LayoutSource source = source()) {
			final RangeHandle original = handle(source);
			final RangeHandle outer;
			final RangeHandle inner;
			try (final ScratchReplayScope scope = new ScratchReplayScope()) {
				outer = handle(source);
				source.retainFrom(0); // ハンドルを持たない一時リースも回収対象
				assertEquals(ReplayIntent.MEASURE, ReplayIntent.current());
				assertIllegalState(() -> original.bind(null, null));
				try (final ScratchReplayScope nested = new ScratchReplayScope();
						final ReplayIntent.Scope main = ReplayIntent.MAIN.enter()) {
					inner = handle(source);
					assertEquals(ReplayIntent.MEASURE, ReplayIntent.current());
				}
				assertEquals(State.ABANDONED, inner.state());
				assertEquals(State.OPEN, outer.state());
				assertEquals(State.OPEN, original.state());
			}
			assertEquals(State.ABANDONED, outer.state());
			assertEquals(State.OPEN, original.state());
			assertEquals(1, leaseCount(source));
			assertEquals(ReplayIntent.MAIN, ReplayIntent.current());
			original.abandon();
			assertEquals(0, leaseCount(source));
		}
	}

	public void testScratchScopeAbandonsOnException() throws Exception {
		try (final LayoutSource source = source()) {
			final RangeHandle[] temporary = new RangeHandle[1];
			try {
				try (final ScratchReplayScope scope = new ScratchReplayScope()) {
					temporary[0] = handle(source);
					throw new IllegalArgumentException("計測途中の失敗");
				}
			} catch (final IllegalArgumentException expected) {
				assertEquals(State.ABANDONED, temporary[0].state());
				assertEquals(0, leaseCount(source));
				assertEquals(ReplayIntent.MAIN, ReplayIntent.current());
			}
		}
	}

	/**
	 * 同じセル範囲を独立したハンドルで「main-only」「scratch 2回→main」と再生し、
	 * 描画後の表示リストをbyte比較します。全文書も追加計測の有無で比較します。
	 */
	public void testTwoMeasurementsThenMainMatchesMainOnly() throws Exception {
		for (final String doc : TwoPassFlowSealTest.SCRATCH_DOCUMENTS) {
			checkDocument(new File("files/unittest", doc));
		}
	}

	public void testNestedFloatContentLossRegression() throws Exception {
		final File source = new File("files/fuzz-repro/nested-float-content-loss.html");
		final List<byte[]> pages = checkDocument(source);
		final StringBuilder text = new StringBuilder();
		final var pattern = java.util.regex.Pattern.compile("Text\\[\"([^\"]*)\"");
		for (final byte[] page : pages) {
			final var matcher = pattern.matcher(new String(page, StandardCharsets.UTF_8));
			while (matcher.find()) {
				text.append(matcher.group(1));
			}
		}
		assertTrue("事故歴の内側floatのT96が消えています: " + text, text.toString().contains("T96"));

	}

	static List<byte[]> checkDocument(final File source) throws Exception {
		ContinuationStats.reset();
		final List<byte[]> baseline = TwoPassFlowSealTest.render(source);
		final Field hook = RetainedTableBuilder.class.getDeclaredField("cellBindShadow");
		hook.setAccessible(true);
		final Object saved = hook.get(null);
		final Class<?> contract = hook.getType();
		final Method rangeBody = Class.forName("net.zamasoft.foliojet.layout.builder.impl.CellContent")
				.getDeclaredMethod("rangeBody");
		rangeBody.setAccessible(true);
		final AtomicInteger checked = new AtomicInteger();
		final boolean[] busy = { false };
		final Object shadow = Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] { contract },
				(proxy, method, args) -> {
					if (method.getName().equals("afterCellBind") && !busy[0]
							&& ReplayIntent.current() == ReplayIntent.MAIN) {
						final TwoPassBlockBuilder.DeferredBind body =
								(TwoPassBlockBuilder.DeferredBind) rangeBody.invoke(args[0]);
						if (body != null) {
							assertEquals("本bind後はCONSUMED", State.CONSUMED, body.handle().state());
							assertIllegalState(() -> body.handle().bind(null, null));
							assertIllegalState(() -> body.handle().measure(null, null));
						}
					}
					if (!method.getName().equals("beforeCellBind") || busy[0]
							|| ReplayIntent.current() == ReplayIntent.MEASURE) {
						return null;
					}
					final TwoPassBlockBuilder.DeferredBind body =
							(TwoPassBlockBuilder.DeferredBind) rangeBody.invoke(args[0]);
					final TableCellBox cell = (TableCellBox) args[1];
					if (body == null || !cell.canMeasureReplica()) {
						return null;
					}
					busy[0] = true;
					try {
						final LayoutStack stack = (LayoutStack) args[2];
						final RangeHandle original = body.handle();
						assertEquals(State.OPEN, original.state());
						final int leases = leaseCount(original.source());
						final Map<ReplayLeaseSession, Boolean> sessions = sessionLeases(stack.getPageContext());
						final byte[] mainOnly = replayCell(original, cell, stack, 0);
						final byte[] measured = replayCell(original, cell, stack, 2);
						assertTrue(source + ": scratch 2回後のmainとmain-onlyの表示リストが異なります",
								Arrays.equals(mainOnly, measured));
						// 本来のDeferredBind自身にも2回の計測を挟み、後続の本bindを残す。
						for (int i = 0; i < 2; ++i) {
							try (final ScratchReplayScope scope = new ScratchReplayScope()) {
								final BlockBuilder target = new BlockBuilder(stack, cell.newMeasureReplica());
								body.measureInto(target);
								target.close();
							}
							assertEquals(State.OPEN, original.state());
							assertEquals("一時リースが残っています", leases, leaseCount(original.source()));
						}
						assertEquals("既存ReplayLeaseSessionの消費状態が変わりました",
								sessions, sessionLeases(stack.getPageContext()));
						checked.incrementAndGet();
					} finally {
						busy[0] = false;
					}
					return null;
				});
		final List<byte[]> actual;
		try {
			ContinuationStats.reset();
			hook.set(null, shadow);
			actual = TwoPassFlowSealTest.render(source);
		} finally {
			hook.set(null, saved);
		}
		assertTrue(source + ": 実セルのscratch比較が未発火", checked.get() > 0);
		TwoPassFlowSealTest.assertPagesEqual(source.toString(), baseline, actual);
		System.err.println("[T1 scratch] " + source + " comparedCells=" + checked.get());
		TwoPassFlowSealTest.report(source.toString());
		return actual;
	}

	private static byte[] replayCell(final RangeHandle original, final TableCellBox cell,
			final LayoutStack stack, final int measurements) throws Exception {
		final RangeHandle copy = new RangeHandle(original.source(), original.fromId(), original.toId(),
				original.sizes(), original.replayMode());
		final PageGenerator generator = stack.getPageContext().getPageGenerator();
		try {
			final int leases = leaseCount(original.source());
			for (int i = 0; i < measurements; ++i) {
				try (final ScratchReplayScope scope = new ScratchReplayScope()) {
					final BlockBuilder target = new BlockBuilder(stack, cell.newMeasureReplica());
					copy.measure(target, generator);
					target.close();
				}
				assertEquals(State.OPEN, copy.state());
				assertEquals("scratch中の全一時リースを回収する", leases, leaseCount(original.source()));
			}
			final TableCellBox result = cell.newMeasureReplica();
			final BlockBuilder target = new BlockBuilder(stack, result);
			copy.bind(target, generator);
			target.close();
			assertEquals(State.CONSUMED, copy.state());
			result.finishLayout(stack.getContextBox());
			final Drawer drawer = new Drawer(0);
			final PageBox page = (PageBox) stack.getPageContext().getRootBox();
			result.frames(page, drawer, null, new AffineTransform(), 0, 0);
			result.draw(page, drawer, ArtifactVisitor.INSTANCE, null, new AffineTransform(), 0, 0, 0, 0);
			final StringBuilder dump = new StringBuilder();
			drawer.dump(dump, "");
			return dump.toString().getBytes(StandardCharsets.UTF_8);
		} finally {
			if (copy.state() == State.OPEN) {
				copy.abandon();
			}
		}
	}

	/** 既存の参照カウント台帳を診断する。productionにテスト用APIを増やさない。 */
	private static int leaseCount(final LayoutSource source) throws Exception {
		final Field leases = LayoutSource.class.getDeclaredField("retentionLeases");
		leases.setAccessible(true);
		final Map<?, ?> counts = (Map<?, ?>) leases.get(source);
		return counts.values().stream().mapToInt(value -> (Integer) value).sum();
	}

	/** 未消費が元々ある継続も許し、計測前後の診断値の不変性だけを検査する。 */
	private static Map<ReplayLeaseSession, Boolean> sessionLeases(final RootBuilder root) throws Exception {
		final Field field = RootBuilder.class.getDeclaredField("sessions");
		field.setAccessible(true);
		final Map<ReplayLeaseSession, Boolean> result = new IdentityHashMap<>();
		for (final Object session : (Iterable<?>) field.get(root)) {
			final ReplayLeaseSession lease = (ReplayLeaseSession) session;
			result.put(lease, lease.hasUnconsumedLeases());
		}
		return result;
	}

	private static void assertIllegalState(final Runnable action) {
		try {
			action.run();
			fail("二重終端または終端後の再生を受理した");
		} catch (final IllegalStateException expected) {
			// OPENからだけ遷移できる。
		}
	}
}
