package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.segment.BarrierReason;

/**
 * 継続(改ページ運搬)の種別カウンタです(P4: OpenTailShape 縮小の
 * 定量基盤。TableBuildStats と同じ発火カウンタの流儀)。
 * テストからの観測用で、機能には影響しない。
 */
public final class ContinuationStats {
	/** チェーン子フレーム(Child)での消費。 */
	public static final AtomicLong CHILD_FRAMES = new AtomicLong();

	/**
	 * {@code ColumnsContainer.splitPageAxis()}が呼ばれた回数(2026-07-21
	 * 新設、M6b Phase B5d-0)。{@link #COLUMNS_LAST_COLUMN_MOVE_CANDIDATE}
	 * の分母。
	 *
	 * <p>
	 * <b>退役条件(2026-07-24 E-5)</b>:
	 * {@link #COLUMNS_LAST_COLUMN_MOVE_CANDIDATE}と同時に退役する
	 * (分母としてのみ意味を持つ——単独では残さない)。
	 * </p>
	 */
	public static final AtomicLong COLUMNS_SPLIT_ATTEMPTS = new AtomicLong();

	/**
	 * {@code ColumnsContainer}が段数2以上を持つ状態で、委譲先の最後列
	 * (`getLastColumn()`)自身の分割結果が「その列の内容が丸ごと
	 * 次フラグメンテナへ移動した」(3引数版: 戻り値が最後列自身と同一/
	 * 4引数版: {@code ContainerCut.Plain}のcontainerが最後列自身と同一)
	 * であった回数(2026-07-21新設、M6b Phase B5d-0)。これは「段組全体の
	 * MOVE」の上位集合(最後列だけがMOVEし前方列はそのまま残る通常
	 * ケースも含む)。挙動には一切影響しない(カウンタ加算のみ)。
	 *
	 * <p>
	 * <b>退役条件(2026-07-24 E-5)</b>: 当初目的(B5d本実装の要否判断)は
	 * 2026-07-22にclose済み(docs/history/2026-07-22-b5d-closed-no
	 * -implementation-needed.md——実測0件+既存の{@code remainder ==
	 * activeColumn}判定で正しく処理されることを確認)。現在の残置理由は
	 * {@code ContainerCut.Plain}のsentinel(null/this)が層ごとに異なる
	 * identity比較で解釈される現行挙動の観測(E-4で明文化、
	 * {@code ContainerCut.Plain}のjavadoc参照)——Plain sentinelの
	 * {@code Keep}/{@code Move}型化(legacy 3引数{@code Container
	 * .splitPageAxis}契約の撤去と同時に行う)が完了したら、
	 * {@link #COLUMNS_SPLIT_ATTEMPTS}および参照assert
	 * ({@code ResumeTraceGoldenTest})ごと退役してよい。
	 * </p>
	 */
	public static final AtomicLong COLUMNS_LAST_COLUMN_MOVE_CANDIDATE = new AtomicLong();

	/**
	 * 直近の改段(COLUMN)で選択されたowner(改段対象box)の設定段数
	 * (CSS {@code column-count}相当)を記録します(2026-07-21新設、
	 * nested multicol owner選択のテスト観測用)。{@code
	 * BreakableBuilder.findColumnBreak()}が最内側の{@code
	 * canColumnBreak()}なownerを選ぶ既存挙動は変更していない——単に
	 * 「実際にどのownerが選ばれたか」をテストから観測できるようにする
	 * だけの計測。
	 */
	public static final java.util.concurrent.atomic.AtomicInteger LAST_COLUMN_OWNER_COLUMN_COUNT = new java.util.concurrent.atomic.AtomicInteger(
			-1);

	/** チェーン末端の OpenTailShape 消費(prefix 吸収済み)。 */
	public static final AtomicLong OPEN_TAILS = new AtomicLong();

	/** 収集不能な破断(チェーンなし)の全ボックス restyle。 */
	public static final AtomicLong UNCHAINED_RESTYLES = new AtomicLong();

	/**
	 * open 段落の handoff(M3b Phase 1 のスライス運搬経由)。
	 * Phase 2/3 で TextTail 型付き item へ移行する対象の実測。
	 */
	public static final AtomicLong OPEN_TEXT_HANDOFFS = new AtomicLong();

	/**
	 * PAGE(RootBuilder.pageBreak経由)のOpenTailShape深さの最大値
	 * (0 = 開きボックスなし、1 = 開きテキストのみ、2+ = moved-open
	 * 入れ子)。旧{@code MAX_OPEN_TAIL_DEPTH}はPAGE/COLUMNを混同していた
	 * ため2026-07-21にCOLUMN側と分離した(ChatGPT Pro相談で判明:
	 * {@code BreakableBuilder.columnBreak()}はPAGE側の深さガードを一切
	 * 通らない別経路のため、両者を混同すると片方の異常が見えなくなる)。
	 */
	public static final AtomicLong MAX_PAGE_OPEN_TAIL_DEPTH = new AtomicLong();

	/**
	 * COLUMN(BreakableBuilder.columnBreak経由、段組内の改段)の
	 * OpenTailShape深さの最大値(2026-07-21新設)。
	 */
	public static final AtomicLong MAX_COLUMN_OPEN_TAIL_DEPTH = new AtomicLong();

