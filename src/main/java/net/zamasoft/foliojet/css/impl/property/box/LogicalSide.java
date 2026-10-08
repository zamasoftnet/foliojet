package net.zamasoft.foliojet.css.impl.property.box;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.Value;
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
	 * Resolves the value of a physical side from its physical property (e.g. margin-top) and the logical property that
	 * maps to the same side under the element's writing-mode/direction (e.g. margin-block-start). As CSS Logical 1 §4
	 * requires, the one the cascade puts later wins ({@link #logicalWins}); if neither is declared, the physical
	 * property's default applies.
	 * <p>
	 * Until 2026-10-08 an explicit physical property always won, because the two live in separate slots whose source
	 * order was not kept. EPUB style sheets reset {@code margin: 0} on every element, which silently cancelled every
	 * {@code margin-block}/{@code padding-inline} a print style sheet added after it.
	 * </p>
	 *
	 * @param style           target style
	 * @param requestedSide   physical side requested by the caller (before rotation)
	 * @param physicalBySide  array of physical properties indexed by {@link Side#ordinal()}
	 * @param logicalBySide   array of logical properties indexed by {@link LogicalSide#ordinal()}
	 */
	public static Value resolve(CSSStyle style, Side requestedSide, PrimitivePropertyInfo[] physicalBySide,
			PrimitivePropertyInfo[] logicalBySide) {
		return resolve(style, requestedSide.ordinal(), physicalBySide, logicalBySide, SIDES);
	}

	/**
	 * Resolves {@code width} or {@code height} from it and the logical size that sets the same thing under the
	 * element's writing-mode ({@code inline-size}/{@code block-size}), like {@link #resolve}. Images included
	 * (2026-10-08): they used to skip the logical sizes, a leftover of the abolished {@code -cssj-direction-mode}, so
	 * an image ignored {@code inline-size} and {@code img { max-inline-size: 100% }} of modern reset style sheets left
	 * large images overflowing.
	 */
	public static Value size(final CSSStyle style, final boolean height) {
		return resolve(style, height ? 1 : 0, Sizes.SIZE, Sizes.LOGICAL_SIZE, SIZES);
	}

	/** {@code min-width}/{@code min-height} and {@code min-inline-size}/{@code min-block-size} ({@link #size}). */
	public static Value minSize(final CSSStyle style, final boolean height) {
		return resolve(style, height ? 1 : 0, Sizes.MIN, Sizes.LOGICAL_MIN, SIZES);
	}

	/** {@code max-width}/{@code max-height} and {@code max-inline-size}/{@code max-block-size} ({@link #size}). */
	public static Value maxSize(final CSSStyle style, final boolean height) {
		return resolve(style, height ? 1 : 0, Sizes.MAX, Sizes.LOGICAL_MAX, SIZES);
	}

	/** The size properties, width then height and inline then block (a holder: the classes refer to each other). */
	private static final class Sizes {
		static final PrimitivePropertyInfo[] SIZE = { Width.INFO, Height.INFO };
		static final PrimitivePropertyInfo[] LOGICAL_SIZE = { InlineSize.INFO, BlockSize.INFO };
		static final PrimitivePropertyInfo[] MIN = { MinWidth.INFO, MinHeight.INFO };
		static final PrimitivePropertyInfo[] LOGICAL_MIN = { MinInlineSize.INFO, MinBlockSize.INFO };
		static final PrimitivePropertyInfo[] MAX = { MaxWidth.INFO, MaxHeight.INFO };
		static final PrimitivePropertyInfo[] LOGICAL_MAX = { MaxInlineSize.INFO, MaxBlockSize.INFO };
	}

	/** Where a logical property lands on a style: the index of its physical counterpart. */
	private interface Mapping {
		int physical(CSSStyle style, int logical);
	}

	private static final Mapping SIDES = (style, logical) -> VALUES[logical].toPhysical(style).ordinal();

	/** Inline (0) is the width (0) in horizontal writing and the height (1) in vertical writing; block the other. */
	private static final Mapping SIZES = (style, logical) -> BlockFlow.get(style).isVertical() ? 1 - logical
			: logical;

	/**
	 * The pair resolution behind {@link #resolve} and {@link #size}. An explicit {@code inherit} of the
	 * winning declaration takes the parent's value of the pair (2026-10-08, CSS Logical 1 §4: the two share one
	 * computed value): the parent's physical property for a physical {@code inherit} (with the parent's logical
	 * counterpart competing), the physical property the logical one lands on in the parent's writing mode for a
	 * logical {@code inherit}. Following the slot with the same name instead gave {@code margin-left: inherit} the
	 * parent's losing {@code margin-left}, and {@code all: inherit} in a vertical child took the parent's
	 * {@code margin-block-end} for its left margin. Iterative, like {@link CSSStyle#get}, for long inherit chains.
	 */
	private static Value resolve(CSSStyle style, int physical, final PrimitivePropertyInfo[] physicals,
			final PrimitivePropertyInfo[] logicals, final Mapping mapping) {
		List<CSSStyle> levels = null;
		List<PrimitivePropertyInfo> infos = null;
		Value value;
		for (;;) {
			final PrimitivePropertyInfo physicalInfo = physicals[physical];
			int logical = -1;
			for (int i = 0; i < logicals.length; ++i) {
				if (mapping.physical(style, i) == physical) {
					logical = i;
					break;
				}
			}
			final boolean logicalWon = logical != -1 && logicalWins(style, physicalInfo, logicals[logical]);
			final PrimitivePropertyInfo winner = logicalWon ? logicals[logical] : physicalInfo;
			final CSSStyle parent = style.getParentStyle();
			if (parent == null || !style.isDeclaredInherit(winner)) {
				value = style.get(winner);
				break;
			}
			if (levels == null) {
				levels = new ArrayList<CSSStyle>();
				infos = new ArrayList<PrimitivePropertyInfo>();
			}
			levels.add(style);
			infos.add(winner);
			if (logicalWon) {
				physical = mapping.physical(parent, logical);
			}
			style = parent;
		}
		if (levels != null) {
			// The computed value of each inheriting level, as CSSStyle.get does for inherit
			for (int i = levels.size() - 1; i >= 0; --i) {
				value = infos.get(i).getComputedValue(value, levels.get(i));
			}
		}
		return value;
	}

	/**
	 * Whether a logical property decides the value its physical counterpart also sets (2026-10-08): the later of the
	 * two in the cascade wins ({@code !important} first, then declaration order; {@link CSSStyle#declarationRank}).
	 * A shorthand's omitted part counts as a declaration of the initial value: {@code border-inline-start: 2px}
	 * resets the style to none over an earlier {@code border-left: 8px solid} (CSS Backgrounds 3 §3.4).
	 */
	public static boolean logicalWins(final CSSStyle style, final PrimitivePropertyInfo physical,
			final PrimitivePropertyInfo logical) {
		final int logicalRank = style.declarationRank(logical);
		if (logicalRank == 0 && !style.isDeclared(logical)) {
			return false;
		}
		final int physicalRank = style.declarationRank(physical);
		if (physicalRank == 0 && !style.isDeclared(physical)) {
			return true;
		}
		return logicalRank > physicalRank;
	}
}
