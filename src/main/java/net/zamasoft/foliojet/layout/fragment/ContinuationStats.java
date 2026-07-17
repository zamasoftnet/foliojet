package net.zamasoft.foliojet.layout.fragment;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 継続(改ページ運搬)の種別カウンタです(P4: LegacyOpenTail 縮小の
 * 定量基盤。TableBuildStats と同じ発火カウンタの流儀)。
 * テストからの観測用で、機能には影響しない。
 */
public final class ContinuationStats {
	/** チェーン子フレーム(Child)での消費。 */
	public static final AtomicLong CHILD_FRAMES = new AtomicLong();

	/** チェーン末端の LegacyOpenTail 消費(prefix 吸収済み)。 */
	public static final AtomicLong LEGACY_TAILS = new AtomicLong();

	/** 収集不能な破断(チェーンなし)の全ボックス restyle。 */
	public static final AtomicLong LEGACY_ROOTS = new AtomicLong();

	private ContinuationStats() {
		// counters
	}

	public static void reset() {
		CHILD_FRAMES.set(0);
		LEGACY_TAILS.set(0);
		LEGACY_ROOTS.set(0);
	}
}
