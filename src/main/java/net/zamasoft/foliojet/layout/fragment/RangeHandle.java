package net.zamasoft.foliojet.layout.fragment;

import java.util.Objects;

import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/** The sole owner of a sealed body's range and dimensions, and of its lease or text slice. */
public final class RangeHandle {
	/** fromId/toId are the actual closed replay interval extracted at seal time according to the mode. */
	public enum ReplayMode {
		/** Only children of an existing root (ordinary TwoPass or takeover item): [anchor+1, end-1]. */
		CHILDREN_ONLY,
		/** Also rebuilds the authored root inside a neutral wrapper: [anchor, end]; replaced elements: [anchor, anchor]. */
		ROOTED_SUBTREE,
		/** Children excluding an anonymous item's synthetic Start/End: [anchor+1, end-1]. */
		ANONYMOUS_CHILDREN
	}

	/** Disallows replay or repeated termination from a terminal state. */
	public enum State { OPEN, CONSUMED, SUBSUMED, ABANDONED }

	private final LayoutSource source;
	private final long fromId, toId;
	private final LayoutSource.RetentionLease lease;
	private LayoutSource.SealedTextSlice textSlice;
	private final IntrinsicSizes sizes;
	private final ReplayMode replayMode;
	private State state = State.OPEN;
	private boolean replaying;
	private boolean cell;
	private ScratchOwner scratchOwner = ScratchReplayScope.currentOwner();
	private boolean scratchComplete;
	private java.util.function.Consumer<State> ownerStateObserver;

	/** Test-only observation point. Null in normal conversion; does not retain handles globally. */
	static volatile java.util.function.Consumer<RangeHandle> sealObserver;
	static volatile java.util.function.BiConsumer<RangeHandle, ReplayIntent> replayStartObserver;
	static volatile java.util.function.BiConsumer<RangeHandle, ReplayIntent> replayObserver;

	/** Retains a validated closed interval. Dimensions are an immutable snapshot. */
	public RangeHandle(final LayoutSource source, final long fromId, final long toId,
			final IntrinsicSizes sizes, final ReplayMode replayMode) {
		this(source, fromId, toId, sizes, replayMode, false);
	}

	/** Only Retained table cells not absorbed into a parent range specify sliceText. */
	public RangeHandle(final LayoutSource source, final long fromId, final long toId,
			final IntrinsicSizes sizes, final ReplayMode replayMode, final boolean sliceText) {
		this.source = Objects.requireNonNull(source);
		this.sizes = Objects.requireNonNull(sizes);
		this.replayMode = Objects.requireNonNull(replayMode);
		if (fromId < 0 || toId < fromId) {
			throw new IllegalArgumentException("不正な本文範囲: [" + fromId + ", " + toId + "]");
		}
		this.fromId = fromId;
		this.toId = toId;
		final LayoutSource.RetentionLease retained = source.retainFrom(fromId);
		try {
			this.textSlice = source.retainTextSlice(fromId, toId);
			if (this.textSlice == null && sliceText) this.textSlice = source.sealTextSlice(fromId, toId);
		} catch (final RuntimeException | Error e) {
			retained.close();
			throw e;
		}
		if (this.textSlice != null) retained.close();
		this.lease = this.textSlice == null ? retained : null;
		source.registerRange(this);
		ScratchReplayScope.register(this);
		ContinuationStats.recordTwoPassSealEligible();
		final var observer = sealObserver;
		if (observer != null) {
			observer.accept(this);
		}
	}

	public LayoutSource source() { return this.source; }
	public long fromId() { return this.fromId; }
	public long toId() { return this.toId; }
	public IntrinsicSizes sizes() { return this.sizes; }
	public ReplayMode replayMode() { return this.replayMode; }
	public State state() { return this.state; }
	public boolean isReplaying() { return this.replaying; }
	public boolean hasTextSlice() { return this.textSlice != null; }

	/**
	 * Lifetime-end notification called by the host's final bind, or close for a host that is not placed.
	 * Borrowing through MEASURE is not termination. Does not touch bodies of other scratches or MAIN.
	 * Actual abandon occurs at the owner's safe point after delivery/replay returns.
	 */
	public void completeScratchHost() {
		if (this.scratchOwner != null && this.scratchOwner == ScratchReplayScope.currentOwner()) {
			this.requireOpen();
			this.scratchComplete = true;
		}
	}

	boolean isScratchComplete() { return this.scratchComplete; }

	/** Notifies the host's ownership ledger of termination. Null severs the association on detach. */
	public void observeOwnerState(final java.util.function.Consumer<State> observer) {
		this.ownerStateObserver = observer;
		if (observer != null && this.state != State.OPEN) {
			this.notifyOwnerState();
		}
	}

