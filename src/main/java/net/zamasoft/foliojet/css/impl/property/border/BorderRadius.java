package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.box.LogicalSide;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.layout.box.params.RectBorder.Radius;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * border-*-radius properties (four corners).
 *
 * @author MIYABE Tatsuhiko
 */
public final class BorderRadius extends AbstractPrimitivePropertyInfo {
	public static final BorderRadius TOP_LEFT = new BorderRadius(Corner.TOP_LEFT);

	public static final BorderRadius TOP_RIGHT = new BorderRadius(Corner.TOP_RIGHT);

	public static final BorderRadius BOTTOM_RIGHT = new BorderRadius(Corner.BOTTOM_RIGHT);

	public static final BorderRadius BOTTOM_LEFT = new BorderRadius(Corner.BOTTOM_LEFT);

	private static final BorderRadius[] BY_CORNER = { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT };

	/**
	 * Flow-relative corner radii (css-logical-1 §6.4, 2026-10-08): border-[block side]-[inline side]-radius. Which
	 * physical corner they round follows the element's writing-mode and direction; until then they were aliases of
	 * the horizontal ltr corners. The two radii are not swapped: as for the physical corners, the first is the
	 * horizontal one.
	 */
	public static final BorderRadius START_START = new BorderRadius("start-start", LogicalSide.BLOCK_START,
			LogicalSide.INLINE_START);

	public static final BorderRadius START_END = new BorderRadius("start-end", LogicalSide.BLOCK_START,
			LogicalSide.INLINE_END);

	public static final BorderRadius END_START = new BorderRadius("end-start", LogicalSide.BLOCK_END,
			LogicalSide.INLINE_START);

	public static final BorderRadius END_END = new BorderRadius("end-end", LogicalSide.BLOCK_END,
			LogicalSide.INLINE_END);

	private static final BorderRadius[] LOGICAL = { START_START, START_END, END_START, END_END };

	private final LogicalSide blockSide, inlineSide;

	private BorderRadius(Corner corner) {
		super("border-" + corner.text() + "-radius");
		this.blockSide = null;
		this.inlineSide = null;
	}

	private BorderRadius(final String logical, final LogicalSide blockSide, final LogicalSide inlineSide) {
		super("border-" + logical + "-radius");
		this.blockSide = blockSide;
		this.inlineSide = inlineSide;
	}

	/** The physical corner a flow-relative radius rounds under the element's writing-mode and direction. */
	private Corner physicalCorner(final CSSStyle style) {
		final Side a = this.blockSide.toPhysical(style);
		final Side b = this.inlineSide.toPhysical(style);
		final boolean top = a == Side.TOP || b == Side.TOP;
		final boolean left = a == Side.LEFT || b == Side.LEFT;
		return top ? (left ? Corner.TOP_LEFT : Corner.TOP_RIGHT) : (left ? Corner.BOTTOM_LEFT : Corner.BOTTOM_RIGHT);
	}

	public static Radius get(CSSStyle style, Corner corner) {
		BorderRadius info = BY_CORNER[corner.ordinal()];
		for (final BorderRadius logical : LOGICAL) {
			// The later of the physical and the flow-relative declaration wins (css-logical-1 §4)
			if (logical.physicalCorner(style) == corner && LogicalSide.logicalWins(style, info, logical)) {
				info = logical;
				break;
			}
		}
		final BorderRadiusValue r = (BorderRadiusValue) style.get(info);
		// Keep percentage components as ratios; resolve them during rendering once dimensions are known.
		final double hr, hrRatio, vr, vrRatio;
		if (r.hr instanceof PercentageValue percent) {
			hr = 0;
			hrRatio = percent.getRatio();
		} else {
			hr = ((AbsoluteLengthValue) r.hr).getLength();
			hrRatio = 0;
		}
		if (r.vr instanceof PercentageValue percent) {
			vr = 0;
			vrRatio = percent.getRatio();
		} else {
			vr = ((AbsoluteLengthValue) r.vr).getLength();
			vrRatio = 0;
		}
		return Radius.create(hr, vr, hrRatio, vrRatio);
	}

	public Value getDefault(CSSStyle style) {
		return BorderRadiusValue.ZERO_RADIUS;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final BorderRadiusValue r = (BorderRadiusValue) value;
		// Percentages remain ratios even in computed values (emExToAbsoluteLength passes % through).
		final QuantityValue hr = (QuantityValue) ValueUtils.emExToAbsoluteLength(r.hr, style);
		final QuantityValue vr = (QuantityValue) ValueUtils.emExToAbsoluteLength(r.vr, style);
		return BorderRadiusValue.create(hr, vr);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final BorderRadiusValue value = BorderValueUtils.toBorderRadius(ua, tokens);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
