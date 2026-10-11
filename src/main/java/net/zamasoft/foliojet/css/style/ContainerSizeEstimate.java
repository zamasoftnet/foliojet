package net.zamasoft.foliojet.css.style;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.box.BoxSizing;
import net.zamasoft.foliojet.css.impl.property.box.CSSFloat;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.MaxWidth;
import net.zamasoft.foliojet.css.impl.property.box.MinWidth;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.impl.property.column.ColumnCount;
import net.zamasoft.foliojet.css.impl.property.column.ColumnWidth;
import net.zamasoft.foliojet.css.impl.property.flex.FlexDirectionProperty;
import net.zamasoft.foliojet.css.impl.property.flex.FlexWrapProperty;
import net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.FlexDirectionValue;
import net.zamasoft.foliojet.css.value.FlexWrapValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * The inline size of a query container ({@code container-type: inline-size}) for {@code @container} and container
 * units, estimated from styles before layout (2026-10-11): its content-box width, from the widths, margins, borders
 * and padding of it and its ancestors down from the page area. Any number of passes gives the same answer as one; the
 * measured sizes of the previous pass were used until then, so a query never held in one pass and nested containers
 * took a pass per level.
 *
 * <p>
 * What it knows: a block-level box in normal flow (block, list-item, flow-root, flex or grid container, not a flex or
 * grid item) and a flex item stretched across a single-line column of auto width fill their containing block less
 * their margins, borders and padding; a length or percentage width, min-width or max-width applies to them. A float,
 * an inline-block or an absolutely positioned box is known only by an absolute width (an absolutely positioned one
 * with no percentage in its padding or min/max widths: their basis is a positioned ancestor). Other flex and grid
 * items, tables and their contents, non-replaced inlines (width does not apply), multi-column contents and vertical
 * writing are not known. The root's containing block is the page area of the configured paper (output.page-width less
 * output.page-margins), as media queries and viewport units take it; the document's {@code @page} is not consulted, and
 * floats beside a box are not taken off its width. Unknown is NaN: a query against it does not hold and a container
 * unit is 0.
 * </p>
 */
final class ContainerSizeEstimate {
	/** The width each estimated box gives the boxes in it (weak keys: the styles of closed elements go). */
	private final Map<CSSStyle, Double> inner = new WeakHashMap<CSSStyle, Double>();

	/**
	 * The content-box width of the box the style makes, NaN if it is not known. Walks up to an estimated ancestor or
	 * the root, then down, so a deep tree costs each box once and no recursion.
	 *
	 * @param pageArea the inline size of the configured page area
	 */
	double contentWidth(final CSSStyle style, final double pageArea) {
		final ArrayDeque<CSSStyle> chain = new ArrayDeque<CSSStyle>();
		double cb = pageArea;
		for (CSSStyle s = style.getParentStyle(); s != null; s = s.getParentStyle()) {
			final Double known = this.inner.get(s);
			if (known != null) {
				cb = known;
				break;
			}
			chain.push(s);
		}
		while (!chain.isEmpty()) {
			final CSSStyle s = chain.pop();
			cb = innerWidth(s, ownWidth(s, cb), cb);
			this.inner.put(s, cb);
		}
		return ownWidth(style, cb);
	}

	/**
	 * The width a box gives the boxes in it: its content width; its containing block's through inline and contents
	 * boxes; NaN for multi-column and table boxes (columns, anonymous cells).
	 */
	private static double innerWidth(final CSSStyle box, final double content, final double cb) {
		final byte display = Display.get(box);
		if (display == DisplayValue.INLINE || display == DisplayValue.CONTENTS) {
			return cb;
		}
		if (display >= DisplayValue.TABLE && display <= DisplayValue.TABLE_CAPTION) {
			return Double.NaN;
		}
		if (ColumnCount.get(box) > 1 || !LayoutUtils.isNone(ColumnWidth.get(box))) {
			return Double.NaN;
		}
		return content;
	}

