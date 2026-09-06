package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;

/**
 * 表Pass B(行計測)の計測プリミティブです(E-6増分5b-1、2026-07-24——
 * {@code docs/consultations/consult-e6b-remaining-increments-codex.md}
 * §4.4「確定列幅でセルrangeを一つずつ再生し、使用ページ方向寸法・
 * first ascentだけを取得してcell box treeを破棄する」)。
 *
 * <p>
 * <b>計測文脈はbind文脈そのもの</b>: Pass Bは表終端(列幅確定後、
 * {@code RetainedTableBuilder.bindRows}と同じ時点)で走るため、
 * 汎用wrapper+scratchページ({@code SourceReplayer.measure}の
 * MeasurePageGenerator)ではなく、<b>本bindと同一の機構</b>——列幅適用済み
 * セルboxの複製({@link TableCellBox#newMeasureReplica})の上の
 * {@code BlockBuilder}を、同じlive {@code LayoutStack}・同じ
 * {@code PageGenerator}でSegmentExecutor駆動——で計測する。scratchページ
 * 方式はwrapperのパラメータ写し(line-height・text-indent等)と祖先文脈
 * (%解決の{@code getFixedWidth/Height}連鎖)の再現が原理的に不完全で、
 * Pass Cが実際には要求しない困難を持ち込むため採らない。ボックス木は
 * 複製の上に作られ、値の採取後に到達不能(破棄)——「幅固定・高さ無限」は
 * セルbindの構造そのもの(セルはbind中に改ページしない)。
 * </p>
 *
 * <p>
 * <b>非破壊性</b>: 再生はrangeの再captureで行い(リースは保持したまま)、
 * liveのボックス・ビルダー状態には触れない。従って計測→本bindの順で
 * 同じセルを二度再生できる(shadow検証{@code RetainedCellPassBShadowTest}
 * はこれをdisplay list parityでも固定する)。
 * </p>
 */
final class CellPassBMeasurer {
	/**
	 * Pass B計測の結果です。行高計算({@code bindRows})がbind済みセルboxから
	 * 読む値の全て:
	 * <ul>
	 * <li>{@code pageAxisSize}: 使用ページ方向寸法(横表={@code getHeight()}、
	 * 縦表={@code getWidth()}。フレーム込みの外寸——rowspan最小寸法・
	 * 行高maxの両方がこの値を読む)</li>
	 * <li>{@code firstAscent}: 先頭アセント({@code getFirstAscent()}。
	 * baseline整列の{@code maxFirstAscent}が読む。無ければNaN)</li>
	 * </ul>
	 * セル内容整列({@code verticalAlign()})が読む内容ページ寸法
	 * ({@code TableCellBox.pageSize})はPass Cの本bindが自前で再設定する
	 * ため転送不要(採取対象外)。
	 */
	record Result(double pageAxisSize, double firstAscent) {
	}

	private CellPassBMeasurer() {
		// static functions
	}

	/**
	 * seal済みセルの本文rangeを確定列幅でscratch再生し、行高計算が必要と
	 * する値を計測します。
	 *
	 * @param cell        計測対象セル(列幅適用済み——
	 *                    {@code bindRows}のsetWidth/setHeight後)
	 * @param layoutStack 本bindと同じlive layoutStack
	 * @param vertical    表が縦書きならtrue(ページ方向軸の選択)
	 * @return 計測結果。Pass B対象外(records保持の未sealセル・段組セル)は
	 *         null。records空の未sealセル(空セル——E-6増分5b-2)は本文
	 *         非依存のためclose-onlyで計測する
	 */
	static Result measure(final CellContent cell, final LayoutStack layoutStack, final boolean vertical) {
		final TwoPassBlockBuilder.DeferredBind body = cell.rangeBody();
		if (body == null && !cell.isPassBMeasurable()) {
			// records保持の未sealセル・extendedセルはPass B対象外
			return null;
		}
		final TableCellBox replica = cell.getCellBox().newMeasureReplica();
		if (replica == null) {
			return null;
		}
		try (ScratchReplayScope scope = new ScratchReplayScope()) {
			final BlockBuilder builder = new BlockBuilder(layoutStack, replica);
			if (body != null) {
				body.measureInto(builder);
			}
			// bodyがnullの適格セルはrecords空(bindが何も再演しない)——close-only
			builder.close();
		}
		return new Result(vertical ? replica.getWidth() : replica.getHeight(), replica.getFirstAscent());
	}
}
