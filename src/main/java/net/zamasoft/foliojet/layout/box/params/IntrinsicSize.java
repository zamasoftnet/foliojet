package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.layout.sizing.Sizing;

/**
 * Layout-side representation of intrinsic size keywords
 * (css-sizing-3 §2.2 {@code max-content}/{@code min-content}/{@code fit-content}/{@code fit-content(L)})
 * (2026-08-29).
 *
 * <p>
 * <b>Why these are carried separately instead of adding values to {@link LengthType}</b>:
 * {@link Dimension} packs the type into two bits, and there are 176 {@code LengthType} switches
 * throughout main. These keywords are not lengths but <b>sizing methods</b> that derive size from
 * content. Thus, {@code Dimension} remains AUTO (= follows the existing path for content-based
 * line-direction sizing), while {@link BlockParams}'s
 * {@code intrinsicLine}/{@code intrinsicMinLine}/{@code intrinsicMaxLine} carry only the method.
 * Resolution occurs in {@code AbstractStaticBlockBox.shrinkToFit} (floats, inline-blocks, and
 * normal-flow boxes with intrinsic sizing) and {@code AbsoluteBlockBox.shrinkToFit}. Block-axis
 * (page-direction) keywords mean content height (= auto) per the specification, so the mapper discards them.
 * </p>
 *
 * @param kind     sizing method
 * @param argument upper limit L for {@code fit-content(L)}; null if absent (uses the available size as the limit)
 */
public record IntrinsicSize(Kind kind, Length argument) {
	public enum Kind {
		MAX_CONTENT, MIN_CONTENT, FIT_CONTENT;
	}

	public static final IntrinsicSize MAX_CONTENT = new IntrinsicSize(Kind.MAX_CONTENT, null);
	public static final IntrinsicSize MIN_CONTENT = new IntrinsicSize(Kind.MIN_CONTENT, null);
	public static final IntrinsicSize FIT_CONTENT = new IntrinsicSize(Kind.FIT_CONTENT, null);

	/**
	 * Creates {@code fit-content(L)}. If L is auto, equivalent to no argument.
	 */
	public static IntrinsicSize fitContent(final Length bound) {
		if (bound == null || bound.getType() == LengthType.AUTO) {
			return FIT_CONTENT;
		}
		return new IntrinsicSize(Kind.FIT_CONTENT, bound);
	}

	/** Whether this has the upper limit L for {@code fit-content(L)}. */
	public boolean hasArgument() {
		return this.argument != null;
	}

	/**
	 * Returns the used line-direction size (content-box).
	 *
	 * @param minContent min-content size
	 * @param maxContent max-content size
	 * @param bound      fit-content limit (available size or resolved argument L)
	 * @return used size
	 */
	public double resolve(final double minContent, final double maxContent, final double bound) {
		return switch (this.kind) {
		case MAX_CONTENT -> maxContent;
		case MIN_CONTENT -> minContent;
		case FIT_CONTENT -> Sizing.fitContent(minContent, maxContent, bound);
		};
	}

	public String toString() {
		return this.argument == null ? this.kind.name() : this.kind.name() + "(" + this.argument + ")";
	}
}
