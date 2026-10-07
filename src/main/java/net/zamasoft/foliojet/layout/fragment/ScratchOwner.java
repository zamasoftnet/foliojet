package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

/** Retains resources of a scratch driven intermittently on the same thread, even outside attachments. */
public final class ScratchOwner implements AutoCloseable {
	private final List<RangeHandle> handles = new ArrayList<>();
	private final List<LayoutSource.RetentionLease> leases = new ArrayList<>();
	private final List<RetainedTextLimit.Scope> scopes = new ArrayList<>();
	private final RetainedTextLimit.MeasurementAccount account;
	private LayoutSource.RetentionLease pin;
	private boolean released;

	/** For existing one-shot measurements, the caller owns the accounting. */
	public ScratchOwner() {
		this.account = null;
	}

	/** A long-lived scratch also owns independent accounting with the same lifetime as its resources. */
	public ScratchOwner(final RetainedTextLimit limit, final String elementName) {
		this.account = limit.measurementAccount(elementName);
	}

	/**
	 * Direct reentry into the same owner does not switch accounting.
	 * A→B→A with independent accounting throws IllegalStateException at the third level because A is already
	 * attached. A→legacy measurement()→A inherits the owner's attachment and keeps temporary measurement
	 * accounting.
	 * A failed attachment changes neither the current owner, intent, nor accounting.
	 */
	public ScratchReplayScope attach() {
		return new ScratchReplayScope(this);
	}

	RetainedTextLimit.MeasurementAccount account() {
		return this.account;
	}

	void requireOpen() {
		if (this.released) throw new IllegalStateException("解放済みscratch所有者");
	}

	void register(final RangeHandle handle) {
		this.requireOpen();
		this.handles.add(handle);
	}

	void register(final LayoutSource.RetentionLease lease) {
		this.requireOpen();
		this.leases.add(lease);
	}

	void register(final RetainedTextLimit.Scope scope) {
		this.requireOpen();
		this.scopes.add(scope);
	}

	/**
	 * Protects the main log from the start ID of an unfinished TwoPass host.
	 * Can be acquired while detached; even while another owner is attached, does not register the lease with
	 * it.
	 */
	public void retainFrom(final LayoutSource source, final long fromId) {
		this.requireOpen();
		if (this.pin != null && fromId <= this.pin.fromId()) return;
		final LayoutSource.RetentionLease next = source.retainFrom(fromId, false);
		if (this.pin != null) this.pin.close();
		this.pin = next;
	}

	/**
	 * A safe point after delivery/replay returns. Discards only bodies whose hosts have notified final
	 * bind/close.
	 * IDs and page watermarks do not prove host lifetime. Unbound table cells and captions protect the main log
	 * with their own leases; MAIN/other-scratch bodies borrowed through MEASURE are excluded from notification.
	 */
	public void reclaimCompleted() {
		this.requireOpen();
		for (final RangeHandle handle : this.handles) {
			if (handle.state() == RangeHandle.State.OPEN && !handle.isReplaying() && handle.isScratchComplete()) {
				handle.abandon();
			}
		}
		this.handles.removeIf(handle -> handle.state() != RangeHandle.State.OPEN);
		this.leases.removeIf(LayoutSource.RetentionLease::isClosed);
		this.scopes.removeIf(RetainedTextLimit.Scope::isClosed);
	}

	public long retainedFrom() {
		return this.pin == null ? -1 : this.pin.fromId();
	}

	/** The lower bound of sealed bodies still waiting for host bind/close. */
	public long oldestOpenSourceId(final LayoutSource source) {
		long oldest = Long.MAX_VALUE;
		for (final RangeHandle handle : this.handles) {
			if (handle.source() == source && handle.state() == RangeHandle.State.OPEN) {
				oldest = Math.min(oldest, handle.fromId());
			}
		}
		return oldest;
	}

	public int registeredResourceCount() {
		return this.handles.size() + this.scopes.size() + this.retainedLeaseCount();
	}

	public long finishPage() {
		return this.account == null ? 0 : this.account.finishPage();
	}

	public long currentBytes() {
		return this.account == null ? 0 : this.account.currentBytes();
	}

	/** Number of registered leases remaining after reclamation, including the main log's moving pin. */
	public int retainedLeaseCount() {
		return this.leases.size() + (this.pin == null ? 0 : 1);
	}

	/** Called after input is cut off. Reclaims all resources without restoring the attachment. Idempotent. */
	public void release() {
		if (this.released) return;
		this.released = true;
		Throwable failure = null;
		for (final RangeHandle handle : this.handles) {
			try {
				if (handle.state() == RangeHandle.State.OPEN) handle.abandon();
			} catch (final RuntimeException | Error e) {
				failure = accumulate(failure, e);
			}
		}
		this.handles.clear();
		// Also reclaims leases outside handles, such as capture leases. close is idempotent.
		for (final LayoutSource.RetentionLease lease : this.leases) {
			try {
				lease.close();
			} catch (final RuntimeException | Error e) {
				failure = accumulate(failure, e);
			}
		}
		this.leases.clear();
		if (this.pin != null) {
			try {
				this.pin.close();
			} catch (final RuntimeException | Error e) {
				failure = accumulate(failure, e);
			} finally {
				this.pin = null;
			}
		}
		for (int i = this.scopes.size() - 1; i >= 0; --i) {
			try {
				this.scopes.get(i).close();
			} catch (final RuntimeException | Error e) {
				failure = accumulate(failure, e);
			}
		}
		this.scopes.clear();
		try {
			if (this.account != null) this.account.release();
		} catch (final RuntimeException | Error e) {
			failure = accumulate(failure, e);
		}
		rethrow(failure);
	}

	@Override
	public void close() {
		this.release();
	}

	static Throwable accumulate(final Throwable failure, final Throwable next) {
		if (failure == null) return next;
		if (failure != next) failure.addSuppressed(next);
		return failure;
	}

	static void rethrow(final Throwable failure) {
		if (failure instanceof Error error) throw error;
		if (failure instanceof RuntimeException exception) throw exception;
	}
}
