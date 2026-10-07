package net.zamasoft.foliojet.layout.fragment;

/**
 * Read-only plan for turning a break into a continuation (C1d-C).
 *
 * <p>
 * Describes the ancestor chain approved by pageBreak preflight (box sequence in flowStack[1..]), total depth, and
 * current descent position. The split cascade reads it to turn only targeted boxes into continuations; fragments
 * propagate to the parent as {@link SplitResult.Frame} return values. <b>The plan holds no output</b> (passing a
 * mutable collector as an argument merely relocates the side channel; external review).
 * </p>
 *
 * @param chain approved chain (outside in; chain.get(i) = flowStack[i+1])
 * @param depth depth of the entire continuation (flowStack element count at the break)
 * @param index current descent position (index in chain)
 * @param columnLimit content limit for the target column only; null if there is no column host
 */
public record BreakPlan(java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> chain, int depth,
		int index, ColumnLimit columnLimit) {
	/** Reservation applied only to the current column's content during a page cut, not to owner dimensions. */
	public record ColumnLimit(net.zamasoft.foliojet.layout.box.AbstractContainerBox owner, double reservation) {
		public double contentLimit(final net.zamasoft.foliojet.layout.box.AbstractContainerBox box,
				final double ownerExtent) {
			return box == this.owner && this.reservation != 0 ? ownerExtent - this.reservation : ownerExtent;
		}
	}

	public BreakPlan(final java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> chain,
			final int depth, final int index) {
		this(chain, depth, index, null);
	}

	public BreakPlan withColumnLimit(final ColumnLimit limit) {
		return new BreakPlan(this.chain, this.depth, this.index, limit);
	}

	/** Passes only the content limit to ordinary box splitting, without the continuation chain. */
	public BreakPlan withoutChain() {
		return this.columnLimit == null ? null : new BreakPlan(java.util.List.of(), 0, 0, this.columnLimit);
	}

	public double contentLimit(final net.zamasoft.foliojet.layout.box.AbstractContainerBox box,
			final double ownerExtent) {
		return this.columnLimit == null ? ownerExtent : this.columnLimit.contentLimit(box, ownerExtent);
	}
	/**
	 * True if box is the current descent target (the next chain member).
	 */
	public boolean selects(final net.zamasoft.foliojet.layout.box.IBox box) {
		return this.index < this.chain.size() && this.chain.get(this.index) == box;
	}

	/**
	 * Returns a plan descended one level inward.
	 */
	public BreakPlan next() {
		return new BreakPlan(this.chain, this.depth, this.index + 1, this.columnLimit);
	}

	/**
	 * OpenTailShape depth if the current target member (flowStack[index+1]) becomes the innermost continuation level
	 * (number of open levels remaining in its container).
	 */
	public int openTailDepth() {
		return this.depth - this.index - 1;
	}
}
