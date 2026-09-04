package net.zamasoft.foliojet.layout.util;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

/**
 * sideways 行の水平ローカル座標を物理座標へ写します。
 *
 * <p>
 * ローカル座標では baseline の始点を {@code (0, 0)}、行内の送りを
 * {@code +x}、descent 側を {@code +y} とします。CW は物理座標の
 * {@code (x + descent, y)} を原点に純粋な {@code +90} 度回転を行い、
 * CCW は {@code (x + ascent, y + advance)} を原点に純粋な
 * {@code -90} 度回転を行います。したがって CW の descent/under 側は左、
 * ascent/over 側は右、CCW ではそれぞれ右と左です。線幅、下線距離、影の
 * ローカルな寸法は拡縮されません。
 * </p>
 *
 * <p>
 * direction を組み合わせた四象限の論理的な行内進行は、CW×LTR=上から下、
 * CW×RTL=下から上、CCW×LTR=下から上、CCW×RTL=上から下です。変換そのものは
 * glyph の論理順・視覚順を入れ替えず、呼び出し側が bidi 後の run をそのまま
 * 水平ローカル座標で描きます。
 * </p>
 *
 * <p>
	 * Stage 2 では CW/CCW の両方がこの共通契約を使い、物理的な
	 * 行内反転は呼び出し側が行ボックスの範囲内で行います。
 * </p>
 */
public final class SidewaysGeometry {
	private SidewaysGeometry() {
	}

	/**
	 * 水平 run の baseline 座標を sideways 行の物理座標へ写す変換を返します。
	 *
	 * @param variant SIDEWAYS_CW または SIDEWAYS_CCW
	 * @param x       物理 run box の左端
	 * @param y       物理 run box の上端
	 * @param ascent  水平組版の ascent
	 * @param descent 水平組版の descent
	 * @param advance 水平 run の advance
	 * @return 平行移動と純粋な四分の一回転からなる変換
	 */
	public static AffineTransform runTransform(final WritingModeVariant variant, final double x, final double y,
			final double ascent, final double descent, final double advance) {
		switch (variant) {
		case SIDEWAYS_CW:
			// 明示の行列(cos/sin の丸めや -0.0 を避ける。PDF の Tm も 0 1 -1 0 になる)
			return new AffineTransform(0, 1, -1, 0, x + descent, y);
		case SIDEWAYS_CCW:
			return new AffineTransform(0, -1, 1, 0, x + ascent, y + advance);
		case NORMAL:
		default:
			throw new IllegalArgumentException("A normal writing mode has no sideways run transform");
		}
	}

	/**
	 * {@code [0, advance] x [-ascent, descent]} の水平 run box を回転した物理外接矩形を返します。
	 */
	public static Rectangle2D bounds(final WritingModeVariant variant, final double x, final double y,
			final double ascent, final double descent, final double advance) {
		switch (variant) {
		case SIDEWAYS_CW:
		case SIDEWAYS_CCW:
			return new Rectangle2D.Double(x, y, ascent + descent, advance);
		case NORMAL:
		default:
			throw new IllegalArgumentException("A normal writing mode has no sideways run bounds");
		}
	}
}