	/**
	 * 開いたままの祖先チェーン(moved-open)を{@code FlowContainer.restyle}が
	 * ボックス再生(restyle-chain)で1段降りるたびに1加算します(2026-07-20、
	 * M6b Phase B「切断ブロックチェーン」ソース再生化の可視化基盤=B0)。
	 * ソース再生化が進むほどこの値は0に近づくべき値で、着手前の現状把握と、
	 * 各段階での box-restyle 依存の縮小を実測するための発火カウンタです
	 * (docs/consultations/consult-open-chain-replay-*.md参照)。
	 */
	public static final AtomicLong RESTYLE_CHAIN_FIRINGS = new AtomicLong();

	/** PAGE経路での{@link #RESTYLE_CHAIN_FIRINGS}(2026-07-21新設、B1)。 */
	public static final AtomicLong PAGE_RESTYLE_CHAIN_FIRINGS = new AtomicLong();

	/** COLUMN経路での{@link #RESTYLE_CHAIN_FIRINGS}(2026-07-21新設、B1)。 */
	public static final AtomicLong COLUMN_RESTYLE_CHAIN_FIRINGS = new AtomicLong();

	/**
	 * worklist executorの{@code descendWorklist}が、OpenChain降下先の
	 * box/container組み合わせをframe/scopeとして表現できず、多態的な
	 * {@code containerBox.restyle(builder, inner)}互換フォールバックへ
	 * 落ちた回数です(2026-07-30新設、増分0。増分4fで
	 * 旧{@code WORKLIST_RECURSIVE_FALLBACKS}から改名——旧driver撤去後は
	 * 「再帰driverへの退避」ではなく「未知型の互換経路」の意味)。
	 * 既知の全型(FlowBlockBox配下のFlowContainer/ColumnsContainer)で
	 * 常時0を固定し、将来の新しいコンテナ型が黙ってこの経路へ入ることを
	 * 検出する(初回のみWARNINGログも出る)。
	 *
	 * <p>
	 * なお旧{@code LEGACY_RECURSIVE_DESCENTS}(旧再帰driverの発火数)は
	 * 増分4fで削除した——producerである{@code RECURSIVE_DESCENDER}自体が
	 * 物理撤去され、常時0の定数と化したため(撤去の証明過程は
	 * docs/consultations/consult-codex-2026-07-30-*.txtと
	 * {@code WorklistDescentCensusTest}の履歴に残る)。
	 * </p>
	 */
	public static final AtomicLong WORKLIST_COMPAT_FALLBACKS = new AtomicLong();

	/**
	 * worklist driverがMULTICOL境界を再帰なしのnative scope
	 * ({@code FlowContainer.MulticolRestyleScope})として降下した回数です
	 * (2026-07-30新設、増分1)。native化の非空振り証明
	 * (「テストが実際にこの経路を通った」ことの確認)に使う。
	 */
	public static final AtomicLong MULTICOL_NATIVE_DESCENTS = new AtomicLong();

	/**
	 * 現在の継続経路(PAGE/COLUMN)を追跡するスタックです(2026-07-21、B1)。
	 * {@link ResumeTrace#begin(String)}と同じ「入れ子破断はスタックで
	 * 表現する」設計だが、こちらはデバッグ用プロパティに関わらず常に
	 * 有効(観測カウンタの分類に使うため)。
	 *
	 * <p>
	 * 2026-07-24(アーキテクチャレビュー指摘): staticな単一Dequeだと
	 * 複数変換の並行実行でpush/popが混線し、誤集計だけでなく空Dequeの
	 * {@code pop()}例外で変換を落とし得るため、ThreadLocalへ変更した
	 * (クラッシュ排除は絶対要件)。
	 * </p>
	 */
	private static final ThreadLocal<ArrayDeque<Boolean>> continuationPathStack = ThreadLocal
			.withInitial(ArrayDeque::new);

	/**
	 * 継続経路の追跡を開始します。{@code RootBuilder.ResumeSession.resume()}・
	 * {@code BreakableBuilder.columnBreak()}がtry/finallyで対応する
	 * {@link #endContinuationPath()}と対にして呼びます。
	 *
	 * @param column true なら改段(COLUMN)経路、false なら改ページ(PAGE)経路
	 */
	public static void beginContinuationPath(final boolean column) {
		continuationPathStack.get().push(column);
	}

	/** {@link #beginContinuationPath(boolean)}に対応する終了。 */
	public static void endContinuationPath() {
		continuationPathStack.get().pop();
	}

	private static boolean isColumnPath() {
		final Boolean top = continuationPathStack.get().peek();
		return top != null && top;
	}

	/**
	 * {@code FlowContainer.restyle}のOpenChain分岐が1段降りるたびに
	 * 呼びます。{@link #RESTYLE_CHAIN_FIRINGS}に加え、現在の継続経路
	 * (PAGE/COLUMN)に応じた分離カウンタも加算します。
	 */
	public static void recordChainFiring() {
		RESTYLE_CHAIN_FIRINGS.incrementAndGet();
		(isColumnPath() ? COLUMN_RESTYLE_CHAIN_FIRINGS : PAGE_RESTYLE_CHAIN_FIRINGS).incrementAndGet();
	}

