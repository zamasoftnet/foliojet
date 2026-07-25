package net.zamasoft.foliojet.layout.rescue;

/**
 * 救済分割(visual rescue split)を実際に行うかどうかの、
 * <b>テスト専用の注入点</b>です(2026-07-25新設、増分4/5。
 * 増分8で位置づけを確定)。
 *
 * <h2>公開設定ではありません</h2>
 *
 * <p>
 * <b>恒久的なUAプロパティ・CSSプロパティは作りません</b>(答申§6の裁定)。
 * 最終的な発動条件が「既存のoverflow終端」だけなので、通常文書に恒久的な
 * 分岐設定を残す利点がないためです。本番の既定は{@link #ENABLED}で固定で、
 * これを{@link #DISABLED}にする経路はテストコードにしかありません
 * ({@link #scoped()}のみ)。
 * </p>
 *
 * <h2>なぜ撤去せず残すか(2026-07-25、増分8の判断)</h2>
 *
 * <p>
 * 答申§6-8は「productionを既定ONにし、一時的な切替を撤去」でした。
 * 既定ONは増分5で完了しています。切替の方は<b>撤去しません</b>——撤去すると
 * 次の検証が同時に失われ、それらは救済分割の中心的な合意事項だからです。
 * </p>
 *
 * <ul>
 * <li><b>「触っていない」ことの証明</b>: 絶対配置は救済しないという合意仕様
 * ({@code VisualRescuePlanner.isRescuablePos})を、
 * {@code VisualRescueSplitTest.testAbsoluteImageIsNotRescued}は
 * ENABLED/DISABLEDの<b>表示リスト完全一致</b>で固定しています。同じ主張を
 * 期待値の直書きで書くと「たまたま一致した」と区別できません。複数行段落を
 * 救済しないこと({@code testMultiLineParagraphIsSplitByLinesNotSliced})も
 * 同じ形です。</li>
 * <li><b>置き換えた対象の記録</b>: 救済は「はみ出したまま描画」という
 * 従来の終端<b>だけ</b>を置き換えます。{@code DISABLED}での出力は、その
 * 置き換え前が何だったか(情報が失われていたこと)を実行可能な形で
 * 残します({@code testDisabledPolicyKeepsLegacyOverflow}、
 * {@code testTallFloatWithoutRescueLosesTheRemainder})。</li>
 * <li><b>分岐表の従来行</b>: {@code FloatingsSplitPageAxisTest}の分岐表4→5
 * (first・分割不能はKEEPではみ出し許容)は、救済分割とは独立した
 * {@code FloatSplitPlan}の分類規則です。救済が上書きする前の行を
 * 分岐表テストに残しておかないと、分類規則そのものの回帰を検出できません。
 * </li>
 * </ul>
 *
 * <p>
 * 撤去してよいのは「消しても失われる検証がない足場」だけです。ここは
 * その条件を満たしません。<b>ただし増やしません</b>——救済の適用範囲や
 * 切り方を選ぶ設定をここへ足すのは禁止で、この型が持ってよい状態は
 * 「する / 従来どおりしない」の2値だけです。
 * </p>
 *
 * <h2>スレッド</h2>
 *
 * <p>
 * 複数変換の並行実行で混線しないよう{@link ThreadLocal}で保持します
 * ({@code ContinuationStats}の継続経路スタックと同じ理由)。
 * </p>
 */
public enum RescuePolicy {

	/**
	 * 救済しない(従来どおり、ページ先頭でもはみ出したまま描画する)。
	 * <b>テストからしか選べません</b>。
	 */
	DISABLED,

	/** 救済する(本番の既定)。 */
	ENABLED;

	/**
	 * 既定値です。増分5で有効化し、増分6/7で全経路へ広げ、増分8で確定
	 * しました。設定で変える手段はありません。
	 */
	public static final RescuePolicy DEFAULT = ENABLED;

	private static final ThreadLocal<RescuePolicy> CURRENT = ThreadLocal.withInitial(() -> DEFAULT);

	/** 現在のスレッドの方針です。 */
	public static RescuePolicy current() {
		return CURRENT.get();
	}

	/** 救済を行う方針であればtrueを返します。 */
	public static boolean isEnabled() {
		return current() == ENABLED;
	}

	/**
	 * このスレッドの方針を一時的に差し替えます(テスト専用)。
	 *
	 * <pre>
	 * try (RescuePolicy.Scope scope = RescuePolicy.DISABLED.scoped()) {
	 * 	// 従来の挙動
	 * }
	 * </pre>
	 *
	 * @return 復帰用のスコープ
	 */
	public Scope scoped() {
		final RescuePolicy previous = CURRENT.get();
		CURRENT.set(this);
		return () -> {
			if (previous == DEFAULT) {
				// ThreadLocalを残さない(プールされたスレッドでのリーク防止)
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		};
	}

	/** {@link RescuePolicy#scoped()}の復帰ハンドルです。 */
	public interface Scope extends AutoCloseable {
		void close();
	}
}
