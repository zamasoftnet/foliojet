package net.zamasoft.foliojet.layout.rescue;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.BoxType;

/**
 * 救済分割(visual rescue split)の発火カウンタです(2026-07-25新設、増分4。
 * {@code ContinuationStats}と同じ「テストからの観測用・機能には影響しない」
 * 流儀)。
 *
 * <p>
 * 増分4では判定器を実際の判定地点へ配線しますが、<b>結果には従わず</b>
 * ここへ記録するだけです。これにより
 * </p>
 *
 * <ul>
 * <li>配線ミスがない(既存goldenが完全に不変)ことの証拠</li>
 * <li>コーパスで救済が何回発動しうるかの実測(増分5のfixture要否)</li>
 * <li>増分5で有効化する範囲(通常フローの置換要素だけ)の切り分け</li>
 * </ul>
 *
 * <p>
 * を同時に得ます。
 * </p>
 */
public final class RescueStats {

	private RescueStats() {
		// unused
	}

	/**
	 * 「フラグメント先頭・分割不能・なお超過」という非進行点に到達した
	 * 回数です({@link #SLICES}と理由別{@link #reasons(RescueDecision.Reason)}
	 * の合計に一致します)。
	 */
	public static final AtomicLong CANDIDATES = new AtomicLong();

	/** 判定が{@link RescueDecision.Slice}を返した回数です。 */
	public static final AtomicLong SLICES = new AtomicLong();

	/**
	 * {@link RescueDecision.Slice}のうち、増分5で実際に救済する対象
	 * (通常フローの置換要素、および救済断片の続き)であった回数です。
	 * 巨大行・ブロック・表・段組は増分6以降なのでここには入りません。
	 */
	public static final AtomicLong ENABLED_SLICES = new AtomicLong();

	private static final Map<RescueDecision.Reason, AtomicLong> REASONS = new EnumMap<>(RescueDecision.Reason.class);
	static {
		for (final RescueDecision.Reason r : RescueDecision.Reason.values()) {
			REASONS.put(r, new AtomicLong());
		}
	}

	private static final Map<BoxType, AtomicLong> CANDIDATE_BOX_TYPES = new EnumMap<>(BoxType.class);
	static {
		for (final BoxType t : BoxType.values()) {
			CANDIDATE_BOX_TYPES.put(t, new AtomicLong());
		}
	}

	/**
	 * 非進行点での判定結果を記録します(増分4の影検証。挙動には一切
	 * 影響しません)。
	 *
	 * @param boxType  非進行点に到達したボックスの種類
	 * @param decision 判定結果
	 * @return {@code decision}をそのまま返します(呼び出し側の記述を短くする)
	 */
	public static RescueDecision record(final BoxType boxType, final RescueDecision decision) {
		CANDIDATES.incrementAndGet();
		CANDIDATE_BOX_TYPES.get(boxType).incrementAndGet();
		switch (decision) {
		case RescueDecision.Slice slice -> SLICES.incrementAndGet();
		case RescueDecision.None none -> REASONS.get(none.reason()).incrementAndGet();
		}
		return decision;
	}

	/** 実際に救済した(増分5で有効化した範囲の)断片を記録します。 */
	public static void recordEnabled() {
		ENABLED_SLICES.incrementAndGet();
	}

	/** {@code reason}で救済しなかった回数です。 */
	public static long reasons(final RescueDecision.Reason reason) {
		return REASONS.get(reason).get();
	}

	/** {@code boxType}が非進行点に到達した回数です。 */
	public static long candidateBoxTypes(final BoxType boxType) {
		return CANDIDATE_BOX_TYPES.get(boxType).get();
	}

	/** 人間が読める内訳です(コーパス実測レポート用)。 */
	public static String report() {
		final StringBuilder s = new StringBuilder();
		s.append("[rescue split shadow]\n");
		s.append("  CANDIDATES=").append(CANDIDATES.get()).append('\n');
		s.append("  SLICES=").append(SLICES.get()).append('\n');
		s.append("  ENABLED_SLICES=").append(ENABLED_SLICES.get()).append('\n');
		for (final BoxType t : BoxType.values()) {
			final long count = candidateBoxTypes(t);
			if (count != 0) {
				s.append("  CANDIDATE_").append(t).append('=').append(count).append('\n');
			}
		}
		for (final RescueDecision.Reason r : RescueDecision.Reason.values()) {
			final long count = reasons(r);
			if (count != 0) {
				s.append("  NONE_").append(r).append('=').append(count).append('\n');
			}
		}
		return s.toString();
	}

	public static void reset() {
		CANDIDATES.set(0);
		SLICES.set(0);
		ENABLED_SLICES.set(0);
		for (final AtomicLong counter : REASONS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : CANDIDATE_BOX_TYPES.values()) {
			counter.set(0);
		}
	}
}