	/**
	 * worklist executorが互換フォールバックへ落ちる直前に呼びます
	 * ({@link #WORKLIST_COMPAT_FALLBACKS}参照)。
	 */
	public static void recordWorklistCompatFallback() {
		WORKLIST_COMPAT_FALLBACKS.incrementAndGet();
	}

	/**
	 * worklist driverがMULTICOL境界をnative scopeとして降下する直前に
	 * 呼びます({@link #MULTICOL_NATIVE_DESCENTS}参照)。
	 */
	public static void recordMulticolNativeDescent() {
		MULTICOL_NATIVE_DESCENTS.incrementAndGet();
	}

	/** {@code ColumnsContainer.splitPageAxis}の試行回数です(M6c-1でAPI集約)。 */
	public static void recordColumnsSplitAttempt() {
		COLUMNS_SPLIT_ATTEMPTS.incrementAndGet();
	}

	/** 複数カラム時に最終カラム全体がMOVE候補になった回数です(M6c-1でAPI集約)。 */
	public static void recordLastColumnMoveCandidate() {
		COLUMNS_LAST_COLUMN_MOVE_CANDIDATE.incrementAndGet();
	}

	/**
	 * {@code LayoutSource}のイベントリスト保持数(compact前の最大)の
	 * high-waterです(2026-07-24新設、E-6増分1: spillableテープ基盤の
	 * spill閾値・対象選定の実測基盤。挙動には影響しない)。
	 */
	public static final AtomicLong SOURCE_EVENT_HIGH_WATER = new AtomicLong();

	/** LayoutSourceのイベント保持数の観測です(E-6増分1、最大値を保持)。 */
	public static void recordSourceEventRetention(final int size) {
		SOURCE_EVENT_HIGH_WATER.accumulateAndGet(size, Math::max);
	}

	/**
	 * {@code LayoutSource}のinline text payload保持量(bytes、UTF-16
	 * 見積り=char数×2)のhigh-waterです(2026-07-24新設、E-6増分3b-2)。
	 * spill予算({@code processing.text-spill-budget})が守られている
	 * こと(この値≦予算)を耐久試験の合格条件が読む。
	 */
	public static final AtomicLong LIVE_TEXT_PAYLOAD_BYTES = new AtomicLong();

	/** text payloadのspill record数です(E-6増分3b-2)。 */
	public static final AtomicLong SPILLED_TEXT_RECORDS = new AtomicLong();

	/** text payloadのspill済みbytes総量です(E-6増分3b-2)。 */
	public static final AtomicLong SPILLED_TEXT_BYTES = new AtomicLong();

	/** inline text payload保持量の観測です(E-6増分3b-2、最大値を保持)。 */
	public static void recordLiveTextPayloadBytes(final long bytes) {
		LIVE_TEXT_PAYLOAD_BYTES.accumulateAndGet(bytes, Math::max);
	}

	/** text payloadのspill発火の観測です(E-6増分3b-2)。 */
	public static void recordTextSpill(final long bytes) {
		SPILLED_TEXT_RECORDS.incrementAndGet();
		SPILLED_TEXT_BYTES.addAndGet(bytes);
	}

	// ---- E-6増分4a/4b(2026-07-24): TwoPass range化の発火カウンタ群 ----

	/**
	 * {@code TwoPassBlockBuilder}のbindが{@code SourceRangeBody}
	 * (LayoutSource範囲のSegmentExecutor再駆動)で行われた回数です
	 * (2026-07-24新設、E-6増分4a/4b)。
	 */
	public static final AtomicLong RANGE_FIRST_BINDS = new AtomicLong();

	/**
	 * 表外float/absolute/inline-blockの録画完了(close)時sealが適格で、
	 * {@code SourceRangeBody}へ切り替わった回数です(E-6増分4a/4b)。
	 * 不適格の内訳は{@link #twoPassSealRejects(TwoPassSealReject)}。
	 */
	public static final AtomicLong TWO_PASS_SEALS_ELIGIBLE = new AtomicLong();

	/**
	 * 表キャプションがOpaque(再生不能)として記録された回数です
	 * (caption recipe化C0の観測、2026-08-01——
	 * consult-codex-2026-08-01-caption-recipe.txt。C1のrecipe記録化で
	 * 0になるべき値。これを含む親範囲はcontainsOpaqueで不適格になる
	 * ——legacy残23件のうちTOPLEVEL 10件の原因)。
	 */
	public static final AtomicLong CAPTION_OPAQUE_RECORDS = new AtomicLong();

	/**
	 * キャプションStartが再生範囲の根(または表文脈なし)として拒否された
	 * 回数です(C2のcontext-completeゲート——C0時点では常に0)。
	 */
	public static final AtomicLong CAPTION_ROOT_REJECTS = new AtomicLong();

	/**
	 * キャプションを含む範囲が表文脈確立済みとして受理された回数です
	 * (C2——C0時点では常に0)。
	 */
	public static final AtomicLong CAPTION_CONTEXT_ACCEPTS = new AtomicLong();

