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
		if (log.containsOpaque(selfId + 1, endId - 1) || log.containsMulticol(selfId + 1, endId - 1)
				|| log.containsMixedFlow(selfId + 1, endId - 1, template.flow)) {
			// 縦横混在の再生はサブビルダー文脈が未設計のため模倣計測へ
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
		final double maxContent = usedLineExtent(wide.getLastPage().getContainer(), flow);
		final double minContent = usedLineExtent(narrow.getLastPage().getContainer(), flow);
		final double minPage = wide.getLastPage().getContainer().getContentSize();
		return new IntrinsicSizes(minContent, maxContent, minPage);
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

	private static double boxLineExtent(final IBox box, final WritingMode flow) {
		final boolean vertical = flow.isVertical();
		switch (box.getType()) {
		case TEXT_BLOCK:
			return ((TextBlockBox) box).getLineSize();
		case BLOCK: {
			final AbstractContainerBox block = (AbstractContainerBox) box;
			if (!block.isAutoLineSize()) {
				// 幅指定のブロックは指定幅がそのまま使用幅
				return vertical ? block.getHeight() : block.getWidth();
			}
			return block.getFrame().getFrameLineExtent(flow) + usedLineExtent(block.getContainer(), flow);
		}
		default:
			// 置換要素等は実寸
			return vertical ? box.getHeight() : box.getWidth();
		}
	}
}
