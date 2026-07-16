package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * フローコンテナの切断判定です(M4-A2)。FlowContainer.splitPageAxis の
 * 判定部分をボックスに触れない純関数として抽出したものです。
 *
 * <p>
 * 注意: 主ループの「子への分割試行」は成功時に子を変異させるため、
 * 完全な三相分離(純粋な選択相)は子分割の副作用フリー化(M6 の
 * フラグメント化)後に完成する。ここでは前段・機会走査の判定のみを純化し、
 * 実行ループの骨格は FlowContainer に残る。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlowCutter {
	/**
	 * 主ループに入る前の切断判定の結果です。各値は実行時の切断線
	 * (旧実装の pageLimit / prevPageSize の使い分け)を保持します。
	 */
	public sealed interface PreDecision {
		/** 先頭を切断します(cutHead)。 */
		record CutHead(double atLimit) implements PreDecision {
		}

		/** 全体を前ページに残します(フロートのみ分割検討)。 */
		record KeepFloats(double atLimit) implements PreDecision {
		}

		/** 全体を次ページへ送ります(フロート分割なし)。 */
		record MoveAll() implements PreDecision {
		}

		/** 全体を次ページへ送ります(フロートも分割検討)。 */
		record MoveWithFloats(double atLimit) implements PreDecision {
		}

		/** 末尾で切断します(cutTail)。 */
		record CutTail(double atLimit) implements PreDecision {
		}

		/** 主ループへ進みます(必要なら切断線を調整済み)。 */
		record Proceed(double adjustedPageLimit) implements PreDecision {
		}
	}

	private FlowCutter() {
		// utility
	}

	/**
	 * 主ループ前の切断判定を行います。旧実装の比較演算子の向き
	 * (切断線と辺が一致した場合に移動しない=改ページ最小化ポリシー)を
	 * 忠実に維持しています。
	 *
	 * @param pageLimit     ボックスの内上辺から切断線までの距離
	 * @param pageSize      ボックスのページ方向寸法
	 * @param pageInnerSize ボックスのページ方向内寸
	 * @param frameStart    ページ方向始端のフレーム幅
	 * @param flags         IPageBreakableBox.FLAGS_* のビット和
	 * @param hasFlows      通常フローの子が存在するか
	 * @return 判定結果
	 */
	public static PreDecision preDecide(final double pageLimit, final double pageSize, final double pageInnerSize,
			final double frameStart, final byte flags, final boolean hasFlows) {
		if (LayoutUtils.compare(pageLimit, 0) <= 0) {
			// 切断線が内上辺以上にある場合
			// ** <= を使うのは、切断線と上辺が一致した場合に移動しないため **
			if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// 先頭を切断
				return new PreDecision.CutHead(pageLimit);
			}
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// ページ先頭にある場合
				if (LayoutUtils.compare(frameStart, 0) > 0) {
					// 上辺があれば切断
					return new PreDecision.CutHead(pageLimit);
				}
				// 前ページに残す
				return new PreDecision.KeepFloats(pageLimit);
			}
			// 次に送る
			return new PreDecision.MoveAll();
		}
		if ((flags & (IPageBreakableBox.FLAGS_SPLIT | IPageBreakableBox.FLAGS_LAST)) == 0
				&& LayoutUtils.compare(pageLimit, pageSize) >= 0) {
			// 自動改ページで切断線が内底辺以下にある場合
			// ** >= を使うのは、切断線と底辺が一致した場合に移動しないため **
			// 前ページに残す
			return new PreDecision.KeepFloats(pageLimit);
		}
		double adjusted = pageLimit;
		if ((flags & (IPageBreakableBox.FLAGS_SPLIT | IPageBreakableBox.FLAGS_LAST)) == 0
				&& LayoutUtils.compare(pageLimit, pageInnerSize) >= 0) {
			adjusted = pageInnerSize - LayoutUtils.THRESHOLD * 2;
		}
		if (!hasFlows) {
			// 通常のフローが存在しない場合
			if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// 切断
				return new PreDecision.CutTail(pageLimit);
			}
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// 高さがなければ残す
				if (LayoutUtils.compare(pageInnerSize, 0) > 0) {
					return new PreDecision.CutTail(pageLimit);
				}
				return new PreDecision.KeepFloats(pageLimit);
			}
			// 高さがあれば次に送る
			if ((flags & IPageBreakableBox.FLAGS_LAST) != 0 || LayoutUtils.compare(pageSize, 0) > 0) {
				return new PreDecision.MoveWithFloats(pageLimit);
			}
			return new PreDecision.KeepFloats(pageLimit);
		}
		return new PreDecision.Proceed(adjusted);
	}

	/**
	 * 切断線以下に収まる最後のフローの直後のインデックスを返します
	 * (0=収まるフローなし、length=全フローが収まる)。
	 *
	 * @param flowBottoms 各フローの実効底辺位置
	 * @param pageLimit   切断線
	 * @return 前ページに残せる最後のフローの直後のインデックス
	 */
	public static int lastOrphan(final double[] flowBottoms, final double pageLimit) {
		int lastOrphan;
		for (lastOrphan = flowBottoms.length - 1; lastOrphan >= 0; --lastOrphan) {
			if (LayoutUtils.compare(flowBottoms[lastOrphan], pageLimit) <= 0) {
				break;
			}
		}
		return lastOrphan + 1;
	}
}
