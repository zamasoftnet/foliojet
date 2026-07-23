package net.zamasoft.foliojet.layout.fragment;

/**
 * 現在のスレッドのレイアウト実行パスです(2026-07-24新設、排除域P2の
 * M6c-1——`docs/consultations/consult-exclusion-p2-design-codex.txt`の
 * 設計)。
 *
 * <p>
 * M6cバランスプローブ(候補commit方式)は、同じレイアウトコードを
 * 「捨てる可能性のある候補」の構築に再入させる。その間、
 * {@link ContinuationStats}等のproduction診断カウンタ・trace書き込みを
 * 抑制しないと、試行回数だけ診断が水増しされる。グローバルカウンタの
 * 保存・復元は並行変換を壊すため行わず、スレッド局所の実行パスで
 * 書き込み側をゲートする。
 * </p>
 *
 * <p>
 * 既定は{@link LayoutPass#LIVE}(全書き込み有効)。
 * {@link #enterBalanceProbe()}はM6cプローブだけが使う(M6c-1時点では
 * 未配線のため、挙動は完全に不変)。
 * </p>
 */
public final class LayoutExecutionScope {

	/** レイアウト実行パスの種別。 */
	public enum LayoutPass {
		/** 本番のレイアウト(既定)。診断書き込みは全て有効。 */
		LIVE,
		/** M6cバランスプローブの候補構築。production診断は抑制される。 */
		BALANCE_PROBE
	}

	private static final ThreadLocal<LayoutPass> PASS = ThreadLocal.withInitial(() -> LayoutPass.LIVE);

	private LayoutExecutionScope() {
	}

	/** 現在のスレッドの実行パスです。 */
	public static LayoutPass current() {
		return PASS.get();
	}

	/** 本番パス(診断書き込み有効)かどうかです。 */
	public static boolean isLive() {
		return PASS.get() == LayoutPass.LIVE;
	}

	/**
	 * バランスプローブパスへ入ります。返り値のclose()で元のパスへ
	 * 戻る(try-with-resources用)。
	 */
	public static Scope enterBalanceProbe() {
		final LayoutPass previous = PASS.get();
		PASS.set(LayoutPass.BALANCE_PROBE);
		return () -> PASS.set(previous);
	}

	/** {@link #enterBalanceProbe()}の復元ハンドルです。 */
	@FunctionalInterface
	public interface Scope extends AutoCloseable {
		@Override
		void close();
	}
}
