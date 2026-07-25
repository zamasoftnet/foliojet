package net.zamasoft.foliojet.layout.rescue;

/**
 * 救済分割(visual rescue split)を実際に行うかどうかの切替です
 * (2026-07-25新設、増分4/5。答申§6)。
 *
 * <p>
 * <b>恒久的なUAプロパティ・CSSプロパティは作りません</b>(答申§6の裁定)。
 * 最終的な発動条件が「既存のoverflow終端」だけなので、通常文書に恒久的な
 * 分岐設定を残す利点がないためです。ここにあるのは
 * </p>
 *
 * <ul>
 * <li>増分4(影検証)で「判定はするが従わない」を表すため</li>
 * <li>テストが{@code DISABLED}(従来の挙動)と{@code ENABLED}(救済)を
 * 同一文書で比較できるようにするため</li>
 * </ul>
 *
 * <p>
 * の一時的な足場で、全増分の完了後に撤去します(答申§6-8)。
 * </p>
 *
 * <h2>スレッド</h2>
 *
 * <p>
 * 複数変換の並行実行で混線しないよう{@link ThreadLocal}で保持します
 * ({@code ContinuationStats}の継続経路スタックと同じ理由)。
 * </p>
 */
public enum RescuePolicy {

	/** 救済しない(従来どおり、ページ先頭でもはみ出したまま描画する)。 */
	DISABLED,

	/** 救済する。 */
	ENABLED;

	/**
	 * 既定値です。増分5で通常フローの置換要素に限って有効化しました。
	 */
	public static final RescuePolicy DEFAULT = ENABLED;

	private static final ThreadLocal<RescuePolicy> CURRENT = ThreadLocal.withInitial(() -> DEFAULT);

	/** 現在のスレッドの方針です。 */
	public static RescuePolicy current() {
		return CURRENT.get();
	}

	/** 救済を行う方針であればtrueを返します。 */
	public static boolean isEnabled() {
		return current() == ENABLED;
	}

	/**
	 * このスレッドの方針を一時的に差し替えます(テスト専用)。
	 *
	 * <pre>
	 * try (RescuePolicy.Scope scope = RescuePolicy.DISABLED.scoped()) {
	 * 	// 従来の挙動
	 * }
	 * </pre>
	 *
	 * @return 復帰用のスコープ
	 */
	public Scope scoped() {
		final RescuePolicy previous = CURRENT.get();
		CURRENT.set(this);
		return () -> {
			if (previous == DEFAULT) {
				// ThreadLocalを残さない(プールされたスレッドでのリーク防止)
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		};
	}

	/** {@link RescuePolicy#scoped()}の復帰ハンドルです。 */
	public interface Scope extends AutoCloseable {
		void close();
	}
}
