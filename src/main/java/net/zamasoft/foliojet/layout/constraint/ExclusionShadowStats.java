package net.zamasoft.foliojet.layout.constraint;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 排除域のConstraintSpace入力化(P0)で、既存の可変状態ベースの走査
 * ({@code BlockBuilder.floatings}等)と{@link ExclusionSpace}による
 * queryの結果を突き合わせる、shadow比較の集計です(2026-07-23新設)。
 *
 * <p>
 * この段階では{@link ExclusionSpace}の結果は実際のレイアウトには
 * 使わない(既存ループの結果だけが実際の挙動を決める)——この集計は
 * 「新旧が一致し続けているか」を実測するための診断専用カウンタ。
 * `docs/consultations/consult-exclusion-zone-codex.txt`のP0
 * Step2-3参照。
 * </p>
 */
public final class ExclusionShadowStats {
	/**
	 * shadow比較の許容誤差です(2026-07-23、multicol回避の実測で判明した
	 * round-trip誤差——`docs/history/2026-07-23-exclusion-space-p0-step3
	 * -multicol.md`参照——を踏まえ、全消費者のshadow比較で共有する)。
	 * 高々1ULP(10^-13台)のround-trip誤差より十分大きく、実際の論理
	 * 不一致(通常10^-2以上)より十分小さい。
	 */
	public static final double EPSILON = 1e-9;

	public static final AtomicLong MULTICOL_SESSIONS = new AtomicLong();
	public static final AtomicLong MULTICOL_MATCHES = new AtomicLong();
	public static final AtomicLong MULTICOL_MISMATCHES = new AtomicLong();

	public static final AtomicLong CLEAR_SESSIONS = new AtomicLong();
	public static final AtomicLong CLEAR_MATCHES = new AtomicLong();
	public static final AtomicLong CLEAR_MISMATCHES = new AtomicLong();

	public static final AtomicLong BOUND_SESSIONS = new AtomicLong();
	public static final AtomicLong BOUND_MATCHES = new AtomicLong();
	public static final AtomicLong BOUND_MISMATCHES = new AtomicLong();

	public static final AtomicLong LINE_SCAN_SESSIONS = new AtomicLong();
	public static final AtomicLong LINE_SCAN_MATCHES = new AtomicLong();
	public static final AtomicLong LINE_SCAN_MISMATCHES = new AtomicLong();

	public static final AtomicLong FLOAT_PLACEMENT_SESSIONS = new AtomicLong();
	public static final AtomicLong FLOAT_PLACEMENT_MATCHES = new AtomicLong();
	public static final AtomicLong FLOAT_PLACEMENT_MISMATCHES = new AtomicLong();

	/** {@code BlockBuilder.startFlowBlock}のmulticol回避1回分のshadow比較結果を記録します。 */
	public static void recordMulticol(final boolean matched) {
		MULTICOL_SESSIONS.incrementAndGet();
		(matched ? MULTICOL_MATCHES : MULTICOL_MISMATCHES).incrementAndGet();
	}

	/** {@code BlockBuilder.startFlowBlock}のclear処理1回分のshadow比較結果を記録します。 */
	public static void recordClear(final boolean matched) {
		CLEAR_SESSIONS.incrementAndGet();
		(matched ? CLEAR_MATCHES : CLEAR_MISMATCHES).incrementAndGet();
	}

	/** {@code BlockBuilder.addBound}の浮動体回避1回分のshadow比較結果を記録します。 */
	public static void recordBound(final boolean matched) {
		BOUND_SESSIONS.incrementAndGet();
		(matched ? BOUND_MATCHES : BOUND_MISMATCHES).incrementAndGet();
	}

	/** {@code TextBuilder.locateLine}の1回分の行帯走査のshadow比較結果を記録します。 */
	public static void recordLineScan(final boolean matched) {
		LINE_SCAN_SESSIONS.incrementAndGet();
		(matched ? LINE_SCAN_MATCHES : LINE_SCAN_MISMATCHES).incrementAndGet();
	}

	/** {@code BlockBuilder.addStartFloat}/{@code addEndFloat}の1回分の配置先探索のshadow比較結果を記録します。 */
	public static void recordFloatPlacement(final boolean matched) {
		FLOAT_PLACEMENT_SESSIONS.incrementAndGet();
		(matched ? FLOAT_PLACEMENT_MATCHES : FLOAT_PLACEMENT_MISMATCHES).incrementAndGet();
	}

	public static void reset() {
		MULTICOL_SESSIONS.set(0);
		MULTICOL_MATCHES.set(0);
		MULTICOL_MISMATCHES.set(0);
		CLEAR_SESSIONS.set(0);
		CLEAR_MATCHES.set(0);
		CLEAR_MISMATCHES.set(0);
		BOUND_SESSIONS.set(0);
		BOUND_MATCHES.set(0);
		BOUND_MISMATCHES.set(0);
		LINE_SCAN_SESSIONS.set(0);
		LINE_SCAN_MATCHES.set(0);
		LINE_SCAN_MISMATCHES.set(0);
		FLOAT_PLACEMENT_SESSIONS.set(0);
		FLOAT_PLACEMENT_MATCHES.set(0);
		FLOAT_PLACEMENT_MISMATCHES.set(0);
	}

	private ExclusionShadowStats() {
		// counters
	}
}
