package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * テキストブロックの行境界での切断判定です。orphans/widows 制約を満たす
 * 切断位置を決める純関数で、ボックスには触れません。
 *
 * <p>
 * widows/orphans は対象範囲の高さを line-height で割った仮想行数を基準に
 * 判定します。両方を満たせない場合は全体を次ページへ送りますが、
 * ページ先頭(first)では orphans を無視し、少なくとも1行を前ページに残します。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LineCutter {
	/**
	 * 切断判定の結果です。
	 */
	public sealed interface Decision {
		/** 切断線が底辺以下 — 全体が前ページに収まります。 */
		Decision KEEP = new Keep();

		/** 全体を次ページへ送ります。 */
		Decision MOVE = new Move();

		record Keep() implements Decision {
		}

		record Move() implements Decision {
		}

		/**
		 * 行 lastLine の直後で切断します。
		 *
		 * @param lastLine 前ページに残す最後の行のインデックス
		 */
		record CutAfter(int lastLine) implements Decision {
		}
	}

	private LineCutter() {
		// utility
	}

	/**
	 * 実質的に高さのある行が2行未満か(=行境界での切断点が存在しない)を
	 * 返します。
	 *
	 * <p>
	 * この場合{@link #decide}はフラグメント先頭で<b>無条件に</b>
	 * {@link Decision#KEEP}を返します——つまり行分割では一切前進できず、
	 * 容量を超えていればはみ出したまま描かれます。救済分割(2026-07-25)は
	 * その「非進行点」だけを置き換えるため、同じ判定をここから使います
	 * (規則の定義を二重に持たないため)。
	 * </p>
	 *
	 * @param lineStarts 各行の上辺位置
	 * @param lineEnds   各行の底辺位置
	 * @return 実質1行以下ならtrue
	 */
	public static boolean singleEffectiveLine(final double[] lineStarts, final double[] lineEnds) {
		int nonZeroLines = 0;
		for (int i = 0; i < lineStarts.length; ++i) {
			if (lineStarts[i] > 0 || lineEnds[i] - lineStarts[i] > 0) {
				if (++nonZeroLines >= 2) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 切断位置を判定します。
	 *
	 * @param pageLimit  ボックス上辺から切断線までの距離
	 * @param pageSize   ボックスのページ方向寸法
	 * @param lineHeight 仮想行数の計算に使う行高さ
	 * @param orphans    前ページに残すべき最小仮想行数
	 * @param widows     次ページへ送るべき最小仮想行数
	 * @param first      ボックスがページ先頭にある(FLAGS_FIRST)
	 * @param lineStarts 各行の上辺位置
	 * @param lineEnds   各行の底辺位置
	 * @return 切断判定
	 */
	public static Decision decide(final double pageLimit, final double pageSize, final double lineHeight,
			final int orphans, final int widows, final boolean first, final double[] lineStarts,
			final double[] lineEnds) {
		if (LayoutUtils.compare(pageLimit, pageSize) >= 0) {
			// 切断線が底辺以下にある場合は移動なし
			return Decision.KEEP;
		}

		if (!singleEffectiveLine(lineStarts, lineEnds)) {
			if (!first && LayoutUtils.compare(pageLimit, lineEnds[0]) < 0) {
				// 切断線が最初の行の底辺より上にある場合は全部移動
				return Decision.MOVE;
			}
		} else {
			// １行だけの場合
			return first ? Decision.KEEP : Decision.MOVE;
		}

		// 前ページに残すことができる最後の行を求める
		int lastOrphan;
		for (lastOrphan = lineEnds.length - 1; lastOrphan > 0; --lastOrphan) {
			if (LayoutUtils.compare(pageLimit, lineEnds[lastOrphan]) >= 0) {
				break;
			}
		}

		// 'widows'による制約
		while (lastOrphan >= 0) {
			final double virHeight = pageSize - lineEnds[lastOrphan];
			final int virWidows = (int) Math.round(virHeight / lineHeight);
			if (virWidows >= widows) {
				break;
			}
			--lastOrphan;
		}
		if (lastOrphan == -1) {
			if (!first) {
				return Decision.MOVE;
			}
			lastOrphan = 0;
		}
		if (!first) {
			// 'orphans'による制約
			final int virOrphans = (int) Math.round(lineEnds[lastOrphan] / lineHeight);
			if (virOrphans < orphans) {
				return Decision.MOVE;
			}
		}
		return new Decision.CutAfter(lastOrphan);
	}
}
