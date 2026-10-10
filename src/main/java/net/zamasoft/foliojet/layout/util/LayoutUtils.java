package net.zamasoft.foliojet.layout.util;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: LayoutUtils.java 1574 2018-10-26 02:44:00Z miyabe $
 */
public final class LayoutUtils {
	private LayoutUtils() {
		// unused
	}

	// magic number
	public static final double NONE = Double.MAX_VALUE * 0.958324758437;

	public static final boolean isNone(double v) {
		return v == NONE;
	}

	/**
	 * Sentinel meaning "how far this box paints on the sheet cannot be measured" (added 2026-07-27).
	 *
	 * <p>
	 * Returned by {@link net.zamasoft.foliojet.layout.box.IBox#paintedPageExtent} for
	 * <b>fragments whose contents are not yet finalized</b> (such as a text block tail awaiting
	 * source replay). Decisions treat it as "paints without limit," so blank-page suppression
	 * always chooses the <b>safe side</b> (= make the page break; do not discard the fragment).
	 * </p>
	 *
	 * <p>
	 * <b>Do not use this value as a layout dimension.</b>
	 * Use it only in comparisons that ask "is there anything to paint?"
	 * </p>
	 */
	public static final double PAINTS_UNKNOWN = Double.POSITIVE_INFINITY;

	/**
	 * Maximum absolute coordinate/dimension allowed in a display list (points).
	 *
	 * <p>
	 * 1e8 pt is about 3,500 km, nearly 7,000 times the PDF page-size limit of 14,400 pt (200 inches),
	 * well beyond any legitimate layout. Conversely, {@link #NONE} (around 10<sup>308</sup>),
	 * values derived from it by arithmetic, or {@code Double.MAX_VALUE} carried as "unconstrained"
	 * always exceed this limit if they leak into positions or finalized dimensions.
	 * </p>
	 */
	public static final double DRAWABLE_LIMIT = 1e8;

	/**
	 * Returns whether a value is safe to pass to drawing (finite and plausible for printed material).
	 *
	 * <p>
	 * <b>Why {@link #isNone(double)} is insufficient.</b>
	 * {@code isNone} matches only <b>the sentinel itself</b>, so it cannot detect
	 * <b>a sentinel transformed by scaling arithmetic</b> ({@code NONE / 2},
	 * {@code -NONE}, or a coordinate-transform scale). These pass through despite remaining
	 * garbage coordinates around 10<sup>307</sup>. {@code NaN} passes through the same hole
	 * ({@code isNone(NaN)} is false because {@code NaN != NONE}).
	 * Neither causes an exception; instead, <b>content silently disappears without appearing
	 * anywhere on the sheet</b>. This is the worst failure mode for business forms,
	 * so reject out-of-range values.
	 * </p>
	 *
	 * <p>
	 * (Addition such as {@code NONE + 10} is not an escape route: double spacing at this magnitude
	 * is around 10<sup>292</sup>, so not a single bit changes.)
	 * </p>
	 *
	 * <p>
	 * All comparisons with {@code NaN} are false, so these two inequalities reject it automatically
	 * (no explicit {@code isNaN} is needed).
	 * </p>
	 *
	 * <p>
	 * <b>Unprotected hole.</b> {@code NONE - NONE} becomes zero. A difference between sentinels
	 * can turn into a plausible coordinate, so a range check cannot detect it
	 * (documented in {@code DrawableRangeGuardTest}).
	 * </p>
	 *
	 * <p>
	 * Used to <b>fail closed through assertions</b>. Assertions are disabled in production, so the cost
	 * is zero. Do not apply this to constraints (e.g., {@code Double.MAX_VALUE} meaning "no upper limit"
	 * for {@code max-width}); constraints are legitimately huge.
	 * Apply it only to <b>positions and finalized dimensions</b>.
	 * </p>
	 */
	public static final boolean isDrawable(double v) {
		return v > -DRAWABLE_LIMIT && v < DRAWABLE_LIMIT;
	}

	public static final double THRESHOLD = .5;

