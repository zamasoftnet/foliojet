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

	/**
	 * itemの確定配置のスナップショットです(2026-08-08にpageAxis単独から
	 * 拡張)。widthとheightはrestyle再構築が再解決で潰す
	 * ({@code startFlowBlock.calculateSize}が主軸寸法を包含幅へ、
	 * {@code endFlowBlock}が内容高へ)ため、位置と一緒に復元する。
	 */
	private record Anchor(double pageAxis, double width, double height) {
	}

	private Map<IFlowBox, Anchor> anchored;

	/**
	 * アンカー時点の内容進行の終端(コンテンツ原点基準、2026-08-08)。
	 * 汎用restyleはitemを縦積みで再登録するためbuilderカーソルが
	 * 「Σitem高」まで進む——item座標を復元しても、カーソル(と、そこから
	 * endFlowBlockが書き戻すコンテナのheight/contentSize)は縦積みの値の
	 * ままになり、flexコンテナの箱高が内容高のほぼ2倍に膨らんでいた
	 * (yahoo.co.jp天気モジュールの熱中症指数〜雨雲レーダー間の余白)。
	 * 復元時にカーソルを差分だけ巻き戻すための基準値。
	 */
	private double anchoredContentEnd;

	/**
	 * 寸法(width/height)も復元するか。自己アンカー(丸ごと移動後の
	 * 再構築——itemの寸法は変わらないのが正)でのみtrue。分割継続
	 * ({@link #anchorCurrent})では、以降の再分割がitemの寸法を正当に
	 * 縮めるためfalse(trueにすると再分割の帳簿が壊れ、複数ページの
	 * flexが2ページへ潰れる——0510-flex/float-itemで実測)。
	 */
	private boolean restoreExtents;

	/**
	 * 現在{@link #flows}にある全itemの配置を正としてスナップショット
	 * します。{@code FlexBox.split}が{@code addFlow}/{@code migrateFlowsFrom}
	 * で{@code cont}の組み立てを終えた直後(=restyleに触れられる前)に
	 * 一度だけ呼ぶ。それ以外の再構築({@link #restyle}参照)は入口で
	 * 自己アンカーする。
	 */
	public void anchorCurrent() {
		this.anchorCurrent(false);
	}

	private void anchorCurrent(final boolean withExtents) {
		if (this.flows == null) {
			return;
		}
		if (this.anchored == null) {
			this.anchored = new HashMap<>();
		}
		this.restoreExtents = withExtents;
		for (final Flow f : this.flows) {
			this.anchored.put(f.box, new Anchor(f.pageAxis, f.box.getWidth(), f.box.getHeight()));
		}
		this.anchoredContentEnd = this.contentEnd();
	}

	/** 現在のflowsの内容進行の終端(pageAxis+item内容高の最大)。 */
	private double contentEnd() {
		final boolean vertical = this.box != null && this.box.getBlockParams().flow.isVertical();
		double end = 0;
		for (final Flow f : this.flows) {
			end = Math.max(end, f.pageAxis + (vertical ? f.box.getWidth() : f.box.getHeight()));
		}
		return end;
	}

	@Override
	public void restyle(final BlockBuilder builder, final OpenShape shape, final boolean restyleAbsolutes,
			final List<SourceRange> prefix) {
		if (this.anchored == null) {
			// 分割継続断片(FlexBox.splitが組み立て直後にanchorCurrent済み)
			// 以外のrestyle——ページ跨ぎの丸ごと移動など——でも、restyleに
			// 触れられる前の現在位置を正としてその場でアンカーする
			// (2026-08-08。restyle前のflowsはFlexBuilder.placeRowが確定
			// させた主軸整列を保持している)。この経路は寸法も復元する
			// (startFlowBlock/endFlowBlockの再解決がwidth:autoを包含幅へ、
			// height:autoを内容高へ潰す——yahoo.co.jpの順位バッジ)
			this.anchorCurrent(true);
		}
		super.restyle(builder, shape, restyleAbsolutes, prefix);
		this.restoreAnchoredPageAxis(builder);
	}

	private void restoreAnchoredPageAxis(final BlockBuilder builder) {
		if (this.anchored == null || this.flows == null) {
			return;
		}
		if (this.restoreExtents && builder != null) {
			// 縦積み再登録で進んだbuilderカーソルを、アンカー時点の内容
			// 進行との差分だけ巻き戻す。この後のendFlowBlockがカーソルから
			// コンテナのheight/contentSizeを書き戻すため、ここを直せば
			// 箱高も追随する。分割継続(restoreExtents=false)は以降の
			// 再分割がカーソルを正として進むため触らない
			final double stackedEnd = this.contentEnd();
			final double delta = stackedEnd - this.anchoredContentEnd;
			if (delta > 0) {
				builder.setPageAxis(builder.getPageAxis() - delta);
				if (this.box instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox flex) {
					// item側のendFlowBlockが親へ書いた縦積みのcontentSizeは
					// Math.maxの単調増加で残るため、代入で戻す
					flex.restoreContentExtent(this.anchoredContentEnd);
				}
			}
		}
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow f = this.flows.get(i);
			final Anchor want = this.anchored.get(f.box);
			if (want == null) {
				continue;
			}
			if (f.pageAxis != want.pageAxis()) {
				this.flows.set(i, new Flow(f.serial, f.box, want.pageAxis()));
			}
			if (this.restoreExtents && f.box instanceof net.zamasoft.foliojet.layout.box.impl.FlexItemBox item
					&& (item.getWidth() != want.width() || item.getHeight() != want.height())) {
				item.restoreExtents(want.width(), want.height());
			}
		}
	}
}