	private void notifyOwnerState() {
		final var observer = this.ownerStateObserver;
		this.ownerStateObserver = null; // Do not retain the host through a terminated handle.
		if (observer != null) {
			observer.accept(this.state);
		}
	}

	/** A marker for recording table-cell-specific accounting at the same termination point. */
	public void markCell() {
		this.requireOpen();
		if (this.cell) {
			throw new IllegalStateException("表セルとして計上済みです");
		}
		this.cell = true;
		ContinuationStats.recordCellRangeSeal();
	}

	/** Final placement. Becomes CONSUMED on success or failure and closes the lease exactly once. */
	public void bind(final BlockBuilder builder, final PageGenerator pageGenerator) {
		this.requireOpen();
		if (ReplayIntent.current() == ReplayIntent.MEASURE) {
			throw new IllegalStateException("MEASURE中に本文を消費できません。measureを使ってください");
		}
		this.state = State.CONSUMED;
		this.source.releaseRange(this);
		this.notifyOwnerState();
		ContinuationStats.TWO_PASS_RANGES_CONSUMED.incrementAndGet();
		try {
			this.observeReplayStart(ReplayIntent.MAIN);
			this.replay(builder, pageGenerator, ReplayIntent.MAIN);
			ContinuationStats.recordTwoPassRangeBind();
			if (this.cell) {
				ContinuationStats.recordCellRangeBind();
			}
		} finally {
			this.releaseBody();
			this.observeReplay(ReplayIntent.MAIN);
		}
	}

	/** Temporary measurement. Leaves the original handle and lease OPEN. */
	public void measure(final BlockBuilder builder, final PageGenerator pageGenerator) {
		this.requireOpen();
		this.replaying = true;
		try {
			this.observeReplayStart(ReplayIntent.MEASURE);
			this.replay(builder, pageGenerator, ReplayIntent.MEASURE);
		} finally {
			this.replaying = false;
			this.observeReplay(ReplayIntent.MEASURE);
		}
	}

	private void observeReplayStart(final ReplayIntent intent) {
		final var observer = replayStartObserver;
		if (observer != null) {
			observer.accept(this, intent);
		}
	}

	private void replay(final BlockBuilder builder, final PageGenerator pageGenerator, final ReplayIntent intent) {
		if (this.textSlice == null) {
			SourceReplayer.bindTwoPassRange(this.source, this.fromId, this.toId, builder, pageGenerator, intent);
		} else {
			SourceReplayer.bindTwoPassRange(this.textSlice.capture(), builder, pageGenerator, intent);
		}
	}

	private void releaseBody() {
		this.scratchOwner = null;
		if (this.textSlice != null) {
			this.textSlice.release();
			this.textSlice = null;
		}
		if (this.lease != null) this.lease.close();
	}

	private void observeReplay(final ReplayIntent intent) {
		final var observer = replayObserver;
		if (observer != null) {
			observer.accept(this, intent);
		}
	}

	/** Transfers ownership to replay of the parent range after acquiring the parent's lease. */
	public void subsume() {
		if (this.textSlice != null) throw new IllegalStateException("吸収対象外のセルsliceを親へ移せません");
		this.terminate(State.SUBSUMED);
		ContinuationStats.recordTwoPassSealSubsumed();
		if (this.cell) {
			ContinuationStats.recordCellRangeSealSubsumed();
		}
	}

	/**
	 * Discards bodies that will not be replayed, such as those of temporary builders.
	 * Preserves terminal state and releases the lease/textSlice even if the termination notification fails.
	 * Propagates notification exceptions to the caller.
	 */
	public void abandon() {
		this.terminate(State.ABANDONED);
		ContinuationStats.TWO_PASS_SEALS_ABANDONED.incrementAndGet();
		if (this.cell) {
			ContinuationStats.CELL_RANGE_SEALS_ABANDONED.incrementAndGet();
		}
	}

	private void terminate(final State terminal) {
		this.requireOpen();
		this.state = terminal;
		try {
			this.notifyOwnerState();
		} finally {
			// Even if ownership-state notification fails, retain no ownership of the terminated range or body.
			this.source.releaseRange(this);
			this.releaseBody();
		}
	}

	private void requireOpen() {
		if (this.state != State.OPEN || this.replaying) {
			throw new IllegalStateException("本文範囲の状態違反: [" + this.fromId + ", " + this.toId
					+ "] mode=" + this.replayMode + " state=" + this.state + " replaying=" + this.replaying);
		}
	}
}
