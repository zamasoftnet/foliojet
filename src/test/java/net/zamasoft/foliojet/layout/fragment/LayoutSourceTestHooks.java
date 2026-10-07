package net.zamasoft.foliojet.layout.fragment;

import java.util.function.Consumer;

/**
 * A bridge for tests to access {@link LayoutSource}'s package-private observation hooks
 * (E-6 increment 2, added 2026-07-24, tests only).
 *
 * <p>
 * Production behavior is unchanged because the hook remains null. Shadow round-trip tests
 * (in {@code net.zamasoft.foliojet.layout.segment}) use it to observe the full append sequence
 * from outside during transcoding.
 * </p>
 */
public final class LayoutSourceTestHooks {
	private LayoutSourceTestHooks() {
	}

	/**
	 * Sets the append observation hook ({@code null} clears it). A test that sets it must always
	 * clear it in finally, because it is shared statically.
	 */
	public static void setAppendObserver(final Consumer<LayoutSource.Event> observer) {
		LayoutSource.appendObserver = observer;
	}
}
