package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;

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
 * (フレーム継続策 — C4 でこの状態ごと廃止予定)。
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
		if (columnSpanning) {
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
			nextSize = vertical ? Dimension.create(rest, size.getHeight(), LengthType.ABSOLUTE, size.getHeightType())
					: Dimension.create(size.getWidth(), rest, size.getWidthType(), LengthType.ABSOLUTE);
		} else {
			nextSize = size;
		}

		final Dimension nextMinSize;
		if ((vertical ? minSize.getWidthType() : minSize.getHeightType()) != LengthType.AUTO) {
			// 最小寸法のページ方向を残量に分割
			final double spec = vertical ? minSize.getWidth() : minSize.getHeight();
			final double rest = Math.max(0, Math.min(spec, pageExtent) - limit);
			nextMinSize = vertical
					? Dimension.create(rest, minSize.getHeight(), LengthType.ABSOLUTE, minSize.getHeightType())
					: Dimension.create(minSize.getWidth(), rest, minSize.getWidthType(), LengthType.ABSOLUTE);
		} else {
			nextMinSize = minSize;
		}
		return new FragmentState(prevFrame, nextFrame, nextSize, nextMinSize, limit);
	}
}
