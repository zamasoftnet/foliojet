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
	/**
	 * OnePass(Incremental=早期コミット可能)構築の発火数。table-layout:fixed
	 * が主だが、それだけではない——{@link TableRetentionReason}参照
	 * (2026-07-19訂正: 「OnePass=fixed」は不正確)。
	 */
	public static final AtomicLong ONE_PASS_BUILDS = new AtomicLong();

	/**
	 * TwoPass(Retained=表全体を保持してからコミット)構築の発火数。
	 * table-layout:autoが主だが、それだけではない——非FLOW配置・
	 * ページ軸寸法指定・行軸auto寸法・ネスト実測パスでもこちらへ
	 * ルーティングされる(2026-07-19訂正: 「TwoPass=auto」は不正確)。
	 */
	public static final AtomicLong TWO_PASS_BUILDS = new AtomicLong();

	/**
	 * OnePass(Incremental)が同時に保持した行単位バッファの最大数(high-water)。
	 * ストリーミング(bounded-memory)が全体保持に退化していないことの証拠
	 * ——未閉鎖rowspanの周辺だけが保持される。**ただし絶対高さrow-group
	 * (`<tbody style="height:...">`)内では、そのrow-groupが閉じるまで
	 * 全行を保持する既知の限界がある**(2026-07-19発見、
	 * CSS-SUPPORT.md参照。table-layout:auto以外の無限成長経路)。
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
