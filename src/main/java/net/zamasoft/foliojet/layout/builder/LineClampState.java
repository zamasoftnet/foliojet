package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.params.BlockParams;

/**
 * {@code line-clamp}の行数の状態です(css-overflow-4 §5、2026-08-29)。
 *
 * <p>
 * 打ち切るブロックの{@link LayoutContext.Flow}に1つ置き、その内側の
 * テキストブロック({@code TextBuilder})が行を確定するたびに数える。
 * 仕様の「ブロックコンテナの行ボックス」は入れ子のブロックの行も含む
 * ので、同じビルダーのフロースタック上にある最も近い打ち切りブロックを
 * {@link #find}で探し、深さに関係なく同じ状態へ数える。浮動体・絶対配置・
 * flex/gridアイテムは別ビルダーになるため数えない(仕様どおりin-flowの
 * 行だけ)。
 * </p>
 *
 * <p>
 * N行目は「後続の内容がある」と分かるまで切らない(N行ちょうどの段落に
 * 省略記号を付けてはいけない)。N行目を閉じた時点では{@link #setPending}
 * で切り方だけ預かり、N+1行目以降が捨てられた瞬間({@link #truncatePending})
 * に実行する。捨てられる行が無ければ預かったまま終わる=省略記号なし。
 * </p>
 */
public final class LineClampState {
	private final int limit;

	private int count;

	private Runnable pending;

	public LineClampState(final int limit) {
		this.limit = limit;
	}

	/**
	 * 最も近い打ち切りブロックの状態を返します(無ければnull)。
	 */
	public static LineClampState find(final LayoutContext context) {
		for (int i = context.getFlowCount() - 1; i >= 0; --i) {
			final LayoutContext.Flow flow = context.getFlow(i);
			if (flow == null || flow.box == null) {
				continue;
			}
			final BlockParams params = flow.box.getBlockParams();
			if (params.lineClamp > 0) {
				if (flow.lineClamp == null) {
					flow.lineClamp = new LineClampState(params.lineClamp);
				}
				return flow.lineClamp;
			}
		}
		return null;
	}

	/** N行に達していて、これ以上の行は捨てるべきか。 */
	public boolean exhausted() {
		return this.count >= this.limit;
	}

	/** 行を1つ数えます。ちょうどN行目ならtrue。 */
	public boolean countLine() {
		++this.count;
		return this.count == this.limit;
	}

	/** N行目の切り方を預かります(後続が出たら実行)。 */
	public void setPending(final Runnable truncate) {
		this.pending = truncate;
	}

	/** 後続の内容が出たので、預かっていたN行目の切り詰めを実行します。 */
	public void truncatePending() {
		final Runnable truncate = this.pending;
		if (truncate != null) {
			this.pending = null;
			truncate.run();
		}
	}
}
