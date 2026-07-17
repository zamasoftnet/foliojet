package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 表のページ方向切断の判定です(C4-T1。FlowCutter/LineCutter と同じ
 * 「判定の純化」を表に適用)。
 *
 * <p>
 * TableBox / TableRowGroupBox の切断ループが行っていた判定
 * (ヘッダ・フッタの予約、グループ間・行間の改ページ禁止、縦横混在、
 * 全残し/全移動)を、ボックス木から切り離した純関数として固定します。
 * ループ自体(子 split の結果に依存する走行)はボックス側に残ります —
 * FlowContainer と FlowCutter の分担と同型。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TableCutter {
	private TableCutter() {
		// utility
	}

	/**
	 * ページ先頭なら全体を残し(KEEP)、そうでなければ全体を送ります
	 * (MOVE)。空の表・ヘッダとフッタが収まらない場合・切断先が
	 * 見つからなかった場合の共通の縮退です。
	 */
	public static SplitResult keepOrMoveAll(final byte flags) {
		return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
	}

	/**
	 * 表の切断線からヘッダ・フッタ等の「改ページしない部分」を差し引きます。
	 *
	 * @param pageLimit      表の外辺からの切断線
	 * @param boxPageExtent  表のページ方向外寸
	 * @param framePageStart ページ方向始端側のフレーム幅
	 * @param framePageEnd   ページ方向終端側のフレーム幅
	 * @param marginPageEnd  ページ方向終端側のマージン幅
	 * @param headerSize     ヘッダ行グループのページ寸(なければ負)
	 * @param footerSize     フッタ行グループのページ寸(なければ負)
	 * @return 本体行グループに使える切断線
	 */
	public static double reserveNonBreakable(double pageLimit, final double boxPageExtent,
			final double framePageStart, final double framePageEnd, final double marginPageEnd,
			final double headerSize, final double footerSize) {
		final double over = boxPageExtent - pageLimit;
		pageLimit -= framePageStart;
		if (headerSize >= 0) {
			pageLimit -= headerSize;
		}
		if (footerSize >= 0) {
			pageLimit -= footerSize;
			pageLimit -= framePageEnd;
		} else if (over > 0 && LayoutUtils.compare(over, marginPageEnd) < 0) {
			// 境界が下マージンに差し掛かった場合は切る
			pageLimit -= marginPageEnd;
		}
		return pageLimit;
	}

	/**
	 * 行グループ境界の改ページ禁止です。前グループの break-after /
	 * 当グループの break-before に加え、境界に接する行(前グループ末尾
	 * 行の break-after・当グループ先頭行の break-before)も見ます。
	 *
	 * @param beforeGroupBreakAfter   前グループの page-break-after
	 * @param groupBreakBefore        当グループの page-break-before
	 * @param beforeGroupLastRowAfter 前グループ末尾行の page-break-after
	 *                                (行がなければ AUTO)
	 * @param groupFirstRowBefore     当グループ先頭行の page-break-before
	 *                                (行がなければ AUTO)
	 */
	public static boolean groupBreakAvoid(final PageBreakMode beforeGroupBreakAfter, final PageBreakMode groupBreakBefore,
			final PageBreakMode beforeGroupLastRowAfter, final PageBreakMode groupFirstRowBefore) {
		return beforeGroupBreakAfter == PageBreakMode.AVOID || groupBreakBefore == PageBreakMode.AVOID
				|| beforeGroupLastRowAfter == PageBreakMode.AVOID || groupFirstRowBefore == PageBreakMode.AVOID;
	}

	/**
	 * 行境界の改ページ禁止です。行の break-after/before に加え、前行の
	 * 縦連結セル(rowspan)による禁止を判定します。
	 *
	 * <p>
	 * 通常は「切断可能(page-break-inside:auto かつ書字方向一致)な
	 * セルはスキップし、連結が次行へ伸びるセルがあれば禁止」。
	 * ページ先頭の 1-2 行目(i==1 かつ FLAGS_FIRST)だけは特例で、
	 * 連結セルのうち書字方向が違うものだけが禁止を立てる(一致する
	 * 連結は禁止を解除しつつ走査を続ける — 旧実装の挙動を忠実に維持)。
	 * </p>
	 *
	 * @param i                    当行のインデックス
	 * @param pageFirst            FLAGS_FIRST(ページ先頭)
	 * @param beforeRowBreakAfter  前行の page-break-after
	 * @param rowBreakBefore       当行の page-break-before
	 * @param beforeCellCuttable   前行の各セルが切断可能
	 *                             (inside==AUTO かつ書字方向一致)
	 * @param beforeCellExtended   前行の各セルの連結が次行へ伸びる
	 * @param beforeCellFlowMatch  前行の各セルの書字方向が表と一致
	 */
	public static boolean rowBreakAvoid(final int i, final boolean pageFirst, final PageBreakMode beforeRowBreakAfter,
			final PageBreakMode rowBreakBefore, final boolean[] beforeCellCuttable, final boolean[] beforeCellExtended,
			final boolean[] beforeCellFlowMatch) {
		boolean breakAvoid = beforeRowBreakAfter == PageBreakMode.AVOID || rowBreakBefore == PageBreakMode.AVOID;
		if (!breakAvoid && (i != 1 || !pageFirst)) {
			// 連結されたセルによる改ページ禁止
			for (int j = 0; j < beforeCellExtended.length; ++j) {
				if (beforeCellCuttable[j]) {
					continue;
				}
				if (beforeCellExtended[j]) {
					breakAvoid = true;
					break;
				}
			}
		} else if (i == 1 && pageFirst) {
			// ページ先頭の1-2行目で連結されたセルがある場合の特例
			for (int j = 0; j < beforeCellExtended.length; ++j) {
				if (!beforeCellExtended[j]) {
					continue;
				}
				if (beforeCellFlowMatch[j]) {
					breakAvoid = false;
					continue;
				}
				// 書字方向が違えば必ず改ページしない
				breakAvoid = true;
				break;
			}
		}
		return breakAvoid;
	}

	/**
	 * 書字方向が表と異なるセルを含む行は切断・移送せず前に残します
	 * (縦横混在の分割は未対応)。
	 *
	 * @param cellFlowMatch 当行の各セルの書字方向が表と一致
	 */
	public static boolean mixedFlowKeep(final boolean[] cellFlowMatch) {
		for (final boolean match : cellFlowMatch) {
			if (!match) {
				return true;
			}
		}
		return false;
	}

	/**
	 * ページ先頭での行フラグを計算します。先頭行、または先頭行と
	 * セルを共有する(rowspan で連結された)行には FLAGS_FIRST_ROW を
	 * 立て、2行目以降は FLAGS_FIRST を落とします。
	 *
	 * @param xflags      FLAGS_FIRST/FLAGS_SPLIT でマスク済みのフラグ
	 * @param i           当行のインデックス
	 * @param linkedToTop 当行が先頭行とセルを共有する
	 */
	public static byte firstRowFlags(byte xflags, final int i, final boolean linkedToTop) {
		if ((xflags & IPageBreakableBox.FLAGS_FIRST) == 0) {
			return xflags;
		}
		if (i > 0) {
			xflags ^= IPageBreakableBox.FLAGS_FIRST;
			if (linkedToTop) {
				xflags |= IPageBreakableBox.FLAGS_FIRST_ROW;
			}
		} else {
			xflags |= IPageBreakableBox.FLAGS_FIRST_ROW;
		}
		return xflags;
	}
}
