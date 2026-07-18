package net.zamasoft.foliojet.layout.fragment;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 継続(改ページ運搬)の種別カウンタです(P4: OpenTailShape 縮小の
 * 定量基盤。TableBuildStats と同じ発火カウンタの流儀)。
 * テストからの観測用で、機能には影響しない。
 */
public final class ContinuationStats {
	/** チェーン子フレーム(Child)での消費。 */
	public static final AtomicLong CHILD_FRAMES = new AtomicLong();

	/** チェーン末端の OpenTailShape 消費(prefix 吸収済み)。 */
	public static final AtomicLong OPEN_TAILS = new AtomicLong();

	/** 収集不能な破断(チェーンなし)の全ボックス restyle。 */
	public static final AtomicLong UNCHAINED_RESTYLES = new AtomicLong();

	/**
	 * open 段落の handoff(M3b Phase 1 のスライス運搬経由)。
	 * Phase 2/3 で TextTail 型付き item へ移行する対象の実測。
	 */
	public static final AtomicLong OPEN_TEXT_HANDOFFS = new AtomicLong();

	/**
	 * OpenTailShape の深さの最大値(Phase 3 の除去範囲の実測。
	 * 0 = 開きボックスなし、1 = 開きテキストのみ、2+ = moved-open 入れ子)。
	 */
	public static final AtomicLong MAX_OPEN_TAIL_DEPTH = new AtomicLong();

	private ContinuationStats() {
		// counters
	}

	public static void reset() {
		CHILD_FRAMES.set(0);
		OPEN_TAILS.set(0);
		UNCHAINED_RESTYLES.set(0);
		OPEN_TEXT_HANDOFFS.set(0);
		MAX_OPEN_TAIL_DEPTH.set(0);
	}
}
