package net.zamasoft.foliojet.layout.box.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange;
import net.zamasoft.foliojet.layout.fragment.OpenShape;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 行分割(flex: Bug C、2026-08-07/grid: G6、2026-08-10)を持つコンテナの
 * item配置を、汎用restyle再構築から守るコンテナです(旧FlexRowContainer。
 * grid行分割の導入で中身をflex/grid共通へ一般化して改名)。
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
public final class RowSplitContainer extends FlowContainer {

	/**
	 * itemの確定配置のスナップショットです(2026-08-08にpageAxis単独から
	 * 拡張)。widthとheightはrestyle再構築が再解決で潰す
	 * ({@code startFlowBlock.calculateSize}が主軸寸法を包含幅へ、
	 * {@code endFlowBlock}が内容高へ)ため、位置と一緒に復元する。
	 */
	private record Anchor(double pageAxis, double width, double height, boolean restoreExtent) {
	}

	private Map<IFlowBox, Anchor> anchored;

	/**
	 * 現在{@link #flows}にある全itemの配置を正としてスナップショット
	 * します。{@code FlexBox.split}が{@code addFlow}/{@code migrateFlowsFrom}
	 * で{@code cont}の組み立てを終えた直後(=restyleに触れられる前)に
	 * 一度だけ呼ぶ。それ以外の再構築({@link #restyle}参照)は入口で
	 * 自己アンカーする。
	 */
	public void anchorCurrent() {
		this.anchorCurrent(this.flows == null ? 0 : this.flows.size());
	}

