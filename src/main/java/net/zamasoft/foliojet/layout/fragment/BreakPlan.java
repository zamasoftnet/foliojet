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
 * @param columnLimit 対象段だけの内容限界。段宿主がなければnull
 */
public record BreakPlan(java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> chain, int depth,
		int index, ColumnLimit columnLimit) {
	/** 頁切断で、現在段の内容だけに適用する予約。owner寸法には適用しない。 */
	public record ColumnLimit(net.zamasoft.foliojet.layout.box.AbstractContainerBox owner, double reservation) {
		public double contentLimit(final net.zamasoft.foliojet.layout.box.AbstractContainerBox box,
				final double ownerExtent) {
			return box == this.owner && this.reservation != 0 ? ownerExtent - this.reservation : ownerExtent;
		}
	}

	public BreakPlan(final java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> chain,
			final int depth, final int index) {
		this(chain, depth, index, null);
	}

	public BreakPlan withColumnLimit(final ColumnLimit limit) {
		return new BreakPlan(this.chain, this.depth, this.index, limit);
	}

	/** 通常の箱分割へは継続チェーンを渡さず、内容限界だけを伝える。 */
	public BreakPlan withoutChain() {
		return this.columnLimit == null ? null : new BreakPlan(java.util.List.of(), 0, 0, this.columnLimit);
	}

	public double contentLimit(final net.zamasoft.foliojet.layout.box.AbstractContainerBox box,
			final double ownerExtent) {
		return this.columnLimit == null ? ownerExtent : this.columnLimit.contentLimit(box, ownerExtent);
	}
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
		return new BreakPlan(this.chain, this.depth, this.index + 1, this.columnLimit);
	}

	/**
	 * 現在の対象メンバー(flowStack[index+1])が最内の継続化レベルに
	 * なった場合の OpenTailShape 深さ(そのコンテナに残る開いた
	 * レベル数)。
	 */
	public int openTailDepth() {
		return this.depth - this.index - 1;
	}
}
