package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;

/** 一時計測中に新規取得したリースだけを、例外時も含めて破棄します。 */
public final class ScratchReplayScope implements AutoCloseable {
	private static final ThreadLocal<ScratchReplayScope> CURRENT = new ThreadLocal<>();
	private final ScratchReplayScope previous;
	private final ReplayIntent.Scope intent;
	private final List<RangeHandle> handles = new ArrayList<>();
	private final List<LayoutSource.RetentionLease> leases = new ArrayList<>();
	private boolean closed;

	public ScratchReplayScope() {
		this.previous = CURRENT.get();
		this.intent = ReplayIntent.MEASURE.enter();
		CURRENT.set(this);
	}

	static void register(final RangeHandle handle) {
		final ScratchReplayScope scope = CURRENT.get();
		if (scope != null) {
			scope.handles.add(handle);
		}
	}

	static void register(final LayoutSource.RetentionLease lease) {
		final ScratchReplayScope scope = CURRENT.get();
		if (scope != null) {
			scope.leases.add(lease);
		}
	}

	@Override
	public void close() {
		if (this.closed || CURRENT.get() != this) {
			throw new IllegalStateException("scratchスコープは取得と逆順に一度だけ閉じます");
		}
		this.closed = true;
		Throwable failure = null;
		try {
			for (final RangeHandle handle : this.handles) {
				try {
					if (handle.state() == RangeHandle.State.OPEN) {
						handle.abandon();
					}
				} catch (final RuntimeException | Error e) {
					failure = accumulate(failure, e);
				}
			}
		} finally {
			try {
				// capture等のハンドル以外の一時リースも回収する。closeは冪等。
				for (final LayoutSource.RetentionLease lease : this.leases) {
					try {
						lease.close();
					} catch (final RuntimeException | Error e) {
						failure = accumulate(failure, e);
					}
				}
			} finally {
				if (this.previous == null) {
					CURRENT.remove();
				} else {
					CURRENT.set(this.previous);
				}
				this.intent.close();
			}
		}
		if (failure instanceof Error error) {
			throw error;
		}
		if (failure instanceof RuntimeException exception) {
			throw exception;
		}
	}

	private static Throwable accumulate(final Throwable failure, final Throwable next) {
		if (failure == null) {
			return next;
		}
		failure.addSuppressed(next);
		return failure;
	}
}
