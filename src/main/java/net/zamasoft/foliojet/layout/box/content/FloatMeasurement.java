package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 単一の浮動体のページ分割判定に必要な実測値を固定した純データです
 * (2026-07-24新設、排除域P2のP2-1。
 * {@code docs/history/2026-07-24-p2-splitfloatings-branch-table.md}と
 * {@code docs/consultations/consult-exclusion-p2-design-codex.txt}§2.1)。
 *
 * <p>
 * {@code FlowContainer.FloatMeasurements}(FlowCutter用の3並列配列)の
 * 隣接拡張で、{@link Floatings#splitPageAxis}の各分岐が参照する入力を
 * 1floatにつき1レコードへ読み取り専用で写し取る。ordinalはlist index
 * ではなく<b>採取時点の安定序数</b>である(addBound事故の教訓——
 * codex設計§2.5「ordinalとlist indexの分離」。{@code splitPageAxis}の
 * ループはremove/--iでindexが変異するため、両者を混同してはならない)。
 * </p>
 *
 * @param ordinal         採取時点の安定序数(0起点、元順序)
 * @param serial          {@link BoxHolder#serial}(SPLITのremainderへ
 *                        引き継がれる識別子)
 * @param box             ボックスidentity(commit時の照合anchor)
 * @param pageStart       実測ページ軸開始位置({@code Floating.pageAxis})
 * @param pageEnd         実測ページ軸終了位置({@code pageStart + pageExtent})
 * @param pageExtent      owner書字方向での実測ページ方向寸法
 * @param sameWritingAxis ownerとfloatの実書字軸(縦/横)が一致するか。
 *                        REPLACEDはatomicで軸判定を通らないため常にtrue
 * @param fragmentHead    物理的にフラグメント先頭にあるか
 *                        ({@code LayoutUtils.compare(pageStart, 0) <= 0})。
 *                        分岐表の「first」はこれと{@code FLAGS_FIRST}の
 *                        論理積(flagsは呼び出しごとに変わるため
 *                        ここでは固定しない)
 * @param boxType         {@link BoxType#BLOCK}か{@link BoxType#REPLACED}
 * @param pageBreakInside BLOCKの{@code page-break-inside}。REPLACEDは
 *                        概念が無いためnull
 */
public record FloatMeasurement(
		int ordinal,
		int serial,
		IFloatBox box,
		double pageStart,
		double pageEnd,
		double pageExtent,
		boolean sameWritingAxis,
		boolean fragmentHead,
		BoxType boxType,
		PageBreakMode pageBreakInside) {

	/**
	 * 配置済み浮動体から実測値を採取します(読み取り専用——
	 * {@code floating}にもそのボックスにも一切影響しない)。
	 *
	 * @param ordinal   採取時点の安定序数
	 * @param floating  対象の浮動体
	 * @param ownerFlow ページ軸を決めるowner(浮動体を保持するコンテナの
	 *                  ボックス)の書字方向
	 * @return 実測値レコード
	 */
	public static FloatMeasurement of(final int ordinal, final Floatings.Floating floating,
			final WritingMode ownerFlow) {
		final double pageExtent = floating.box.getPageExtent(ownerFlow);
		final BoxType boxType = floating.box.getType();
		final boolean sameWritingAxis;
		final PageBreakMode pageBreakInside;
		if (boxType == BoxType.BLOCK) {
			final var params = ((AbstractContainerBox) floating.box).getBlockParams();
			sameWritingAxis = ownerFlow.isVertical() == params.flow.isVertical();
			pageBreakInside = params.pageBreakInside;
		} else {
			sameWritingAxis = true;
			pageBreakInside = null;
		}
		return new FloatMeasurement(ordinal, floating.serial, floating.box, floating.pageAxis,
				floating.pageAxis + pageExtent, pageExtent, sameWritingAxis,
				LayoutUtils.compare(floating.pageAxis, 0) <= 0, boxType, pageBreakInside);
	}
}
