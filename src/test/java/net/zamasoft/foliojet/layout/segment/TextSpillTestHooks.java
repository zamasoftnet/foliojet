package net.zamasoft.foliojet.layout.segment;

import java.io.IOException;

/**
 * テストから{@link TextSpill}のpackage-private障害注入フックへ触るための
 * ブリッジです(E-6耐久試験、2026-07-24新設、テスト専用。
 * {@code LayoutSourceTestHooks}と同じ流儀)。
 *
 * <p>
 * production経路はフックがnullのままなので挙動不変。設定したテストは
 * finallyで必ず{@link #clearFaultInjector()}すること(static共有のため)。
 * </p>
 */
public final class TextSpillTestHooks {
	private TextSpillTestHooks() {
	}

	/** {@link IOException}を投げうるアクションです(注入する障害の記述)。 */
	@FunctionalInterface
	public interface IOAction {
		void run() throws IOException;
	}

	/**
	 * spill I/O障害の注入を設定します。{@code null}のアクションは
	 * 「その経路には注入しない」を意味します。
	 *
	 * @param beforeAppend spill書き込み直前に実行(nullなら注入なし)
	 * @param beforeRead   spill読み出し直前に実行(nullなら注入なし)
	 */
	public static void setFaultInjector(final IOAction beforeAppend, final IOAction beforeRead) {
		TextSpill.faultInjector = new TextSpill.IOFaultInjector() {
			@Override
			public void beforeAppend() throws IOException {
				if (beforeAppend != null) {
					beforeAppend.run();
				}
			}

			@Override
			public void beforeRead() throws IOException {
				if (beforeRead != null) {
					beforeRead.run();
				}
			}
		};
	}

	/** 障害注入を解除します(冪等)。 */
	public static void clearFaultInjector() {
		TextSpill.faultInjector = null;
	}
}
