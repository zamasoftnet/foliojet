package net.zamasoft.foliojet.layout.rescue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 救済分割(visual rescue split)の観測カウンタです(2026-07-25新設、増分4。
 * {@code ContinuationStats}と同じ「テストからの観測用・機能には影響しない」
 * 流儀)。
 *
 * <p>
 * 増分8(2026-07-25)で、増分4/5のコーパス実測用だったもの——理由別内訳・
 * ボックス種別内訳・{@code report()}——を撤去し、<b>恒常的な安全網として
 * 読み手がいる3つ</b>だけを残しました。残す基準は「本番で恒常的に価値が
 * あるか」で、その価値の中身は3つとも<b>検出器</b>です。
 * </p>
 *
 * <ul>
 * <li>{@link #CANDIDATES}——<b>非侵襲性の検出器</b>。通常文書では非進行点に
 * 一度も到達しないこと({@code == 0})を
 * {@code VisualRescueSplitTest.testNormalDocumentNeverReachesTheRescuePoint}
 * が固定します。ここが0でなくなったら、救済の判定が通常経路へ漏れています。
 * </li>
 * <li>{@link #SLICES}——判定器が「切れる」と答えた回数。
 * {@link #CANDIDATES}との差が「非進行点に来たが救済しなかった」件数
 * (絶対配置・極小断片の下限など)です。</li>
 * <li>{@link #ENABLED_SLICES}——<b>fixture空振りの検出器</b>。実際に断片を
 * 作った回数で、{@code EnduranceTest}の耐久fixtureがレイアウト変更で黙って
 * 通常経路へ落ちていないことを固定します(0なら耐久試験は何も試して
 * いない)。</li>
 * </ul>
 */
public final class RescueStats {

	private RescueStats() {
		// unused
	}

	/**
	 * 「フラグメント先頭・分割不能・なお超過」という非進行点に到達した
	 * 回数です。
	 */
	public static final AtomicLong CANDIDATES = new AtomicLong();

	/** 判定が{@link RescueDecision.Slice}を返した回数です。 */
	public static final AtomicLong SLICES = new AtomicLong();

	/** 実際に断片(head/tail)を作った回数です。 */
	public static final AtomicLong ENABLED_SLICES = new AtomicLong();

	/**
	 * 非進行点での判定結果を記録します(観測のみ。挙動には一切影響
	 * しません)。
	 *
	 * @param decision 判定結果
	 * @return {@code decision}をそのまま返します(呼び出し側の記述を短くする)
	 */
	public static RescueDecision record(final RescueDecision decision) {
		CANDIDATES.incrementAndGet();
		if (decision instanceof RescueDecision.Slice) {
			SLICES.incrementAndGet();
		}
		return decision;
	}

	private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(RescueStats.class.getName());
	private static final java.util.concurrent.atomic.AtomicBoolean WARNED = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * 実際に断片を作ったことを記録します。
	 *
	 * <p>
	 * 救済分割は意図した fail-open だが、以前は誰にも知らせなかった
	 * (設計レビュー 2026-09-02 §1-6)。プロセスで1回だけ WARNING を残す——
	 * 掃過で大量に出さないため。
	 * </p>
	 */
	public static void recordEnabled() {
		ENABLED_SLICES.incrementAndGet();
		if (WARNED.compareAndSet(false, true)) {
			LOG.warning("rescue slicing was used to make progress at a non-advancing break;"
					+ " the layout continues with a fallback fragment (reported once per process)");
		}
	}

	public static void reset() {
		CANDIDATES.set(0);
		SLICES.set(0);
		ENABLED_SLICES.set(0);
	}
}
