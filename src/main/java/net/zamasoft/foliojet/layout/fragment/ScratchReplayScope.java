package net.zamasoft.foliojet.layout.fragment;

/** scratch所有者とその会計を、呼び出し中だけ同じスレッドへ接続します。 */
public final class ScratchReplayScope implements AutoCloseable {
	private static final ThreadLocal<ScratchReplayScope> CURRENT = new ThreadLocal<>();
	private final ScratchReplayScope previous;
	private final ScratchOwner owner;
	private final boolean releaseOwner;
	private final ReplayIntent.Scope intent;
	private final net.zamasoft.foliojet.layout.RetainedTextLimit.MeasurementAttachment accounting;
	private boolean closed;

	/** 従来の一回限りの計測。close時に新品の所有者も解放します。 */
	public ScratchReplayScope() {
		this(new ScratchOwner(), true);
	}

	/**
	 * closeは接続だけを戻します。所有者は明示的にreleaseしてください。
	 * 再入と一時計測の会計は{@link ScratchOwner#attach()}の契約に従います。
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

	/** scratch文書は生成時の所有者を保持し、途中破棄でその資源を清算します。 */
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

	/** ビルダーの未完スコープも、その会計へ結び付いたまま回収します。 */
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
