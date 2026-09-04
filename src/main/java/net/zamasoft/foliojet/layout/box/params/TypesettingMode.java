package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * 物理的な行・ブロック軸と、行の内部で使う組版モデルを分離します。
 *
 * <p>
 * {@code vertical-*} は縦組版ですが、{@code sideways-*} は物理的には縦の行へ
 * 水平組版した字形列を回転して置きます。したがって {@link WritingMode#isVertical()}
 * は箱の物理軸を選ぶときだけ使い、font metrics・bidi・vertical-align 等はこの
 * クラスの組版モード判定を使います。
 * </p>
 */
public final class TypesettingMode {
	/** 行内の物理的な進行方向です。 */
	public enum InlineProgression {
		LEFT_TO_RIGHT(1),
		RIGHT_TO_LEFT(-1),
		TOP_TO_BOTTOM(1),
		BOTTOM_TO_TOP(-1);

		private final int sign;

		private InlineProgression(final int sign) {
			this.sign = sign;
		}

		/** 物理軸の正方向なら {@code 1}、負方向なら {@code -1}。 */
		public int sign() {
			return this.sign;
		}
	}

	/** 回転後の水平組版 baseline に対する over(ascent)側です。 */
	public enum PhysicalSide {
		TOP,
		RIGHT,
		BOTTOM,
		LEFT
	}

	private TypesettingMode() {
	}

	/** 水平組版なら {@code true}。sideways は物理 flow にかかわらず水平組版です。 */
	public static boolean isHorizontal(final WritingMode flow, final WritingModeVariant variant) {
		return !flow.isVertical() || variant != WritingModeVariant.NORMAL;
	}

	/** {@code vertical-*} の通常字形による縦組版だけなら {@code true}。 */
	public static boolean isVertical(final WritingMode flow, final WritingModeVariant variant) {
		return flow.isVertical() && variant == WritingModeVariant.NORMAL;
	}

	/** 物理的な縦の行へ sideways の論理行内座標を使う場合は {@code true}。 */
	public static boolean usesSidewaysInlineAxis(final WritingMode flow, final WritingModeVariant variant) {
		return flow.isVertical() && variant != WritingModeVariant.NORMAL;
	}

	/** sideways 行へ適用する回転です。{@link WritingModeVariant#NORMAL} は回転なしです。 */
	public static WritingModeVariant glyphRotation(final WritingModeVariant variant) {
		return variant;
	}

	/**
	 * FontStyle へ渡す used {@code text-orientation}を返します。sideways は作者指定の
	 * computed value を変更せず、行全体の回転と重ならないよう used value だけ
	 * {@link FontStyle.TextOrientation#MIXED}へ正規化します。
	 */
	public static FontStyle.TextOrientation usedTextOrientation(final WritingModeVariant variant,
			final FontStyle.TextOrientation computed) {
		return variant == WritingModeVariant.NORMAL ? computed : FontStyle.TextOrientation.MIXED;
	}

	/**
	 * 行内の物理的な進行方向を返します。
	 *
	 * <p>
	 * sideways の四象限は、水平 run の {@code direction} と回転だけから導けます。
	 * CW×LTR=上→下、CW×RTL=下→上、CCW×LTR=下→上、CCW×RTL=上→下です。
	 * RL/LR はブロック進行を表すため、この四象限の行内進行には影響しません。
	 * </p>
	 *
	 * <p>
	 * {@link WritingMode#TB} と sideways variant の組は内部 longhand でだけ作れる
	 * 非標準の組合せです。この場合は物理的な横の行軸を優先します。
	 * </p>
	 */
	public static InlineProgression inlineProgression(final WritingMode flow,
			final WritingModeVariant variant, final byte direction) {
		final boolean ltr;
		switch (direction) {
		case AbstractTextParams.DIRECTION_LTR:
			ltr = true;
			break;
		case AbstractTextParams.DIRECTION_RTL:
			ltr = false;
			break;
		default:
			throw new IllegalArgumentException("direction=" + direction);
		}
		if (!flow.isVertical()) {
			return ltr ? InlineProgression.LEFT_TO_RIGHT : InlineProgression.RIGHT_TO_LEFT;
		}
		if (variant == WritingModeVariant.SIDEWAYS_CCW) {
			return ltr ? InlineProgression.BOTTOM_TO_TOP : InlineProgression.TOP_TO_BOTTOM;
		}
		return ltr ? InlineProgression.TOP_TO_BOTTOM : InlineProgression.BOTTOM_TO_TOP;
	}

	/** 行内進行が物理軸の正方向(右または下)なら {@code 1}、負方向なら {@code -1}。 */
	public static int inlineProgressionSign(final WritingMode flow,
			final WritingModeVariant variant, final byte direction) {
		return inlineProgression(flow, variant, direction).sign();
	}

	/**
	 * 組版 baseline の over(ascent)側に対応する物理辺を返します。
	 * CW の over は右、CCW の over は左なので、SIDEWAYS_CCW では
	 * SIDEWAYS_CW に対して ascent/descent の物理側が反転します。
	 */
	public static PhysicalSide overSide(final WritingMode flow, final WritingModeVariant variant) {
		return switch (variant) {
		case SIDEWAYS_CW -> PhysicalSide.RIGHT;
		case SIDEWAYS_CCW -> PhysicalSide.LEFT;
		case NORMAL -> switch (flow) {
			case TB -> PhysicalSide.TOP;
			case RL -> PhysicalSide.RIGHT;
			case LR -> PhysicalSide.LEFT;
		};
		};
	}
}