	/** TwoPassのseal不適格。呼び出し側は不変条件例外で変換を失敗させる。 */
	public enum TwoPassSealReject {
		/** 主ソースまたはページ文脈がない。 */
		NO_SOURCE,
		/** アンカー・終端がない、または空範囲に計測内容がある。 */
		NO_RANGE,
		/** recipeで再生できないイベントまたは表文脈がある。 */
		OPAQUE_RANGE,
		/** 範囲内の絶対配置と排他所有の証明が一致しない。 */
		ABSOLUTE_RANGE,
		/** 子または実行計画の所有を親範囲へ移せない。 */
		NESTED_BUILDER,
		/** 範囲がcompact等で欠落している。 */
		RANGE_NOT_INTACT
	}

	private static final Map<TwoPassSealReject, AtomicLong> TWO_PASS_SEAL_REJECTS = new EnumMap<>(
			TwoPassSealReject.class);
	static {
		for (final TwoPassSealReject r : TwoPassSealReject.values()) {
			TWO_PASS_SEAL_REJECTS.put(r, new AtomicLong());
		}
	}

	/** 範囲censusの根の分類。 */
	public enum TwoPassRootKind {
		TOPLEVEL, NESTED, GRID_ITEM, FLEX_ITEM, INCREMENTAL_CELL, INCREMENTAL_CAPTION,
		RETAINED_CELL, RETAINED_CAPTION
	}

	/** BINDは範囲再生の総数に対応する。他は補助観測。 */
	public enum TwoPassCensusEvent {
		BIND, SEAL, MEASURE_RANGE, EMPTY_BIND
	}

	/** Grid/Flex項目の構築種別。NONEは項目以外。 */
	public enum TwoPassItemKind { NONE, ANONYMOUS, TAKEOVER, ELEMENT }

	/** 文書名は試験側でreset/snapshotの単位に付ける。barrierReasonのnullはNONE。 */
	public record TwoPassCensusKey(TwoPassRootKind rootKind,
			boolean sealAttempted, String sealOutcome, boolean measurement, BarrierReason barrierReason,
			TwoPassItemKind itemKind) {
	}

	/**
	 * 全域の census(既存の AtomicLong カウンタと同じく static)。DirectSession の変換は
	 * 試験とは別スレッドで走るので ThreadLocal では計上が届かない(2026-09-05 実測: 0 件)。
	 * 同時に 1 つの census しか開けない。
	 */
	private static volatile TwoPassCensus twoPassCensus;

	/**
	 * 文書単位の範囲クロス集計。試験が明示的に開始した期間だけ有効。
	 * 通常変換ではmap/タグ/文字列を作らず、ログの追加走査もしない。
	 * 従来のAtomicLongカウンタを変更せず、同じ計上点で独立に突き合わせる。
	 */
	public static final class TwoPassCensus implements AutoCloseable {
		private final Map<TwoPassCensusEvent, Map<TwoPassCensusKey, AtomicLong>> counts = new EnumMap<>(
				TwoPassCensusEvent.class);
		private volatile boolean measurement;

		private TwoPassCensus() {
			for (final TwoPassCensusEvent event : TwoPassCensusEvent.values()) {
				this.counts.put(event, new java.util.concurrent.ConcurrentHashMap<>());
			}
		}

		public Map<TwoPassCensusKey, Long> snapshot(final TwoPassCensusEvent event) {
			final Map<TwoPassCensusKey, Long> result = new HashMap<>();
			this.counts.get(event).forEach((key, count) -> result.put(key, count.get()));
			return Map.copyOf(result);
		}

		private void reset() {
			this.counts.values().forEach(Map::clear);
		}

		@Override
		public void close() {
			twoPassCensus = null;
		}
	}

	/** 同期DirectSession変換を囲む。文書ごとに既存のreset()を呼ぶ。 */
	public static TwoPassCensus beginTwoPassCensus() {
		if (twoPassCensus != null) {
			throw new IllegalStateException("TwoPass census is already active");
		}
		final TwoPassCensus census = new TwoPassCensus();
		twoPassCensus = census;
		return census;
	}

	/** 再生意図に対応するcensusのphaseスコープ。本体の伝播はReplayIntentが担います。 */
	public static final class TwoPassMeasurement implements AutoCloseable {
		private final TwoPassCensus census;
		private final boolean previous;

		private TwoPassMeasurement(final TwoPassCensus census, final ReplayIntent intent) {
			this.census = census;
			this.previous = census.measurement;
			census.measurement |= intent == ReplayIntent.MEASURE;
		}

		@Override
		public void close() {
			this.census.measurement = this.previous;
		}
	}

	public static TwoPassMeasurement twoPassMeasurement(final ReplayIntent intent) {
		final TwoPassCensus census = twoPassCensus;
		return census == null ? null : new TwoPassMeasurement(census, intent);
	}

	/** builderからDeferredBindへ引き継ぐ診断タグ。箱・ログは保持しない。 */
	public static final class TwoPassCensusTag {
		private final TwoPassCensus census;
		private TwoPassRootKind rootKind = TwoPassRootKind.TOPLEVEL;
		private boolean attempted;
		private String outcome = "NOT_ATTEMPTED";
		private BarrierReason barrier;
		private TwoPassItemKind itemKind = TwoPassItemKind.NONE;

		private TwoPassCensusTag(final TwoPassCensus census) {
			this.census = census;
		}

		public void rootKind(final TwoPassRootKind kind) {
			this.rootKind = kind;
		}

		public void itemKind(final TwoPassItemKind kind) {
			this.itemKind = kind;
		}