	/** The content-box width of the box a style makes in a containing block {@code cb} wide, NaN if not known. */
	private static double ownWidth(final CSSStyle style, final double cb) {
		if (BlockFlow.get(style).isVertical()) {
			return Double.NaN;
		}
		final byte display = Display.get(style);
		if (display == DisplayValue.NONE || display == DisplayValue.CONTENTS || display == DisplayValue.INLINE
				|| (display >= DisplayValue.TABLE && display <= DisplayValue.TABLE_CAPTION)) {
			return Double.NaN;
		}
		final CSSStyle box = boxParent(style);
		final byte parentDisplay = box == null ? DisplayValue.BLOCK : Display.get(box);
		final byte position = CSSPosition.get(style);
		final boolean absolute = position == PositionValue.ABSOLUTE || position == PositionValue.FIXED;
		final boolean item = !absolute && (parentDisplay == DisplayValue.FLEX || parentDisplay == DisplayValue.GRID);
		// float does not apply to a flex or grid item
		final boolean floating = !absolute && !item && CSSFloat.get(style) != CSSFloatValue.NONE;
		// A flex item stretched across a single-line column fills it as a block does
		final boolean stretched = item && parentDisplay == DisplayValue.FLEX && stretched(style, box);
		if (item && !stretched) {
			// Sized by the flex or grid algorithm
			return Double.NaN;
		}
		final boolean blockLevel = stretched || display == DisplayValue.BLOCK || display == DisplayValue.LIST_ITEM
				|| display == DisplayValue.FLEX || display == DisplayValue.GRID;
		// The percentage basis of an absolutely positioned box is a positioned ancestor's: unknown here
		final double base = absolute ? Double.NaN : cb;
		final RectBorder border = BoxStyleMapper.createRectBorder(style);
		final double frame = length(Padding.get(style, Side.LEFT), base, 0)
				+ length(Padding.get(style, Side.RIGHT), base, 0) + border.getLeft().width + border.getRight().width;
		final double boxSizing = BoxSizing.get(style) == BoxSizingMode.BORDER_BOX ? frame : 0;
		final Value width = Width.get(style);
		double content;
		if (width instanceof AbsoluteLengthValue length) {
			content = length.getLength() - boxSizing;
		} else if (!blockLevel || absolute || floating) {
			// Sized by its content
			return Double.NaN;
		} else if (width == KeywordValue.AUTO) {
			content = cb - length(Margin.get(style, Side.LEFT), cb, 0) - length(Margin.get(style, Side.RIGHT), cb, 0)
					- frame;
		} else {
			content = length(width, base, Double.NaN) - boxSizing;
		}
		final Value max = MaxWidth.get(style);
		if (max != KeywordValue.NONE) {
			content = Math.min(content, length(max, base, Double.NaN) - boxSizing);
		}
		final Value min = MinWidth.get(style);
		if (min != KeywordValue.AUTO) {
			content = Math.max(content, length(min, base, Double.NaN) - boxSizing);
		}
		return Double.isNaN(content) ? Double.NaN : Math.max(0, content);
	}

	/** The style of the box an element's box is in: its parent's, past display: contents. */
	private static CSSStyle boxParent(final CSSStyle style) {
		for (CSSStyle p = style.getParentStyle(); p != null; p = p.getParentStyle()) {
			if (Display.get(p) != DisplayValue.CONTENTS) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Whether a flex item is stretched across its container's single column (the cross axis being the line axis): a
	 * nowrap column container, align-self normal or stretch (auto taking the container's align-items), no auto margin
	 * across.
	 */
	private static boolean stretched(final CSSStyle item, final CSSStyle container) {
		final FlexDirectionValue direction = FlexDirectionProperty.get(container);
		if ((direction != FlexDirectionValue.COLUMN && direction != FlexDirectionValue.COLUMN_REVERSE)
				|| FlexWrapProperty.get(container) != FlexWrapValue.NOWRAP) {
			return false;
		}
		BoxAlignmentValue align = GridAlignmentProperty.get(item, GridAlignmentProperty.ALIGN_SELF);
		if (align == BoxAlignmentValue.AUTO) {
			align = GridAlignmentProperty.get(container, GridAlignmentProperty.ALIGN_ITEMS);
		}
		return (align == BoxAlignmentValue.NORMAL || align == BoxAlignmentValue.STRETCH)
				&& Margin.get(item, Side.LEFT) != KeywordValue.AUTO && Margin.get(item, Side.RIGHT) != KeywordValue.AUTO;
	}

	/** A length or percentage of {@code base}; {@code auto} for auto, NaN for keywords and an unknown base. */
	private static double length(final Value value, final double base, final double auto) {
		if (value instanceof AbsoluteLengthValue length) {
			return length.getLength();
		}
		if (value instanceof PercentageValue percentage) {
			return base * percentage.getRatio();
		}
		if (value instanceof CalcLengthValue calc) {
			return calc.getRatio() == 0 ? calc.getAbsolute() : calc.getAbsolute() + calc.getRatio() * base;
		}
		return value == KeywordValue.AUTO ? auto : Double.NaN;
	}
}
