package net.zamasoft.foliojet.layout.constraint;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * Exclusion band from one placed floating box (added 2026-07-23 when making exclusion spaces ConstraintSpace
 * inputs, based on the design in `design consultation
 * -exclusion-zone-codex.txt`).
 *
 * <p>
 * Deliberately retains no {@code IFloatBox} (live box), so constraint inputs cannot keep referring to old boxes
 * after replay/rebuilding. {@code order} is a sequential insertion number allowing this value type alone to
 * reproduce the existing {@code BlockBuilder.FLOAT_COMP} stable-sort contract (floats with equal {@code pageEnd}
 * follow insertion order).
 * </p>
 *
 * <p>
 * {@code shape} is the resolved exclusion shape from {@code shape-outside} (2026-08-29). null means the existing
 * margin-box rectangle. Only line-box placement ({@link ExclusionSpace#scanLineBand}) inspects the shape through
 * {@link #lineSpanAt}; placement between floats and avoidance by blocks establishing a BFC continue using
 * rectangular {@code lineSpan}, per css-shapes-1 §4.1.
 * </p>
 */
public record FloatExclusion(long order, FloatSide side, AxisSpan pageSpan, AxisSpan lineSpan,
		ExclusionShape shape) {
	public FloatExclusion {
		if (side == null) {
			throw new IllegalArgumentException("side must not be null");
		}
		if (pageSpan == null || lineSpan == null) {
			throw new IllegalArgumentException("pageSpan/lineSpan must not be null");
		}
	}

	/** Exclusion band without a shape (margin-box rectangle). */
	public FloatExclusion(final long order, final FloatSide side, final AxisSpan pageSpan, final AxisSpan lineSpan) {
		this(order, side, pageSpan, lineSpan, null);
	}

	/**
	 * Line-direction range occupied by this float within page-direction band [pageStart, pageEnd]. Always {@link
	 * #lineSpan} without a shape. With a shape, null if the shape and band do not intersect (does not narrow lines in
	 * that band).
	 */
	public AxisSpan lineSpanAt(final double pageStart, final double pageEnd) {
		if (this.shape == null) {
			return this.lineSpan;
		}
		return this.shape.lineSpanAt(pageStart, pageEnd);
	}
}
