package net.zamasoft.foliojet.layout.fragment;

/**
 * 改ページ・改段判定の分断・変異・巻き戻しを構造化観測する監査機構です
 * (2026-07-21新設、M6b Phase B/M6d設計相談 M6d-0.5)。
 *
 * <p>
 * codexへのM6d設計相談(docs/consultations/consult-m6d-codex-2026-07-21.txt)
 * で提案された最初の安全な一歩——{@code ConstraintSpace}の空型を置く
 * だけでなく、現在の{@code FlowContainer.splitPageAxis}等の変異切断・
 * pushback/avoidループの巻き戻しを先に観測することで、M6dの値型設計
 * (何を安定cursorで表すべきか)の材料にする。
 * </p>
 *
 * <p>
 * <b>必須制約(codexの助言どおり)</b>: デフォルト無効。既存のsplitを
 * 再実行しない。live box(IBox等)をtraceへ保持しない(識別のための
 * 軽量なfingerprint文字列のみ)。観測のために制御順序を変えない
 * (このクラスへの呼び出しはすべて既存コードパスの「ついで」に行う
 * 副作用のない記録であり、戻り値や分岐条件には一切影響しない)。
 * デバッグ用のdeep copyは作らない。
 * </p>
 */
public final class FragmentationAudit {
	private FragmentationAudit() {
	}

	private static volatile boolean enabled = false;

	private static final ThreadLocal<FragmentationTrace> CURRENT = new ThreadLocal<FragmentationTrace>();

	/** 観測を有効にするかを返します(既定false、テストからのみ切り替える)。 */
	public static boolean isEnabled() {
		return enabled;
	}

	/** 観測の有効/無効を切り替えます。 */
	public static void setEnabled(final boolean value) {
		enabled = value;
	}

	/**
	 * 現在のスレッドのtraceを返します。無効化されている場合はnull
	 * (呼び出し側はnullチェックしてから{@code record}すること——
	 * 無効時にtrace生成すら行わないことで観測コストをゼロにする)。
	 */
	public static FragmentationTrace current() {
		if (!enabled) {
			return null;
		}
		FragmentationTrace trace = CURRENT.get();
		if (trace == null) {
			trace = new FragmentationTrace();
			CURRENT.set(trace);
		}
		return trace;
	}

	/** 現在のスレッドのtraceを破棄します(次のpageBreak/columnBreak前に呼ぶ)。 */
	public static void reset() {
		CURRENT.remove();
	}

	/**
	 * 別スレッドから引き継いだtraceを現在のスレッドへ明示的に紐付けます
	 * (2026-07-23新設、`processing.large-stack-thread`用)。
	 *
	 * <p>
	 * {@code current()}はスレッドごとに独立して新規traceを遅延生成する
	 * ため、実際のformat処理を専用スレッドへ委譲する構成では、呼び出し
	 * 元スレッドが{@code current()}で読もうとしたtraceと専用スレッドが
	 * 記録したtraceが別物になってしまう。呼び出し元スレッドで取得した
	 * traceをこのメソッドで専用スレッドへ明示的に引き継ぐことで、
	 * どちらのスレッドで実行されても同一のtraceオブジェクトに記録が
	 * 集約される(専用スレッド実行中は呼び出し元スレッドは
	 * {@code join()}で待機するため、同時書き込みは起きない)。
	 * {@code trace}がnull(無効時)の場合は何もしない。
	 * </p>
	 */
	public static void adopt(final FragmentationTrace trace) {
		if (trace != null) {
			CURRENT.set(trace);
		}
	}
}
