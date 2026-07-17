package net.zamasoft.foliojet.layout.fragment;

/**
 * 破断の継続化計画です(C1d-C。読み取り専用)。
 *
 * <p>
 * pageBreak の事前検分が承認した祖先チェーン(flowStack[1..] のボックス列)と
 * 全体深さ、および現在の降下位置を表します。split カスケードはこれを
 * 読んで対象ボックスだけを継続化し、断片は {@link SplitResult.Frame} の
 * 返り値で親へ伝播します。<b>計画は出力を保持しない</b>(mutable な
 * collector の引数渡し化は side channel の場所を変えるだけ — 外部レビュー)。
 * </p>
 *
 * @param chain 承認されたチェーン(外→内。chain.get(i) = flowStack[i+1])
 * @param depth 継続全体の深さ(破断時の flowStack の要素数)
 * @param index 現在の降下位置(chain のインデックス)
 */
public record BreakPlan(java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> chain, int depth,
		int index) {
	/**
	 * box が現在の降下対象(チェーンの次のメンバー)なら true。
	 */
	public boolean selects(final net.zamasoft.foliojet.layout.box.IBox box) {
		return this.index < this.chain.size() && this.chain.get(this.index) == box;
	}

	/**
	 * 一段内側へ降下した計画を返します。
	 */
	public BreakPlan next() {
		return new BreakPlan(this.chain, this.depth, this.index + 1);
	}

	/**
	 * 現在の対象メンバー(flowStack[index+1])が最内の継続化レベルに
	 * なった場合の LegacyOpenTail 深さ(そのコンテナに残る開いた
	 * レベル数)。
	 */
	public int legacyDepth() {
		return this.depth - this.index - 1;
	}
}
