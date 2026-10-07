package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

/**
 * Element facts for {@code @container} queries (2026-08-15, stage 4; development record §2).
 * Like {@link SelectorFacts}, this uses {@code CSSElement.elementKey} as the key.
 *
 * <p>
 * Stores two kinds of facts separately because their lifetimes and write timing differ:
 * </p>
 * <ul>
 * <li><b>Container type and names</b>: the element's own {@code container-type}/
 * {@code container-name} (the value consists only of one bit indicating
 * {@code container-type: inline-size} and a list of names). These can be written
 * **during style resolution** (just after the element's declarations are finalized, before layout).
 * They are the element's specified values and do not change across passes. Overwriting them is harmless,
 * but the timing of {@link #reset()} follows SelectorFacts.</li>
 * <li><b>Measured inline-size</b>: known only after layout is finalized (design §2, "when finishLayout
 * finalizes the dimensions"). **Overwrite it with this pass's value every time**
 * (unlike :has(), which never changes once true: dimensions can shrink or grow from the previous pass).
 * {@code StyleContext.merge} in the next pass (N+1) reads it.
 * Pass 1 has no value ({@link #getInlineSize} returns {@code NaN}), so all queries are false,
 * matching the current fallback (design §2).</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerFacts {
	/** Tolerance for fixed-point detection (pt). Same value as {@code LayoutUtils.THRESHOLD} (design §3). */
	private static final double CONVERGENCE_THRESHOLD = 0.5;

	private Map<Long, String[]> containerNames;

	private Map<Long, Double> inlineSize;

	/**
	 * Snapshot of {@link #inlineSize} at the start of the previous pass
	 * (stage 5, for fixed-point detection in design §3/§4).
	 * {@link #beginPass()} updates it at the start of each pass.
	 */
	private Map<Long, Double> previousInlineSize;

	/** Snapshot at the start of the pass two passes ago (stage 7, for oscillation detection). */
	private Map<Long, Double> beforePreviousInlineSize;

	/**
	 * Containers whose values are locked after detecting oscillation
	 * (stage 7, design §4, "resolve oscillation toward the narrower size").
	 * Once locked, they are not overwritten in subsequent passes.
	 */
	private Map<Long, Double> pinnedInlineSize;

	/**
	 * Call before recording new facts in this pass. Like {@link SelectorFacts#reset()},
	 * this only needs to be called once at the start of STRUCTURE_SCAN. Subsequent passes
	 * keep accumulating and overwriting facts because {@code container-type}/{@code container-name}
	 * never disappear.
	 */
	public void reset() {
		this.containerNames = null;
		this.inlineSize = null;
		this.previousInlineSize = null;
		this.beforePreviousInlineSize = null;
		this.pinnedInlineSize = null;
	}

	/**
	 * Call at the start of each pass that performs actual layout (MIDDLE_PASS/LAST_PASS).
	 * This snapshot fixes the "dimensions from pass N-1" in stage 5, design §3:
	 * "evaluate queries using pass N-1 dimensions, perform layout, then record dimensions."
	 * Do not call during STRUCTURE_SCAN/DOCUMENT (single-pass conversion): there are no dimension facts.
	 */
	public void beginPass() {
		this.beforePreviousInlineSize = this.previousInlineSize;
		this.previousInlineSize = this.inlineSize == null ? null : new HashMap<Long, Double>(this.inlineSize);
	}

	/**
	 * Whether the measured inline-sizes written since the latest {@link #beginPass()}
	 * have reached a fixed point relative to the preceding snapshot (design §3/§4).
	 * Uses a tolerance of 0.5 pt. A key present on only one side (newly identified as a container,
	 * or absent in the previous pass) also counts as a mismatch.
	 *
	 * <p>
	 * {@link #setInlineSize} detects period-2 oscillation and locks the value to the narrower size.
	 * Once locked, the value is treated as a fixed point (and therefore as converged here).
	 * {@link #hasOscillation()} indicates whether any value was locked.
	 * </p>
	 */
	public boolean isConverged() {
		final Map<Long, Double> before = this.previousInlineSize;
		final Map<Long, Double> after = this.inlineSize;
		final int beforeSize = before == null ? 0 : before.size();
		final int afterSize = after == null ? 0 : after.size();
		if (beforeSize != afterSize) {
			return false;
		}
		if (after == null) {
			return true;
		}
		for (final Map.Entry<Long, Double> entry : after.entrySet()) {
			final Double beforeValue = before.get(entry.getKey());
			if (beforeValue == null || Math.abs(beforeValue.doubleValue() - entry.getValue().doubleValue()) >= CONVERGENCE_THRESHOLD) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Records that the element with elementKey is a {@code container-type: inline-size} query container.
	 * Does not record {@code normal} (non-container), saving space and matching the default false
	 * returned by {@link #isInlineSizeContainer}.
	 * Does not record {@code container-type: size}, which is outside the first stage's scope
	 * (design §4, "exclude container-type: size from the initial implementation").
	 */
	public void setInlineSizeContainer(long elementKey, String[] names) {
		if (this.containerNames == null) {
			this.containerNames = new HashMap<Long, String[]>();
		}
		this.containerNames.put(elementKey, names);
	}

	public boolean isInlineSizeContainer(long elementKey) {
		return this.containerNames != null && this.containerNames.containsKey(elementKey);
	}

	/** The container's {@code container-name} (an empty array if unnamed). */
	public String[] getContainerNames(long elementKey) {
		if (this.containerNames == null) {
			return EMPTY_NAMES;
		}
		String[] names = this.containerNames.get(elementKey);
		return names != null ? names : EMPTY_NAMES;
	}

	private static final String[] EMPTY_NAMES = new String[0];

	/**
	 * Records the container's used inline-size (pt) after layout is finalized.
	 *
	 * <p>
	 * Stage 7 (second half of design §4): detect period-2 <b>oscillation</b>, where a query that
	 * "shrinks when matched and grows when unmatched" alternates A→B→A→B… without reaching a fixed point,
	 * and <b>lock it to the narrower size</b>. The narrower size is the fallback side, closer to
	 * the current behavior and less likely to lose content. Once locked, a container is not overwritten
	 * in subsequent passes (locking would be pointless if it oscillated again in the next pass).
	 * </p>
	 */
	public void setInlineSize(long elementKey, double lengthPt) {
		if (this.inlineSize == null) {
			this.inlineSize = new HashMap<Long, Double>();
		}
		final Long key = elementKey;
		if (this.pinnedInlineSize != null) {
			final Double pinned = this.pinnedInlineSize.get(key);
			if (pinned != null) {
				this.inlineSize.put(key, pinned);
				return;
			}
		}
		// The current value matches N-2 but differs from N-1: a period-2 oscillation.
		final Double twoAgo = this.beforePreviousInlineSize == null ? null
				: this.beforePreviousInlineSize.get(key);
		final Double oneAgo = this.previousInlineSize == null ? null : this.previousInlineSize.get(key);
		if (twoAgo != null && oneAgo != null
				&& Math.abs(twoAgo.doubleValue() - lengthPt) < CONVERGENCE_THRESHOLD
				&& Math.abs(oneAgo.doubleValue() - lengthPt) >= CONVERGENCE_THRESHOLD) {
			final double narrower = Math.min(lengthPt, oneAgo.doubleValue());
			if (this.pinnedInlineSize == null) {
				this.pinnedInlineSize = new HashMap<Long, Double>();
			}
			this.pinnedInlineSize.put(key, narrower);
			this.inlineSize.put(key, narrower);
			return;
		}
		this.inlineSize.put(key, lengthPt);
	}

	/** Whether any container's value was locked after detecting oscillation (for diagnostics). */
	public boolean hasOscillation() {
		return this.pinnedInlineSize != null && !this.pinnedInlineSize.isEmpty();
	}

	/** Measured inline-size (pt) through the previous pass. {@code Double.NaN} if not yet determined. */
	public double getInlineSize(long elementKey) {
		if (this.inlineSize == null) {
			return Double.NaN;
		}
		final Double value = this.inlineSize.get(elementKey);
		return value != null ? value.doubleValue() : Double.NaN;
	}
}
