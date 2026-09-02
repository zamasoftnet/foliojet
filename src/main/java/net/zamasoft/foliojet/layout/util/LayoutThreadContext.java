package net.zamasoft.foliojet.layout.util;

import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.rescue.RescuePolicy;

/**
 * レイアウトを別のスレッドで走らせるときに引き継ぐ、スレッド束縛の方針です
 * (2026-09-02)。
 *
 * <p>
 * レイアウトは64MBのスタックを持つ専用スレッドで走る
 * ({@code DirectSession.runOnLargeStack})。さらにEPUBの項目は並列の
 * ワーカーで組む。どちらも<b>呼び出し側スレッドのThreadLocalは引き継がれない</b>
 * ので、外から設定される方針は明示的に運ぶ必要がある。2026-07-26に
 * {@code RescuePolicy}の引き継ぎ漏れでテストが4件落ちて発覚し、以後は
 * 「ThreadLocalを増やしたらここも増やす」が約束だったが、その場所が
 * {@code DirectSession}の1関数に埋まっていた(2026-09-02の設計レビュー)。
 * ここに集めて、スレッドを作る側はこれだけを運ぶ。
 * </p>
 *
 * <p>
 * 引き継がないもの: {@code FlowContainer.tailSealDepth}と
 * {@code ContinuationStats.continuationPathStack}は処理中の深さや経路の
 * 一時記録で、新しいスレッドは空から始めるのが正しい。
 * </p>
 */
public final class LayoutThreadContext {
	/**
	 * レイアウトを走らせるスレッドのスタックの大きさ(64MB)。
	 *
	 * <p>
	 * 深い入れ子の文書で必要になる。実測(2026-07-25、{@code DirectSession}の
	 * 記録): 入れ子1000で約2MB、5000で約10MB。64MBは10倍の余裕で、
	 * 予約されるだけで使わない限りコミットされない。
	 * </p>
	 */
	public static final int LAYOUT_STACK_SIZE = 64 * 1024 * 1024;

	private final RescuePolicy rescuePolicy;
	private final String displayListDir;
	private final boolean detailedDisplayListGeometry;

	private LayoutThreadContext(final RescuePolicy rescuePolicy, final String displayListDir,
			final boolean detailedDisplayListGeometry) {
		this.rescuePolicy = rescuePolicy;
		this.displayListDir = displayListDir;
		this.detailedDisplayListGeometry = detailedDisplayListGeometry;
	}

	/** 現在のスレッドの方針を写し取ります。 */
	public static LayoutThreadContext capture() {
		return new LayoutThreadContext(RescuePolicy.current(), DisplayListDumper.currentDir(),
				DisplayListDumper.currentDetailedGeometry());
	}

	/**
	 * 写し取った方針を現在のスレッドに適用します。閉じると元に戻ります。
	 * 新しいスレッドの先頭で{@code try (var scope = context.apply())}の形で使う。
	 */
	public AutoCloseable apply() {
		final AutoCloseable policy = this.rescuePolicy.scoped();
		final AutoCloseable dump = DisplayListDumper.scopedDir(this.displayListDir);
		final AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(this.detailedDisplayListGeometry);
		return () -> {
			try (geometry; dump; policy) {
				// 逆順に閉じる
			}
		};
	}
}
