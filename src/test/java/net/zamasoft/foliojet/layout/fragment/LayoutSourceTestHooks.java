package net.zamasoft.foliojet.layout.fragment;

import java.util.function.Consumer;

/**
 * テストから{@link LayoutSource}のpackage-private観測フックへ触るための
 * ブリッジです(E-6増分2、2026-07-24新設、テスト専用)。
 *
 * <p>
 * production経路はフックがnullのままなので挙動不変。shadow round-trip
 * テスト({@code net.zamasoft.foliojet.layout.segment}側)が
 * transcode中の全append列を外から観測するために使う。
 * </p>
 */
public final class LayoutSourceTestHooks {
	private LayoutSourceTestHooks() {
	}

	/**
	 * append観測フックを設定します({@code null}で解除)。設定した
	 * テストはfinallyで必ず解除すること(static共有のため)。
	 */
	public static void setAppendObserver(final Consumer<LayoutSource.Event> observer) {
		LayoutSource.appendObserver = observer;
	}
}
