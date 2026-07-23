package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;

/**
 * {@link PlainFlowTailProgram}に対する実行時の観測を蓄積し、予測どおり
 * だったかを検証する読み取り専用のshadow比較です(2026-07-22新設、B6a0
 * ——codex設計相談で確定、{@code docs/history/2026-07-22-b6a0
 * -plain-flow-tail-shadow-design.md}参照)。
 *
 * <p>
 * {@link ResumeProgramTrace}(既存、PAGE/COLUMNの`resumeFrame`本体が
 * 実際に選んだ操作と予告op列を突き合わせる)と対だが、こちらは
 * legacy `OpenChain`実行系(現行の{@code FlowContainer.restyle()}内の
 * 再帰、{@code FlowContainer.java:1511}の`OpenChain`分岐・:1453の
 * `OpenText`終端判定)の**内部**を観測する——{@code ResumeProgramTrace}
 * が予告する最終opは`restyle()`を呼ぶところまでで、その内部で実際に
 * 辿られたbox列までは検証していない(既存のshadow不一致ゼロが証明する
 * のは「呼ぶところまでは予測どおり」だけで「内部の各boxが一直線
 * だったか」ではない、というcodexの指摘に対応する)。
 * </p>
 */
public final class PlainFlowTailTrace {
	/**
	 * 予告された残存tailの終端(index==levels().size()の位置)へ実際に
	 * どう到達したかです(2026-07-22、591文書コーパス実測で判明した
	 * 複数の正当な経路をenumへ統合——旧実装は経路ごとに別々のboolean
	 * フィールドだったが、TABLEケース発見で3つ目が必要になり、
	 * 排他性をenumで表現する形へ整理した)。
	 */
	public enum TerminalKind {
		/** まだ終端へ到達していない。 */
		NONE,
		/**
		 * 終端がTextBlockBoxで、実際にopenとして継続された
		 * ({@code FlowContainer.java}のTEXT_BLOCKケース、:1453付近)。
		 */
		OPEN_TEXT,
		/**
		 * 終端がTextBlockBoxではなくBLOCK型だったため、既存の
		 * {@code FlowContainer.restyle()}の仕様どおりCLOSEDとして
		 * 扱われた(BLOCKケース、:1520付近の{@code lastFlow == holder
		 * && shape instanceof OpenText}分岐——元々「lastFlow &&
		 * OpenText の末尾も閉じたボックス(次段は Closed)」という
		 * 既存コメントがあった、既存の意図した仕様)。
		 */
		CLOSED_AS_BLOCK,
		/**
		 * 終端がTABLE型だったため、{@code lastFlow}/{@code shape}を
		 * 一切見ない既存の無条件`builder.addBound(tableBox)`経路を
		 * 通った(TABLEケース、:1577付近)。改ページ契約ルール1が
		 * 「表の行境界を除き」としているとおり、表は元々この
		 * plain-flowチェーンのモデルの対象外——591文書コーパス実測
		 * (`0140-table-separate/060-CELL_WIDTH.html`)で発見。
		 */
		BOUND_AS_TABLE,
		/**
		 * 終端がBLOCK型だが、幹(root)と書字方向(縦横)が異なるため、
		 * {@code lastFlow}/{@code shape}を見る手前で
		 * {@code builder.addBound(containerBox)}が発火した
		 * (BLOCKケース冒頭の書字方向判定、:1513付近——
		 * {@link #CLOSED_AS_BLOCK}とは別のearly-out分岐)。
		 * codex設計相談で「3分類は閉じていない」と指摘され発見。
		 * この分岐を実際に踏む文書の構築は3回試行して未達
		 * (2026-07-22、`docs/history/2026-07-22-b6a0-bound-as-float-
		 * terminal-kind.md`参照)——書字方向不一致ボックスは改ページ契約
		 * (§5.10ルール3)でatomic化されており、plain-flowチェーンの終端に
		 * 現れる入力自体が極めて作りにくい。コードリーディングによる
		 * 正しさ確認で足りるとして到達実証は打ち切り(2026-07-23、
		 * ユーザー方針「不可能・過剰なことをゴールにしない」の適用)。
		 */
		BOUND_AS_WRITING_MODE_MISMATCH_BLOCK,
		/**
		 * 終端がREPLACED型(置換要素、{@code FlowReplacedBox}は
		 * {@code IFlowBox}を実装するためlastFlowになりうる)だった
		 * ため、{@code lastFlow}/{@code shape}を一切見ない既存の
		 * 無条件`builder.addBound(replacedBox)`経路を通った(REPLACED
		 * ケース、:1599付近、float以外の場合)。{@link #BOUND_AS_TABLE}
		 * と同型——codex設計相談で「明示的な第4候補」と指摘され発見。
		 * この分岐を実際に踏む文書の構築は3回試行して未達(2026-07-22、
		 * {@link #BOUND_AS_WRITING_MODE_MISMATCH_BLOCK}と同じ経緯)——
		 * 非floatの置換要素がplain-flowチェーンの終端(改ページ点の
		 * 開いた祖先チェーン末尾)に来る入力は極めて作りにくい。
		 * コードリーディングによる正しさ確認で足りるとして到達実証は
		 * 打ち切り(2026-07-23)。
		 */
		BOUND_AS_REPLACED,
		/**
		 * 終端がfloatだったため({@code Floating}は{@code this.flows}に
		 * 追加されず、{@code lastFlow}は常に{@code Flow}型——floatが
		 * lastFlowになることは構造的にありえない)、{@code lastFlow}/
		 * {@code shape}を見る判定そのものを通らない既存の無条件
		 * {@code ((Floating) holder).restyle(builder)}経路(BLOCK
		 * ケース末尾のfloat分岐・REPLACEDケース末尾のfloat分岐、どちらも
		 * 同型)を通った。414文書コーパス実測
		 * (`0110-clear/avoid-before-image.html`・
		 * `0110-clear/avoid-before-br.html`)で発見——floatされた画像が
		 * plain-flowチェーンの終端に来ると、既存の5分類のどれにも
		 * 一致せず{@link #NONE}のまま観測が終わっていた。
		 */
		BOUND_AS_FLOAT;
	}

