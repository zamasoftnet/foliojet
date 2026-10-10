package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Continuation state of a block fragment (ARCHITECTURE §5.7 C1).
 *
 * <p>
 * For a page-axis split, represents which frame edges the preceding and continuation fragments
 * retain (cut surfaces equivalent to box-decoration-break: slice), the continuation fragment's
 * remaining specified and minimum sizes, and the preceding fragment's page-axis usage.
 * This types the implicit state previously embedded in the horizontal/vertical mirror versions
 * of splitPage (about 65 lines × 2). It supplies the material to reconstruct continuation
 * fragments without transporting the box tree (C1: chain reinstantiation from the log).
 * </p>
 *
 * <p>
 * For a page break through multi-column layout (columnSpanning = former FLAGS_COLUMN), keeps
 * the frame intact and expands the preceding fragment's usage to the actual content size
 * (frame continuation policy; scheduled for removal together with this state in C4).
 * However, when the cut line is at or before the inner start edge
 * (={@code pageLimit <= 0}, so the preceding fragment takes no content), this policy does not
 * apply; see {@link #of} for the reason.
 * </p>
 *
 * @param prevFrame      preceding fragment's frame (with the page-end edge removed)
 * @param nextFrame      continuation fragment's frame (with the page-start edge removed)
 * @param nextSize       continuation fragment's specified size (remainder on the page axis)
 * @param nextMinSize    continuation fragment's minimum size (remainder on the page axis)
 * @param prevPageExtent preceding fragment's page-axis usage
 * @param nextMaxPageExtent continuation fragment's page-axis maximum (content box; what the box's max-size left after
 *                       the preceding fragment, 2026-10-10), or {@code Double.MAX_VALUE} without one
 * @author MIYABE Tatsuhiko
 */