		public void seal(final boolean attempted, final String outcome, final BarrierReason barrier) {
			this.attempted = attempted;
			this.outcome = outcome;
			this.barrier = barrier;
			this.record(TwoPassCensusEvent.SEAL);
		}

		public void record(final TwoPassCensusEvent event) {
			final TwoPassCensusKey key = new TwoPassCensusKey(this.rootKind, this.attempted,
					this.outcome, this.census.measurement, this.barrier, this.itemKind);
			this.census.counts.get(event).computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
		}
	}

	public static TwoPassCensusTag newTwoPassCensusTag() {
		final TwoPassCensus census = twoPassCensus;
		return census == null ? null : new TwoPassCensusTag(census);
	}

	/** range bind(SourceRangeBody)の発火の集計です(E-6増分4a/4b)。 */
	public static void recordTwoPassRangeBind() {
		RANGE_FIRST_BINDS.incrementAndGet();
	}

	/**
	 * 空本文sealの回数です(DP増分2、
	 * 2026-07-30)。ソース範囲も計測内容も空のビルダーがclose時に
	 * {@code ReplayBody.Empty}へ切り替わった回数。
	 */
	public static final AtomicLong TWO_PASS_EMPTY_SEALS = new AtomicLong();

	/** 空本文bind(no-op)の回数です(DP増分2)。 */
	public static final AtomicLong TWO_PASS_EMPTY_BINDS = new AtomicLong();

	/**
	 * seal済み(適格計上済み)ビルダーが親のrange化に吸収され、bindされずに
	 * リースを手放した回数です(DP増分3、2026-07-30)。seal:bind 1:1検出の
	 * T1の収支観測は{@code TWO_PASS_SEALS_ELIGIBLE == TWO_PASS_RANGES_CONSUMED +
	 * TWO_PASS_SEALS_SUBSUMED + TWO_PASS_SEALS_ABANDONED}。保証はハンドルの状態機械が担う。
	 */
	public static final AtomicLong TWO_PASS_SEALS_SUBSUMED = new AtomicLong();

	/** ハンドルが本配置で消費された数。例外による消費も含む収支観測です。 */
	public static final AtomicLong TWO_PASS_RANGES_CONSUMED = new AtomicLong();

	/** 主ソースを持たない独立イベント再生の回数です。 */
	public static final AtomicLong TWO_PASS_REPLAY_ONLY_BINDS = new AtomicLong();

	/** 再生せず破棄したハンドル数。一時計測で取得した本文も含みます。 */
	public static final AtomicLong TWO_PASS_SEALS_ABANDONED = new AtomicLong();

	/** 破棄したハンドルのうち表セルの数です。 */
	public static final AtomicLong CELL_RANGE_SEALS_ABANDONED = new AtomicLong();

	/** 親range化への吸収の集計です(DP増分3)。 */
	public static void recordTwoPassSealSubsumed() {
		TWO_PASS_SEALS_SUBSUMED.incrementAndGet();
	}

	/**
	 * seal済み表セル(CELL_RANGE_SEALS計上済み)が親のrange化に吸収され、
	 * bindされずにリースを手放した回数です(表吸収=codex増分5、
	 * 2026-07-30)。セル側のリース収支の完了条件は
	 * {@code CELL_RANGE_SEALS == CELL_RANGE_BINDS +
	 * CELL_RANGE_SEALS_SUBSUMED + CELL_RANGE_SEALS_ABANDONED}として観測する。
	 */
	public static final AtomicLong CELL_RANGE_SEALS_SUBSUMED = new AtomicLong();

	/** 表セルseal吸収の集計です(表吸収=codex増分5)。 */
	public static void recordCellRangeSealSubsumed() {
		CELL_RANGE_SEALS_SUBSUMED.incrementAndGet();
	}

	/** 空本文sealの集計です(DP増分2)。 */
	public static void recordTwoPassEmptySeal() {
		TWO_PASS_EMPTY_SEALS.incrementAndGet();
	}

	/** 空本文bind(no-op)の集計です(DP増分2)。 */
	public static void recordTwoPassEmptyBind() {
		TWO_PASS_EMPTY_BINDS.incrementAndGet();
	}

	/** seal適格の集計です(E-6増分4a/4b)。 */
	public static void recordTwoPassSealEligible() {
		TWO_PASS_SEALS_ELIGIBLE.incrementAndGet();
	}

	/** seal不適格(理由つき)の集計です(E-6増分4a/4b)。 */
	public static void recordTwoPassSealReject(final TwoPassSealReject reason) {
		TWO_PASS_SEAL_REJECTS.get(reason).incrementAndGet();
	}

	// ---- E-6増分5a(2026-07-24): 表セル(CellContent)range化の発火カウンタ群 ----

	/**
	 * Retained表のセルclose時sealが適格で、{@code CellContent}が
	 * 「IntrinsicSizes数値+SourceRange(+lease)」保持へ切り替わった回数
	 * です(2026-07-24新設、E-6増分5a)。セルのsealは
	 * {@code TwoPassBlockBuilder.sealBodyForRangeBind}を経由するため、
	 * この値は{@link #TWO_PASS_SEALS_ELIGIBLE}の部分集合。不適格の内訳も
	 * 同じ{@link #twoPassSealRejects(TwoPassSealReject)}に計上される。
	 */
	public static final AtomicLong CELL_RANGE_SEALS = new AtomicLong();

