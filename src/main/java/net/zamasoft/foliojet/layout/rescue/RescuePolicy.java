package net.zamasoft.foliojet.layout.rescue;

/**
 * A <b>test-only injection point</b> controlling whether visual rescue splitting actually runs
 * (introduced 2026-07-25, increments 4/5; its role was finalized in increment 8).
 *
 * <h2>Not a public setting</h2>
 *
 * <p>
 * <b>No permanent UA property or CSS property is provided</b> (decision in recommendation §6).
 * The final trigger is only the existing overflow terminal path, so there is no benefit in leaving a
 * permanent branch setting for normal documents. Production always defaults to {@link #ENABLED} ;
 * only test code can select {@link #DISABLED} (through {@link #scoped()} alone).
 * </p>
 *
 * <h2>Why this remains (2026-07-25, increment 8 decision)</h2>
 *
 * <p>
 * Recommendation §6-8 called for enabling production by default and removing the temporary switch.
 * Increment 5 completed the default enablement. The switch <b>is not removed</b>:
 * doing so would also lose the following verification, central to the rescue-splitting agreement.
 * </p>
 *
 * <ul>
 * <li><b>Proof that behavior is untouched</b>: The agreement excludes absolute positioning from rescue
 * ({@code VisualRescuePlanner.isRescuablePos}).
 * {@code VisualRescueSplitTest.testAbsoluteImageIsNotRescued} enforces this by
 * <b>exact display-list equality</b> between ENABLED and DISABLED.
 * Hard-coding expected values for the same claim cannot distinguish it from a coincidental match.
 * The check that multi-line paragraphs are not rescued
 * ({@code testMultiLineParagraphIsSplitByLinesNotSliced}) follows the same pattern.</li>
 * <li><b>A record of what was replaced</b>: Rescue replaces <b>only</b> the former terminal behavior
 * of drawing with overflow. Output under {@code DISABLED} preserves an executable record of what
 * preceded the replacement (that information was lost)
 * ({@code testDisabledPolicyKeepsLegacyOverflow}, {@code testTallFloatWithoutRescueLosesTheRemainder} ).</li>
 * <li><b>The original decision-table row</b>: Decision-table transition 4→5 in
 * {@code FloatingsSplitPageAxisTest} (first and unsplittable means KEEP with overflow allowed) is a
 * {@code FloatSplitPlan} classification rule independent of rescue splitting.
 * Without retaining the row before rescue overrides it, decision-table tests cannot detect regressions
 * in the classification rule itself.
 * </li>
 * </ul>
 *
 * <p>
 * Only scaffolding whose removal loses no verification may be removed. This does not meet that condition.
 * <b>Do not expand it, however</b>: adding settings to select rescue scope or slicing strategy is prohibited.
 * The only states this type may have are "perform rescue" and "do not perform rescue, as before."
 * </p>
 *
 * <h2>Threads</h2>
 *
 * <p>
 * Stored in {@link ThreadLocal} to prevent interference between concurrent conversions
 * (the same reason as the continuation-path stack in {@code ContinuationStats} ).
 * </p>
 */
public enum RescuePolicy {

	/**
	 * No rescue (as before, draw with overflow even at the page start).
	 * <b>Selectable only from tests</b>.
	 */
	DISABLED,

	/** Perform rescue (the production default). */
	ENABLED;

	/**
	 * The default value. Enabled in increment 5, extended to all paths in increments 6/7,
	 * and finalized in increment 8. No configuration can change it.
	 */
	public static final RescuePolicy DEFAULT = ENABLED;

	private static final ThreadLocal<RescuePolicy> CURRENT = ThreadLocal.withInitial(() -> DEFAULT);

	/** The current thread's policy. */
	public static RescuePolicy current() {
		return CURRENT.get();
	}

	/** Returns true if the policy enables rescue. */
	public static boolean isEnabled() {
		return current() == ENABLED;
	}

	/**
	 * Temporarily replaces the policy on this thread (test only).
	 *
	 * <pre>
	 * try (RescuePolicy.Scope scope = RescuePolicy.DISABLED.scoped()) {
	 * 	// Existing behavior
	 * }
	 * </pre>
	 *
	 * @return scope for restoring the policy
	 */
	public Scope scoped() {
		final RescuePolicy previous = CURRENT.get();
		CURRENT.set(this);
		return () -> {
			if (previous == DEFAULT) {
				// Do not leave the ThreadLocal behind (prevents leaks on pooled threads).
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		};
	}

	/** The restoration handle for {@link RescuePolicy#scoped()}. */
	public interface Scope extends AutoCloseable {
		void close();
	}
}