	private final PlainFlowTailProgram program;
	private int nextIndex = 0;
	private TerminalKind terminalKind = TerminalKind.NONE;
	private String mismatch = null;

	private PlainFlowTailTrace(final PlainFlowTailProgram program) {
		this.program = program;
	}

	public static PlainFlowTailTrace of(final PlainFlowTailProgram program) {
		return new PlainFlowTailTrace(program);
	}

	public PlainFlowTailProgram program() {
		return this.program;
	}

	/**
	 * {@code OpenChain}再帰で1段のboxが実際に開かれた際に呼びます
	 * (配線後は{@code FlowContainer.restyle()}の:1511付近から)。
	 */
	public void observeChainBox(final AbstractContainerBox box) {
		if (this.terminalKind != TerminalKind.NONE) {
			this.recordMismatch("終端へ到達済みのはずが、さらにchain boxを観測しました: " + box.getClass());
			return;
		}
		if (this.nextIndex >= this.program.levels().size()) {
			this.recordMismatch("予告された残存tailの深さ(" + this.program.levels().size() + ")を超えてchain boxを観測しました");
			return;
		}
		final OpenPathSnapshot.FragmentSignature expected = this.program.levels().get(this.nextIndex)
				.fragmentSignature();
		final OpenPathSnapshot.FragmentSignature actual = OpenPathSnapshot.FragmentSignature.from(box);
		if (!expected.equals(actual)) {
			this.recordMismatch(
					"chain box[" + this.nextIndex + "]のsignatureが不一致です: expected=" + expected + ", actual=" + actual);
		}
		++this.nextIndex;
	}

	/**
	 * 終端の開きテキストへ実際に到達した際に呼びます(配線後は
	 * {@code FlowContainer.restyle()}の:1453付近から)。
	 */
	public void observeOpenText() {
		this.observeTerminal(TerminalKind.OPEN_TEXT);
	}

	/**
	 * 終端(予告された残存tailの最後、{@code shape}が{@code OpenText}の
	 * 位置)が実際にはTextBlockBoxではなくBLOCK型だったため、既存の
	 * {@code FlowContainer.restyle()}の仕様どおりCLOSEDとして扱われた
	 * (openとしては継続されなかった)際に呼びます(配線後は
	 * {@code FlowContainer.java}のBLOCKケース、:1520付近から)。これは
	 * 実装バグではなく既存の意図した仕様——詳細は{@link TerminalKind
	 * #CLOSED_AS_BLOCK}参照。
	 */
	public void observeTerminalClosedAsBlock() {
		this.observeTerminal(TerminalKind.CLOSED_AS_BLOCK);
	}

