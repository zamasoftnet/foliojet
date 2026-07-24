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
	 * ({@code ResumeTraceGoldenTest}・{@code BalanceProbeSessionTest})
	 * ごと退役してよい。
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
	 * `RootBuilder`の終端`OpenTailShape`処理で、worklist executorへの
	 * 適格判定({@link WorklistTailGate})が**適格**だった回数です
	 * (2026-07-22新設、B6a1。2026-07-24のB6検証インフラ退役後も
	 * legacy再帰経路の使用頻度観測として残置——591文書コーパスの最終
	 * 実測はELIGIBLE=10/INELIGIBLE=0)。
	 */
	public static final AtomicLong WORKLIST_ELIGIBLE_TERMINALS = new AtomicLong();

	/**
	 * 同判定が**偽**だった回数です(legacy再帰へフォールバックした
	 * 実際の回数)。この値が実測コーパスで恒常的に0なら、legacy実装の
	 * 削除を検討する強い根拠になる——0でなければ、削除前にworklist
	 * executor側がそれらのケース(ruby・float・absolute・書字方向
	 * 不一致祖先等)も正しく扱えるかの追加検証が必要。
	 */
	public static final AtomicLong WORKLIST_INELIGIBLE_TERMINALS = new AtomicLong();

	/**
	 * {@code OpenChain}再帰から戻った直後、同じ{@code items}レベルに
	 * まだ処理すべき残りitem(chain子よりserialが後のfloating/replay等)
	 * があった回数です(2026-07-22新設、B6a1のworklist executor設計で
	 * codexが指摘した「子再帰から戻った後に処理すべき親itemが存在し
	 * 得る」という構造の実測——単なる`Deque<FlowBlockBox>`では表現
	 * できないためworklist設計上重要。読み取り専用、既存挙動には一切
	 * 影響しない)。分母は{@link #RESTYLE_CHAIN_FIRINGS}。
	 */
	public static final AtomicLong OPEN_CHAIN_TRAILING_ITEMS = new AtomicLong();

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
		if (!live()) {
			return;
		}
		RESTYLE_CHAIN_FIRINGS.incrementAndGet();
		(isColumnPath() ? COLUMN_RESTYLE_CHAIN_FIRINGS : PAGE_RESTYLE_CHAIN_FIRINGS).incrementAndGet();
	}

	/**
	 * production診断writeの共通ゲートです(2026-07-24、排除域P2のM6c-1)。
	 * M6cバランスプローブの候補構築中({@link LayoutExecutionScope}の
	 * {@code BALANCE_PROBE})は、捨てる可能性のある試行で診断を汚さない
	 * よう書き込みを抑制する。M6c-1時点ではプローブ未配線のため常に
	 * true(挙動不変)。
	 */
	private static boolean live() {
		return LayoutExecutionScope.isLive();
	}

	/** {@code ColumnsContainer.splitPageAxis}の試行回数です(M6c-1でAPI集約)。 */
	public static void recordColumnsSplitAttempt() {
		if (live()) {
			COLUMNS_SPLIT_ATTEMPTS.incrementAndGet();
		}
	}

	/** 複数カラム時に最終カラム全体がMOVE候補になった回数です(M6c-1でAPI集約)。 */
	public static void recordLastColumnMoveCandidate() {
		if (live()) {
			COLUMNS_LAST_COLUMN_MOVE_CANDIDATE.incrementAndGet();
		}
	}

	/**
	 * {@code LayoutSource}のイベントリスト保持数(compact前の最大)の
	 * high-waterです(2026-07-24新設、E-6増分1: spillableテープ基盤の
	 * spill閾値・対象選定の実測基盤。挙動には影響しない)。
	 */
	public static final AtomicLong SOURCE_EVENT_HIGH_WATER = new AtomicLong();

	/** LayoutSourceのイベント保持数の観測です(E-6増分1、最大値を保持)。 */
	public static void recordSourceEventRetention(final int size) {
		if (live()) {
			SOURCE_EVENT_HIGH_WATER.accumulateAndGet(size, Math::max);
		}
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
		if (live()) {
			LIVE_TEXT_PAYLOAD_BYTES.accumulateAndGet(bytes, Math::max);
		}
	}

	/** text payloadのspill発火の観測です(E-6増分3b-2)。 */
	public static void recordTextSpill(final long bytes) {
		if (live()) {
			SPILLED_TEXT_RECORDS.incrementAndGet();
			SPILLED_TEXT_BYTES.addAndGet(bytes);
		}
	}

	/** open textのスライス運搬(M3b)の発火回数です(M6c-1でAPI集約)。 */
	public static void recordOpenTextHandoff() {
		if (live()) {
			OPEN_TEXT_HANDOFFS.incrementAndGet();
		}
	}

	/** OpenChain分岐で末尾以外のitemを観測した回数です(M6c-1でAPI集約)。 */
	public static void recordOpenChainTrailingItem() {
		if (live()) {
			OPEN_CHAIN_TRAILING_ITEMS.incrementAndGet();
		}
	}

	/** 改段時のowner段数の観測です(M6c-1でAPI集約)。 */
	public static void recordLastColumnOwnerColumnCount(final int columnCount) {
		if (live()) {
			LAST_COLUMN_OWNER_COLUMN_COUNT.set(columnCount);
		}
	}

	/**
	 * M6cバランスプローブが起動された回数です(2026-07-24新設、排除域P2の
	 * M6c-3。オプトイン{@code processing.balance-probe}有効時のみ増える)。
	 */
	public static final AtomicLong BALANCE_PROBE_SESSIONS = new AtomicLong();

	/**
	 * うち、入力の凍結(source capture+frozen recipe化、Barrierゼロ)に
	 * 成功しプローブ適格だった回数です。
	 */
	public static final AtomicLong BALANCE_PROBE_ELIGIBLE = new AtomicLong();

	/**
	 * うち、winnerなしで既存balanceへフォールバックした回数です
	 * (不適格・上界不発見・非単調観測の合計)。
	 */
	public static final AtomicLong BALANCE_PROBE_FALLBACKS = new AtomicLong();

	/** プローブが構築した候補の総数です(探索コストの実測)。 */
	public static final AtomicLong BALANCE_PROBE_BUILDS = new AtomicLong();

	/**
	 * winnerがownerへ実際にcommitされた回数です(2026-07-24新設、
	 * M6c-4——実採用切替)。{@code BALANCE_PROBE_SESSIONS ==
	 * BALANCE_PROBE_COMMITS + BALANCE_PROBE_FALLBACKS}が常に成り立つ。
	 */
	public static final AtomicLong BALANCE_PROBE_COMMITS = new AtomicLong();

	/** プローブ起動の集計です(M6c-3。全試行終了後に一セッション一回)。 */
	public static void recordBalanceProbeSession() {
		if (live()) {
			BALANCE_PROBE_SESSIONS.incrementAndGet();
		}
	}

	/** プローブ適格(入力凍結成功)の集計です(M6c-3)。 */
	public static void recordBalanceProbeEligible() {
		if (live()) {
			BALANCE_PROBE_ELIGIBLE.incrementAndGet();
		}
	}

	/** 既存balanceへのフォールバックの集計です(M6c-3)。 */
	public static void recordBalanceProbeFallback() {
		if (live()) {
			BALANCE_PROBE_FALLBACKS.incrementAndGet();
		}
	}

	/** 構築した候補数の集計です(M6c-3)。 */
	public static void recordBalanceProbeBuilds(final int builds) {
		if (live()) {
			BALANCE_PROBE_BUILDS.addAndGet(builds);
		}
	}

	/** winnerのowner commitの集計です(M6c-4)。 */
	public static void recordBalanceProbeCommit() {
		if (live()) {
			BALANCE_PROBE_COMMITS.incrementAndGet();
		}
	}

	/** worklist executorの適格/不適格terminalの集計です(M6c-1でAPI集約)。 */
	public static void recordWorklistTerminal(final boolean eligible) {
		if (live()) {
			(eligible ? WORKLIST_ELIGIBLE_TERMINALS : WORKLIST_INELIGIBLE_TERMINALS).incrementAndGet();
		}
	}

	/** チェーン子フレーム(Child)消費の集計です(M6c-1でAPI集約)。 */
	public static void recordChildFrame() {
		if (live()) {
			CHILD_FRAMES.incrementAndGet();
		}
	}

	/** チェーン外restyleの集計です(M6c-1でAPI集約)。 */
	public static void recordUnchainedRestyle() {
		if (live()) {
			UNCHAINED_RESTYLES.incrementAndGet();
		}
	}

	/** open tail消費の集計です(M6c-1でAPI集約)。 */
	public static void recordOpenTail() {
		if (live()) {
			OPEN_TAILS.incrementAndGet();
		}
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
		if (live()) {
			CAPABILITY_SCAN_STOPS.get(reason).incrementAndGet();
		}
	}

	/**
	 * COLUMN継続の相対open pathスキャンが各レベルをどう分類したかの集計
	 * です(2026-07-21新設、M6b Phase B4-Step3、未配線)。{@link
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
		if (live()) {
			COLUMN_CAPABILITY_SCAN_STOPS.get(reason).incrementAndGet();
		}
	}


	/**
	 * PAGE経路(RootBuilder.pageBreak)での深さアラーム発火回数
	 * ({@code OpenTailShape}の深さが閾値以上になった回数。2026-07-20、
	 * M6b Phase B着手前の暫定安全策として旧{@code OPEN_CHAIN_DEPTH_ALARMS}
	 * で導入し、2026-07-21にCOLUMN側と分離)。
	 */
	public static final AtomicLong PAGE_OPEN_DEPTH_ALARMS = new AtomicLong();

	/**
	 * COLUMN経路(BreakableBuilder.columnBreak、段組内の改段)での
	 * 深さアラーム発火回数(2026-07-21新設)。この経路は2026-07-20時点では
	 * ガード自体が存在せず、無防備だった(ChatGPT Pro相談で発見、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-full-fix.md)。
	 */
	public static final AtomicLong COLUMN_OPEN_DEPTH_ALARMS = new AtomicLong();

	/**
	 * {@link #PAGE_OPEN_DEPTH_ALARMS}/{@link #COLUMN_OPEN_DEPTH_ALARMS}の
	 * 閾値。実文書でこの深さの開いた祖先チェーンが必要になることは通常
	 * ありえない、十分に保守的なアラーム線(素のStackOverflowErrorが
	 * 起きうる深さよりずっと手前)として設定している——実際にここへ
	 * 到達した場合は、想定より深いopen-chainが実在するという強い証拠で
	 * あり、M6b Phase Bの着手根拠になる。
	 */
	public static final int OPEN_CHAIN_DEPTH_ALARM_THRESHOLD = 64;

	private ContinuationStats() {
		// counters
	}

	/**
	 * 開いたままの祖先チェーンの深さを検査し、安全閾値
	 * ({@link #OPEN_CHAIN_DEPTH_ALARM_THRESHOLD})以上ならログ警告の上、
	 * {@link ContinuationDepthLimitExceededException}を投げます
	 * (2026-07-21、PAGE/COLUMNの両経路で共有する単一の実装。以前は
	 * {@code RootBuilder.resumeFrame()}内にのみこのチェックがあり、
	 * {@code BreakableBuilder.columnBreak()}は完全に素通りしていた)。
	 *
	 * @param openDepth 開いたままの祖先チェーンの深さ({@link OpenShape#depth()})
	 * @param column    true なら改段(COLUMN)経路、false なら改ページ(PAGE)経路
	 */
	public static void guardOpenDepth(final int openDepth, final boolean column) {
		// 深さ観測はproduction診断(M6c-3: バランスプローブの候補構築中は
		// 抑制する)。安全閾値の例外自体はプローブ中も等しく発動する——
		// 候補中の異常を握り潰してlegacy再実行してはいけない(codex設計§1.7)
		if (live()) {
			(column ? MAX_COLUMN_OPEN_TAIL_DEPTH : MAX_PAGE_OPEN_TAIL_DEPTH).accumulateAndGet(openDepth, Math::max);
		}
		if (openDepth >= OPEN_CHAIN_DEPTH_ALARM_THRESHOLD) {
			if (live()) {
				(column ? COLUMN_OPEN_DEPTH_ALARMS : PAGE_OPEN_DEPTH_ALARMS).incrementAndGet();
			}
			final String message = "open ancestor chain depth=" + openDepth + " reached the safety alarm threshold ("
					+ OPEN_CHAIN_DEPTH_ALARM_THRESHOLD + ") on the " + (column ? "COLUMN" : "PAGE")
					+ " continuation path; FlowContainer.restyle's OpenChain branch is still recursive and would "
					+ "risk an uncontrolled StackOverflowError beyond this point (see docs/PLAN.md \"M6b Phase B\")";
			java.util.logging.Logger.getLogger(ContinuationStats.class.getName()).warning(message);
			throw new ContinuationDepthLimitExceededException(message);
		}
	}

	public static void reset() {
		BALANCE_PROBE_SESSIONS.set(0);
		BALANCE_PROBE_ELIGIBLE.set(0);
		BALANCE_PROBE_FALLBACKS.set(0);
		BALANCE_PROBE_BUILDS.set(0);
		BALANCE_PROBE_COMMITS.set(0);
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
		WORKLIST_ELIGIBLE_TERMINALS.set(0);
		WORKLIST_INELIGIBLE_TERMINALS.set(0);
		OPEN_CHAIN_TRAILING_ITEMS.set(0);
		PAGE_OPEN_DEPTH_ALARMS.set(0);
		COLUMN_OPEN_DEPTH_ALARMS.set(0);
		for (final AtomicLong counter : CAPABILITY_SCAN_STOPS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : COLUMN_CAPABILITY_SCAN_STOPS.values()) {
			counter.set(0);
		}
	}

}
