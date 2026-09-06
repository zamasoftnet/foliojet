package net.zamasoft.foliojet.layout.fragment;

import java.util.Objects;

import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/** seal済み本文の範囲・寸法と、そのリースまたは文字sliceの単一所有者です。 */
public final class RangeHandle {
	/** fromId/toIdはモードに応じてseal時に切り出した実際の再生閉区間です。 */
	public enum ReplayMode {
		/** 既存の根(通常のTwoPass・takeover項目)の子だけ: [anchor+1, end-1]。 */
		CHILDREN_ONLY,
		/** 中立wrapper内へauthored rootも再構築: [anchor, end]。置換要素は[anchor, anchor]。 */
		ROOTED_SUBTREE,
		/** 匿名項目の合成Start/Endを除く子: [anchor+1, end-1]。 */
		ANONYMOUS_CHILDREN
	}

	/** 終端からの再生・再終端は許しません。 */
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
	private java.util.function.Consumer<State> ownerStateObserver;

	/** 試験専用の観測点。通常変換ではnullで、ハンドルを全域に保持しません。 */
	static volatile java.util.function.Consumer<RangeHandle> sealObserver;
	static volatile java.util.function.BiConsumer<RangeHandle, ReplayIntent> replayStartObserver;
	static volatile java.util.function.BiConsumer<RangeHandle, ReplayIntent> replayObserver;

	/** 検証済みの閉区間を保持します。寸法は不変値のスナップショットです。 */
	public RangeHandle(final LayoutSource source, final long fromId, final long toId,
			final IntrinsicSizes sizes, final ReplayMode replayMode) {
		this(source, fromId, toId, sizes, replayMode, false);
	}

	/** sliceTextは親範囲に吸収されないRetained表のセルだけが指定する。 */
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
	public boolean hasTextSlice() { return this.textSlice != null; }

	/** 宿主のownership ledgerへ終端を通知する。detach時はnullで関連を切る。 */
	public void observeOwnerState(final java.util.function.Consumer<State> observer) {
		this.ownerStateObserver = observer;
		if (observer != null && this.state != State.OPEN) {
			this.notifyOwnerState();
		}
	}

	private void notifyOwnerState() {
		final var observer = this.ownerStateObserver;
		this.ownerStateObserver = null; // 終端後のハンドルから宿主を保持しない。
		if (observer != null) {
			observer.accept(this.state);
		}
	}

	/** 表セル専用の収支も同じ終端で計上するための印です。 */
	public void markCell() {
		this.requireOpen();
		if (this.cell) {
			throw new IllegalStateException("表セルとして計上済みです");
		}
		this.cell = true;
		ContinuationStats.recordCellRangeSeal();
	}

	/** 本配置。成功・失敗を問わずCONSUMEDになり、リースを一度だけ閉じます。 */
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

	/** 一時計測。元のハンドルとリースはOPENのまま残します。 */
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

	/** 親のリース取得後、親範囲の再生へ所有を移します。 */
	public void subsume() {
		if (this.textSlice != null) throw new IllegalStateException("吸収対象外のセルsliceを親へ移せません");
		this.terminate(State.SUBSUMED);
		ContinuationStats.recordTwoPassSealSubsumed();
		if (this.cell) {
			ContinuationStats.recordCellRangeSealSubsumed();
		}
	}

	/** 一時ビルダー等、再生しない本文を破棄します。 */
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
		this.notifyOwnerState();
		this.source.releaseRange(this);
		this.releaseBody();
	}

	private void requireOpen() {
		if (this.state != State.OPEN || this.replaying) {
			throw new IllegalStateException("本文範囲の状態違反: [" + this.fromId + ", " + this.toId
					+ "] mode=" + this.replayMode + " state=" + this.state + " replaying=" + this.replaying);
		}
	}
}