	/**
	 * Returns a negative value for a &lt; b, positive for a &gt; b, and zero for a = b.<br>
	 * Use this for comparisons in line wrapping, floats, line positioning, and page-break control
	 * to prevent incorrect decisions due to numerical error.
	 *
	 * @param a
	 * @param b
	 * @return
	 */
	public static int compare(double a, double b) {
		// Treat differences below 0.5 as equal
		// IE appears to truncate, while Firefox rounds to one decimal place
		// Note: rounding before comparison can produce different decisions for the same difference,
		// so even after determining that content overflows the page,
		// decisions could contradict each other during box splitting
		double diff = a - b;
		if (diff < THRESHOLD && diff > -THRESHOLD) {
			return 0;
		}
		return a < b ? -1 : 1;
	}

	/**
	 * Returns true if the box's line-axis size depends on content (requiring a measurement pass).
	 * This predicate family centralizes two-pass decisions (ARCHITECTURE.md §5.2b).
	 * For absolute positioning, only ABSOLUTE specifications count as fixed
	 * (percentages and inset-derived sizes depend on content).
	 *
	 * <p>
	 * Determine axes from <b>the box's own writing direction</b> (2026-08-10).
	 * They must match shrinkToFit's internal axes (this.params.flow). Using the parent's flow
	 * misaligns axes for orthogonal blocks, incorrectly concluding "no measurement needed from
	 * the specified page-axis size, even though the box's own line axis is auto."
	 * The line-axis fit-content then became zero from empty measurements, making the box and content
	 * disappear (a horizontal block with height inside a vertical writing document).
	 * Equivalent to the old behavior for boxes sharing the parent's axes.
	 *
	 * @param blockBox target box
	 * @return true if measurement is required
	 */
	public static boolean needsIntrinsicSizing(AbstractContainerBox blockBox) {
		final BlockParams params = blockBox.getBlockParams();
		if (params.hasIntrinsicLine()) {
			// An intrinsic-size keyword in any of width/min-width/max-width
			// (2026-08-29): even if width is definite, as in width:10pt; min-width:max-content,
			// min/max still require measurement
			return true;
		}
		final LengthType lineType = params.size.getLineType(params.flow);
		if (blockBox.getPos().getType() == PosType.ABSOLUTE) {
			return lineType != LengthType.ABSOLUTE;
		}
		if (net.zamasoft.foliojet.layout.sizing.CyclicPercent.active()
				&& (lineType == LengthType.RELATIVE || lineType == LengthType.MIXED)) {
			// While an intrinsic size is measured, a percentage width is cyclic and counts as auto (2026-10-09)
			return true;
		}
		return lineType == LengthType.AUTO;
	}

