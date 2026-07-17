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
	 * ブロック間の改ページ禁止(avoid)による押し戻しの結果です。
	 *
	 * @param resumeIndex  ループを再開するインデックス(ループの ++i 前提)
	 * @param newPageLimit 押し戻し後の切断線
	 */
	public record AvoidPushback(int resumeIndex, double newPageLimit) {
	}

	/**
	 * ブロック間の改ページ禁止をチェックし、必要なら押し戻し先を返します。
	 * 接している(隙間なく積まれた)先行ブロック群を遡り、avoid 指定が
	 * あれば安全な境界まで切断線を引き上げます。切断可能な浮動ボックスが
	 * 切断線に跨る場合は禁止を解除します(旧実装の挙動を忠実に維持)。
	 *
	 * @param i                  現在のフローインデックス
	 * @param pageLimit          現在の切断線
	 * @param flowPageStarts     各フローのページ方向始端
	 * @param flowPageExtents    各フローのページ方向寸法
	 * @param avoidBefore        各フローの break-before:avoid
	 * @param avoidAfter         各フローの break-after:avoid
	 * @param flowPageEndFrames  各フロー(ブロックのみ)のページ方向終端フレーム幅
	 * @param floatPageStarts    各浮動体のページ方向始端(なければ null)
	 * @param floatPageExtents   各浮動体のページ方向寸法
	 * @param floatUncut         各浮動体が切断不能(置換要素または avoid)
	 * @return 押し戻しが必要なら AvoidPushback、不要なら null
	 */
	public static AvoidPushback avoidPushback(final int i, final double pageLimit, final double[] flowPageStarts,
			final double[] flowPageExtents, final boolean[] avoidBefore, final boolean[] avoidAfter,
			final double[] flowPageEndFrames, final double[] floatPageStarts, final double[] floatPageExtents,
			final boolean[] floatUncut) {
		int beforeFlows = 1;
		boolean breakAvoid = avoidBefore[i];
		int beforeIndex = i - 1;
		// 接しているブロックで改ページ禁止されていることを確認
		for (int j = i - 1; j >= 0; --j) {
			final double beforeBottom = flowPageStarts[j] + flowPageExtents[j];
			if (LayoutUtils.compare(beforeBottom, flowPageStarts[i]) < 0) {
				if (j == i - 1) {
					breakAvoid = false;
				}
				break;
			}
			beforeFlows++;
			beforeIndex = j;
			if (avoidAfter[j]) {
				breakAvoid = true;
			}
		}
		if (breakAvoid && floatPageStarts != null) {
			// 切断可能な浮動ボックスがある場合は改ページを区切る。
			// 不変条件: 切断可能な crossing float は keep 後退を解除し
			// (float 自身は splitFloatings が独立に分割する)、切断不能
			// (置換要素 / page-break-inside:avoid = floatUncut)な
			// crossing float は keep 後退を妨げない。FlowCutterTest と
			// 統合フィクスチャ float-split-in-chain(cuttable)/
			// float-uncut-before-prefix(uncut)の対で固定
			for (int k = 0; k < floatPageStarts.length; ++k) {
				if (LayoutUtils.compare(floatPageStarts[k], pageLimit) >= 0) {
					breakAvoid = false;
					break;
				}
				if (LayoutUtils.compare(floatPageStarts[k] + floatPageExtents[k], pageLimit) <= 0) {
					continue;
				}
				if (floatUncut[k]) {
					continue;
				}
				breakAvoid = false;
				break;
			}
		}
		if (!breakAvoid) {
			return null;
		}
		assert beforeFlows >= 2;
		final double newPageLimit = flowPageStarts[beforeIndex] - LayoutUtils.THRESHOLD * 2
				+ flowPageExtents[beforeIndex] - flowPageEndFrames[beforeIndex];
		return new AvoidPushback(i - beforeFlows, newPageLimit);
	}

	/**
	 * 主ループで切断先が見つからなかった場合の後段判定です。
	 * 旧実装 889-919 の分岐を忠実に写しています。
	 *
	 * @param flags          IPageBreakableBox.FLAGS_* のビット和
	 * @param lastOrphan     切断線以下に収まる最後のフローの直後のインデックス
	 * @param pageInnerSize  ボックスのページ方向内寸
	 * @param lastFlowBottom 最後のフローの底辺位置
	 * @param prevPageSize   元の(調整前の)切断線
	 * @return 判定結果(CutTail / KeepFloats / MoveWithFloats のいずれか)
	 */
	public static PreDecision tailDecide(final byte flags, final int lastOrphan, final double pageInnerSize,
			final double lastFlowBottom, final double prevPageSize) {
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
			// 切断
			return new PreDecision.CutTail(prevPageSize);
		}
		if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
			// ページの先頭(FLAGS_LASTを無視する)
			if (LayoutUtils.compare(pageInnerSize, lastFlowBottom) > 0) {
				// 自然の高さより高いボックスは切断
				return new PreDecision.CutTail(prevPageSize);
			}
			return new PreDecision.KeepFloats(prevPageSize);
		}
		if (lastOrphan == 0) {
			// 切断線より上がなければ次ページに送る
			return new PreDecision.MoveWithFloats(prevPageSize);
		}
		// 末尾で切る
		return new PreDecision.CutTail(prevPageSize);
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