	/**
	 * seal済みセルのbind(列幅確定後のSegmentExecutor範囲駆動)の回数です
	 * (E-6増分5a)。{@link #RANGE_FIRST_BINDS}の部分集合。リース1:1検出
	 * (取り残しはcompactを永久にclampする)のため、
	 * {@link #CELL_RANGE_SEALS}と常に一致しなければならない——
	 * DisplayListGoldenTestが固定する。
	 */
	public static final AtomicLong CELL_RANGE_BINDS = new AtomicLong();

	/** セルrange sealの集計です(E-6増分5a)。 */
	public static void recordCellRangeSeal() {
		CELL_RANGE_SEALS.incrementAndGet();
	}

	/** seal済みセルのrange bindの集計です(E-6増分5a)。 */
	public static void recordCellRangeBind() {
		CELL_RANGE_BINDS.incrementAndGet();
	}

	// ---- E-6増分5b-2(2026-07-24): 表Pass C(行単位逐次bind)の発火カウンタ群 ----

	/**
	 * Retained表のbindRowsがPass B/C(全セルscratch計測→行高確定→行単位
	 * 逐次bind)で走った表の数です(2026-07-24新設、E-6増分5b-2)。適格条件は
	 * 表単位のfail closed——全実セルがrange化(またはEmpty)済み・
	 * キャプションなし・全セルが計測複製可能({@code
	 * RetainedTableBuilder.isRowSequentialBindEligible})。
	 */
	public static final AtomicLong TABLE_PASS_C_TABLES = new AtomicLong();

	/**
	 * 不適格でbindRows従来経路(行高計算前の全セル一括bind)へフォール
	 * バックしたRetained表の数です(E-6増分5b-2。適格率の分母側)。
	 */
	public static final AtomicLong TABLE_LEGACY_BINDROWS = new AtomicLong();

	/**
	 * Pass B(行計測)のscratch計測の発火数です(E-6増分5b-2)。Pass C表では
	 * 行高計算はこの計測値だけを読み、bind済みセル本文木は1つも存在しない
	 * (計測木は値の採取後に破棄)——「Pass B中のセル本文木保持ゼロ」の
	 * 観測指標。RetentionHighWaterReportTestが実セル規模での発火を固定する。
	 */
	public static final AtomicLong TABLE_PASS_B_CELL_MEASURES = new AtomicLong();

	/** Pass B/C経路で処理された表の集計です(E-6増分5b-2)。 */
	public static void recordTablePassC() {
		TABLE_PASS_C_TABLES.incrementAndGet();
	}

	/** 従来bindRows経路へフォールバックした表の集計です(E-6増分5b-2)。 */
	public static void recordTableLegacyBindRows() {
		TABLE_LEGACY_BINDROWS.incrementAndGet();
	}

	/** Pass Bのセルscratch計測の集計です(E-6増分5b-2)。 */
	public static void recordTablePassBCellMeasure() {
		TABLE_PASS_B_CELL_MEASURES.incrementAndGet();
	}

	/** {@code reason}によるseal不適格の回数です(E-6増分4a/4b)。 */
	public static long twoPassSealRejects(final TwoPassSealReject reason) {
		return TWO_PASS_SEAL_REJECTS.get(reason).get();
	}

	/** open textのスライス運搬(M3b)の発火回数です(M6c-1でAPI集約)。 */
	public static void recordOpenTextHandoff() {
		OPEN_TEXT_HANDOFFS.incrementAndGet();
	}

	/** 改段時のowner段数の観測です(M6c-1でAPI集約)。 */
	public static void recordLastColumnOwnerColumnCount(final int columnCount) {
		LAST_COLUMN_OWNER_COLUMN_COUNT.set(columnCount);
	}

	/** チェーン子フレーム(Child)消費の集計です(M6c-1でAPI集約)。 */
	public static void recordChildFrame() {
		CHILD_FRAMES.incrementAndGet();
	}

	/** チェーン外restyleの集計です(M6c-1でAPI集約)。 */
	public static void recordUnchainedRestyle() {
		UNCHAINED_RESTYLES.incrementAndGet();
	}

	/** open tail消費の集計です(M6c-1でAPI集約)。 */
	public static void recordOpenTail() {
		OPEN_TAILS.incrementAndGet();
	}

	/**
	 * {@code RootBuilder.pageBreak()}の収集可能プレフィックススキャンが
	 * 各レベルをどう分類したかの集計です(2026-07-21新設、B1)。
	 * {@link ContinuationCapability#PLAIN_FLOW}はスキャンを継続させる
	 * ため通常ここには現れず、スキャンを<b>停止させた</b>理由(または
	 * チェーンが尽きるまで停止しなかった場合の空)を数える。
	 */
	private static final Map<ContinuationCapability, AtomicLong> CAPABILITY_SCAN_STOPS = new EnumMap<>(
			ContinuationCapability.class);
	static {
		for (final ContinuationCapability c : ContinuationCapability.values()) {
			CAPABILITY_SCAN_STOPS.put(c, new AtomicLong());
		}
	}

	/** {@code reason}によってプレフィックススキャンが停止した回数。 */
	public static long capabilityScanStops(final ContinuationCapability reason) {
		return CAPABILITY_SCAN_STOPS.get(reason).get();
	}

