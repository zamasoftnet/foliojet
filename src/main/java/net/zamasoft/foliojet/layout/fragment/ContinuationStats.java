package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
	 * {@code TwoPassBlockBuilder}のbindが{@code LegacyRecords}
	 * (records再演)で行われた回数です(表セル・キャプション等、seal
	 * 対象外のビルダーも含む全records bindの分母)。
	 */
	public static final AtomicLong LEGACY_RECORD_BINDS = new AtomicLong();

	/**
	 * 表外float/absolute/inline-blockの録画完了(close)時sealが適格で、
	 * {@code SourceRangeBody}へ切り替わった回数です(E-6増分4a/4b)。
	 * 不適格の内訳は{@link #twoPassSealRejects(TwoPassSealReject)}。
	 */
	public static final AtomicLong TWO_PASS_SEALS_ELIGIBLE = new AtomicLong();

	/**
	 * TwoPass range seal({@code TwoPassBlockBuilder.sealBodyForRangeBind})の
	 * 不適格理由です(E-6増分4a/4b)。判定はfail closed——少しでも怪しい
	 * 範囲は{@code LegacyRecords}を継続する。
	 */
	public enum TwoPassSealReject {
		/** ページ文脈なし・segment-restyle無効・LayoutSourceなし(scratch計測等)。 */
		NO_SOURCE,
		/**
		 * root boxのSourceAnchorがない、またはStartでないか未閉。増分4e以前は
		 * 絶対配置(Opaque記録)が全てここに計上されていた(4b実測131件中81件)
		 * ——4eのrecipe記録化で絶対配置は適格判定の土俵に乗る。
		 */
		NO_RANGE,
		/** 子イベント範囲が空(空のfloat等。records解放の益がない)。 */
		EMPTY_RANGE,
		/**
		 * 範囲にOpaqueを含む。表セット(2026-07-30)以降のOpaque発生源は
		 * <b>非適格な表(float/inline-table/absolute配置等)と表キャプション
		 * のみ</b>(通常フロー配置の適格な表はTABLE_RANGEへ分類が移った。
		 * ルビ由来のOpaqueは注釈付きテキスト化で160→0)。
		 */
		OPAQUE_RANGE,
		/**
		 * 範囲に絶対配置ブロックのStartを含む(E-6増分4e)。増分4e以前は
		 * 絶対配置がOpaque記録だったためOPAQUE_RANGEに含まれていた分類の
		 * 分離——絶対配置はcontext builderへ係留済み+deferred bindを持つ
		 * ため、範囲再生による再構築は二重登録・リース取り残しを生む
		 * ({@code LayoutSource.containsAbsolute}のjavadoc参照)。
		 */
		ABSOLUTE_RANGE,
		/**
		 * recordsにネストしたビルダー(StfBlock/AbsoluteBlock/InlineBlockEvent/
		 * TableEvent)を含む。ネストした子が既にsealしたリースを親のrange化で
		 * 破棄するとリース解放の再帰配線が必要になるため、4a/4bでは
		 * 「ネストなし(leaf)」のみを適格とする——子ビルダー自身は自分の
		 * bindで独立にrange化される。
		 */
		NESTED_BUILDER,
		/** 範囲の完全性検証(capture)に失敗(compact済みの穴)。 */
		RANGE_NOT_INTACT,
		/**
		 * 範囲にGridのStartを含む(Grid G1d、2026-07-31)。G3d1の
		 * RetainedGrid/GridEventでrecords側もGridBuilderの実トラック配置を
		 * 通るようになり、範囲再生との幾何一致(パリティ)が確立したため
		 * <b>G3d3(同日)でrejectは撤去済み</b>——現在この理由は発火しない
		 * (GridEventのitem本文は通常のネスト子として親rangeへ吸収される)。
		 */
		GRID_RANGE
	}

	private static final Map<TwoPassSealReject, AtomicLong> TWO_PASS_SEAL_REJECTS = new EnumMap<>(
			TwoPassSealReject.class);
	static {
		for (final TwoPassSealReject r : TwoPassSealReject.values()) {
			TWO_PASS_SEAL_REJECTS.put(r, new AtomicLong());
		}
	}

	/**
	 * legacy records bindの由来分類です(DP増分0、2026-07-30——codex相談
	 * consult-codex-2026-07-30-dualpath-endgame.txt 増分0)。
	 * {@code LEGACY_RECORD_BINDS}は全records bindの総数で由来を
	 * 区別しないため、縮退の進捗を由来別に固定する。
	 */
	public enum LegacyBindOrigin {
		/** 表外のfloat/absolute/inline-block等(DocumentBuilder駆動)。 */
		TOPLEVEL,
		/** Incremental表のセル。 */
		INCREMENTAL_CELL,
		/** Incremental表のキャプション。 */
		INCREMENTAL_CAPTION,
		/** Retained表のセル(seal不適格)。 */
		RETAINED_CELL,
		/** Retained表のキャプション。 */
		RETAINED_CAPTION,
		/** ネストした実測ビルダー(shrink-to-fit内のfloat等)。 */
		NESTED
	}

	private static final Map<LegacyBindOrigin, AtomicLong> LEGACY_RECORD_BINDS_BY_ORIGIN = new EnumMap<>(
			LegacyBindOrigin.class);
	static {
		for (final LegacyBindOrigin o : LegacyBindOrigin.values()) {
			LEGACY_RECORD_BINDS_BY_ORIGIN.put(o, new AtomicLong());
		}
	}

	/** {@code origin}由来のlegacy records bind回数です(DP増分0)。 */
	public static long legacyRecordBinds(final LegacyBindOrigin origin) {
		return LEGACY_RECORD_BINDS_BY_ORIGIN.get(origin).get();
	}

	/** range bind(SourceRangeBody)の発火の集計です(E-6増分4a/4b)。 */
	public static void recordTwoPassRangeBind() {
		RANGE_FIRST_BINDS.incrementAndGet();
	}

	/** records bind(LegacyRecords)の発火の集計です(E-6増分4a/4b。DP増分0で由来別集計を追加)。 */
	public static void recordTwoPassLegacyRecordBind(final LegacyBindOrigin origin) {
		LEGACY_RECORD_BINDS.incrementAndGet();
		LEGACY_RECORD_BINDS_BY_ORIGIN.get(origin).incrementAndGet();
	}

	/**
	 * 空本文seal(records経路からの切り離し)の回数です(DP増分2、
	 * 2026-07-30)。ソース範囲もrecordsも空のビルダーがclose時に
	 * {@code ReplayBody.Empty}へ切り替わった回数。
	 */
	public static final AtomicLong TWO_PASS_EMPTY_SEALS = new AtomicLong();

	/** 空本文bind(no-op)の回数です(DP増分2)。 */
	public static final AtomicLong TWO_PASS_EMPTY_BINDS = new AtomicLong();

	/**
	 * seal済み(適格計上済み)ビルダーが親のrange化に吸収され、bindされずに
	 * リースを手放した回数です(DP増分3、2026-07-30)。seal:bind 1:1検出の
	 * 完了条件は{@code TWO_PASS_SEALS_ELIGIBLE == RANGE_FIRST_BINDS +
	 * TWO_PASS_SEALS_SUBSUMED}——DisplayListGoldenTestが固定する。
	 */
	public static final AtomicLong TWO_PASS_SEALS_SUBSUMED = new AtomicLong();

	/** 親range化への吸収の集計です(DP増分3)。 */
	public static void recordTwoPassSealSubsumed() {
		TWO_PASS_SEALS_SUBSUMED.incrementAndGet();
	}

	/**
	 * seal済み表セル(CELL_RANGE_SEALS計上済み)が親のrange化に吸収され、
	 * bindされずにリースを手放した回数です(表吸収=codex増分5、
	 * 2026-07-30)。セル側のリース収支の完了条件は
	 * {@code CELL_RANGE_SEALS == CELL_RANGE_BINDS +
	 * CELL_RANGE_SEALS_SUBSUMED}——DisplayListGoldenTest/
	 * TwoPassRangeBindParityTestが固定する。
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

	/**
	 * seal不適格のままのRetained表セルのbind(従来のrecords再演)の回数
	 * です(E-6増分5a。適格率の分母側。Incremental表のセルはこの経路も
	 * 通らず対象外——保持窓が行単位で短いため別増分)。
	 */
	public static final AtomicLong CELL_LEGACY_BINDS = new AtomicLong();

	/** セルrange seal(records解放)の集計です(E-6増分5a)。 */
	public static void recordCellRangeSeal() {
		CELL_RANGE_SEALS.incrementAndGet();
	}

	/** seal済みセルのrange bindの集計です(E-6増分5a)。 */
	public static void recordCellRangeBind() {
		CELL_RANGE_BINDS.incrementAndGet();
	}

	/** 不適格セルのrecords bindの集計です(E-6増分5a)。 */
	public static void recordCellLegacyBind() {
		CELL_LEGACY_BINDS.incrementAndGet();
	}

	// ---- E-6増分5b-2(2026-07-24): 表Pass C(行単位逐次bind)の発火カウンタ群 ----

	/**
	 * Retained表のbindRowsがPass B/C(全セルscratch計測→行高確定→行単位
	 * 逐次bind)で走った表の数です(2026-07-24新設、E-6増分5b-2)。適格条件は
	 * 表単位のfail closed——全実セルがrange化(またはrecords空)済み・
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
	 * この閾値は実測最大の50倍で、ここへ到達したら実装のライブロックである
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
	public static final int STALLED_AUTO_BREAK_LIMIT = 256;

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
			// この閾値(256)を使うのは、**偽陽性がないと分かっている**唯一の
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
		LEGACY_RECORD_BINDS.set(0);
		TWO_PASS_EMPTY_SEALS.set(0);
		TWO_PASS_EMPTY_BINDS.set(0);
		TWO_PASS_SEALS_SUBSUMED.set(0);
		CELL_RANGE_SEALS_SUBSUMED.set(0);
		TWO_PASS_SEALS_ELIGIBLE.set(0);
		CELL_RANGE_SEALS.set(0);
		CELL_RANGE_BINDS.set(0);
		CELL_LEGACY_BINDS.set(0);
		TABLE_PASS_C_TABLES.set(0);
		TABLE_LEGACY_BINDROWS.set(0);
		TABLE_PASS_B_CELL_MEASURES.set(0);
		for (final AtomicLong counter : TWO_PASS_SEAL_REJECTS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : LEGACY_RECORD_BINDS_BY_ORIGIN.values()) {
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
