package net.zamasoft.foliojet.layout.sizing;

/**
 * Marks the scratch layouts that measure intrinsic sizes ({@link MeasuredIntrinsics}), during which line-axis
 * percentages are cyclic (2026-10-09).
 *
 * <p>
 * CSS Sizing 3 §5.2: while the size a percentage refers to is being found from the content, the percentage counts as
 * auto for the content's contributions; a compressible replaced element (an image, a form control) contributes 0 to
 * the min-content size. The scratch page is 10<sup>6</sup> wide for max-content and 0 for min-content, so resolving
 * {@code width: 100%} against it made a button holding a {@code width: 100%} input as wide as the page (plotly.com's
 * search field), and a {@code width: 100%} image did the same. Line-axis percentages of inline-blocks, floats and
 * replaced elements whose basis is the scratch page's line are resolved as auto instead ({@link #cyclic(double)});
 * after the measurement, the real layout resolves them against the size the measurement gave.
 * </p>
 */
public final class CyclicPercent {
	private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

	private CyclicPercent() {
		// static only
	}

	/** Whether an intrinsic size is being measured here. */
	public static boolean active() {
		return CURRENT.get() != null;
	}

	/**
	 * Whether a line-axis percentage resolved against {@code basis} is cyclic: an intrinsic size is being measured and
	 * the basis is the scratch page's line (10<sup>6</sup> for max-content, 0 for min-content, less the frames of the
	 * auto-width boxes in between). A percentage of a box with a definite size inside the measured content resolves
	 * against that size, as usual.
	 *
	 * @param basis the size the percentage would resolve against
	 */
	public static boolean cyclic(final double basis) {
		final Scope scope = CURRENT.get();
		if (scope == null) {
			return false;
		}
		return scope.maxContent ? basis >= MeasuredIntrinsics.INFINITE / 2 : basis <= 0;
	}

	/** Whether the current measurement is the max-content one (false for min-content or outside a measurement). */
	public static boolean maxContent() {
		final Scope scope = CURRENT.get();
		return scope != null && scope.maxContent;
	}

	/**
	 * Enters a measurement.
	 *
	 * @param maxContent true for the max-content measurement, false for the min-content one
	 */
	public static Scope enter(final boolean maxContent) {
		return new Scope(maxContent);
	}

	/** An entered measurement; closed in the reverse order of entering. */
	public static final class Scope implements AutoCloseable {
		private final Scope previous;
		private final boolean maxContent;

		private Scope(final boolean maxContent) {
			this.previous = CURRENT.get();
			this.maxContent = maxContent;
			CURRENT.set(this);
		}

		@Override
		public void close() {
			if (CURRENT.get() != this) {
				throw new IllegalStateException("cyclic percentage scopes close in reverse order");
			}
			if (this.previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(this.previous);
			}
		}
	}
}
