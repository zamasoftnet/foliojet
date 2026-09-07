package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

/** 同じスレッドで断続的に駆動するscratchの資源を、接続の外でも保持します。 */
public final class ScratchOwner implements AutoCloseable {
	private final List<RangeHandle> handles = new ArrayList<>();
	private final List<LayoutSource.RetentionLease> leases = new ArrayList<>();
	private final List<RetainedTextLimit.Scope> scopes = new ArrayList<>();
	private final RetainedTextLimit.MeasurementAccount account;
	private LayoutSource.RetentionLease pin;
	private boolean released;

	/** 既存の一回限りの計測は、呼び側が会計を所有します。 */
	public ScratchOwner() {
		this.account = null;
	}

	/** 長寿命のscratchは、資源と同じ寿命の独立会計も所有します。 */
	public ScratchOwner(final RetainedTextLimit limit, final String elementName) {
		this.account = limit.measurementAccount(elementName);
	}

	/**
	 * 同一所有者への直連の再入は会計を切り替えません。独立会計付きのA→B→Aは
	 * Aが既に接続中なので三段目でIllegalStateExceptionになります。
	 * A→従来型measurement()→Aは所有者の接続を継ぎ、一時計測の会計のままです。
	 * 失敗した接続は現在の所有者・意図・会計を変更しません。
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
	 * 未完TwoPass宿主の開始IDから主ログを保護します。接続中でなくても取得でき、
	 * 他の所有者の接続中でも、その所有者へリースを登録しません。
	 */
	public void retainFrom(final LayoutSource source, final long fromId) {
		this.requireOpen();
		if (this.pin != null && fromId <= this.pin.fromId()) return;
		final LayoutSource.RetentionLease next = source.retainFrom(fromId, false);
		if (this.pin != null) this.pin.close();
		this.pin = next;
	}

	/**
	 * 配達・再生から戻った安全点。宿主が最後のbind/closeを通知した本文だけを破棄する。
	 * IDやページ水位は宿主の寿命を証明しない。未bindの表セル・captionは自身のリースで
	 * 主ログを保護し、MEASUREで借用したMAIN/別scratchの本文は通知の対象外となる。
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

	/** seal済みで、まだ宿主のbind/closeを待っている本文の下限。 */
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

	/** 回収後に残るリース登録数。主ログの移動pinも含みます。 */
	public int retainedLeaseCount() {
		return this.leases.size() + (this.pin == null ? 0 : 1);
	}

	/** 入力を打ち切った後に呼びます。接続は戻さず、全資源を清算します。冪等。 */
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
		// capture等のハンドル以外のリースも回収する。closeは冪等。
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