	/**
	 * 現在のitemをアンカーし、先頭{@code reflowablePrefixCount}個だけは
	 * restyle後の寸法を採用します。行の強制分割で新しく作った残余itemは
	 * 次の断片に合わせて縮みうる一方、後続行から丸ごと移送したitemは
	 * 分割前の確定寸法を保つ必要があります。後者までpercent寸法を再解決
	 * すると、膨らんだ継続箱を新しい基準に毎世代拡大するためです。
	 */
	public void anchorCurrent(final int reflowablePrefixCount) {
		if (this.flows == null) {
			return;
		}
		if (reflowablePrefixCount < 0 || reflowablePrefixCount > this.flows.size()) {
			throw new IllegalArgumentException("reflowablePrefixCount=" + reflowablePrefixCount
					+ ", flows=" + this.flows.size());
		}
		if (this.anchored == null) {
			this.anchored = new HashMap<>();
		}
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow f = this.flows.get(i);
			this.anchored.put(f.box,
					new Anchor(f.pageAxis, f.box.getWidth(), f.box.getHeight(), i >= reflowablePrefixCount));
		}
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
			this.anchorCurrent(0);
		}
		final boolean vertical = this.box.getBlockParams().flow.isVertical();
		// 空コンテナのrestyleではflows自体がまだ作られていない。
		final List<Flow> restyleFlows = this.flows == null ? List.of() : new ArrayList<>(this.flows);
		for (final Flow f : restyleFlows) {
			if (f == null) {
				continue;
			}
			final Anchor want = this.anchored.get(f.box);
			if (want != null && f.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox item) {
				item.prepareRestyleLineExtent(want.width(), want.height(), vertical);
			}
		}
		try {
			super.restyle(builder, shape, restyleAbsolutes, prefix);
		} finally {
			for (final Flow f : restyleFlows) {
				if (f == null) {
					continue;
				}
				if (f.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox item) {
					item.clearRestyleLineExtent();
				}
			}
		}
		this.restoreAnchoredPageAxis(builder);
	}

	private void restoreAnchoredPageAxis(final BlockBuilder builder) {
		if (this.anchored == null || this.flows == null) {
			return;
		}
		final double stackedEnd = this.contentEnd();
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow f = this.flows.get(i);
			final Anchor want = this.anchored.get(f.box);
			if (want == null) {
				continue;
			}
			if (f.pageAxis != want.pageAxis()) {
				this.flows.set(i, new Flow(f.serial, f.box, want.pageAxis()));
			}
			if (want.restoreExtent() && f.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox item
					&& (item.getWidth() != want.width() || item.getHeight() != want.height())) {
				item.restoreExtents(want.width(), want.height());
			}
		}
		this.pushDownOverlappingRows();
		if (builder != null) {
			// 縦積み再登録で進んだbuilderカーソルを、位置復元の前後の
			// 内容終端差だけ巻き戻す。この後のendFlowBlockがカーソルから
			// コンテナのheight/contentSizeを書き戻すため、ここを直せば
			// 箱高も追随する(yahoo.co.jp天気モジュールの熱中症指数〜
			// 雨雲レーダー間の余白)。2026-08-10までは全itemの寸法を
			// 復元する自己アンカー限定+アンカー時点の終端を基準にしていたが、
			// (1)分割継続断片でも縦積みが箱高を膨らませ後続内容を押し下げる
			// (grid行分割のrow-split-carryで実測: 2item×56ptの継続断片が
			// 112ptへ膨張)、(2)分割直後のアンカーはitem高が未確定(0)で
			// 基準にならない——ため、復元後の実測終端を基準に常時巻き戻す。
			// 再分割の帳簿を壊すのは分割残余itemの**寸法**まで復元すること
			// (上のループでrestoreExtent=false)であって、カーソルの巻き戻し
			// ではない
			final double trueEnd = this.contentEnd();
			final double delta = stackedEnd - trueEnd;
			// 押し下げ(下のpushDownOverlappingRows)で内容が伸びた場合は
			// 負のdelta=カーソル前進として同じ式で扱う(2026-08-19)
			if (delta != 0) {
				builder.setPageAxis(builder.getPageAxis() - delta);
				if (this.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox host) {
					// item側のendFlowBlockが親へ書いた縦積みのcontentSizeは
					// Math.maxの単調増加で残るため、代入で戻す(flex/grid共通)
					host.restoreContentExtent(trueEnd);
				}
			}
		}
	}

	/**
	 * <b>復元後、前の行の実内容に食い込む行を押し下げます</b>(2026-08-19)。
	 *
	 * <p>
	 * 行の強制分割で作った継続断片の先頭行(残余)は、再構築(restyle)で
	 * 元のスライス幾何より背が伸びることがある(smolcssで実測:
	 * 折り返しの違い等で100pt超)。後続の行のアンカーは分割時の幾何で
	 * 固定されているため、そのままでは残余の実内容が次の行に重なる。
	 * ここで行帳簿({@link net.zamasoft.foliojet.layout.box.RowSplitBox})の
	 * グループ単位で「前の行の実内容終端+帳簿上の行間」を下回る行を
	 * 一律に押し下げ、アンカーと帳簿startも同期する(帳簿は以後の
	 * 再分割の境界探索が使う)。
	 * </p>
	 */
	private void pushDownOverlappingRows() {
		if (!(this.box instanceof net.zamasoft.foliojet.layout.box.RowSplitBox rowSplit)
				|| !rowSplit.hasRowSplitLines()) {
			return;
		}
		final double[][] rows = rowSplit.rowLedgerSnapshot();
		if (rows == null || rows.length < 2) {
			return;
		}
		final boolean vertical = this.box.getBlockParams().flow.isVertical();
		final double[] newStarts = new double[rows.length];
		newStarts[0] = rows[0][2];
		// 行0の実内容終端
		double prevEnd = this.rowContentEnd(rows[0], vertical);
		boolean shifted = false;
		for (int r = 1; r < rows.length; ++r) {
			final double ledgerGap = Math.max(0, rows[r][2] - (rows[r - 1][2] + rows[r - 1][3]));
			final double required = prevEnd + ledgerGap;
			double start = rows[r][2];
			if (LayoutUtils.compare(start, required) < 0) {
				final double delta = required - start;
				final int from = (int) rows[r][0];
				final int count = (int) rows[r][1];
				for (int k = from; k < from + count && k < this.flows.size(); ++k) {
					final Flow f = this.flows.get(k);
					this.flows.set(k, new Flow(f.serial, f.box, f.pageAxis + delta));
					final Anchor a = this.anchored.get(f.box);
					if (a != null) {
						// 次のrestyleで押し下げが巻き戻らないようアンカーも更新
						this.anchored.put(f.box,
								new Anchor(a.pageAxis() + delta, a.width(), a.height(), a.restoreExtent()));
					}
				}
				start = required;
				shifted = true;
			}
			newStarts[r] = start;
			prevEnd = Math.max(prevEnd, this.rowContentEnd(new double[] { rows[r][0], rows[r][1], start, rows[r][3] },
					vertical));
		}
		if (shifted) {
			rowSplit.syncRowStarts(newStarts);
		}
	}

	/** 行({flow先頭index, item数, start, extent})の実内容終端。 */
	private double rowContentEnd(final double[] row, final boolean vertical) {
		final int from = (int) row[0];
		final int count = (int) row[1];
		double end = row[2];
		for (int k = from; k < from + count && k < this.flows.size(); ++k) {
			final Flow f = this.flows.get(k);
			end = Math.max(end, f.pageAxis + (vertical ? f.box.getWidth() : f.box.getHeight()));
		}
		return end;
	}
}
