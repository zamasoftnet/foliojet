package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 実レイアウトによる固有寸法の計測です(M2c)。
 *
 * <p>
 * 対象ブロックの子イベント範囲を scratch ページへ2回再生し
 * (行幅∞= max-content、行幅0= min-content)、結果のボックス木から
 * 使用行寸法を読み取ります。旧2パスの模倣計測(IntrinsicMeasurer)と
 * 違い、実際のレイアウト規則そのもので測るため近似の乖離がありません。
 * 範囲が特定できない場合(アンカー無効・Opaque 含み)は null を返し、
 * 呼び出し側が模倣計測へフォールバックします。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class MeasuredIntrinsics {
	/**
	 * max-content 測定に使う「十分に広い」行幅です。
	 */
	private static final double INFINITE = 1e6;

	private MeasuredIntrinsics() {
		// pure functions
	}

	/**
	 * 対象ブロックの固有寸法を実レイアウトで測ります。
	 *
	 * @param log      ソースログ(なければ null 可)
	 * @param box      対象ブロック(アンカー=SourceAnchor)
	 * @param template 書体等を引き継ぐ計算済みパラメータ
	 * @param ua       ユーザーエージェント
	 * @return 実測固有寸法。範囲を特定できなければ null(模倣へフォールバック)
	 */
	public static IntrinsicSizes of(final LayoutSource log, final AbstractContainerBox box, final BlockParams template,
			final UserAgent ua) {
		if (log == null) {
			return null;
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox) {
			// 絶対配置は模倣計測へフォールバック(E-6増分4e、2026-07-24):
			// 増分4e以前はアンカーがOpaque記録でendOfが引けず構造的にnull
			// (=模倣計測)だった。recipe記録化(4e)は本文rangeのbind適格化が
			// 目的であり、M2c実測の適用は寸法変化(出力変化)を伴うため
			// 挙動不変制約で見送る——適用する場合は別増分でgolden再基準化と
			// AbsoluteBlockBox.DeferredBindのsizesスナップショットのbind時
			// 再計測化を同時に行うこと
			return null;
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox
				|| box instanceof net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox) {
			// リストマーカー(外部・内部とも)は模倣計測へフォールバック:
			// マーカー内容の末尾空白は実レイアウトでは行末処理で落ちる
			// (=実測は真の max-content)が、現行のマーカー配置は「末尾空白の
			// advance がマーカーと本文の隙間を作る」ことに依存している。
			// 明示ギャップによるマーカー配置の再設計(M3)までは旧
			// セマンティクスを維持する
			return null;
		}
		final long selfId = box.getSourceAnchor();
		if (selfId < 0) {
			return null;
		}
		final long endId = log.endOf(selfId);
		if (endId < 0 || endId <= selfId + 1) {
			return null;
		}
		if (log.containsTable(selfId + 1, endId - 1)) {
			// 表を含む範囲は模倣計測へフォールバック(表セット、2026-07-30:
			// recipe記録化以前は表がOpaque記録で下のcontainsOpaqueが捕捉
			// していた)。この実測(∞幅/0幅のscratchページへの再生)を表へ
			// 適用すると、%指定セル・auto列幅がscratch幅基準で解決されて
			// max-contentが発散し、shrink-to-fitの幅が壊れる(G-1実測:
			// 0070-table-layout/float-in-auto-4.html の4フロートが
			// 376/414.5/276/216 → 全て500pt=ページ幅)。実測の適用拡大は
			// 出力を変えるため、E-6増分4eの絶対配置と同じく恒久ゲート。
			return null;
		}
		if (!log.isIntact(selfId + 1, endId - 1)) {
			// **範囲が疎になっていたら模倣計測へ**(2026-07-27新設)。
			//
			// `compact()`は水位より前から「開いている(未対応の)Start」だけを
			// 残すので、破断時にまだ開いていた要素は**Startだけ残って中身が
			// 消えた**状態になる。後で閉じると`endOf()`は疎な保持列の上で
			// もっともらしい終端を返し、`contains*`の各ゲートも保持列しか
			// 見ないので何も検出しない。
			//
			// その先の`SourceReplayer.measure`は**フォールバックを持たず
			// 例外を投げる**(「範囲は呼び出し側が生きているうちに確定させる
			// 契約」)。ここで確かめないと変換ごと止まる。
			//
			// 同型の欠陥を`RootBuilder.stampRanges`で実際に踏んだ
			// (10万文書に15件、`吸収済み再生範囲が失われました`)。こちらは
			// 再現を見つけていないが、機序は同一で、**戻り値nullという
			// 安全な逃げ道が既にある**ので塞いでおく。
			return null;
		}
		if (log.containsOpaque(selfId + 1, endId - 1) || log.containsAbsolute(selfId + 1, endId - 1)
				|| log.containsMulticol(selfId + 1, endId - 1)
				|| log.containsMixedFlow(selfId + 1, endId - 1, template.flow)) {
			// 縦横混在の再生はサブビルダー文脈が未設計のため模倣計測へ。
			// 絶対配置を含む範囲は増分4e以前のOpaque記録時代と同じく模倣計測へ
			// (recipe記録化による実測の適用拡大は挙動不変制約で見送り)
			return null;
		}
		if (log.containsGrid(selfId + 1, endId - 1)) {
			// Grid G1d(2026-07-31): 実測(scratch再生)はGridBuilderが
			// 活性化しトラック配置で測る一方、TwoPass本経路の計測・bindは
			// G0(単一列)のまま——混ぜると幅・高さが食い違う。itemの
			// TwoPass計測が入る(G3)まで模倣計測へフォールバック
			return null;
		}
		if (log.containsFlex(selfId + 1, endId - 1)) {
			// Flex F0c(2026-08-02): F0時点では再生も単一列縮退で挙動同一
			// だが、F1dでscratch再生側だけFlexBuilderが活性化する——
			// Grid G1dと同じ食い違いを先回りでfail closedに塞ぐ
			return null;
		}
		if (log.containsFloat(selfId + 1, endId - 1)) {
			// フロートを含む内容は模倣計測へ: max-content では並置フロートの
			// 幅は累積するが、無限幅 scratch の事後読み取りは各フロートの
			// 位置・整列に依存せず累積幅を復元できない(正確な解は配置中の
			// 行占有トラッカー — フロート再設計(M6c)で扱う)
			return null;
		}
		final WritingMode flow = template.flow;
		final boolean vertical = flow.isVertical();
		// max-content: 行幅∞で折り返しなしに組む
		final MeasurePageGenerator wide = SourceReplayer.measure(log, selfId + 1, endId - 1, template, ua, INFINITE,
				INFINITE, false);
		// min-content: 行幅0で全ての分割機会で折る
		final MeasurePageGenerator narrow = SourceReplayer.measure(log, selfId + 1, endId - 1, template, ua,
				vertical ? INFINITE : 0, vertical ? 0 : INFINITE, false);
		if (wide.getLastPage() == null || narrow.getLastPage() == null) {
			return null;
		}
		if (containsMulticolBox(wide.getLastPage().getContainer())) {
			// **auto高さの段組を含む範囲は模倣計測へ**(2026-08-21、掃過
			// seed 615921)。上のcontainsMulticolゲートは固定寸法段組
			// (MulticolumnBlockBox)しか索引に載らず、auto段組
			// (FlowBlockBoxのcolumn-count)を素通ししていた。M2c実測で
			// 測ると段数倍に膨らんだ最小内容寸法がcolumnInflatedフラグ
			// なしで返り、shrinkToFitの段組クランプが効かない——縦書きの
			// 段組内float:rightが行頭より前(紙面の外)へ置かれた。模倣計測
			// (IntrinsicMeasurer)はフラグを立てるので既存クランプが働く
			return null;
		}
		final double maxContent = usedLineExtent(wide.getLastPage().getContainer(), flow);
		final double minContent = usedLineExtent(narrow.getLastPage().getContainer(), flow);
		final double minPage = wide.getLastPage().getContainer().getContentSize();
		return new IntrinsicSizes(minContent, maxContent, minPage);
	}

	/** 組み上がった木にauto段組(段数2以上の容器)が含まれるかを返します。 */
	private static boolean containsMulticolBox(final Container container) {
		final boolean[] found = { false };
		container.eachFlowBox(box -> {
			if (found[0]) {
				return;
			}
			if (box instanceof AbstractContainerBox block) {
				// auto高さの段組はgetColumnCount()が1のままなので指定段数で見る
				if (block.getColumnCount() >= 2 || block.getBlockParams().columns.count >= 2
						|| containsMulticolBox(block.getContainer())) {
					found[0] = true;
				}
			}
		});
		return found[0];
	}

	/**
	 * 組み上がったボックス木の使用行寸法(内容が実際に占める行方向の幅)を
	 * 返します。scratch ページ上では AUTO 幅のブロックは利用可能幅に
	 * 伸びるため、テキスト行・置換要素・固定幅ブロックの実寸から
	 * 読み取ります(マージンボックスの max-content 測定でも使用)。
	 */
	public static double usedLineExtent(final Container container, final WritingMode flow) {
		final double[] max = { 0 };
		container.eachFlowBox(box -> max[0] = Math.max(max[0], boxLineExtent(box, flow)));
		if (container instanceof FlowContainer fc) {
			// フロートは使用幅に直接寄与する
			max[0] = Math.max(max[0], fc.floatingsLineExtent(flow));
		}
		return max[0];
	}

	/**
	 * 行方向の指定マージンの合計を返します(絶対値のみ。%・auto は
	 * 固有寸法の計測では 0 として扱う)。
	 */
	private static double specifiedLineMargins(final AbstractContainerBox block, final WritingMode flow) {
		final net.zamasoft.foliojet.layout.box.params.Insets margin = block.getBlockParams().frame.margin;
		final net.zamasoft.foliojet.layout.box.params.LengthType abs = net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
		if (flow.isVertical()) {
			return (margin.getTopType() == abs ? margin.getTop() : 0)
					+ (margin.getBottomType() == abs ? margin.getBottom() : 0);
		}
		return (margin.getLeftType() == abs ? margin.getLeft() : 0)
				+ (margin.getRightType() == abs ? margin.getRight() : 0);
	}

	private static double boxLineExtent(final IBox box, final WritingMode flow) {
		final boolean vertical = flow.isVertical();
		switch (box.getType()) {
		case TEXT_BLOCK:
			return ((TextBlockBox) box).getLineSize();
		case BLOCK: {
			final AbstractContainerBox block = (AbstractContainerBox) box;
			// マージンは使用値ではなく指定値から復元する: scratch 上の
			// ブロックは利用可能幅の解決(制限しすぎの調整)で終端側の
			// 使用マージンが吸収されるため。%・auto は固有寸法では 0 扱い
			final double margins = specifiedLineMargins(block, flow);
			if (!block.isAutoLineSize()) {
				// 幅指定のブロックは指定幅がそのまま使用幅
				return margins + (vertical ? block.getHeight() : block.getWidth());
			}
			double inner = block.getFrame().getBorderLineExtent(flow) + usedLineExtent(block.getContainer(), flow);
			// min-width/max-width(絶対長のみ)でクランプする(2026-08-08、
			// css-sizingのouter contribution)。scratchページ上のauto幅ブロックは
			// 利用可能幅の解決でmin-widthが実寸へ反映されないことがあり、
			// min-width:100pxの入れ子grid(NHKナビのセクションピル)の
			// ラッパーがテキスト幅までflex-shrinkされてピル背景が隣へ
			// 重なっていた。%・calcは基準未確定のため数えない
			final net.zamasoft.foliojet.layout.box.params.BlockParams bp = block.getBlockParams();
			final double bb = bp.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
					? block.getFrame().getBorderLineExtent(flow)
					: 0;
			if (bp.maxSize.getLineType(flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				inner = Math.min(inner, Math.max(0, bp.maxSize.getLineLength(flow) - bb)
						+ block.getFrame().getBorderLineExtent(flow));
			}

			if (bp.minSize.getLineType(flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				inner = Math.max(inner, Math.max(0, bp.minSize.getLineLength(flow) - bb)
						+ block.getFrame().getBorderLineExtent(flow));
			}
			return margins + inner;
		}
		default:
			// 置換要素等は実寸
			return vertical ? box.getHeight() : box.getWidth();
		}
	}
}
