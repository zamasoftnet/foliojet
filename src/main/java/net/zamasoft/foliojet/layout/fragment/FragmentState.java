package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * ブロック断片の継続状態です(ARCHITECTURE §5.7 C1)。
 *
 * <p>
 * ページ方向の切断で前断片・継続断片がそれぞれ「どの辺のフレームを
 * 保持するか」(box-decoration-break: slice 相当の切断面)、継続断片の
 * 残り指定寸法・最小寸法、前断片のページ方向使用量を表します。
 * 旧実装では splitPage の縦横鏡像(約65行×2)に埋め込まれていた
 * 暗黙状態の型化で、継続断片をボックス木の運搬なしに再構成する
 * (C1: チェーンのログ再インスタンス化)ための材料です。
 * </p>
 *
 * <p>
 * 段組を貫通する改ページ(columnSpanning=旧 FLAGS_COLUMN)では
 * フレームを切らず、前断片の使用量を内容実寸まで広げます
 * (フレーム継続策 — C4 でこの状態ごと廃止予定)。ただし切断線が
 * 内始端辺以上にある(={@code pageLimit <= 0}、前断片が内容を
 * 一切取れない)場合はフレーム継続策を適用しない — 理由は
 * {@link #of} 参照。
 * </p>
 *
 * @param prevFrame      前断片のフレーム(ページ終端側の辺を落とした形)
 * @param nextFrame      継続断片のフレーム(ページ始端側の辺を落とした形)
 * @param nextSize       継続断片の指定寸法(ページ方向は残量)
 * @param nextMinSize    継続断片の最小寸法(ページ方向は残量)
 * @param prevPageExtent 前断片のページ方向使用量
 * @author MIYABE Tatsuhiko
 */
public record FragmentState(AbsoluteRectFrame prevFrame, AbsoluteRectFrame nextFrame, Dimension nextSize,
		Dimension nextMinSize, double prevPageExtent) {

	/**
	 * 切断の断片状態を計算します(純関数)。
	 *
	 * <p>
	 * <b>フレーム継続策は「前断片が内容を取れた」場合に限る</b>
	 * (2026-07-27)。段組貫通の改ページ({@code columnSpanning})は
	 * 前後の断片で枠を切らないため、継続断片は<b>始端フレームを丸ごと
	 * 引き継ぐ</b>。切断線が内始端辺以上({@code pageLimit <= 0})の
	 * ときにこれを適用すると、前断片は内容を1つも取らないまま、
	 * 継続断片が元と寸分違わぬ幾何(同じ始端フレーム = 同じ開始位置)で
	 * 再構成される。次のページでも同じ判定が出るため、
	 * <b>白紙ページを1枚ずつ永久に生成し続ける</b>——ページは
	 * {@code PDFWriterImpl.pageOutputs}に保持されるのでヒープは単調増加し、
	 * 最終的にOutOfMemoryErrorになる(1.2KBの文書で9分15秒・数GB、
	 * 段組を含む極小ページのファジング5シードも同一原因)。
	 * css-break-3 §4.4 の「各フラグメンテナは0でない量の内容を取る」に
	 * 従い、この退化ケースでは通常の切断(始端辺を落とす)へ落とす。
	 * こうすると継続断片の始端フレームが消えて次ページの空きが実際に
	 * 増えるため、必ず前進する。{@code pageLimit > 0} の通常経路は
	 * 一切変えない。
	 * </p>
	 *
	 * @param flow              書字方向
	 * @param columnSpanning    段組を貫通する改ページ(フレーム継続策)
	 * @param frame             切断前のフレーム
	 * @param size              指定寸法
	 * @param minSize           最小寸法
	 * @param pageExtent        切断前のページ方向内容寸法(縦書き=width)
	 * @param pageLimit         切断位置(内辺から)
	 * @param contentSize       内容のページ方向実寸
	 * @param specifiedPageSize ページ方向寸法が指定されているか
	 * @return 断片状態
	 */
	public static FragmentState of(final WritingMode flow, final boolean columnSpanning,
			final AbsoluteRectFrame frame, final Dimension size, final Dimension minSize, final double pageExtent,
			final double pageLimit, final double contentSize, final boolean specifiedPageSize) {
		final boolean vertical = flow.isVertical();
		double limit = Math.max(pageLimit, 0);

		final AbsoluteRectFrame prevFrame, nextFrame;
		if (columnSpanning && LayoutUtils.compare(pageLimit, 0) > 0) {
			// 複数カラムの場合は境界を残し、高さを内容に合わせる
			prevFrame = nextFrame = frame;
			limit = Math.max(limit, contentSize);
		} else if (vertical) {
			// 縦書き: ページ軸は右→左。前断片は左辺(終端)を落とす
			prevFrame = frame.cut(true, true, true, false);
			nextFrame = frame.cut(true, false, true, true);
		} else {
			// 横書き: 前断片は下辺(終端)を落とす
			prevFrame = frame.cut(true, true, false, true);
			nextFrame = frame.cut(false, true, true, true);
		}

		final Dimension nextSize;
		if (specifiedPageSize) {
			// 指定寸法のページ方向を残量に分割
			final double rest = Math.max(0, pageExtent - limit);
			// 行方向(ページ方向でない側)はtype/値をそのまま残すが、MIXED
			// (calc()の絶対長さ+割合混在)の場合は割合成分も一緒に保存しないと
			// 断片化後の解決でratio成分が消える(2026-07-19、外部レビューで発覚)。
			nextSize = vertical
					? Dimension.create(rest, 0, size.getHeight(), size.getHeightRatio(), LengthType.ABSOLUTE,
							size.getHeightType())
					: Dimension.create(size.getWidth(), size.getWidthRatio(), rest, 0, size.getWidthType(),
							LengthType.ABSOLUTE);
		} else {
			nextSize = size;
		}

		final Dimension nextMinSize;
		if ((vertical ? minSize.getWidthType() : minSize.getHeightType()) != LengthType.AUTO) {
			// 最小寸法のページ方向を残量に分割
			final double spec = vertical ? minSize.getWidth() : minSize.getHeight();
			final double rest = Math.max(0, Math.min(spec, pageExtent) - limit);
			nextMinSize = vertical
					? Dimension.create(rest, 0, minSize.getHeight(), minSize.getHeightRatio(), LengthType.ABSOLUTE,
							minSize.getHeightType())
					: Dimension.create(minSize.getWidth(), minSize.getWidthRatio(), rest, 0, minSize.getWidthType(),
							LengthType.ABSOLUTE);
		} else {
			nextMinSize = minSize;
		}
		return new FragmentState(prevFrame, nextFrame, nextSize, nextMinSize, limit);
	}
}
