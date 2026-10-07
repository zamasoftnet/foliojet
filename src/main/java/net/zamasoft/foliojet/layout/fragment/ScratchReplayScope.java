package net.zamasoft.foliojet.layout.fragment;

/** Attaches a scratch owner and its accounting to the same thread only for the duration of the call. */
public final class ScratchReplayScope implements AutoCloseable {
	private static final ThreadLocal<ScratchReplayScope> CURRENT = new ThreadLocal<>();
	private final ScratchReplayScope previous;
	private final ScratchOwner owner;
	private final boolean releaseOwner;
	private final ReplayIntent.Scope intent;
	private final net.zamasoft.foliojet.layout.RetainedTextLimit.MeasurementAttachment accounting;
	private boolean closed;

	/** Legacy one-shot measurement. Also releases the fresh owner on close. */
	public ScratchReplayScope() {
		this(new ScratchOwner(), true);
	}

	/**
	 * close only restores the attachment. Explicitly release the owner.
	 * Reentry and temporary measurement accounting follow the contract of {@link ScratchOwner#attach()} .
	 */
	public ScratchReplayScope(final ScratchOwner owner) {
		this(owner, false);
	}

	private ScratchReplayScope(final ScratchOwner owner, final boolean releaseOwner) {
		owner.requireOpen();
		this.previous = CURRENT.get();
		this.owner = owner;
		this.releaseOwner = releaseOwner;
		this.accounting = owner.account() == null || this.previous != null && this.previous.owner == owner
				? null : owner.account().attach();
		this.intent = ReplayIntent.MEASURE.enter();
		CURRENT.set(this);
	}

	/** Scratch documents retain their creation-time owner and reclaim its resources on early discard. */
	public static ScratchOwner currentOwner() {
		final ScratchReplayScope scope = CURRENT.get();
		return scope == null ? null : scope.owner;
	}

	static void register(final RangeHandle handle) {
		final ScratchOwner owner = currentOwner();
		if (owner != null) owner.register(handle);
	}

	static void register(final LayoutSource.RetentionLease lease) {
		final ScratchOwner owner = currentOwner();
		if (owner != null) owner.register(lease);
	}

	/** Also reclaims unfinished builder scopes while they remain tied to their accounting. */
	public static void register(final net.zamasoft.foliojet.layout.RetainedTextLimit.Scope scope) {
		final ScratchOwner owner = currentOwner();
		if (owner != null) owner.register(scope);
	}

	@Override
	public void close() {
		if (this.closed || CURRENT.get() != this) {
			throw new IllegalStateException("scratchスコープは取得と逆順に一度だけ閉じます");
		}
		this.closed = true;
		Throwable failure = null;
		try {
			if (this.releaseOwner) this.owner.release();
		} catch (final RuntimeException | Error e) {
			failure = ScratchOwner.accumulate(failure, e);
		}
		if (this.previous == null) CURRENT.remove();
		else CURRENT.set(this.previous);
		try {
			this.intent.close();
		} catch (final RuntimeException | Error e) {
			failure = ScratchOwner.accumulate(failure, e);
		}
		try {
			if (this.accounting != null) this.accounting.close();
		} catch (final RuntimeException | Error e) {
			failure = ScratchOwner.accumulate(failure, e);
		}
		ScratchOwner.rethrow(failure);
	}
}
