package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Separates physical line/block axes from the typesetting model used within lines.
 *
 * <p>
 * {@code vertical-*} uses vertical typesetting, whereas {@code sideways-*} places a horizontally
 * typeset glyph run into physically vertical lines by rotating it. Therefore,
 * {@link WritingMode#isVertical()} is used only to select the box's physical axes; font metrics,
 * bidi, vertical-align, etc. use this class's typesetting-mode checks.
 * </p>
 */
public final class TypesettingMode {
	/** Physical inline progression direction. */
	public enum InlineProgression {
		LEFT_TO_RIGHT(1),
		RIGHT_TO_LEFT(-1),
		TOP_TO_BOTTOM(1),
		BOTTOM_TO_TOP(-1);

		private final int sign;

		private InlineProgression(final int sign) {
			this.sign = sign;
		}

		/** {@code 1} along the positive physical axis, {@code -1} along the negative axis. */
		public int sign() {
			return this.sign;
		}
	}

	/** The over (ascent) side of the rotated horizontal-typesetting baseline. */
	public enum PhysicalSide {
		TOP,
		RIGHT,
		BOTTOM,
		LEFT
	}

	private TypesettingMode() {
	}

	/** {@code true} for horizontal typesetting. sideways uses horizontal typesetting regardless of physical flow. */
	public static boolean isHorizontal(final WritingMode flow, final WritingModeVariant variant) {
		return !flow.isVertical() || variant != WritingModeVariant.NORMAL;
	}

	/** {@code true} only for vertical typesetting with the ordinary glyphs of {@code vertical-*}. */
	public static boolean isVertical(final WritingMode flow, final WritingModeVariant variant) {
		return flow.isVertical() && variant == WritingModeVariant.NORMAL;
	}

	/** {@code true} when using sideways logical inline coordinates for physically vertical lines. */
	public static boolean usesSidewaysInlineAxis(final WritingMode flow, final WritingModeVariant variant) {
		return flow.isVertical() && variant != WritingModeVariant.NORMAL;
	}

	/** Rotation applied to sideways lines. {@link WritingModeVariant#NORMAL} means no rotation. */
	public static WritingModeVariant glyphRotation(final WritingModeVariant variant) {
		return variant;
	}

	/**
	 * Returns the used {@code text-orientation} passed to FontStyle. For sideways, preserves the author's
	 * computed value and normalizes only the used value to {@link FontStyle.TextOrientation#MIXED}
	 * to avoid compounding it with rotation of the entire line.
	 */
	public static FontStyle.TextOrientation usedTextOrientation(final WritingModeVariant variant,
			final FontStyle.TextOrientation computed) {
		return variant == WritingModeVariant.NORMAL ? computed : FontStyle.TextOrientation.MIXED;
	}

	/**
	 * Returns the physical inline progression direction.
	 *
	 * <p>
	 * The four sideways cases follow solely from the horizontal run's {@code direction} and rotation:
	 * CW×LTR = top → bottom, CW×RTL = bottom → top, CCW×LTR = bottom → top, CCW×RTL = top → bottom.
	 * RL/LR describe block progression, so they do not affect inline progression in these four cases.
	 * </p>
	 *
	 * <p>
	 * Combining {@link WritingMode#TB} with a sideways variant is nonstandard and possible only through
	 * internal longhands. In this case, the physically horizontal line axis takes precedence.
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

	/** {@code 1} for inline progression along the positive physical axis (right or down), {@code -1} for negative. */
	public static int inlineProgressionSign(final WritingMode flow,
			final WritingModeVariant variant, final byte direction) {
		return inlineProgression(flow, variant, direction).sign();
	}

	/**
	 * Returns the physical edge corresponding to the over (ascent) side of the typesetting baseline.
	 * Over is right for CW and left for CCW, so SIDEWAYS_CCW reverses the physical sides of ascent/descent
	 * relative to SIDEWAYS_CW.
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
