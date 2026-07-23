package net.zamasoft.foliojet.layout.fragment;

import junit.framework.TestCase;

/**
 * {@link FragmentationAudit}(2026-07-21新設、M6d-0.5)の直接単体テストです。
 * 実文書を経由せず、観測API自体の契約(既定無効・スレッドごとのtrace・
 * reset)を固定する。
 */
public class FragmentationAuditTest extends TestCase {
	protected void tearDown() throws Exception {
		FragmentationAudit.setEnabled(false);
		FragmentationAudit.reset();
	}

	public void testDisabledByDefault() {
		assertFalse("既定では無効のはずです", FragmentationAudit.isEnabled());
		assertNull("無効時はcurrent()がnullを返すはずです", FragmentationAudit.current());
	}

	public void testEnabledReturnsSameTraceInstancePerThread() {
		FragmentationAudit.setEnabled(true);
		final FragmentationTrace first = FragmentationAudit.current();
		assertNotNull(first);
		final FragmentationTrace second = FragmentationAudit.current();
		assertSame("同一スレッド内では同じtraceインスタンスを返すはずです", first, second);
	}

	public void testRecordAndReset() {
		FragmentationAudit.setEnabled(true);
		final FragmentationTrace trace = FragmentationAudit.current();
		trace.record(new FragmentationEvent.LoopExamine(false, 123, 0, 0, false, false));
		assertEquals(1, trace.events().size());

		FragmentationAudit.reset();
		final FragmentationTrace afterReset = FragmentationAudit.current();
		assertNotSame("resetですると新しいtraceインスタンスに切り替わるはずです", trace, afterReset);
		assertEquals(0, afterReset.events().size());
	}

	public void testDisablingStopsReturningATrace() {
		FragmentationAudit.setEnabled(true);
		assertNotNull(FragmentationAudit.current());
		FragmentationAudit.setEnabled(false);
		assertNull("無効化後はcurrent()がnullを返すはずです", FragmentationAudit.current());
	}

	/**
	 * {@code adopt}(2026-07-23新設、{@code processing.large-stack-thread}
	 * 用)は、呼び出し元スレッドで取得したtraceを別スレッドへ明示的に
	 * 引き継ぐ——両スレッドが同一のtraceインスタンスに記録を集約する。
	 */
	public void testAdoptSharesTraceAcrossThreads() throws InterruptedException {
		FragmentationAudit.setEnabled(true);
		final FragmentationTrace mainThreadTrace = FragmentationAudit.current();
		mainThreadTrace.record(new FragmentationEvent.LoopExamine(false, 1, 0, 0, false, false));

		final FragmentationTrace[] workerThreadTrace = new FragmentationTrace[1];
		final Thread worker = new Thread(() -> {
			FragmentationAudit.adopt(mainThreadTrace);
			workerThreadTrace[0] = FragmentationAudit.current();
			workerThreadTrace[0].record(new FragmentationEvent.LoopExamine(false, 2, 0, 0, false, false));
		});
		worker.start();
		worker.join();

		assertSame("adopt後は別スレッドでも同一のtraceインスタンスを返すはずです", mainThreadTrace, workerThreadTrace[0]);
		assertEquals("両スレッドの記録が同じtraceに集約されているはずです", 2, mainThreadTrace.events().size());
	}

	/** {@code trace}がnull(無効時)の場合、adoptは何もしない(現在のスレッドのCURRENTを汚染しない)。 */
	public void testAdoptWithNullTraceIsNoOp() throws InterruptedException {
		FragmentationAudit.setEnabled(false);
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try {
				FragmentationAudit.adopt(null);
				assertNull("無効時はadopt(null)後もcurrent()はnullのはずです", FragmentationAudit.current());
			} catch (Throwable t) {
				failure[0] = t;
			}
		});
		worker.start();
		worker.join();
		if (failure[0] != null) {
			throw new AssertionError(failure[0]);
		}
	}
}