	/** スキャン停止理由を記録します(常に{@code PLAIN_FLOW}以外)。 */
	public static void recordCapabilityScanStop(final ContinuationCapability reason) {
		CAPABILITY_SCAN_STOPS.get(reason).incrementAndGet();
	}

	/**
	 * COLUMN継続の相対open pathスキャンが各レベルをどう分類したかの集計
	 * です(2026-07-21新設、M6b Phase B4-Step3。2026-07-25時点で配線済み)。{@link
	 * #CAPABILITY_SCAN_STOPS}/{@link #capabilityScanStops}はPAGE専用の
	 * カウンタである(名前は汎用的だが、現状PAGE側からしか呼ばれない)ため、
	 * COLUMN側は別カウンタにする——同一文書がPAGE/COLUMN両方の継続経路を
	 * 持つことは普通にあり(段組内部の改段等)、共有カウンタにすると
	 * 既存のPAGE専用テストの期待値がCOLUMN分の寄与で狂ってしまう。
	 */
	private static final Map<ContinuationCapability, AtomicLong> COLUMN_CAPABILITY_SCAN_STOPS = new EnumMap<>(
			ContinuationCapability.class);
	static {
		for (final ContinuationCapability c : ContinuationCapability.values()) {
			COLUMN_CAPABILITY_SCAN_STOPS.put(c, new AtomicLong());
		}
	}

	/** {@code reason}によってCOLUMN側のプレフィックススキャンが停止した回数。 */
	public static long columnCapabilityScanStops(final ContinuationCapability reason) {
		return COLUMN_CAPABILITY_SCAN_STOPS.get(reason).get();
	}

	/** COLUMN側のスキャン停止理由を記録します(常に{@code PLAIN_FLOW}以外)。 */
	public static void recordColumnCapabilityScanStop(final ContinuationCapability reason) {
		COLUMN_CAPABILITY_SCAN_STOPS.get(reason).incrementAndGet();
	}


	/**
	 * <b>進捗のない自動改ページ</b>の連続回数の最大値です(2026-07-27新設)。
	 * 「同じ状態のまま改ページだけが繰り返される」ライブロックの観測値。
	 */
	public static final AtomicLong MAX_STALLED_AUTO_BREAK_RUN = new AtomicLong();

	/** {@link #STALLED_AUTO_BREAK_LIMIT}に到達した回数です(2026-07-27新設)。 */
	public static final AtomicLong STALLED_AUTO_BREAK_ALARMS = new AtomicLong();

	/**
	 * {@link #guardBreakProgress}の安全閾値です(2026-07-27新設)。
	 *
	 * <p>
	 * <b>実測に基づく</b>: 全873テストを計測したところ、状態が変わらないまま
	 * 連続する<b>自動</b>改ページは最大5回だった
	 * ({@code FloatSplitCommitSmokeTest})。強制改ページは97回まで観測された
	 * ため、ガードは自動改ページに限定している——強制改ページは
     * 「作者が枚数を指定した」ものであり、進捗の有無で測ってはいけない。
	 * この閾値は実測最大の6倍超で、ここへ到達したら実装のライブロックである
	 * という強い証拠になる。
	 * </p>
	 *
	 * <p>
	 * <b>なぜ必要か</b>: このライブロックは1回転ごとに白紙のPDFページを
	 * 1枚割り当てる。ページは{@code PDFWriterImpl.pageOutputs}に恒久保持
	 * されるためヒープは単調増加し、4GBでも枯渇する。さらに
	 * {@code AbstractUserAgent.checkAbort}の無進捗締切は
	 * 「ページが出たこと」を進捗とみなすので<b>発火しない</b>
	 * (2026-07-27に実測: 1.6KBの文書で約45,000ページ・OOM)。
	 * </p>
	 */
	public static final int STALLED_AUTO_BREAK_LIMIT = 32;

	private ContinuationStats() {
		// counters
	}

	/**
	 * 自動改ページが進捗しているかを検査し、同一状態の反復が安全閾値
	 * ({@link #STALLED_AUTO_BREAK_LIMIT})に達したら
	 * {@link ContinuationInvariantViolationException}を投げます
	 * (2026-07-27新設)。
	 *
	 * @param stalledRun 直前の自動改ページと状態が変わらないまま繰り返した回数
	 */
	public static boolean guardBreakProgress(final int stalledRun) {
		MAX_STALLED_AUTO_BREAK_RUN.accumulateAndGet(stalledRun, Math::max);
		if (stalledRun >= STALLED_AUTO_BREAK_LIMIT) {
			STALLED_AUTO_BREAK_ALARMS.incrementAndGet();
			final String message = "auto page break repeated " + stalledRun
					+ " times without any progress (same break target, same page cursor, same flow depth, no new "
					+ "source events); the layout is livelocked, so page breaking is abandoned and the content is "
					+ "laid out in place (it may overflow the page)";
			java.util.logging.Logger.getLogger(ContinuationStats.class.getName()).warning(message);
			// **例外ではなく「改ページをあきらめる」を返す**(2026-07-29)。
			//
			// ここまで来たライブロックは実在する
			// (`FloatSplitPlan.classify`の分岐表5の逃げ道へ構造的に到達
			// できない浮動体。`docs/NEXT-SESSION.md`)。従来はここで
			// {@code ContinuationInvariantViolationException}を投げていたが、
			// それは<b>変換の失敗</b>であり、{@code ARCHITECTURE.md} §5.13 は
			// 変換の失敗を「常にエンジンの不具合」と定めている
			// ——版面が破綻した文書であることを理由に除外できない。
			//
			// 同§5.13は「紙面に収まらない箱を含む文書でも、エンジンは
			// <b>はみ出させるなり次ページへ送るなりして出力を返さなければ
			// ならない</b>」とも定めている。したがって**出力を返す側**へ倒す。
			//
			// この閾値(32)を使うのは、**偽陽性がないと分かっている**安全な
			// 点だからである。低い閾値(2)で同じことをすると正当な改ページ
			// まで潰す(実測: `FloatTableTest`が4ページ→3ページに退行)。
			return true;
		}
		return false;
	}

