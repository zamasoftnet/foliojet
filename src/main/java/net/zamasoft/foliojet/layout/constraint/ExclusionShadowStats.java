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
	public static final AtomicLong MULTICOL_SESSIONS = new AtomicLong();
	public static final AtomicLong MULTICOL_MATCHES = new AtomicLong();
	public static final AtomicLong MULTICOL_MISMATCHES = new AtomicLong();

	/** {@code BlockBuilder.startFlowBlock}のmulticol回避1回分のshadow比較結果を記録します。 */
	public static void recordMulticol(final boolean matched) {
		MULTICOL_SESSIONS.incrementAndGet();
		(matched ? MULTICOL_MATCHES : MULTICOL_MISMATCHES).incrementAndGet();
	}

	public static void reset() {
		MULTICOL_SESSIONS.set(0);
		MULTICOL_MATCHES.set(0);
		MULTICOL_MISMATCHES.set(0);
	}

	private ExclusionShadowStats() {
		// counters
	}
}
