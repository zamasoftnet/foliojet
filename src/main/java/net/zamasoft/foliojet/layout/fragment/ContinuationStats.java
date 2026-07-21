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
	 * OpenTailShape の深さの最大値(Phase 3 の除去範囲の実測。
	 * 0 = 開きボックスなし、1 = 開きテキストのみ、2+ = moved-open 入れ子)。
	 *
	 * @deprecated 2026-07-21、{@link #MAX_PAGE_OPEN_TAIL_DEPTH}/
	 *             {@link #MAX_COLUMN_OPEN_TAIL_DEPTH}へ分離した
	 *             (ChatGPT Pro相談で判明: {@code BreakableBuilder
	 *             .columnBreak()}はPAGE側の深さガードを一切通らない別経路
	 *             のため、両者を混同すると片方の異常が見えなくなる)。
	 *             既存テスト互換のため当面残すが、両者の合計ではなく
	 *             最後に観測したいずれか一方の値になる点に注意。
	 */
	@Deprecated
	public static final AtomicLong MAX_OPEN_TAIL_DEPTH = new AtomicLong();

	/** PAGE(RootBuilder.pageBreak経由)のOpenTailShape深さの最大値。 */
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
	 * 現在の継続経路(PAGE/COLUMN)を追跡するスタックです(2026-07-21、B1)。
	 * {@link ResumeTrace#begin(String)}と同じ「入れ子破断はスタックで
	 * 表現する」設計だが、こちらはデバッグ用プロパティに関わらず常に
	 * 有効(観測カウンタの分類に使うため)。
	 */
	private static final ArrayDeque<Boolean> continuationPathStack = new ArrayDeque<>();

	/**
	 * 継続経路の追跡を開始します。{@code RootBuilder.ResumeSession.resume()}・
	 * {@code BreakableBuilder.columnBreak()}がtry/finallyで対応する
	 * {@link #endContinuationPath()}と対にして呼びます。
	 *
	 * @param column true なら改段(COLUMN)経路、false なら改ページ(PAGE)経路
	 */
	public static void beginContinuationPath(final boolean column) {
		continuationPathStack.push(column);
	}

	/** {@link #beginContinuationPath(boolean)}に対応する終了。 */
	public static void endContinuationPath() {
		continuationPathStack.pop();
	}

	private static boolean isColumnPath() {
		final Boolean top = continuationPathStack.peek();
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
	 * {@link ResumeProgram}がfirst-classにコンパイルしたlevel(root除く)の
	 * capability別件数です(2026-07-21新設、B3)。{@link
	 * #capabilityScanStops}が「スキャンを止めた理由」を数えるのに対し、
	 * こちらは「実際にResumeLevelとしてコンパイルできた理由」を数える
	 * ——{@code MULTICOL}を収集可能にした後は、`capabilityScanStops
	 * (MULTICOL)`が0のままでも`pageCompiledLevels(MULTICOL)`は増える
	 * ため、両者を見比べることで「段組levelを実際に通過したか」を区別
	 * できる(ChatGPT Pro相談で提案、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b3-multicol-split-through.md)。
	 */
	private static final Map<ContinuationCapability, AtomicLong> PAGE_COMPILED_LEVELS = new EnumMap<>(
			ContinuationCapability.class);
	static {
		for (final ContinuationCapability c : ContinuationCapability.values()) {
			PAGE_COMPILED_LEVELS.put(c, new AtomicLong());
		}
	}

	/** {@code capability}のレベルが実際にfirst-classコンパイルされた回数。 */
	public static long pageCompiledLevels(final ContinuationCapability capability) {
		return PAGE_COMPILED_LEVELS.get(capability).get();
	}

	/**
	 * コンパイル済み{@link ResumeProgram}のlevel(root除く)をcapability別に
	 * 集計します。{@code RootBuilder.pageBreak()}が`ResumeProgramCompiler
	 * .compile()`直後、`flowStack.clear()`より前に呼ぶ。
	 */
	public static void recordCompiledProgram(final ResumeProgram program) {
		for (final ResumeProgram.ResumeLevel level : program.levels()) {
			if (level.descriptor().role() instanceof OpenPathSnapshot.OpenLevelRole.Ancestor(
					final ContinuationCapability capability)) {
				PAGE_COMPILED_LEVELS.get(capability).incrementAndGet();
			}
		}
	}

	/**
	 * {@code OpenTailShape}の深さがこの値以上になった回数(2026-07-20、
	 * M6b Phase B着手前の暫定安全策)。{@code FlowContainer.restyle}の
	 * {@code OpenChain}分岐はまだ反復化されていない
	 * ({@code docs/PLAN.md}「M6b Phase B」参照)ため、この深さに達すると
	 * 素のStackOverflowErrorへ到達するリスクがある。
	 *
	 * @deprecated 2026-07-21、{@link #PAGE_OPEN_DEPTH_ALARMS}/
	 *             {@link #COLUMN_OPEN_DEPTH_ALARMS}へ分離。
	 */
	@Deprecated
	public static final AtomicLong OPEN_CHAIN_DEPTH_ALARMS = new AtomicLong();

	/** PAGE経路(RootBuilder.pageBreak)での深さアラーム発火回数。 */
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
		(column ? MAX_COLUMN_OPEN_TAIL_DEPTH : MAX_PAGE_OPEN_TAIL_DEPTH).accumulateAndGet(openDepth, Math::max);
		MAX_OPEN_TAIL_DEPTH.accumulateAndGet(openDepth, Math::max);
		if (openDepth >= OPEN_CHAIN_DEPTH_ALARM_THRESHOLD) {
			(column ? COLUMN_OPEN_DEPTH_ALARMS : PAGE_OPEN_DEPTH_ALARMS).incrementAndGet();
			OPEN_CHAIN_DEPTH_ALARMS.incrementAndGet();
			final String message = "open ancestor chain depth=" + openDepth + " reached the safety alarm threshold ("
					+ OPEN_CHAIN_DEPTH_ALARM_THRESHOLD + ") on the " + (column ? "COLUMN" : "PAGE")
					+ " continuation path; FlowContainer.restyle's OpenChain branch is still recursive and would "
					+ "risk an uncontrolled StackOverflowError beyond this point (see docs/PLAN.md \"M6b Phase B\")";
			java.util.logging.Logger.getLogger(ContinuationStats.class.getName()).warning(message);
			throw new ContinuationDepthLimitExceededException(message);
		}
	}

	public static void reset() {
		CHILD_FRAMES.set(0);
		OPEN_TAILS.set(0);
		UNCHAINED_RESTYLES.set(0);
		OPEN_TEXT_HANDOFFS.set(0);
		MAX_OPEN_TAIL_DEPTH.set(0);
		MAX_PAGE_OPEN_TAIL_DEPTH.set(0);
		MAX_COLUMN_OPEN_TAIL_DEPTH.set(0);
		RESTYLE_CHAIN_FIRINGS.set(0);
		PAGE_RESTYLE_CHAIN_FIRINGS.set(0);
		COLUMN_RESTYLE_CHAIN_FIRINGS.set(0);
		OPEN_CHAIN_DEPTH_ALARMS.set(0);
		PAGE_OPEN_DEPTH_ALARMS.set(0);
		COLUMN_OPEN_DEPTH_ALARMS.set(0);
		for (final AtomicLong counter : CAPABILITY_SCAN_STOPS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : PAGE_COMPILED_LEVELS.values()) {
			counter.set(0);
		}
	}
}