public record FragmentState(AbsoluteRectFrame prevFrame, AbsoluteRectFrame nextFrame, Dimension nextSize,
		Dimension nextMinSize, double prevPageExtent, double nextMaxPageExtent) {

	/** Without a page-axis maximum for the continuation. */
	public FragmentState(final AbsoluteRectFrame prevFrame, final AbsoluteRectFrame nextFrame, final Dimension nextSize,
			final Dimension nextMinSize, final double prevPageExtent) {
		this(prevFrame, nextFrame, nextSize, nextMinSize, prevPageExtent, Double.MAX_VALUE);
	}

	/**
	 * This state for a box held by its max-size (2026-10-10): the preceding fragment uses {@code prevPageExtent} (no more
	 * than the box itself reaches, when only its overflowing content runs past the cut) and the continuation may take
	 * what is left of {@code maxPageExtent} (content box). A float with {@code height: auto} and
	 * {@code max-height: 60pt} split at 30pt went on with all its content on the next pages (30 + 80 + 50pt); Chrome stops
	 * the box at 60pt in all and lets the rest of the content overflow.
	 */
	public FragmentState withMaxPageExtent(final double prevPageExtent, final double maxPageExtent) {
		return new FragmentState(this.prevFrame, this.nextFrame, this.nextSize, this.nextMinSize, prevPageExtent,
				Math.max(0, maxPageExtent - prevPageExtent));
	}

	/**
	 * {@link #withMaxPageExtent} for a box that ends, with its end frame, before the cut: only its overflowing content
	 * runs past it (2026-10-10). The preceding fragment keeps {@code frame} whole and the continuation, which holds only
	 * that content, draws no frame edge on the page axis.
	 */
	public FragmentState withBoxEndingBefore(final AbsoluteRectFrame frame, final WritingMode flow,
			final double prevPageExtent, final double maxPageExtent) {
		final AbsoluteRectFrame next = flow.isVertical() ? frame.cut(true, false, true, false)
				: frame.cut(false, true, false, true);
		return new FragmentState(frame, next, this.nextSize, this.nextMinSize, prevPageExtent,
				Math.max(0, maxPageExtent - prevPageExtent));
	}

	/**
	 * Calculates fragment state for a split (pure function).
	 *
	 * <p>
	 * <b>The frame continuation policy applies only if the preceding fragment takes content</b>
	 * (2026-07-27). A page break through multi-column layout ({@code columnSpanning}) does not cut
	 * the frames of either fragment, so the continuation fragment <b>inherits the entire start frame</b>.
	 * Applying this when the cut line is at or before the inner start edge ({@code pageLimit <= 0})
	 * lets the preceding fragment take no content, while reconstructing the continuation fragment
	 * with exactly the original geometry (same start frame = same start position).
	 * The next page yields the same decision, <b>generating blank pages one by one forever</b>.
	 * Pages are retained in {@code PDFWriterImpl.pageOutputs}, so heap usage grows monotonically,
	 * eventually causing OutOfMemoryError (9 minutes 15 seconds and several GB for a 1.2 KB document;
	 * five fuzzing seeds with tiny pages containing multi-column layout had the same cause).
	 * Following css-break-3 §4.4, "each fragmentainer takes a nonzero amount of content," this
	 * degenerate case falls back to a normal split (removing the start edge). This removes the
	 * continuation fragment's start frame and actually increases the space on the next page,
	 * guaranteeing progress. The normal {@code pageLimit > 0} path is unchanged.
	 * </p>
	 *
	 * @param flow              writing direction
	 * @param columnSpanning    page break through multi-column layout (frame continuation policy)
	 * @param frame             frame before splitting
	 * @param size              specified size
	 * @param minSize           minimum size
	 * @param pageExtent        page-axis content size before splitting (width in vertical writing)
	 * @param pageLimit         split position (from the inner edge)
	 * @param contentSize       actual page-axis content size
	 * @param specifiedPageSize whether the page-axis size is specified
	 * @return fragment state
	 */
	public static FragmentState of(final WritingMode flow, final boolean columnSpanning,
			final AbsoluteRectFrame frame, final Dimension size, final Dimension minSize, final double pageExtent,
			final double pageLimit, final double contentSize, final boolean specifiedPageSize) {
		return of(flow, columnSpanning, frame, size, minSize, pageExtent, pageLimit, contentSize, specifiedPageSize,
				false);
	}

	/**
	 * Variant of
	 * {@link #of(WritingMode, boolean, AbsoluteRectFrame, Dimension, Dimension, double, double, double, boolean)}
	 * for a fixed-size box whose first indivisible content moves in full.
	 */
	public static FragmentState of(final WritingMode flow, final boolean columnSpanning,
			final AbsoluteRectFrame frame, final Dimension size, final Dimension minSize, final double pageExtent,
			final double pageLimit, final double contentSize, final boolean specifiedPageSize,
			final boolean preserveSpecifiedPageSize) {
		return of(flow, columnSpanning, frame, size, minSize, pageExtent, pageLimit, pageLimit, contentSize,
				specifiedPageSize, preserveSpecifiedPageSize);
	}

	/** Column reservation reduces only the content limit; fragment sizes are derived from ownerExtent. */
	public static FragmentState of(final WritingMode flow, final boolean columnSpanning,
			final AbsoluteRectFrame frame, final Dimension size, final Dimension minSize, final double pageExtent,
			final double contentLimit, final double ownerExtent, final double contentSize, final boolean specifiedPageSize,
			final boolean preserveSpecifiedPageSize) {
		final boolean vertical = flow.isVertical();
		double limit = Math.max(ownerExtent, 0);

		final AbsoluteRectFrame prevFrame, nextFrame;
		if (columnSpanning && LayoutUtils.compare(contentLimit, 0) > 0) {
			// For multiple columns, keep the boundary and size the height to the content
			prevFrame = nextFrame = frame;
			limit = Math.max(limit, contentSize);
		} else if (vertical) {
			// Vertical writing: the page axis runs right to left. Drop the preceding fragment's left (end) edge
			prevFrame = frame.cut(true, true, true, false);
			nextFrame = frame.cut(true, false, true, true);
		} else {
			// Horizontal writing: drop the preceding fragment's bottom (end) edge
			prevFrame = frame.cut(true, true, false, true);
			nextFrame = frame.cut(false, true, true, true);
		}

		final Dimension nextSize;
		if (specifiedPageSize) {
			// When the first indivisible content moves entirely to the next fragment, the preceding fragment
			// has not consumed the specified size. Subtracting the cut line clips a fixed-height thumbnail image
			// to a continuation box only a few px tall, making it disappear.
			final double consumed = preserveSpecifiedPageSize ? 0 : limit;
			final double rest = Math.max(0, pageExtent - consumed);
			// Keep the type/value on the line axis (the non-page axis), but for MIXED
			// (calc() mixing absolute lengths and percentages), also preserve the percentage component, or
			// resolution after fragmentation loses the ratio component (2026-07-19, found by external review).
			nextSize = vertical
					? Dimension.create(rest, 0, size.getHeight(), size.getHeightRatio(), LengthType.ABSOLUTE,
							size.getHeightType())
					: Dimension.create(size.getWidth(), size.getWidthRatio(), rest, 0, size.getWidthType(),
							LengthType.ABSOLUTE);
		} else {
			nextSize = size;
		}

		final Dimension nextMinSize;
		if ((vertical ? minSize.getWidthType() : minSize.getHeightType()) != LengthType.AUTO) {
			// Split the page-axis minimum size into the remainder
			final double spec = vertical ? minSize.getWidth() : minSize.getHeight();
			final double rest = Math.max(0, Math.min(spec, pageExtent) - limit);
			nextMinSize = vertical
					? Dimension.create(rest, 0, minSize.getHeight(), minSize.getHeightRatio(), LengthType.ABSOLUTE,
							minSize.getHeightType())
					: Dimension.create(minSize.getWidth(), minSize.getWidthRatio(), rest, 0, minSize.getWidthType(),
							LengthType.ABSOLUTE);
		} else {
			nextMinSize = minSize;
		}
		return new FragmentState(prevFrame, nextFrame, nextSize, nextMinSize, limit);
	}
}
