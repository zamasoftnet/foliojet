package net.zamasoft.foliojet.layout.segment;

import java.io.IOException;

/**
 * A bridge for tests to access {@link TextSpill}'s package-private fault-injection hooks
 * (E-6 endurance tests, added 2026-07-24, tests only; the same approach as {@code LayoutSourceTestHooks}).
 *
 * <p>
 * Production behavior is unchanged because the hook remains null. A test that sets it must always
 * call {@link #clearFaultInjector()} in finally, because it is shared statically.
 * </p>
 */
public final class TextSpillTestHooks {
	private TextSpillTestHooks() {
	}

	/** An action that may throw {@link IOException} (describes the fault to inject). */
	@FunctionalInterface
	public interface IOAction {
		void run() throws IOException;
	}

	/**
	 * Sets up spill I/O fault injection. A {@code null} action means no injection on that path.
	 *
	 * @param beforeAppend runs just before a spill write (null means no injection)
	 * @param beforeRead   runs just before a spill read (null means no injection)
	 */
	public static void setFaultInjector(final IOAction beforeAppend, final IOAction beforeRead) {
		TextSpill.faultInjector = new TextSpill.IOFaultInjector() {
			@Override
			public void beforeAppend() throws IOException {
				if (beforeAppend != null) {
					beforeAppend.run();
				}
			}

			@Override
			public void beforeRead() throws IOException {
				if (beforeRead != null) {
					beforeRead.run();
				}
			}
		};
	}

	/** Clears fault injection (idempotent). */
	public static void clearFaultInjector() {
		TextSpill.faultInjector = null;
	}
}
