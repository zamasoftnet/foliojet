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
 * @param moveToNext      配置時に2-D bottom帯との交差で確定した一回限りの移送
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
		boolean moveToNext,
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
		final double pageExtent = occupiedPageExtent(floating.box, ownerFlow);
		final BoxType boxType = floating.box.getType();
		final boolean sameWritingAxis;
		final PageBreakMode pageBreakInside;
		if (boxType == BoxType.BLOCK) {
			final var params = ((AbstractContainerBox) floating.box).getBlockParams();
			sameWritingAxis = sameWritingAxis(ownerFlow, floating.box);
			pageBreakInside = params.pageBreakInside;
		} else {
			sameWritingAxis = true;
			pageBreakInside = null;
		}
		return new FloatMeasurement(ordinal, floating.serial, floating.box, floating.pageAxis,
				floating.pageAxis + pageExtent, pageExtent, sameWritingAxis,
				LayoutUtils.compare(floating.pageAxis, 0) <= 0, floating.moveToNext, boxType, pageBreakInside);
	}

	/**
	 * 親までの{@code FIRST}とfloat自身のfragment先頭を合成します。
	 *
	 * <p>
	 * 入れ子の内容箱ではfloatのローカル開始位置が0でも、その箱自身が親の
	 * 先頭でなければページ/段の先頭ではありません。配置時と分割時は必ず
	 * この合成を通し、同じfloatの{@code first}を一致させます(2026-09-04)。
	 * </p>
	 */
	public static boolean isFragmentStart(final boolean ancestorsFirst, final boolean fragmentHead) {
		return ancestorsFirst && fragmentHead;
	}

	/**
	 * 分割不能floatの占有終端を、painted-sliver規則で判定します。
	 * 1pt未満の超過だけを収まるものとして扱い、ちょうど1pt以上は送り
	 * ます(2026-08-10/2026-09-04)。分割可能floatの分岐表1はこの許容を
	 * 使わず、従来どおり{@link LayoutUtils#compare}で判定します。
	 */
	public static boolean fitsPageUnsplittable(final double pageEnd, final double pageLimit) {
		return pageEnd - pageLimit < 1.0;
	}

	/**
	 * 浮動体が<b>ページ軸上で実際に占める</b>寸法を返します(2026-07-28新設)。
	 *
	 * <p>
	 * {@code getPageExtent()}は<b>箱の幾何</b>しか答えないため、ページ軸
	 * 方向の寸法を明示した浮動体(縦書きの{@code width}、横書きの
	 * {@code height})では、指定寸法を超えた中身が
	 * {@code overflow:visible}のまま箱の外へ描かれても<b>0扱い</b>に
	 * なっていた。その結果{@link FloatSplitPlan#classify}の分岐表1
	 * (全体が切断線以前)が成立し、<b>切断されないまま紙の外まで中身が
	 * 並ぶ</b>(local/shrink/strict-149858-min.html)。
	 * </p>
	 *
	 * <p>
	 * これは通常フローの{@code FlowContainer.computeFlowBottoms()}が
	 * すでに{@code Math.max(内寸, getContentSize())}で行っている補正と
	 * 同じもので、浮動体だけがこの補正を欠いていた。{@code max}を取るのは
	 * 「何も描かない余りの寸法」でも従来どおり切断予約されるようにする
	 * ため——描画量が幾何より小さいときの抑制は
	 * {@code BreakableBuilder.paintsNothingBeyondPage()}の役目である。
	 * </p>
	 *
	 * @param box       対象の浮動体
	 * @param ownerFlow ページ軸を決めるownerの書字方向
	 * @return 幾何寸法と描画が及ぶ寸法の大きいほう
	 */
	public static double occupiedPageExtent(final net.zamasoft.foliojet.layout.box.IFloatBox box,
			final WritingMode ownerFlow) {
		return Math.max(box.getPageExtent(ownerFlow), box.paintedPageExtent(ownerFlow));
	}

	/** ownerと浮動体内部の実書字軸(縦/横)が一致するかを返します(2026-09-04)。 */
	public static boolean sameWritingAxis(final WritingMode ownerFlow, final IFloatBox box) {
		if (box.getType() != BoxType.BLOCK) {
			return true;
		}
		final WritingMode floatFlow = ((AbstractContainerBox) box).getBlockParams().flow;
		return ownerFlow.isVertical() == floatFlow.isVertical();
	}

	/**
	 * 浮動体をページ軸で分割できないかを返します(2026-09-04)。
	 *
	 * <p>
	 * 配置時と断片化時で同じ述語を使うための唯一の入口です。BLOCKは
	 * ownerとの書字軸不一致、または非先頭の{@code break-inside:avoid}で
	 * 分割不能になり、REPLACED/RESCUEは常にatomicです。
	 * </p>
	 *
	 * @param boxType         対象のbox種別
	 * @param sameWritingAxis 実際のownerと浮動体の書字軸が一致するか
	 * @param pageBreakInside BLOCKのbreak-inside。その他のbox種別ではnull
	 * @param first           物理的なフラグメント先頭として扱うならtrue
	 */
	public static boolean isUnsplittable(final BoxType boxType, final boolean sameWritingAxis,
			final PageBreakMode pageBreakInside, final boolean first) {
		switch (boxType) {
		case BLOCK:
			return !sameWritingAxis || (pageBreakInside == PageBreakMode.AVOID && !first);
		case REPLACED:
		case RESCUE:
			return true;
		default:
			throw new IllegalStateException(boxType.toString());
		}
	}
}
