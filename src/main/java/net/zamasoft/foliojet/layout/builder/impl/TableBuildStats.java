package net.zamasoft.foliojet.layout.builder.impl;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 表構築の特性計測です(P2-1。§5.2b 表ビルダー統一の保存契約)。
 *
 * <p>
 * OnePass/TwoPass の置換にあたり、置換前後で保存すべき特性
 * (ルーティング・ストリーミングの保持上限・rowspan 切断・断片数)を
 * 観測可能にします。SegmentReplayCoverageTest と同じ「空虚な緑の防止」:
 * golden 一致だけでは「fixed が全体保持に退化した」ことを検出できない
 * ため、特性テストがこれらのカウンタで経路と有界性を固定します。
 * </p>
 */
public final class TableBuildStats {
	/** OnePass(fixed ストリーミング)構築の発火数。 */
	public static final AtomicLong ONE_PASS_BUILDS = new AtomicLong();

	/** TwoPass(実測後 bind)構築の発火数。 */
	public static final AtomicLong TWO_PASS_BUILDS = new AtomicLong();

	/**
	 * OnePass が同時に保持した行単位バッファの最大数(high-water)。
	 * fixed のストリーミング(bounded-memory)が全体保持に退化していない
	 * ことの証拠。未閉鎖 rowspan の周辺だけが保持される。
	 */
	public static final AtomicLong ONE_PASS_ROW_HIGH_WATER = new AtomicLong();

	/** rowspan 連結セルの切断(cutRowspanCells)の発火数。 */
	public static final AtomicLong ROWSPAN_CUTS = new AtomicLong();

	/** 表断片(splitTableBox)の生成数。 */
	public static final AtomicLong TABLE_FRAGMENTS = new AtomicLong();

	private TableBuildStats() {
		// stats
	}

	/**
	 * OnePass の行保持数を報告します(最大値を保持)。
	 */
	public static void reportRowRetention(final int retained) {
		ONE_PASS_ROW_HIGH_WATER.accumulateAndGet(retained, Math::max);
	}
}