	/**
	 * 終端がTABLE型だったため、既存の無条件{@code addBound}経路を
	 * 通った際に呼びます(配線後は{@code FlowContainer.java}のTABLE
	 * ケース、:1577付近から)。改ページ契約が表を元々対象外としている
	 * ことの表れ——詳細は{@link TerminalKind#BOUND_AS_TABLE}参照。
	 */
	public void observeTerminalBoundAsTable() {
		this.observeTerminal(TerminalKind.BOUND_AS_TABLE);
	}

	/**
	 * 終端がBLOCK型で、幹と書字方向が異なるため、{@code lastFlow}/
	 * {@code shape}を見る手前で{@code addBound}が発火した際に呼びます
	 * (配線後は{@code FlowContainer.java}のBLOCKケース冒頭の書字方向
	 * 判定分岐から)。詳細は{@link TerminalKind
	 * #BOUND_AS_WRITING_MODE_MISMATCH_BLOCK}参照。
	 */
	public void observeTerminalBoundAsWritingModeMismatchBlock() {
		this.observeTerminal(TerminalKind.BOUND_AS_WRITING_MODE_MISMATCH_BLOCK);
	}

	/**
	 * 終端がREPLACED型だったため、既存の無条件{@code addBound}経路を
	 * 通った際に呼びます(配線後は{@code FlowContainer.java}のREPLACED
	 * ケースから)。詳細は{@link TerminalKind#BOUND_AS_REPLACED}参照。
	 */
	public void observeTerminalBoundAsReplaced() {
		this.observeTerminal(TerminalKind.BOUND_AS_REPLACED);
	}

	/**
	 * 終端がfloatだったため、既存の無条件{@code ((Floating) holder)
	 * .restyle(builder)}経路を通った際に呼びます(配線後は
	 * {@code FlowContainer.java}のBLOCK/REPLACEDケース末尾のfloat分岐
	 * から)。詳細は{@link TerminalKind#BOUND_AS_FLOAT}参照。
	 */
	public void observeTerminalBoundAsFloat() {
		this.observeTerminal(TerminalKind.BOUND_AS_FLOAT);
	}

	private void observeTerminal(final TerminalKind kind) {
		if (this.terminalKind != TerminalKind.NONE) {
			this.recordMismatch("終端を複数回観測しました(" + this.terminalKind + " の後に " + kind + ")");
			return;
		}
		this.terminalKind = kind;
	}

	private void recordMismatch(final String reason) {
		if (this.mismatch == null) {
			this.mismatch = reason;
		}
	}

	/**
	 * resume()正常終了後に呼びます。2026-07-22の配線試行で
	 * {@code allPlainFlow()==true}時に例外化する設計にしたところ、
	 * 591文書コーパスの複数カテゴリ(avoid-break・表ヘッダー/フッター・
	 * 書字方向混在の表ラッパー)およびgolden回帰テストで不一致が実発生し、
	 * 「legacy OpenChain実行系が実際にOpenTextへ到達する経路」について
	 * こちらの理解が不完全だったと判明した(詳細:
	 * {@code docs/history/2026-07-22-b6a0-wiring-attempt-and-revert.md}
	 * )。そのため**このメソッドは一切例外化しない**——
	 * {@code allPlainFlow()}の真偽によらず観測結果を{@link Result}として
	 * 返すだけに留め、呼び出し側が{@code ContinuationStats}等へ記録できる
	 * ようにする(codexの助言どおり「いきなり削除/強制ではなく
	 * reachability検証から始める」を、当初の想定より厳密に適用した形)。
	 */
	public Result verifyComplete() {
		final boolean lengthMatches = this.nextIndex == this.program.levels().size();
		final boolean reachedTerminal = this.terminalKind != TerminalKind.NONE;
		final boolean consistent = this.mismatch == null && lengthMatches && reachedTerminal;
		return new Result(consistent, this.program.allPlainFlow(), this.mismatch, this.nextIndex,
				this.program.levels().size(), this.terminalKind);
	}

	/** {@link #verifyComplete()}の観測結果です(2026-07-22、{@link TerminalKind}導入で非例外化)。 */
	public record Result(boolean consistent, boolean allPlainFlow, String mismatch, int observedLevels,
			int expectedLevels, TerminalKind terminalKind) {
	}
}
