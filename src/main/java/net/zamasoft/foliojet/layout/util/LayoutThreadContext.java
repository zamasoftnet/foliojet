package net.zamasoft.foliojet.layout.util;

import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.rescue.RescuePolicy;

/**
 * Thread-bound policies to transfer when running layout on another thread (2026-09-02).
 *
 * <p>
 * Layout runs on a dedicated thread with a 64 MB stack ({@code DirectSession.runOnLargeStack}).
 * EPUB items are also laid out by parallel workers. Neither <b>inherits the caller thread's
 * ThreadLocal values</b>, so externally configured policies must be passed explicitly.
 * On 2026-07-26, four failed tests exposed missing transfer of {@code RescuePolicy}.
 * Afterward, the rule was "add it here whenever adding a ThreadLocal," but that location was
 * buried in one function in {@code DirectSession} (design review, 2026-09-02).
 * Consolidating them here lets thread creators transfer just this object.
 * </p>
 *
 * <p>
 * Not transferred: {@code ContinuationStats.continuationPathStack} temporarily records the path
 * being processed; a new thread should correctly start empty.
 * </p>
 */
public final class LayoutThreadContext {
	/**
	 * Stack size for the thread running layout (64 MB).
	 *
	 * <p>
	 * Needed for deeply nested documents. Measurements (2026-07-25, {@code DirectSession} records):
	 * about 2 MB at nesting depth 1000, about 10 MB at 5000. 64 MB provides a tenfold margin;
	 * it is only reserved and is not committed until used.
	 * </p>
	 */
	public static final int LAYOUT_STACK_SIZE = 64 * 1024 * 1024;

	private final RescuePolicy rescuePolicy;
	private final String displayListDir;
	private final boolean detailedDisplayListGeometry;

	private LayoutThreadContext(final RescuePolicy rescuePolicy, final String displayListDir,
			final boolean detailedDisplayListGeometry) {
		this.rescuePolicy = rescuePolicy;
		this.displayListDir = displayListDir;
		this.detailedDisplayListGeometry = detailedDisplayListGeometry;
	}

	/** Captures the current thread's policies. */
	public static LayoutThreadContext capture() {
		return new LayoutThreadContext(RescuePolicy.current(), DisplayListDumper.currentDir(),
				DisplayListDumper.currentDetailedGeometry());
	}

	/**
	 * Applies captured policies to the current thread. Closing restores the originals.
	 * Use {@code try (var scope = context.apply())} at the start of a new thread.
	 */
	public AutoCloseable apply() {
		final AutoCloseable policy = this.rescuePolicy.scoped();
		final AutoCloseable dump = DisplayListDumper.scopedDir(this.displayListDir);
		final AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(this.detailedDisplayListGeometry);
		return () -> {
			try (geometry; dump; policy) {
				// Close in reverse order
			}
		};
	}
}
