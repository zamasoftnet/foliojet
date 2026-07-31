package net.zamasoft.foliojet.layout.box.params;

/**
 * CSS Box Alignmentの値です(Grid G5a、2026-07-31——
 * consult-codex-2026-07-31-grid-g5.txt Q2)。Gridのitem/content配置
 * サブセット: {@code AUTO}はself系のみ(コンテナ値を参照)、
 * {@code NORMAL}はレイアウトモード既定(G5のGridでは{@code STRETCH}へ
 * 解決)。baseline・space-*・safe/unsafeはサブセット外(宣言無効)。
 * 既存の{@link Align}(ブロック/表のauto margin解決)とは別物。
 *
 * @author MIYABE Tatsuhiko
 */
public enum BoxAlignment {
	AUTO, NORMAL, START, CENTER, END, STRETCH;

	/**
	 * used valueへ解決します(答申Q2: capture時に書き換えず、bind時に
	 * 凍結値から毎回同じ解決を得る——再生決定性)。
	 *
	 * @param self      itemのself値(auto可)
	 * @param container コンテナのitems値(autoは来ない)
	 * @return START/CENTER/END/STRETCHのいずれか
	 */
	public static BoxAlignment resolve(final BoxAlignment self, final BoxAlignment container) {
		final BoxAlignment effective = self == AUTO ? container : self;
		return effective == NORMAL || effective == AUTO ? STRETCH : effective;
	}
}