	/**
	 * 開いたままの祖先チェーンの深さを記録します(2026-07-21新設、
	 * PAGE/COLUMNの両経路で共有する単一の実装)。
	 *
	 * <p>
	 * 2026-07-30(legacy再帰撤去=増分4c): 旧{@code guardOpenDepth}は
	 * 「FlowContainer.restyleのOpenChain再帰がStackOverflowErrorを起こす
	 * 前に止める」ために深さ64で型付き例外を投げていたが、worklist
	 * executorが唯一のdriverになりOpenChain降下は非再帰となったため、
	 * このガードは<b>偽のクラッシュ要因</b>でしかなくなった——例外・
	 * アラーム・閾値({@code ContinuationDepthLimitExceededException}/
	 * {@code PAGE/COLUMN_OPEN_DEPTH_ALARMS}/64)を退役し、観測用の
	 * 最大深さ記録だけを残した(codex相談
	 * consult-codex-2026-07-30-increment4-removal-spec.txt §3)。
	 * </p>
	 *
	 * @param openDepth 開いたままの祖先チェーンの深さ({@link OpenShape#depth()})
	 * @param column    true なら改段(COLUMN)経路、false なら改ページ(PAGE)経路
	 */
	public static void recordOpenDepth(final int openDepth, final boolean column) {
		(column ? MAX_COLUMN_OPEN_TAIL_DEPTH : MAX_PAGE_OPEN_TAIL_DEPTH).accumulateAndGet(openDepth, Math::max);
	}

	public static void reset() {
		final TwoPassCensus census = twoPassCensus;
		if (census != null) {
			census.reset();
		}
		CHILD_FRAMES.set(0);
		COLUMNS_SPLIT_ATTEMPTS.set(0);
		COLUMNS_LAST_COLUMN_MOVE_CANDIDATE.set(0);
		LAST_COLUMN_OWNER_COLUMN_COUNT.set(-1);
		OPEN_TAILS.set(0);
		SOURCE_EVENT_HIGH_WATER.set(0);
		LIVE_TEXT_PAYLOAD_BYTES.set(0);
		SPILLED_TEXT_RECORDS.set(0);
		SPILLED_TEXT_BYTES.set(0);
		UNCHAINED_RESTYLES.set(0);
		OPEN_TEXT_HANDOFFS.set(0);
		MAX_PAGE_OPEN_TAIL_DEPTH.set(0);
		MAX_COLUMN_OPEN_TAIL_DEPTH.set(0);
		RESTYLE_CHAIN_FIRINGS.set(0);
		PAGE_RESTYLE_CHAIN_FIRINGS.set(0);
		COLUMN_RESTYLE_CHAIN_FIRINGS.set(0);
		WORKLIST_COMPAT_FALLBACKS.set(0);
		MULTICOL_NATIVE_DESCENTS.set(0);
		MAX_STALLED_AUTO_BREAK_RUN.set(0);
		STALLED_AUTO_BREAK_ALARMS.set(0);
		RANGE_FIRST_BINDS.set(0);
		TWO_PASS_EMPTY_SEALS.set(0);
		TWO_PASS_EMPTY_BINDS.set(0);
		TWO_PASS_SEALS_SUBSUMED.set(0);
		TWO_PASS_RANGES_CONSUMED.set(0);
		TWO_PASS_REPLAY_ONLY_BINDS.set(0);
		TWO_PASS_SEALS_ABANDONED.set(0);
		CELL_RANGE_SEALS_ABANDONED.set(0);
		CELL_RANGE_SEALS_SUBSUMED.set(0);
		TWO_PASS_SEALS_ELIGIBLE.set(0);
		CAPTION_OPAQUE_RECORDS.set(0);
		CAPTION_ROOT_REJECTS.set(0);
		CAPTION_CONTEXT_ACCEPTS.set(0);
		CELL_RANGE_SEALS.set(0);
		CELL_RANGE_BINDS.set(0);
		TABLE_PASS_C_TABLES.set(0);
		TABLE_LEGACY_BINDROWS.set(0);
		TABLE_PASS_B_CELL_MEASURES.set(0);
		for (final AtomicLong counter : TWO_PASS_SEAL_REJECTS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : CAPABILITY_SCAN_STOPS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : COLUMN_CAPABILITY_SCAN_STOPS.values()) {
			counter.set(0);
		}
	}

}
