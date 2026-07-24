package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
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

	// ---- E-6増分1(2026-07-24): spillableテープ基盤の観測カウンタ群 ----
	// spill閾値・対象選定の実測基盤。読み取り・max更新のみで挙動には
	// 影響しない(docs/consultations/consult-e6-spillable-tape-codex.md §3-1)。

	/**
	 * Retained(表全体保持)1表あたりの行数(header+body+footer)のhigh-water。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong RETAINED_ROW_HIGH_WATER = new AtomicLong();

	/**
	 * Retained 1表あたりの実セル数(rowspan/colspan継続の補完slotを除く、
	 * TwoPassBlockBuilderを持つCellContent)のhigh-water。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong RETAINED_CELL_HIGH_WATER = new AtomicLong();

	/**
	 * Retained 1表あたりの論理セルslot数(行数×列数)のhigh-water。
	 * 行・セルMap、collapsed border配列等のO(R×C)保持の上界に対応する。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong RETAINED_CELL_SLOT_HIGH_WATER = new AtomicLong();

	/**
	 * Retained 1表あたりの反復ヘッダ(thead)行数のhigh-water。spillable化後も
	 * ヒープ常駐が必要な部分(ページごとに反復されるためtapeへ流せない)。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong RETAINED_REPEATED_HEADER_ROW_HIGH_WATER = new AtomicLong();

	/**
	 * Retained 1表あたりの反復フッタ(tfoot)行数のhigh-water(同上)。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER = new AtomicLong();

	/**
	 * Retained 1表あたりの、AutoColumnWidthsが保持する異なる(開始列, colspan)
	 * 制約数(Uspan、最悪O(C²))のhigh-water。spillable化後もヒープ常駐となる
	 * 列統計のサイズ見積もりに使う。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER = new AtomicLong();

	/**
	 * Retainedへルーティングされた理由({@link TableRetentionReason})別の
	 * 発生回数。1表が複数理由を持てば各理由へ1ずつ加算されるため、合計は
	 * {@link #TWO_PASS_BUILDS}以上になり得る。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	private static final Map<TableRetentionReason, AtomicLong> RETENTION_REASON_COUNTS = new EnumMap<>(
			TableRetentionReason.class);
	static {
		for (final TableRetentionReason reason : TableRetentionReason.values()) {
			RETENTION_REASON_COUNTS.put(reason, new AtomicLong());
		}
	}

	/**
	 * TwoPassBlockBuilder 1ビルダーあたりのrecords要素数のhigh-water。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong TWO_PASS_RECORD_HIGH_WATER = new AtomicLong();

	/**
	 * TwoPassBlockBuilder 1ビルダーあたりのglyphイベント数合計(recordsに
	 * 保持されるTextImplのglyph総量の概算)のhigh-water。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong TWO_PASS_GLYPH_HIGH_WATER = new AtomicLong();

	/**
	 * TwoPassBlockBuilderのネスト深さ(layoutStack鎖上の連続する
	 * TwoPassBlockBuilder数、自身を含む)のhigh-water。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	public static final AtomicLong TWO_PASS_NEST_DEPTH_HIGH_WATER = new AtomicLong();

	private TableBuildStats() {
		// stats
	}

	/**
	 * OnePass の行保持数を報告します(最大値を保持)。
	 */
	public static void reportRowRetention(final int retained) {
		ONE_PASS_ROW_HIGH_WATER.accumulateAndGet(retained, Math::max);
	}

	/**
	 * Retained表の保持形状を報告します(prepareLayout時点の1表分。
	 * E-6増分1、max更新のみ)。
	 *
	 * @param rows              行数(header+body+footer)
	 * @param cells             実セル数(継続slotを除く)
	 * @param slots             論理セルslot数(行数×列数)
	 * @param headerRows        反復ヘッダ行数
	 * @param footerRows        反復フッタ行数
	 * @param colspanConstraints 異なる(開始列, colspan)制約数(Uspan)
	 */
	public static void reportRetainedTableShape(final int rows, final int cells, final long slots, final int headerRows,
			final int footerRows, final int colspanConstraints) {
		RETAINED_ROW_HIGH_WATER.accumulateAndGet(rows, Math::max);
		RETAINED_CELL_HIGH_WATER.accumulateAndGet(cells, Math::max);
		RETAINED_CELL_SLOT_HIGH_WATER.accumulateAndGet(slots, Math::max);
		RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.accumulateAndGet(headerRows, Math::max);
		RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER.accumulateAndGet(footerRows, Math::max);
		RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.accumulateAndGet(colspanConstraints, Math::max);
	}

	/**
	 * Retainedへのルーティング理由を記録します(E-6増分1。ルーティング
	 * 実行点=TableBuilderLifecycle.startから1表につき1回だけ呼ぶこと——
	 * TableBuildPlanner.planは問い合わせ用途でも呼ばれるためplan内では
	 * 数えない)。
	 */
	public static void recordRetentionReasons(final Set<TableRetentionReason> reasons) {
		for (final TableRetentionReason reason : reasons) {
			RETENTION_REASON_COUNTS.get(reason).incrementAndGet();
		}
	}

	/** {@code reason}によるRetainedルーティングの発生回数(E-6増分1)。 */
	public static long retentionReasonCount(final TableRetentionReason reason) {
		return RETENTION_REASON_COUNTS.get(reason).get();
	}

	/** TwoPassBlockBuilderのrecords保持数を報告します(E-6増分1、最大値を保持)。 */
	public static void reportTwoPassRecordRetention(final int records) {
		TWO_PASS_RECORD_HIGH_WATER.accumulateAndGet(records, Math::max);
	}

	/** TwoPassBlockBuilderのglyphイベント数合計を報告します(E-6増分1、最大値を保持)。 */
	public static void reportTwoPassGlyphRetention(final long glyphs) {
		TWO_PASS_GLYPH_HIGH_WATER.accumulateAndGet(glyphs, Math::max);
	}

	/** TwoPassBlockBuilderのネスト深さを報告します(E-6増分1、最大値を保持)。 */
	public static void reportTwoPassNestDepth(final int depth) {
		TWO_PASS_NEST_DEPTH_HIGH_WATER.accumulateAndGet(depth, Math::max);
	}
}