	/**
	 * Draws text.
	 *
	 * @param gc
	 * @param fontSize
	 * @param text
	 * @param x
	 * @param y
	 * @param width
	 */
	public static void drawText(GC gc, FontPolicyList fontPolicy, double fontSize, String text, double x, double y,
			double width) throws GraphicsException {
		assert isDrawable(x) : "描画位置xが異常: " + x;
		assert isDrawable(y) : "描画位置yが異常: " + y;
		assert isDrawable(width) : "描画幅が異常: " + width;
		try (final var gcState = gc.begin()) {
			gc.transform(AffineTransform.getTranslateInstance(x, y));

			PageLayoutGlyphHandler lineHandler = new PageLayoutGlyphHandler(gc);
		lineHandler.setLineAdvance(width);

		TextLayoutHandler tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules(null), lineHandler);
		tlf.setFontFamilies(FontFamilyList.SERIF);
			tlf.setFontPolicy(fontPolicy);
			tlf.setFontSize(fontSize);
			tlf.characters(text);
			tlf.flush();

			lineHandler.close();
		}
	}

	/**
	 * Calculates a length.
	 *
	 * @param length
	 * @param ref
	 * @return
	 */
	public static double computeLength(Length length, double ref) {
		switch (length.getType()) {
		case RELATIVE:
			return length.getLength() * ref;
		case ABSOLUTE:
			return length.getLength();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return length.getLength() + length.getRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Calculates insets, treating AUTO as zero.
	 *
	 * @param ainsets
	 * @param insets
	 * @param refSize
	 */
	public static void computeMarginsAutoToZero(AbsoluteInsets ainsets, Insets insets, double refSize) {
		double top, right, bottom, left;
		switch (insets.getTopType()) {
		case ABSOLUTE:
			top = insets.getTop();
			break;
		case RELATIVE:
			top = insets.getTop() * refSize;
			break;
		case MIXED:
			top = insets.getTop() + insets.getTopRatio() * refSize;
			break;
		case AUTO:
			top = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getRightType()) {
		case ABSOLUTE:
			right = insets.getRight();
			break;
		case RELATIVE:
			right = insets.getRight() * refSize;
			break;
		case MIXED:
			right = insets.getRight() + insets.getRightRatio() * refSize;
			break;
		case AUTO:
			right = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getBottomType()) {
		case ABSOLUTE:
			bottom = insets.getBottom();
			break;
		case RELATIVE:
			bottom = insets.getBottom() * refSize;
			break;
		case MIXED:
			bottom = insets.getBottom() + insets.getBottomRatio() * refSize;
			break;
		case AUTO:
			bottom = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getLeftType()) {
		case ABSOLUTE:
			left = insets.getLeft();
			break;
		case RELATIVE:
			left = insets.getLeft() * refSize;
			break;
		case MIXED:
			left = insets.getLeft() + insets.getLeftRatio() * refSize;
			break;
		case AUTO:
			left = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		ainsets.top = top;
		ainsets.right = right;
		ainsets.bottom = bottom;
		ainsets.left = left;
	}

	public static void computePaddings(AbsoluteInsets ainsets, Insets insets, double refSize) {
		double top, right, bottom, left;
		switch (insets.getTopType()) {
		case ABSOLUTE:
			top = insets.getTop();
			break;
		case RELATIVE:
			top = insets.getTop() * refSize;
			break;
		case MIXED:
			top = insets.getTop() + insets.getTopRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getRightType()) {
		case ABSOLUTE:
			right = insets.getRight();
			break;
		case RELATIVE:
			right = insets.getRight() * refSize;
			break;
		case MIXED:
			right = insets.getRight() + insets.getRightRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getBottomType()) {
		case ABSOLUTE:
			bottom = insets.getBottom();
			break;
		case RELATIVE:
			bottom = insets.getBottom() * refSize;
			break;
		case MIXED:
			bottom = insets.getBottom() + insets.getBottomRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getLeftType()) {
		case ABSOLUTE:
			left = insets.getLeft();
			break;
		case RELATIVE:
			left = insets.getLeft() * refSize;
			break;
		case MIXED:
			left = insets.getLeft() + insets.getLeftRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}
		ainsets.top = top;
		ainsets.right = right;
		ainsets.bottom = bottom;
		ainsets.left = left;
	}

	/**
	 * Calculates the width of a Dimension. Returns NaN for AUTO.
	 *
	 * @param size
	 * @param ref
	 * @return
	 */
	public static double computeDimensionWidth(Dimension size, double ref) {
		switch (size.getWidthType()) {
		case RELATIVE:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getWidth() * ref;
		case ABSOLUTE:
			return size.getWidth();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getWidth() + size.getWidthRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Calculates the height of a Dimension. Returns NaN for AUTO.
	 *
	 * @param size
	 * @param ref
	 * @return
	 */
	public static double computeDimensionHeight(Dimension size, double ref) {
		switch (size.getHeightType()) {
		case RELATIVE:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getHeight() * ref;
		case ABSOLUTE:
			return size.getHeight();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getHeight() + size.getHeightRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Calculates the line-axis size of a Dimension. Returns NONE for AUTO.
	 *
	 * @param size dimensions
	 * @param flow writing direction determining the axes
	 * @param ref  reference for relative values
	 * @return line-axis size
	 */
	public static double computeDimensionLine(Dimension size, WritingMode flow, double ref) {
		return flow.isVertical() ? computeDimensionHeight(size, ref) : computeDimensionWidth(size, ref);
	}

	/**
	 * Returns the physical X coordinate of a child placed at logical coordinates
	 * (line axis: childLineStart; page axis: childPageStart/childPageEnd)
	 * relative to the parent's physical origin x.
	 *
	 * <h2>Only this method and {@link #drawY} know the page-axis direction</h2>
	 *
	 * <p>
	 * Writing direction consists of <b>two independent attributes</b>:
	 * <b>which physical dimension is the page axis</b> and <b>whether that axis advances positively
	 * or negatively</b>. {@link WritingMode#isVertical()} answers the former and is used throughout
	 * the code, but <b>only this method and {@link #drawY} convert the latter into physical
	 * coordinates across all of main</b> (verified by measurement on 2026-07-25).
	 * </p>
	 *
	 * <table border="1">
	 * <caption>Page-axis dimension and direction</caption>
	 * <tr><th>Writing direction</th><th>Page axis</th><th>Direction</th><th>Added to X</th></tr>
	 * <tr><td>TB (horizontal writing)</td><td>Y</td><td>Positive (top → bottom)</td><td>Line axis only</td></tr>
	 * <tr><td>RL (vertical writing, right → left)</td><td>X</td><td><b>Negative</b></td>
	 * <td>{@code parentPageExtent - childPageEnd}</td></tr>
	 * <tr><td>LR (vertical writing, left → right)</td><td>X</td><td>Positive</td><td>{@code childPageStart}</td></tr>
	 * </table>
	 *
	 * <p>
	 * LR has the same "just add the start" form as TB not by coincidence,
	 * but because both <b>advance in the positive direction</b>.
	 * </p>
	 *
	 * <p>
	 * <b>Do not bypass this method with custom sign calculations.</b>
	 * Handwritten {@code x += parent inner size; x - child pageAxis - child size} is an RL-only
	 * formula and fails for LR (when implementing LR on 2026-07-25, ten handwritten instances
	 * were found across FlowContainer, Floatings, tables, and rescue splitting;
	 * all were consolidated into this method).
	 * </p>
	 *
	 * @param flow             writing direction
	 * @param x                parent's physical X origin
	 * @param parentPageExtent parent's page-axis size
	 * @param childPageStart   child's page-axis start
	 * @param childPageEnd     child's page-axis end (start + child size)
	 * @param childLineStart   child's line-axis start
	 * @return child's physical X coordinate
	 */
	public static double drawX(WritingMode flow, double x, double parentPageExtent, double childPageStart,
			double childPageEnd, double childLineStart) {
		return switch (flow) {
		case TB -> x + childLineStart;
		case RL -> x + parentPageExtent - childPageEnd;
		case LR -> x + childPageStart;
		};
	}

	/**
	 * Returns the page-axis <b>direction</b> (+1 or -1).
	 *
	 * <p>
	 * {@link #drawX} converts "logical position → physical coordinate," but operations that
	 * <b>shift something already in physical coordinates along the page axis</b>
	 * (e.g., cell {@code vertical-align}) need the same direction.
	 * TB and LR are positive; <b>only RL is negative</b>.
	 * Added 2026-07-25 for vertical-lr support; previously, code branched on {@code isVertical()}
	 * and used {@code -=} specifically for RL.
	 * </p>
	 *
	 * @param flow writing direction
	 * @return +1 for a positive page-axis direction, -1 for a negative direction (RL)
	 */
	public static double pageAxisSign(WritingMode flow) {
		return flow == WritingMode.RL ? -1 : 1;
	}

	/**
	 * Returns the physical Y coordinate of a child at a logical position relative to the
	 * parent's physical origin y. See {@link #drawX} for direction handling
	 * (in vertical writing the page axis is X, so Y always depends only on the line axis,
	 * with no difference between RL and LR).
	 *
	 * @param flow           writing direction
	 * @param y              parent's physical Y origin
	 * @param childPageStart child's page-axis start
	 * @param childLineStart child's line-axis start
	 * @return child's physical Y coordinate
	 */
	public static double drawY(WritingMode flow, double y, double childPageStart, double childLineStart) {
		return flow.isVertical() ? y + childLineStart : y + childPageStart;
	}

	/**
	 * Returns the physical start of the logical inline interval {@code [start, end]}.
	 *
	 * <p>
	 * When sideways inline progression is bottom to top, preserves logical positions and reverses
	 * to {@code lineExtent - end} only when converting to physical coordinates.
	 * Normal vertical writing keeps its existing coordinates. To map a point, call with
	 * {@code start == end}. The inverse mapping is the same.
	 * </p>
	 *
	 * @param params     line's layout direction
	 * @param lineExtent physical size of the inline axis
	 * @param start      logical interval start
	 * @param end        logical interval end
	 * @return position from the physical top
	 */
	public static double inlineToPhysical(final AbstractTextParams params, final double lineExtent,
			final double start, final double end) {
		if (params.flow.isVertical()
				&& net.zamasoft.foliojet.layout.box.params.TypesettingMode.isHorizontal(params.flow,
						params.writingModeVariant)
				&& net.zamasoft.foliojet.layout.box.params.TypesettingMode.inlineProgression(params.flow,
						params.writingModeVariant, params.direction)
						== net.zamasoft.foliojet.layout.box.params.TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
			return lineExtent - end;
		}
		return start;
	}

	public static double computeInsetsTop(Insets insets, double ref) {
		switch (insets.getTopType()) {
		case ABSOLUTE:
			return insets.getTop();
		case RELATIVE:
			return insets.getTop() * ref;
		case MIXED:
			return insets.getTop() + insets.getTopRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsLeft(Insets insets, double ref) {
		switch (insets.getLeftType()) {
		case ABSOLUTE:
			return insets.getLeft();
		case RELATIVE:
			return insets.getLeft() * ref;
		case MIXED:
			return insets.getLeft() + insets.getLeftRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsRight(Insets insets, double ref) {
		switch (insets.getRightType()) {
		case ABSOLUTE:
			return insets.getRight();
		case RELATIVE:
			return insets.getRight() * ref;
		case MIXED:
			return insets.getRight() + insets.getRightRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsBottom(Insets insets, double ref) {
		switch (insets.getBottomType()) {
		case ABSOLUTE:
			return insets.getBottom();
		case RELATIVE:
			return insets.getBottom() * ref;
		case MIXED:
			return insets.getBottom() + insets.getBottomRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeOffsetX(Offset offset, IBox containerBox) {
		switch (offset.getXType()) {
		case ABSOLUTE:
			return offset.getX();
		case RELATIVE:
			// this.offsetX = pos.offset.getX() * container.getInnerWidth();
			// break;
		case MIXED:
			// Like RELATIVE, this path is unimplemented (existing TODO; for now, also treat MIXED
			// like RELATIVE to at least avoid an exception).
		case AUTO:
			return 0;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeOffsetY(Offset offset, IBox containerBox) {
		switch (offset.getYType()) {
		case ABSOLUTE:
			return offset.getY();
		case RELATIVE:
			// this.offsetY = pos.offset.getY() * container.getInnerWidth();
			// break;
		case MIXED:
			// Like RELATIVE, this path is unimplemented (existing TODO; for now, also treat MIXED
			// like RELATIVE to at least avoid an exception).
		case AUTO:
			return 0;
		default:
			throw new IllegalStateException();
		}
	}

	public static void calculateReplacedSize(Builder builder, AbstractReplacedBox replacedBox) {
		//
		// ■ Width and height calculation
		//
		double refWidth, refHeight, refMaxWidth, refMaxHeight;
		final AbstractContainerBox containerBox = builder.getFlowBox();
		final BlockParams params = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		// The inner flow of position:relative also serves as a context box for absolute positioning,
		// but remains the containing block for normal-flow replaced elements.
		// Search the context side only when the root at the builder boundary is itself the context;
		// do not omit definite sizes held by nested contexts from the flow search.
		final boolean rootContext = containerBox == builder.getRootBox()
				&& containerBox == builder.getContextBox();
		// The neutral wrapper of a flex item is not the element's containing block: its padding resolves against the
		// flex container, as the wrapper's own insets do (2026-10-10)
		final double insetBase = containerBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexItemBox item
				&& item.isNeutralLineFill() && !LayoutUtils.isNone(item.getInsetBase()) ? item.getInsetBase() : lineSize;
		replacedBox.calculateFrame(insetBase);
		if (params.flow.isVertical()) {
			// Vertical writing
			AbstractContainerBox box;
			if (rootContext) {
				box = builder.getFixedWidthContextBox();
			} else {
				box = builder.getFixedWidthFlowBox();
			}
			if (box == null) {
				if (builder.getContextBox().getType() == BoxType.TABLE_CELL && builder instanceof BlockBuilder) {
					// When a page break occurred inside a cell
					return;
				}
				refMaxWidth = refWidth = LayoutUtils.NONE;
				refMaxHeight = refHeight = LayoutUtils.NONE;
			} else {
				refWidth = box.getType()== BoxType.PAGE ? LayoutUtils.NONE : pageAxisReference(box, true);
				refMaxWidth = pageAxisReference(box, true);
				// Find a flow when line width is unreliable because this is not normal flow
				if (builder.isTwoPass()) {
					refMaxHeight =refHeight = LayoutUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL
						&& !sizedShrinkToFitRoot(builder, containerBox)) {
					if (rootContext) {
						box = builder.getFixedHeightContextBox();
					} else {
						box = builder.getFixedHeightFlowBox();
					}
					if (box == null) {
						refMaxHeight =refHeight = LayoutUtils.NONE;
					} else {
						refMaxHeight =refHeight = box.getLineSize();
					}
				} else {
					refMaxHeight =refHeight = lineSize;
				}
			}
		} else {
			// Horizontal writing
			AbstractContainerBox box;
			if (rootContext) {
				box = builder.getFixedHeightContextBox();
			} else {
				box = builder.getFixedHeightFlowBox();
			}
			if (box == null) {
				if (builder.getContextBox().getType() == BoxType.TABLE_CELL && builder instanceof BlockBuilder) {
					// When a page break occurred inside a cell
					return;
				}
				refMaxHeight = refHeight = LayoutUtils.NONE;
				refMaxWidth = refWidth = LayoutUtils.NONE;
			} else {
				refHeight = box.getType()== BoxType.PAGE ? LayoutUtils.NONE : pageAxisReference(box, false);
				refMaxHeight = pageAxisReference(box, false);
				// Find a flow when line width is unreliable because this is not normal flow
				if (builder.isTwoPass()) {
					refMaxWidth = refWidth = LayoutUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL
						&& !sizedShrinkToFitRoot(builder, containerBox)) {
					if (rootContext) {
						box = builder.getFixedWidthContextBox();
					} else {
						box = builder.getFixedWidthFlowBox();
					}
					if (box == null) {
						refMaxWidth = refWidth = LayoutUtils.NONE;
					} else {
						refMaxWidth = refWidth = box.getLineSize();
					}
				} else {
					refMaxWidth = refWidth = lineSize;
				}
			}
		}
		// A line-axis percentage against the scratch page's line is cyclic in the max-content measurement and counts as
		// auto: the natural size (2026-10-09). In the min-content one it resolves against 0, as compressible replaced
		// elements contribute 0 (CSS Sizing 3 §5.2). Resolved against 10^6, a width: 100% image made its inline-block
		// as wide as the page.
		if (net.zamasoft.foliojet.layout.sizing.CyclicPercent.maxContent()) {
			if (params.flow.isVertical()) {
				if (net.zamasoft.foliojet.layout.sizing.CyclicPercent.cyclic(refHeight)) {
					refMaxHeight = refHeight = LayoutUtils.NONE;
				}
			} else if (net.zamasoft.foliojet.layout.sizing.CyclicPercent.cyclic(refWidth)) {
				refMaxWidth = refWidth = LayoutUtils.NONE;
			}
		}
		// Fill the neutral wrapper (flex item) along the line axis (2026-08-09). The wrapper has
		// already resolved authored percentages against the flex container (NeutralTransfer), so
		// reapplying the same percentage to the wrapper's inner size in the child applies it twice
		// (width:50% shrinks to the equivalent of 25%). Replace the percentage reference so the child's
		// expression returns exactly the wrapper's inner size; 100% is a fixed point, unchanged. Absolute lengths
		// are not applied twice, so leave them alone.
		// The wrapper is the element's border box and holds its margins (NeutralTransfer, 2026-10-10): the element
		// leaves its own margins out, and for content-box its border and padding come off the wrapper's inner size
		final net.zamasoft.foliojet.layout.box.impl.FlexItemBox wrapper = containerBox
				instanceof net.zamasoft.foliojet.layout.box.impl.FlexItemBox item && item.isNeutralLineFill() ? item : null;
		if (wrapper != null) {
			wrapper.setReplacedChild(replacedBox);
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = replacedBox.getFrame();
			frame.margin.top = frame.margin.right = frame.margin.bottom = frame.margin.left = 0;
			if (!LayoutUtils.isNone(wrapper.getPageBase())) {
				// Percentages along the page axis are of the flex container (FlexItemBox.pageBase)
				if (params.flow.isVertical()) {
					refWidth = refMaxWidth = wrapper.getPageBase();
				} else {
					refHeight = refMaxHeight = wrapper.getPageBase();
				}
			}
			final Dimension size = replacedBox.getReplacedParams().size;
			final boolean borderBox = replacedBox.getReplacedParams().boxSizing == BoxSizingMode.BORDER_BOX;
			if (params.flow.isVertical()) {
				final double innerHeight = withoutBox(containerBox.getInnerHeight(),
						borderBox ? 0 : frame.getBorderHeight());
				if (size.getHeightType() == LengthType.RELATIVE && size.getHeight() != 0) {
					refHeight = refMaxHeight = innerHeight / size.getHeight();
				} else if (size.getHeightType() == LengthType.MIXED && size.getHeightRatio() != 0) {
					refHeight = refMaxHeight = (innerHeight - size.getHeight()) / size.getHeightRatio();
				}
			} else {
				final double innerWidth = withoutBox(containerBox.getInnerWidth(), borderBox ? 0 : frame.getBorderWidth());
				if (size.getWidthType() == LengthType.RELATIVE && size.getWidth() != 0) {
					refWidth = refMaxWidth = innerWidth / size.getWidth();
				} else if (size.getWidthType() == LengthType.MIXED && size.getWidthRatio() != 0) {
					refWidth = refMaxWidth = (innerWidth - size.getWidth()) / size.getWidthRatio();
				}
			}
		}
		replacedBox.calculateSize(refWidth, refHeight, refMaxWidth, refMaxHeight);
		if (wrapper != null && !builder.isTwoPass()
				&& (wrapper.isReplacedMainFill() || wrapper.isReplacedCrossFill())) {
			// In a row the flex algorithm sized the wrapper, its constraints included: the element fills it along the
			// main axis, whatever its own width, as the flex item itself does in Chrome (2026-10-10). Its own
			// percentages resolved again against the wrapper shrank it (max-width: 35% of its own item), and with a
			// flex-basis, flex-grow or flex-shrink an absolute width stayed as it was beside the item's size. In a
			// column the wrapper's width is the item's cross size, stretched or from the element's width and limits:
			// left to calculateSize, width 40pt, height 80pt and min-height: 90% took CSS 2.1's ratio-keeping table and
			// came out 45 x 90 (Chrome 40 x 90)
			final boolean vertical = params.flow.isVertical();
			final double inner = vertical ? containerBox.getInnerHeight() : containerBox.getInnerWidth();
			if (!LayoutUtils.isNone(inner)) {
				final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = replacedBox.getFrame();
				replacedBox.fillLine(vertical,
						Math.max(0, inner - (vertical ? frame.getBorderHeight() : frame.getBorderWidth())),
						vertical ? refWidth : refHeight, vertical ? refMaxWidth : refMaxHeight);
			}
		}
	}

	/** The inner size of a flex item's wrapper less its replaced child's border and padding; NONE stays NONE. */
	private static double withoutBox(final double inner, final double box) {
		return LayoutUtils.isNone(inner) ? inner : inner - box;
	}

	/**
	 * Whether the container is the root of a single-pass builder laying out the content of an inline-block or an
	 * absolutely positioned box, whose size is then determined (2026-10-09). Its inner size, not the nearest ancestor
	 * with a specified size, is the percentage basis: a width: 100% image in an inline-block was as wide as the page
	 * (CSS Sizing 3 §5.2). Floats are already in the list of the caller.
	 */
	private static boolean sizedShrinkToFitRoot(final Builder builder, final AbstractContainerBox containerBox) {
		if (builder.isTwoPass() || containerBox != builder.getRootBox()) {
			return false;
		}
		final PosType type = containerBox.getPos().getType();
		return type == PosType.INLINE || type == PosType.ABSOLUTE;
	}

	/**
	 * Containing block's inner page-axis size used as the percentage-size reference for replaced elements
	 * (2026-10-04). An absolutely positioned box determines its page-axis size after laying out its content,
	 * and its inner size is zero during layout, so use a size determined independently of the content
	 * (if unavailable, resolve as NONE=auto). Using the inner size directly as the reference made
	 * {@code height: 100%} images zero-sized and invisible.
	 */
	private static double pageAxisReference(final AbstractContainerBox box, final boolean vertical) {
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute
				&& absolute.getBlockParams().flow.isVertical() == vertical) {
			return absolute.getDefinitePageAxis();
		}
		return vertical ? box.getInnerWidth() : box.getInnerHeight();
	}

	public static double getMaxAdvance(final AbstractContainerBox box) {
		final BlockParams params = box.getBlockParams();
		final double lineSize;
		if (params.flow.isVertical()) {
			// Vertical writing
			lineSize = box.getInnerHeight();
		} else {
			// Horizontal writing
			lineSize = box.getInnerWidth();
		}
		return lineSize;
	}

	/**
	 * Minimum <b>used value</b> of {@code column-width} (1 px = 0.75 pt).
	 *
	 * <p>
	 * css-multicol-1 §3.1 states that "{@code column-width:0} is valid as a specified and computed value,
	 * but <b>the used value never falls below 1 px</b>." Actual browsers behave the same way.
	 * </p>
	 *
	 * <p>
	 * This concerns <b>termination, not appearance</b>. Allowing zero causes division by zero in
	 * {@link #getColumnCount}, attempts to create {@code (int)Infinity} = 2,147,483,647 columns,
	 * and effectively never terminates (WPT {@code css-multicol/zero-column-width-layout.html}).
	 * </p>
	 */
	private static final double MIN_COLUMN_WIDTH = 0.75;

	public static int getColumnCount(final AbstractContainerBox box) {
		final BlockParams params = box.getBlockParams();
		if (LayoutUtils.isNone(params.columns.width)) {
			return params.columns.count;
		}
		final double lineSize = LayoutUtils.getMaxAdvance(box);
		// Apply the used-value minimum before dividing (see {@link #MIN_COLUMN_WIDTH}).
		// Since gap cannot be negative, the divisor is now always positive
		final double width = Math.max(params.columns.width, MIN_COLUMN_WIDTH);
		if (width >= lineSize) {
			return 1;
		}
		final int count = (int) Math.floor((lineSize + params.columns.gap) / (width + params.columns.gap));
		// The branch above has width < lineSize, so count should be >= 1, but
		// ensure it cannot become zero or negative when lineSize is NaN or huge
		// because callers interpret a column count of zero as "not multi-column," changing the meaning
		return Math.max(1, count);
	}


}
