package net.zamasoft.foliojet.layout.box.content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange;
import net.zamasoft.foliojet.layout.fragment.OpenShape;

/**
 * flex行分割(Bug C、2026-08-07)の継続断片専用コンテナです。
 *
 * <p>
 * ページ確定後にbuilder状態をchainless再構築する経路
 * ({@code RootBuilder.resumeFragmentChain}の「収集不能な破断」分岐
 * ——{@code plan.selects()}がfalseの箱はここを通る)は、各flow子を
 * {@code BlockBuilder.startFlowBlock}経由で通常の逐次ブロック積み上げ
 * として再登録する。tableの行はこの経路を通らない
 * ({@code FlowContainer.restyleItem}のTABLE分岐は{@code replayFromSource}
 * /{@code addBound}のみで、セルの中身を汎用再構築しない)が、
 * {@code BoxType.BLOCK}のflexは同じ経路を素通りする——コンテナの
 * 一次元フロー帳簿(pageAxis)へcross軸位置を横流ししているだけの
 * flex itemにとって、この汎用再構築は主軸整列を破壊する
 * (実測: 強制分割した3枚のカードが階段状にずれた)。
 * </p>
 *
 * <p>
 * restyleが担う副作用(構造タグ・string-set・孫要素の再帰処理等)は
 * すべて{@code super.restyle}に任せて保ち、その直後にitemの位置だけを
 * {@link #anchor}で記録した値へ復元する——症状を治すのではなく、
 * 汎用機構が壊した結果を都度上書きで治す非対称な設計だが、汎用機構
 * 自体を触るより影響範囲が狭い。
 * </p>
 */
public final class FlexRowContainer extends FlowContainer {
	private Map<IFlowBox, Double> anchored;

	/**
	 * 現在{@link #flows}にある全itemのpageAxisを正としてスナップショット
	 * します。{@code FlexBox.split}が{@code addFlow}/{@code migrateFlowsFrom}
	 * で{@code cont}の組み立てを終えた直後(=restyleに触れられる前)に
	 * 一度だけ呼ぶ。
	 */
	public void anchorCurrent() {
		if (this.flows == null) {
			return;
		}
		if (this.anchored == null) {
			this.anchored = new HashMap<>();
		}
		for (final Flow f : this.flows) {
			this.anchored.put(f.box, f.pageAxis);
		}
	}

	@Override
	public void restyle(final BlockBuilder builder, final OpenShape shape, final boolean restyleAbsolutes,
			final List<SourceRange> prefix) {
		super.restyle(builder, shape, restyleAbsolutes, prefix);
		this.restoreAnchoredPageAxis();
	}

	private void restoreAnchoredPageAxis() {
		if (this.anchored == null || this.flows == null) {
			return;
		}
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow f = this.flows.get(i);
			final Double want = this.anchored.get(f.box);
			if (want != null && f.pageAxis != want.doubleValue()) {
				this.flows.set(i, new Flow(f.serial, f.box, want));
			}
		}
	}
}
