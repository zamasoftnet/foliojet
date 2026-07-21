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
}
