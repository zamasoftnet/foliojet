package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * Logical sides (block-start/end and inline-start/end). Resolves physical sides
 * ({@link Side}) using both writing-mode ({@link BlockFlow}) and direction ({@link Direction}).
 * <p>
 * {@link Side} itself does not rotate sides (foliojet4's proprietary physical-property
 * "rotation" mechanism {@code -cssj-direction-mode} was abolished on 2026-07-20).
 * Separately, {@link LogicalSide} implements CSS logical properties by resolving physical
 * sides using not only the block axis but also the inline axis
 * (directionality, including left/right reversal for RTL) (implemented 2026-07-19).
 * </p>
 */
public enum LogicalSide {
	BLOCK_START, BLOCK_END, INLINE_START, INLINE_END;

	static final LogicalSide[] VALUES = values();

	/**
	 * Returns the physical side corresponding to this logical side under the current
	 * writing-mode/direction.
	 */
	public Side toPhysical(CSSStyle style) {
		// Expanding tate-chu-yoko (text-combine-upright) overrides the element's block-flow to horizontal (TB),
		// but its writing-mode does not change (css-writing-modes-3
		// §9.1: the combined text is one character in a vertical line). Resolve logical sides using the
		// parent/line writing direction. Otherwise margin-inline-start affected the physical left side
		// (found in tate-chu-yoko page numbers in running headers on 2026-09-08, fixed 2026-09-11).
		if (net.zamasoft.foliojet.css.impl.property.text.TextCombineMode
				.get(style) != net.zamasoft.foliojet.css.value.TextCombineValue.NONE
				&& style.getParentStyle() != null) {
			style = style.getParentStyle();
		}
		WritingMode flow = BlockFlow.get(style);
		final byte direction = Direction.get(style);
		boolean rtl = direction == AbstractTextParams.DIRECTION_RTL;
		final boolean bottomToTop = flow.isVertical()
				&& TypesettingMode.inlineProgression(flow,
						net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.get(style), direction)
						== TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		switch (flow) {
		case TB:
			// Horizontal writing: block axis=top/bottom, inline axis=left/right.
			switch (this) {
			case BLOCK_START:
				return Side.TOP;
			case BLOCK_END:
				return Side.BOTTOM;
			case INLINE_START:
				return rtl ? Side.RIGHT : Side.LEFT;
			case INLINE_END:
				return rtl ? Side.LEFT : Side.RIGHT;
			default:
				throw new IllegalStateException();
			}
		case RL:
			// Vertical writing (right→left): block axis=left/right, inline axis=top/bottom.
			switch (this) {
			case BLOCK_START:
				return Side.RIGHT;
			case BLOCK_END:
				return Side.LEFT;
			case INLINE_START:
				return bottomToTop ? Side.BOTTOM : Side.TOP;
			case INLINE_END:
				return bottomToTop ? Side.TOP : Side.BOTTOM;
			default:
				throw new IllegalStateException();
			}
		case LR:
			// Vertical writing (left→right): block axis=left/right, inline axis=top/bottom
			// (in vertical writing, RL/LR have the same inline direction, per CSS Writing Modes).
			switch (this) {
			case BLOCK_START:
				return Side.LEFT;
			case BLOCK_END:
				return Side.RIGHT;
			case INLINE_START:
				return bottomToTop ? Side.BOTTOM : Side.TOP;
			case INLINE_END:
				return bottomToTop ? Side.TOP : Side.BOTTOM;
			default:
				throw new IllegalStateException();
			}
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Resolves the value based on which physical or logical property is explicitly specified.
	 * If the physical property (e.g. margin-top) is explicit, it takes precedence.
	 * Otherwise, checks the corresponding logical property (e.g. margin-block-start,
	 * which resolves to margin-top depending on writing-mode/direction).
	 * If neither is specified, returns the physical property's default.
	 * <p>
	 * Does not implement the strict physical/logical precedence required by CSS,
	 * where the later declaration in cascade order wins. This implementation stores
	 * each property name in an independent slot and cannot compare source order across slots.
	 * Instead, it uses the simplified rule "an explicit physical property always wins"
	 * (see the development plan).
	 * </p>
	 *
	 * @param style           target style
	 * @param requestedSide   physical side requested by the caller (before rotation)
	 * @param physicalBySide  array of physical properties indexed by {@link Side#ordinal()}
	 * @param logicalBySide   array of logical properties indexed by {@link LogicalSide#ordinal()}
	 */
	public static net.zamasoft.foliojet.css.value.Value resolve(CSSStyle style, Side requestedSide,
			PrimitivePropertyInfo[] physicalBySide, PrimitivePropertyInfo[] logicalBySide) {
		PrimitivePropertyInfo physicalInfo = physicalBySide[requestedSide.ordinal()];
		if (style.isDeclared(physicalInfo)) {
			return style.get(physicalInfo);
		}
		for (LogicalSide logical : VALUES) {
			if (logical.toPhysical(style) == requestedSide) {
				PrimitivePropertyInfo logicalInfo = logicalBySide[logical.ordinal()];
				if (style.isDeclared(logicalInfo)) {
					return style.get(logicalInfo);
				}
				break;
			}
		}
		return style.get(physicalInfo);
	}
}
