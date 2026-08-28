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
	 * {@code Split}を返すはずの子splitの結果から、期待する型の残余を
	 * 取り出します。契約違反は{@link ContinuationInvariantViolationException}
	 * にします(2026-07-25。従来は素のcastで、契約違反が
	 * {@code ClassCastException}という原因の読めない形で現れていた。
	 * {@code TableRowBox.forcedCellRemainder}と同じ扱いへ揃えたもの——
	 * 正常経路のロジックは不変)。
	 *
	 * @param <T>      期待する残余の型
	 * @param result   子splitの結果
	 * @param type     期待する残余の型
	 * @param context  例外メッセージに載せる呼び出し元の説明
	 * @return 残余
	 */
	public static <T extends IPageBreakableBox> T requireSplitRemainder(final SplitResult result, final Class<T> type,
			final String context) {
		if (result instanceof SplitResult.Split(final IPageBreakableBox remainder) && type.isInstance(remainder)) {
			return type.cast(remainder);
		}
		throw new ContinuationInvariantViolationException(
				context + " must return Split(" + type.getSimpleName() + ") but returned " + result);
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
			// 連結されたセルによる改ページ禁止。rowspanが跨ぐ行間はavoid相当
			// (説明書4550の仕様)。cuttable=著者が明示的にpage-break-inside:auto
			// を宣言したセル(TableCellPos.breakInsideDeclaredAuto)だけが
			// オプトアウトできる——UA既定のセルavoid撤去(2026-08-27)後も、
			// 既定のrowspanブロックはまとめて持ち越す
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
	 * セル断片の寸法・フレーム状態です(C4-T2。ブロックの
	 * {@link FragmentState} に相当するが、最小寸法の残量計算が異なる —
	 * セルは実寸から引き、ブロックは min(指定, 実寸) から引く)。
	 *
	 * @param nextSize    継続断片の指定寸法
	 * @param nextMinSize 継続断片の最小寸法
	 * @param nextFrame   継続断片のフレーム(始端側を落とした形)
	 * @param prevFrame   前断片のフレーム(終端側を落とした形)
	 */
	public record CellFragmentState(net.zamasoft.foliojet.layout.box.params.Dimension nextSize,
			net.zamasoft.foliojet.layout.box.params.Dimension nextMinSize,
			net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame,
			net.zamasoft.foliojet.layout.part.AbsoluteRectFrame prevFrame) {
	}

	/**
	 * セル断片の状態を計算します(純関数。旧 TableCellBox.splitPage の
	 * 縦横鏡像 約30行×2 の共通化)。
	 *
	 * <p>
	 * 縦書き分岐の Dimension.create の引数順は、旧実装では横書き・
	 * FragmentState と非対称(width スロットに交差軸指定)だったが、
	 * FragmentState と同じ規約(width=ページ方向残量)に正規化した。
	 * 実寸は行分割(setWidth/setHeight)と列幅機構が支配するため出力は
	 * 不変(vert-cell-specified-pagebreak の交差検証で同一を確認 —
	 * PLAN サイクル18)。
	 * </p>
	 *
	 * @param vertical   縦書きか
	 * @param size       指定寸法
	 * @param minSize    最小寸法
	 * @param frame      切断前のフレーム
	 * @param pageExtent 切断前のページ方向内寸(縦書き=width)
	 * @param pageLimit  切断位置(内辺から)
	 */
	public static CellFragmentState cellFragmentState(final boolean vertical,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame, final double pageExtent,
			final double pageLimit) {
		final net.zamasoft.foliojet.layout.box.params.Dimension nextSize, nextMinSize;
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame, prevFrame;
		final double rest = Math.max(0, pageExtent - pageLimit);
		if (vertical) {
			nextSize = size.getWidthType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(rest, size.getHeight(),
							net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE, size.getHeightType())
					: size;
			nextMinSize = minSize.getWidthType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(rest, minSize.getHeight(),
							net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE, minSize.getHeightType())
					: minSize;
			nextFrame = frame.cut(true, false, true, true);
			prevFrame = frame.cut(true, true, true, false);
		} else {
			nextSize = size.getHeightType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(size.getWidth(), rest,
							size.getWidthType(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: size;
			nextMinSize = minSize.getHeightType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(minSize.getWidth(), rest,
							minSize.getWidthType(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: minSize;
			nextFrame = frame.cut(false, true, true, true);
			prevFrame = frame.cut(true, true, false, true);
		}
		return new CellFragmentState(nextSize, nextMinSize, nextFrame, prevFrame);
	}

	/**
	 * 表断片のフレームです(C4-T2)。ヘッダは全断片で繰り返されるため
	 * 継続断片も始端フレームを保持し、フッタも同様に前断片が終端
	 * フレームを保持します。
	 *
	 * @param prevFrame 前断片のフレーム
	 * @param nextFrame 継続断片のフレーム
	 */
	public record TableFragmentFrames(net.zamasoft.foliojet.layout.part.AbsoluteRectFrame prevFrame,
			net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame) {
	}

	/**
	 * 表断片のフレームを計算します(純関数。旧 splitTableBox の
	 * フレーム切断判定)。
	 *
	 * @param vertical     縦書きか
	 * @param repeatHeader ヘッダ行グループがある(全断片で繰り返す)
	 * @param repeatFooter フッタ行グループがある(全断片で繰り返す)
	 * @param frame        切断前のフレーム
	 */
	public static TableFragmentFrames tableFragmentFrames(final boolean vertical, final boolean repeatHeader,
			final boolean repeatFooter, final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame) {
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame = repeatHeader ? frame
				: (vertical ? frame.cut(true, false, true, true) : frame.cut(false, true, true, true));
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame prevFrame = repeatFooter ? frame
				: (vertical ? frame.cut(true, true, true, false) : frame.cut(true, true, false, true));
		return new TableFragmentFrames(prevFrame, nextFrame);
	}

	/**
	 * 行切断の前置判定です(C4-T3。旧 TableRowBox.split の先頭部)。
	 * KEEP/MOVE で確定するか、null なら主処理(セル分割)へ進みます。
	 *
	 * @param pageFirst            FLAGS_FIRST(ページ先頭)
	 * @param firstRow             FLAGS_FIRST_ROW(先頭行または先頭行と連結)
	 * @param pageLimit            切断線(行の上端から)
	 * @param rowPageSize          行のページ寸
	 * @param rowInsideAvoid       行の page-break-inside: avoid
	 * @param cellPageExtents      各セルのページ寸(rowspan で行より
	 *                             大きいことがある)
	 * @param cellFlowMatch        各セルの書字方向が表と一致
	 * @param cellInsideAvoid      各セルの page-break-inside: avoid
	 * @param cellCollapsedAtStart 各セルが上部境界なしかつ高さゼロ
	 *                             (分割を諦める)
	 * @param fragmentCapacity     フラグメンテナ(ページ/段)のページ方向内寸
	 *                             (不明なら-1。{@code AutoBreakMode.fragmentCapacity})
	 */
	public static SplitResult rowPreDecide(final boolean pageFirst, final boolean firstRow, final double pageLimit,
			final double rowPageSize, final boolean rowInsideAvoid, final double[] cellPageExtents,
			final boolean[] cellFlowMatch, final boolean[] cellInsideAvoid, final boolean[] cellCollapsedAtStart,
			final double fragmentCapacity) {
		if (!pageFirst) {
			// ページ頭ではない場合
			if (LayoutUtils.compare(pageLimit, 0) < 0) {
				// 切断線より下にある場合
				return SplitResult.MOVE;
			}
			if (LayoutUtils.compare(pageLimit, rowPageSize) >= 0) {
				// 切断線より上にある場合。連結されたセルによる高さを考慮する
				// ため、全てのセルの高さをチェック
				boolean leave = true;
				for (final double extent : cellPageExtents) {
					if (LayoutUtils.compare(pageLimit, extent) < 0) {
						leave = false;
						break;
					}
				}
				if (leave) {
					// 移動なし
					return SplitResult.KEEP;
				}
			}
			boolean breakAvoid = false;
			if (rowInsideAvoid) {
				// 行の改ページ禁止
				breakAvoid = true;
			} else {
				for (int i = 0; i < cellFlowMatch.length; ++i) {
					// 書字方向が違う場合は改ページ禁止
					if (!cellFlowMatch[i]) {
						return SplitResult.MOVE;
					}
					if (cellInsideAvoid[i]) {
						// セルの改ページ禁止
						breakAvoid = true;
					}
				}
			}
			if (breakAvoid && !firstRow) {
				return SplitResult.MOVE;
			}
			// 行境界の切断を優先する(2026-08-27、css-break/Chrome準拠。UA既定の
			// セルavoid撤去と対)。切断線が掛かった行が新しいフラグメンテナに
			// 丸ごと収まるなら、行の内部では切らず行ごと持ち越す。丸ごとでも
			// 収まらない行(1行ラッパー表の巨大な行等)は、送っても結局内部で
			// 切ることになり送り元に大きな空白だけが残るため、その場で切る
			if (!firstRow && fragmentCapacity > 0
					&& LayoutUtils.compare(rowPageSize, fragmentCapacity) <= 0) {
				return SplitResult.MOVE;
			}
			return null;
		}
		// ページ頭の場合
		if (LayoutUtils.compare(pageLimit, rowPageSize) >= 0) {
			// rowspanによる連結のはみ出しがあったとしても、あとの処理で切る
			return SplitResult.KEEP;
		}
		// 書字方向が違う場合は移動しない
		if (mixedFlowKeep(cellFlowMatch)) {
			return SplitResult.KEEP;
		}
		// 上部境界がなく、高さがゼロのセルが存在すれば分割を諦める
		for (final boolean collapsed : cellCollapsedAtStart) {
			if (collapsed) {
				return SplitResult.KEEP;
			}
		}
		return null;
	}

	/**
	 * 明示的な行間強制改ページの位置です(C4-T3)。
	 *
	 * @param rowGroup  切断直前の本体行グループのインデックス
	 * @param row       切断直前の行のインデックス(グループ末尾なら -1)
	 * @param breakMode 指定された改ページ種別(PAGE / COLUMN)
	 */
	public record ForceBreakAt(int rowGroup, int row, PageBreakMode breakMode) {
	}

	/**
	 * 自動テーブルの明示的な強制改ページ(行・行グループの
	 * page-break-before/after: page|column)のうち、切断線までに現れる
	 * 最初のものを探します(純関数。旧 BreakableBuilder.
	 * firstTableForceBreak の走査)。切断線を越えた行に達したら打ち切り
	 * (以降は自動改ページの領分)。
	 *
	 * @param pageLimit        切断線
	 * @param last             本体行グループ先頭のページ位置
	 *                         (表の位置からヘッダ・フッタ等を調整済み)
	 * @param rowSizes         各グループの各行のページ寸
	 * @param groupBreakBefore 各グループの page-break-before
	 * @param groupBreakAfter  各グループの page-break-after
	 * @param rowBreakBefore   各グループの各行の page-break-before
	 * @param rowBreakAfter    各グループの各行の page-break-after
	 * @return 最初の強制改ページ。切断線まで現れなければ null
	 */
	public static ForceBreakAt firstForceBreak(final double pageLimit, double last, final double[][] rowSizes,
			final PageBreakMode[] groupBreakBefore, final PageBreakMode[] groupBreakAfter,
			final PageBreakMode[][] rowBreakBefore, final PageBreakMode[][] rowBreakAfter) {
		final int groupCount = rowSizes.length;
		PageBreakMode breakMode;
		for (int rowGroup = 0; rowGroup < groupCount; ++rowGroup) {
			final int rowCount = rowSizes[rowGroup].length;
			if (rowGroup > 0) {
				breakMode = groupBreakBefore[rowGroup];
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					// 行グループの直前の改ページ
					return new ForceBreakAt(rowGroup - 1, -1, breakMode);
				}
			}
			for (int row = 0; row < rowCount; ++row) {
				last += rowSizes[rowGroup][row];
				if (LayoutUtils.compare(last, pageLimit) > 0) {
					// 切断線を越えた: 以降は自動改ページの領分
					return null;
				}
				if (rowGroup > 0 || row > 0) {
					breakMode = rowBreakBefore[rowGroup][row];
					if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
						// 行の直前の改ページ
						return row - 1 >= 0 ? new ForceBreakAt(rowGroup, row - 1, breakMode)
								: new ForceBreakAt(rowGroup - 1, -1, breakMode);
					}
				}
				if (rowGroup == groupCount - 1 && row == rowCount - 1) {
					// 末尾の場合はループから抜ける
					break;
				}
				breakMode = rowBreakAfter[rowGroup][row];
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					// 行の直後の改ページ
					return row < rowCount - 1 ? new ForceBreakAt(rowGroup, row, breakMode)
							: new ForceBreakAt(rowGroup, -1, breakMode);
				}
			}
			if (rowGroup < groupCount - 1) {
				breakMode = groupBreakAfter[rowGroup];
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					// 行グループの直後に改ページ
					return new ForceBreakAt(rowGroup, -1, breakMode);
				}
			}
		}
		return null;
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
