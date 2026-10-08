package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineBlockQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.text.GlyphMeasureStep;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.layout.control.LineBreak;

/**
 * Measurer for intrinsic sizes (IntrinsicSizes). Receives events from TwoPassBlockBuilder
 * (the recorder) and accumulates min/max content sizes (implements the SizingMode consumer
 * slot in the ideal design §5.2b; planned replacement with actual layout replay in M2c).
 */
final class IntrinsicMeasurer {
	/** For context references from flowStack. */
	private final TwoPassBlockBuilder builder;

	/**
	 * Minimum line width, maximum line width, minimum page height
	 */
	private double minLineSize = 0, maxLineSize = 0, minPageSize = 0;

	private double maxStartFloatAdvance = 0, maxEndFloatAdvance = 0;

	/**
	 * Line-axis contributions of orthogonal children (tables/blocks with a different writing-mode
	 * axis) (2026-10-05). Emulated measurement does not know that child's line-axis dimension
	 * (height for a vertical-writing parent), so it substitutes table width or one line.
	 * {@link #sizes()} includes these as before; {@link #sizesWithoutOrthogonal()} excludes them.
	 * The caller that lays out and remeasures (DocumentBuilder) substitutes actual sizes.
	 */
	private double orthogonalMinLine = 0, orthogonalMaxLine = 0;

	private boolean orthogonalContent;

	private int columnCount = 1;

	/**
	 * Whether {@link #minLineSize} includes multiplication by the column count (2026-07-28).
	 * See {@link net.zamasoft.foliojet.layout.sizing.IntrinsicSizes#columnInflated()}.
	 */
	private boolean columnInflated = false;

	/**
	 * Current line width.
	 */
	private double lineAxis = 0;

	private double atomicLineSize = 0;

	private double letterSpacing = 0;

	private double textIndent;

	private boolean blockHead;

	/**
	 * Line-axis and page-axis frame widths of normal-flow block boxes.
	 */
	private double lineFrame = 0, pageFrame = 0;

	private LineBreak toLineFeed = null;

	private final List<IBox> inlineStack = new ArrayList<IBox>();

	/**
	 * Stacks {@link #minLineSize}/{@link #maxLineSize} from just before entering a flow,
	 * and the line-axis size that flow contributes outward (including ancestor frames)
	 * (2026-08-04).
	 *
	 * <p>
	 * <b>A fixed-width flow fixes its externally visible size to that width</b>, without
	 * propagating overflowing inner content outward. Previously, {@code endFlow} did this
	 * by <b>assigning</b> {@code maxLineSize = minLineSize = flowBox.getWidth()}, which
	 * <b>also erased previously measured sibling sizes</b>. With children ordered
	 * "wide box → narrow box", the last narrow box's width became the overall width,
	 * making table cells, absolutely positioned boxes, and flex items narrower than
	 * their content (found when material-web tab headings overlapped).
	 */
	private final List<double[]> flowSizeStack = new ArrayList<double[]>();

	IntrinsicMeasurer(TwoPassBlockBuilder builder) {
		this.builder = builder;
	}

	IntrinsicSizes sizes() {
		return new IntrinsicSizes(Math.max(this.minLineSize, this.orthogonalMinLine),
				Math.max(this.maxLineSize, this.orthogonalMaxLine), this.minPageSize, this.columnInflated);
	}

	/** Intrinsic sizes excluding orthogonal child contributions (see {@link #orthogonalMinLine}). */
	IntrinsicSizes sizesWithoutOrthogonal() {
		return new IntrinsicSizes(this.minLineSize, this.maxLineSize, this.minPageSize, this.columnInflated);
	}

	/** Whether there are orthogonal children (tables/blocks). */
	boolean hasOrthogonalContent() {
		return this.orthogonalContent;
	}

	void start(AbstractContainerBox containerBox) {
		this.textIndent = containerBox.getTextIndent();
		this.blockHead = true;
		this.letterSpacing = LayoutUtils.computeLength(containerBox.getBlockParams().letterSpacing,
				this.builder.getFlowBox().getLineSize());
	}

	void startFlow(final FlowBlockBox flowBox, final AbstractContainerBox containerBox) {
		assert this.inlineStack.isEmpty();
		BlockParams params = containerBox.getBlockParams();
		FlowPos pos = (FlowPos) flowBox.getPos();
		this.clearFloatAdvance(pos.clear);

		// From outside, multi-column content needs its line-axis size multiplied by the column count.
		// **Scale only the newly added portion**: {@link #lineFrame} accumulates ancestor frames,
		// already scaled at each level. Previously, each startFlow
		// multiplied this again, causing **exponential** growth with nesting depth
		// (fixed 2026-07-26).
		//
		// Observed in a document with two columns nested inside four:
		// lineFrame grew 33 → 132 → 532 → 4256, and
		// maxLineSize used those intermediate values. The resulting shrink-to-fit measurement was
		// 31 times the sheet size, placing columns outside the sheet (REVIEW-STATISTICS §12).
		double frameAdd = flowBox.getFrame().getFrameLineExtent(params.flow);
		if (flowBox.getColumnCount() > 0) {
			frameAdd += flowBox.getBlockParams().columns.gap * (flowBox.getColumnCount() - 1);
		}
		final double lineSize = this.lineFrame + flowBox.getLineExtent(params.flow) * this.columnCount;
		// Save the values before entry and the size this flow itself contributes (used by endFlow).
		this.flowSizeStack.add(new double[] { this.minLineSize, this.maxLineSize, lineSize });
		this.lineFrame += frameAdd * this.columnCount;
		this.pageFrame += flowBox.getFrame().getFramePageExtent(params.flow);
		assert !LayoutUtils.isNone(this.lineFrame);
		if (this.lineFrame > this.minLineSize) {
			this.minLineSize = this.lineFrame;
		}
		if (this.pageFrame > this.minPageSize) {
			this.minPageSize = this.pageFrame;
		}
		if (lineSize > this.maxLineSize) {
			this.maxLineSize = lineSize;
		}
		this.textIndent = flowBox.getTextIndent();
		this.blockHead = true;

		if (flowBox.getColumnCount() >= 2 || flowBox.getBlockParams().columns.count >= 2) {
			// From here, inner minimum content sizes accumulate multiplied by the column count (2026-07-28).
			// **For auto-height multi-column layout, getColumnCount() stays 1** (ColumnsContainer
			// holds the count), so also set the flag from the specified count (columns.count).
			// Otherwise the shrinkToFit multi-column clamp does not apply,
			// placing float:right inside vertical-writing columns before line start (outside the sheet)
			// (2026-08-21, sweep seed 615921).
			this.columnInflated = true;
		}
		this.columnCount *= flowBox.getColumnCount();
		// The original code read getFlowBox().getLineSize() after flowStack.add(flowBox),
		// but getFlowBox() after the push is flowBox itself, so this is equivalent.
		this.letterSpacing = LayoutUtils.computeLength(flowBox.getBlockParams().letterSpacing,
				flowBox.getLineSize());
	}

	void endFlow(final AbstractBlockBox flowBox) {
		assert this.inlineStack.isEmpty();
		// builder.getFlowBox() is the parent box after flowStack.remove.
		AbstractContainerBox containerBox = this.builder.getFlowBox();
		BlockParams params = containerBox.getBlockParams();
		BlockParams flowParams = flowBox.getBlockParams();
		this.columnCount /= flowBox.getColumnCount();
		// Symmetrically with startFlow, remove **only the added portion**, using the same multiplier.
		if (flowBox.getColumnCount() > 0) {
			this.lineFrame -= flowBox.getBlockParams().columns.gap * (flowBox.getColumnCount() - 1)
					* this.columnCount;
		}

		final double[] entered = this.flowSizeStack.remove(this.flowSizeStack.size() - 1);
		final boolean fixedLineSize;
		switch (params.flow) {
		case WritingMode.TB:
			// Horizontal writing
			this.lineFrame -= flowBox.getFrame().getFrameWidth() * this.columnCount;
			this.pageFrame -= flowBox.getFrame().getFrameHeight();
			fixedLineSize = flowParams.size.getWidthType() == LengthType.ABSOLUTE;
			break;
		case WritingMode.LR:
		case WritingMode.RL:
			// Vertical writing
			this.lineFrame -= flowBox.getFrame().getFrameHeight() * this.columnCount;
			this.pageFrame -= flowBox.getFrame().getFrameWidth();
			fixedLineSize = flowParams.size.getHeightType() == LengthType.ABSOLUTE;
			break;
		default:
			throw new IllegalStateException();
		}
		if (fixedLineSize) {
			// **Fixed-width flow**. Do not propagate inner content outward; retain only the size
			// this flow itself contributes. **Do not erase sibling sizes** (2026-08-04).
			this.minLineSize = Math.max(entered[0], entered[2]);
			this.maxLineSize = Math.max(entered[1], entered[2]);
		}
		{
			// max-width (absolute lengths only) caps what the flow contributes, its frame included (2026-10-09,
			// css-sizing outer contribution; before the min-width floor below, which wins over it). Shrink-to-fit
			// blocks get it from the measured intrinsics, but grid and flex items took their content's width whole: a
			// pre { max-inline-size: 300px } with long lines made its grid item, and the grid with it, as wide as the
			// lines (Chrome caps it at 300px). Percentages and calc() have no basis yet and stay uncapped, as for
			// min-width.
			final WritingMode selfFlow = flowParams.flow;
			final net.zamasoft.foliojet.layout.box.params.Dimension maxSpec = flowParams.maxSize;
			if (maxSpec.getLineType(selfFlow) == LengthType.ABSOLUTE) {
				final double bb = flowParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? flowBox.getFrame().getBorderLineExtent(selfFlow)
						: 0;
				final double cap = this.lineFrame + (Math.max(0, maxSpec.getLineLength(selfFlow) - bb)
						+ flowBox.getFrame().getFrameLineExtent(selfFlow)) * this.columnCount;
				this.minLineSize = Math.max(entered[0], Math.min(this.minLineSize, cap));
				this.maxLineSize = Math.max(entered[1], Math.min(this.maxLineSize, cap));
			}
		}
		{
			// min-width (absolute lengths only) sets a floor on minimum content size (2026-08-08,
			// css-sizing outer contribution). The maximum naturally carries it through the resolved width
			// (lineSize in startFlow), but the minimum used content min only,
			// so a nested grid with min-width:100px (NHK navigation section pills)
			// had its wrapper flex-shrunk to text width, overlapping pill backgrounds with adjacent
			// tabs. Do not count %/calc because their basis is unresolved.
			final WritingMode selfFlow = flowParams.flow;
			final net.zamasoft.foliojet.layout.box.params.Dimension minSpec = flowParams.minSize;
			if (minSpec.getLineType(selfFlow) == LengthType.ABSOLUTE && minSpec.getLineLength(selfFlow) > 0) {
				final double bb = flowParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? flowBox.getFrame().getBorderLineExtent(selfFlow)
						: 0;
				final double outer = this.lineFrame + (Math.max(0, minSpec.getLineLength(selfFlow) - bb)
						+ flowBox.getFrame().getBorderLineExtent(selfFlow)) * this.columnCount;
				this.minLineSize = Math.max(this.minLineSize, outer);
			}
		}

		assert !LayoutUtils.isNone(this.lineFrame);

		this.textIndent = 0;
		this.blockHead = false;
		this.letterSpacing = LayoutUtils.computeLength(flowBox.getBlockParams().letterSpacing,
				this.builder.getFlowBox().getLineSize());
	}

	/**
	 * Line-axis min-content contribution of replaced elements (2026-08-08). For replaced
	 * elements with a percentage line-axis size (cyclic percentage), contribute 0 instead
	 * of the resolved value (from natural dimensions), following css-sizing's cyclic %
	 * handling and Chrome. Previously, a large width:100% image raised the minimum to its
	 * natural width, preventing a flex container item from shrinking and pushing its adjacent
	 * fixed-width flex-shrink:0 sidebar off the sheet (the real bug in the breaking-news section
	 * on the asahi.com home page). calc(absolute + %) contributes only the absolute component.
	 *
	 * <p>
	 * <b>Handle cyclic % in max sizes the same way</b> (2026-08-10). Even with auto size,
	 * a % limit such as max-width:100% lets the element shrink to its container, so its min
	 * contribution is 0 (for MIXED, clamp to the absolute component as the upper bound).
	 * Previously, the value resolved from natural dimensions raised min, allowing a fit-content
	 * container (a reference illustration page in a vertical-writing book = an orthogonal block)
	 * to override the paper width limit and overflow the sheet.
	 */
	private static double lineMinContribution(final double usedLine,
			final LengthType lineType, final double lineSpecAbsolute,
			final LengthType maxLineType, final double maxLineSpecAbsolute) {
		if (lineType == LengthType.RELATIVE) {
			return 0;
		}
		if (lineType == LengthType.MIXED) {
			return Math.max(0, lineSpecAbsolute);
		}
		if (maxLineType == LengthType.RELATIVE) {
			return 0;
		}
		if (maxLineType == LengthType.MIXED) {
			return Math.min(usedLine, Math.max(0, maxLineSpecAbsolute));
		}
		return usedLine;
	}

	void bound(final AbstractReplacedBox replacedBox) {
		switch (replacedBox.getPos().getType()) {
		case FLOW: {
			// Static/relative positioning
			AbstractContainerBox containerBox = this.builder.getFlowBox();
			IFlowBox flowBox = (IFlowBox) replacedBox;
			FlowPos pos = (FlowPos) flowBox.getPos();
			this.clearFloatAdvance(pos.clear);
			LayoutUtils.calculateReplacedSize(this.builder, replacedBox);

			double minLineAxis, maxLineAxis = 0, minPageAxis;
			BlockParams params = containerBox.getBlockParams();
			if (params.flow.isVertical()) {
				// Vertical writing
				minLineAxis = lineMinContribution(replacedBox.getHeight(),
						replacedBox.getReplacedParams().size.getHeightType(),
						replacedBox.getReplacedParams().size.getHeight(),
						replacedBox.getReplacedParams().maxSize.getHeightType(),
						replacedBox.getReplacedParams().maxSize.getHeight());
				minPageAxis = replacedBox.getWidth();
				maxLineAxis = replacedBox.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE
						? replacedBox.getReplacedParams().size.getHeight()
						: replacedBox.getHeight();
			} else {
				// Horizontal writing
				minLineAxis = lineMinContribution(replacedBox.getWidth(),
						replacedBox.getReplacedParams().size.getWidthType(),
						replacedBox.getReplacedParams().size.getWidth(),
						replacedBox.getReplacedParams().maxSize.getWidthType(),
						replacedBox.getReplacedParams().maxSize.getWidth());
				minPageAxis = replacedBox.getHeight();
				maxLineAxis = replacedBox.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE
						? replacedBox.getReplacedParams().size.getWidth()
						: replacedBox.getWidth();
			}
			minPageAxis += this.pageFrame;
			minLineAxis *= this.columnCount;
			minLineAxis += this.lineFrame;

			maxLineAxis *= this.columnCount;
			maxLineAxis += this.lineFrame;

			assert !LayoutUtils.isNone(minLineAxis);
			if (minLineAxis > this.minLineSize) {
				this.minLineSize = minLineAxis;
			}
			if (minPageAxis > this.minPageSize) {
				this.minPageSize = minPageAxis;
			}
			if (maxLineAxis > this.maxLineSize) {
				this.maxLineSize = maxLineAxis;
			}
		}
			break;
		case FLOAT: {
			// Float
			AbstractContainerBox containerBox = this.builder.getFlowBox();
			IFloatBox floatingBox = (IFloatBox) replacedBox;
			this.clearFloatAdvance(floatingBox.getFloatPos().clear);
			LayoutUtils.calculateReplacedSize(this.builder, replacedBox);

			// Count the float exclusion area (advance) using used sizes; treat % as 0 only for
			// the minLineSize contribution (the usedLineAxis/minLineAxis distinction below).
			double minLineAxis, minPageAxis, maxLineAxis = 0;
			final double usedLineAxis;
			BlockParams params = containerBox.getBlockParams();
			if (params.flow.isVertical()) {
				// Vertical writing
				usedLineAxis = replacedBox.getHeight();
				minLineAxis = lineMinContribution(usedLineAxis,
						replacedBox.getReplacedParams().size.getHeightType(),
						replacedBox.getReplacedParams().size.getHeight(),
						replacedBox.getReplacedParams().maxSize.getHeightType(),
						replacedBox.getReplacedParams().maxSize.getHeight());
				minPageAxis = replacedBox.getWidth();
				if (replacedBox.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getHeight();
				}
			} else {
				// Horizontal writing
				usedLineAxis = replacedBox.getWidth();
				minLineAxis = lineMinContribution(usedLineAxis,
						replacedBox.getReplacedParams().size.getWidthType(),
						replacedBox.getReplacedParams().size.getWidth(),
						replacedBox.getReplacedParams().maxSize.getWidthType(),
						replacedBox.getReplacedParams().maxSize.getWidth());
				minPageAxis = replacedBox.getHeight();
				if (replacedBox.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getWidth();
				}
			}
			assert !LayoutUtils.isNone(minLineAxis);
			if (minLineAxis > this.minLineSize) {
				this.minLineSize = minLineAxis;
			}
			if (minPageAxis > this.minPageSize) {
				this.minPageSize = minPageAxis;
			}

			switch (floatingBox.getFloatPos().floating) {
			case FloatSide.START: {
				this.maxStartFloatAdvance += usedLineAxis;
			}
				break;
			case FloatSide.END: {
				this.maxEndFloatAdvance += usedLineAxis;
			}
				break;
			default:
				throw new IllegalStateException();
			}
			double xmaxLineAxis = this.maxStartFloatAdvance + this.maxEndFloatAdvance;
			if (xmaxLineAxis > maxLineAxis) {
				maxLineAxis = xmaxLineAxis;
			}
			maxLineAxis *= this.columnCount;
			maxLineAxis += this.lineFrame;
			if (maxLineAxis > this.maxLineSize) {
				this.maxLineSize = maxLineAxis;
			}
		}
			break;

		case ABSOLUTE:
			// Absolute positioning
			replacedBox.calculateFrame(this.builder.getFlowBox().getLineSize());
			break;

		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Content-box contributions of tables, Grid, and Flex.
	 * <p>
	 * **Always add {@link #lineFrame} (accumulated frames of this box and its ancestors)**
	 * (2026-08-08). Omitting it undercounted flex containers (or their ancestor wrappers)
	 * with padding/borders by the frame size during cell measurement for auto table layout.
	 * Subtracting the frame at bind time then made **content consistently narrower by that
	 * frame size**. Found as clipped filenames in GitHub file listings (a flex column with
	 * padding-right:16px). Float contributions ({@code floating}) already add lineFrame;
	 * use the same structure here.
	 */
	private void spannedContribution(final IntrinsicSizes sizes) {
		this.spannedContribution(sizes, null);
	}

	private void spannedContribution(final IntrinsicSizes sizes, final AbstractContainerBox box) {
		this.columnInflated |= sizes.columnInflated();
		double min = sizes.minContent();
		double max = sizes.maxContent();
		if (box != null) {
			// Clamp the contribution using the container’s own width/min-width/max-width
			// (absolute lengths only) (2026-08-08, css-sizing outer contribution).
			// Without this, a nested grid wrapper with min-width:100px (NHK navigation
			// section pills) was flex-shrunk to text width,
			// making the pill background drawn at 100 px overlap the adjacent tab.
			// As before, do not count %/calc while the container main axis is unresolved.
			final WritingMode flow = box.getBlockParams().flow;
			final double bb = box.getBlockParams().boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
					? box.getFrame().getBorderLineExtent(flow)
					: 0;
			final net.zamasoft.foliojet.layout.box.params.Dimension size = box.getBlockParams().size;
			if (size.getLineType(flow) == LengthType.ABSOLUTE) {
				min = max = Math.max(0, size.getLineLength(flow) - bb);
			}
			final net.zamasoft.foliojet.layout.box.params.Dimension maxSize = box.getBlockParams().maxSize;
			if (maxSize.getLineType(flow) == LengthType.ABSOLUTE) {
				final double v = Math.max(0, maxSize.getLineLength(flow) - bb);
				min = Math.min(min, v);
				max = Math.min(max, v);
			}
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize = box.getBlockParams().minSize;
			if (minSize.getLineType(flow) == LengthType.ABSOLUTE) {
				final double v = Math.max(0, minSize.getLineLength(flow) - bb);
				min = Math.max(min, v);
				max = Math.max(max, v);
			}
		}
		this.minLineSize = Math.max(this.minLineSize, min * this.columnCount + this.lineFrame);
		this.maxLineSize = Math.max(this.maxLineSize, max * this.columnCount + this.lineFrame);
	}

	void table(final IntrinsicSizes tableSizes, final boolean orthogonal) {
		if (orthogonal) {
			// Orthogonal table sizes follow its line axis (width), not the parent's. Track the old contribution separately.
			this.orthogonalContent = true;
			this.columnInflated |= tableSizes.columnInflated();
			this.orthogonalMinLine = Math.max(this.orthogonalMinLine,
					tableSizes.minContent() * this.columnCount + this.lineFrame);
			this.orthogonalMaxLine = Math.max(this.orthogonalMaxLine,
					tableSizes.maxContent() * this.columnCount + this.lineFrame);
			return;
		}
		// As before, do not clamp table widths; the table algorithm owns them.
		this.spannedContribution(tableSizes);
	}

	/** Content-box contribution of the entire Grid (Grid G3d2; same structure as table). */
	void grid(final IntrinsicSizes gridSizes, final AbstractContainerBox gridBox) {
		this.spannedContribution(gridSizes, gridBox);
	}

	/** Content-box contribution of the entire Flex (Flex F1f; same structure as grid). */
	void flex(final IntrinsicSizes flexSizes, final AbstractContainerBox flexBox) {
		this.spannedContribution(flexSizes, flexBox);
	}

	void fitFloating(TwoPassBlockBuilder childBuilder) {
		FloatBlockBox floatingBox = (FloatBlockBox) childBuilder.getRootBox();
		this.clearFloatAdvance(floatingBox.getFloatPos().clear);

		BlockParams params = floatingBox.getBlockParams();
		BlockParams flowParams = this.builder.getFlowBox().getBlockParams();
		final WritingMode floatFlow = flowParams.flow;
		double minLineAxis, maxLineAxis;
		// Resolved ledger #1 (2026-07-17): previously, only min in vertical writing added the
		// page-axis frame (FrameWidth), while max used the line axis. Logical axis
		// accessors unify both writing modes and use the line-axis frame for both min/max.
		if (params.size.getLineType(floatFlow) != LengthType.AUTO) {
			minLineAxis = maxLineAxis = floatingBox.getLineExtent(floatFlow);
		} else {
			final IntrinsicSizes childSizes = childBuilder.getIntrinsicSizes();
			this.columnInflated |= childSizes.columnInflated();
			final double frameLine = floatingBox.getFrame().getFrameLineExtent(floatFlow);
			minLineAxis = childSizes.minContent() + frameLine;
			maxLineAxis = childSizes.maxContent() + frameLine;
		}
		assert !LayoutUtils.isNone(maxLineAxis);
		if (minLineAxis > this.minLineSize) {
			this.minLineSize = minLineAxis;
		}

		switch (floatingBox.getFloatPos().floating) {
		case FloatSide.START:
			this.maxStartFloatAdvance += maxLineAxis;
			break;
		case FloatSide.END:
			this.maxEndFloatAdvance += maxLineAxis;
			break;
		default:
			throw new IllegalStateException();
		}
		maxLineAxis = this.maxStartFloatAdvance + this.maxEndFloatAdvance;
		maxLineAxis *= this.columnCount;
		maxLineAxis += this.lineFrame;
		if (maxLineAxis > this.maxLineSize) {
			this.maxLineSize = maxLineAxis;
		}
	}

	/**
	 * Adds the outer contribution of a nested shrink-to-fit block to the parent.
	 *
	 * <p>For orthogonal flow, the child's page axis becomes the parent's line axis,
	 * so min/max-content must not be added directly. Apply the same axis conversion
	 * as inline-block measurement ({@link #control(TextControl, TwoPass)}).</p>
	 */
	void fitBlock(final TwoPassBlockBuilder childBuilder) {
		final AbstractContainerBox block = childBuilder.getRootBox();
		final WritingMode parentFlow = this.builder.getFlowBox().getBlockParams().flow;
		final WritingMode childFlow = block.getBlockParams().flow;
		final IntrinsicSizes childSizes = childBuilder.getIntrinsicSizes();
		this.columnInflated |= childSizes.columnInflated();

		final double frameLine = block.getFrame().getFrameLineExtent(parentFlow);
		final double framePage = block.getFrame().getFramePageExtent(parentFlow);
		final double minLine;
		final double maxLine;
		final double minPage;
		if (parentFlow.isVertical() == childFlow.isVertical()) {
			minLine = childSizes.minContent() + frameLine;
			maxLine = childSizes.maxContent() + frameLine;
			minPage = childSizes.minPage() + framePage;
		} else {
			// The child’s minimum page-axis thickness becomes its line-axis width as seen by the parent.
			minLine = maxLine = childSizes.minPage() + frameLine;
			minPage = childSizes.minContent() + framePage;
			// One-line minimum thickness is not the child's actual height; track it for layout remeasurement (2026-10-05).
			this.orthogonalContent = true;
			this.orthogonalMinLine = Math.max(this.orthogonalMinLine, minLine * this.columnCount + this.lineFrame);
			this.orthogonalMaxLine = Math.max(this.orthogonalMaxLine, maxLine * this.columnCount + this.lineFrame);
			this.minPageSize = Math.max(this.minPageSize, minPage + this.pageFrame);
			return;
		}

		this.minLineSize = Math.max(this.minLineSize,
				minLine * this.columnCount + this.lineFrame);
		this.maxLineSize = Math.max(this.maxLineSize,
				maxLine * this.columnCount + this.lineFrame);
		this.minPageSize = Math.max(this.minPageSize, minPage + this.pageFrame);
	}

	/**
	 * Accounts for the width of one glyph. {@link GlyphMeasureStep} defines CSS width
	 * formula components (the sole definition; connecting the intrinsic path in increment 5
	 * of the 85-point plan completed unification of the three width accounting paths).
	 *
	 * <p>
	 * <b>Intrinsic measurement differs from line accounting in two ways</b>, so add components
	 * separately rather than using the canonical two stages,
	 * {@code baseAndSpacing()+adjustment()}:
	 * </p>
	 *
	 * <ul>
	 * <li>Japanese spacing adjustment A2: count boundary gaps <b>only in max-content (lines)</b>.
	 * A Japanese/Latin boundary is a break opportunity whose gap disappears at the break,
	 * so exclude it from min-content (atomic units). The underestimate of at most 0.125 ic
	 * is recorded as a conservative approximation. Count trim in both min/max
	 * (trim pairs are indivisible under kinsoku (line-breaking rules), T1a).</li>
	 * <li>Preserve the old addition order: gap → (base−trim)+letter-spacing.
	 * The last ULP can differ from the canonical order (base+letter-spacing), so the order
	 * cannot change under the completion requirement that all golden files match byte for
	 * byte (regeneration prohibited).</li>
	 * </ul>
	 */
	void glyph(final double baseAdvance, final double autospaceGap, final double punctuationTrim) {
		final GlyphMeasureStep step = new GlyphMeasureStep(baseAdvance, this.letterSpacing, autospaceGap,
				punctuationTrim);
		if (step.autospaceGap() > 0) {
			this.lineAxis += step.autospaceGap();
		}
		double advance = step.baseAdvance() - step.punctuationTrim();
		advance += step.letterSpacing();
		this.atomicLineSize += advance;
		this.lineAxis += advance;
		double minPageAxis = this.getCurrentLineHeight() + this.pageFrame;
		if (minPageAxis > this.minPageSize) {
			this.minPageSize = minPageAxis;
		}
	}

	void control(final TextControl quad, final TwoPass inlineBlockMeasure) {
		// The original code set toLineFeed before recording (records.add), but
		// measurement state and records are independent, so reordering is equivalent.
		if (quad instanceof LineBreak) {
			this.toLineFeed = (LineBreak) quad;
		}

		double minAdvance, maxAdvance, pageSize;
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			final BlockParams cParams = this.builder.getFlowBox().getBlockParams();
			if (quad instanceof InlineReplacedQuad) {
				// Image
				final AbstractReplacedBox box = (AbstractReplacedBox) inlineQuad.getBox();
				maxAdvance = quad.getAdvance();
				assert LayoutUtils.isDrawable(maxAdvance) : "置換要素の未確定な行寸法が固有寸法へ混入しました: advance="
							+ maxAdvance + ", width=" + box.getWidth() + ", height=" + box.getHeight()
							+ ", innerWidth=" + box.getInnerWidth() + ", innerHeight=" + box.getInnerHeight()
							+ ", frameWidth=" + box.getFrame().getFrameWidth() + ", frameHeight="
							+ box.getFrame().getFrameHeight() + ", size=" + box.getReplacedParams().size + ", minSize="
							+ box.getReplacedParams().minSize + ", maxSize=" + box.getReplacedParams().maxSize
							+ ", intrinsic=" + box.getReplacedParams().image.getWidth() + "x"
							+ box.getReplacedParams().image.getHeight();
				minAdvance = 0;
				if (cParams.flow.isVertical()) {
					// Vertical writing
					if (!box.getReplacedParams().size.getHeightType().needsReference()
							&& !box.getReplacedParams().maxSize.getHeightType().needsReference()) {
						minAdvance = maxAdvance;
					}
					if (box.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
						if(box.getReplacedParams().size.getHeight() > maxAdvance) {
							maxAdvance = box.getReplacedParams().size.getHeight();
						}
					}
					pageSize = box.getWidth();
				} else {
					// Horizontal writing
					if (!box.getReplacedParams().size.getWidthType().needsReference()
							&& !box.getReplacedParams().maxSize.getWidthType().needsReference()) {
						minAdvance = maxAdvance;
					}
					if (box.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
						if(box.getReplacedParams().size.getWidth() > maxAdvance) {
							maxAdvance = box.getReplacedParams().size.getWidth();
						}
					}
					pageSize = box.getHeight();
				}
			} else if (quad instanceof InlineBlockQuad) {
				// Inline block
				final AbstractContainerBox box = (AbstractContainerBox) inlineQuad.getBox();
				final double lineFrame = box.getFrame().getFrameLineExtent(cParams.flow);
				final double pageFrame = box.getFrame().getFramePageExtent(cParams.flow);
				// Inline block
				final BlockParams params = (BlockParams) box.getParams();
				final TwoPass stfBuilder = inlineBlockMeasure;
				if (stfBuilder == null) {
					// Synthetic box without a measurement builder (ruby unit). Its dimensions
					// resolve during construction, so use only the box’s actual size
					// (2026-07-25; paired with the isPreMeasured branch
					// in TwoPassBlockBuilder.control).
					minAdvance = maxAdvance = lineFrame;
					pageSize = pageFrame;
				} else {
					final IntrinsicSizes stfSizes = stfBuilder.getIntrinsicSizes();
					this.columnInflated |= stfSizes.columnInflated();
					if (cParams.flow.isVertical() == params.flow.isVertical()) {
						minAdvance = stfSizes.minContent() + lineFrame;
						maxAdvance = stfSizes.maxContent() + lineFrame;
						pageSize = stfSizes.minPage() + pageFrame;
					} else {
						// Tate-chu-yoko / vertical text in horizontal writing
						minAdvance = maxAdvance = stfSizes.minPage() + pageFrame;
						pageSize = stfSizes.minContent() + lineFrame;
					}
				}
				minAdvance = Math.max(minAdvance, box.getLineExtent(params.flow));
				maxAdvance = Math.max(maxAdvance, box.getLineExtent(params.flow));
				pageSize = Math.max(pageSize, box.getPageExtent(params.flow));
			} else {
				if (inlineQuad instanceof InlineStartQuad) {
					this.inlineStack.add(inlineQuad.getBox());
					final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
					this.letterSpacing = LayoutUtils.computeLength(inlineStartQuad.box.getTextParams().letterSpacing,
							this.builder.getFlowBox().getLineSize());
				} else if (inlineQuad instanceof InlineEndQuad) {
					this.inlineStack.remove(this.inlineStack.size() - 1);
					AbstractTextParams params;
					if (this.inlineStack.isEmpty()) {
						params = this.builder.getFlowBox().getBlockParams();
					} else {
						final InlineBox box = (InlineBox) this.inlineStack.get(this.inlineStack.size() - 1);
						params = box.getTextParams();
					}
					this.letterSpacing = LayoutUtils.computeLength(params.letterSpacing,
							this.builder.getFlowBox().getLineSize());
				}
				minAdvance = maxAdvance = quad.getAdvance();
				pageSize = inlineQuad.getBox().getPageExtent(cParams.flow);
			}
		} else if (quad instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
			// leader() L1: both min-content/max-content use one pattern cycle
			// (do not read the allocated advance, to prevent leakage during remeasurement).
			minAdvance = maxAdvance = leader.minAdvance;
			pageSize = 0;
		} else {
			minAdvance = maxAdvance = quad.getAdvance();
			pageSize = 0;
		}
		pageSize = Math.max(pageSize, this.getCurrentLineHeight());
		pageSize += this.pageFrame;
		if (pageSize > this.minPageSize) {
			this.minPageSize = pageSize;
		}
		this.atomicLineSize += minAdvance;
		this.lineAxis += maxAdvance;
	}

	void flush() {
		double minLineSize = this.atomicLineSize;
		if (this.blockHead) {
			minLineSize += this.textIndent;
			this.blockHead = false;
		}
		minLineSize *= this.columnCount;
		minLineSize += this.lineFrame;
		if (minLineSize > this.minLineSize) {
			this.minLineSize = minLineSize;
			if (minLineSize > this.maxLineSize) {
				this.maxLineSize = minLineSize;
			}
		}
		this.atomicLineSize = 0;
		if (this.toLineFeed != null) {
			assert !LayoutUtils.isNone(this.lineAxis);
			assert !LayoutUtils.isNone(this.lineFrame);
			double maxLineSize = this.textIndent + this.maxStartFloatAdvance + this.maxEndFloatAdvance + this.lineAxis;
			maxLineSize *= this.columnCount;
			maxLineSize += this.lineFrame;
			if (maxLineSize > this.maxLineSize) {
				this.maxLineSize = maxLineSize;
			}
			this.lineAxis = 0;
			this.toLineFeed = null;
			this.textIndent = 0;
			this.clearFloatAdvance(ClearMode.BOTH);
		}
	}

	void endTextBlock() {
		assert !LayoutUtils.isNone(this.lineAxis) : "lineAxis=" + this.lineAxis + ", atomicLineSize="
				+ this.atomicLineSize + ", lineFrame=" + this.lineFrame + ", inlineDepth=" + this.inlineStack.size();
		assert !LayoutUtils.isNone(this.lineFrame) : "lineFrame=" + this.lineFrame + ", lineAxis=" + this.lineAxis;
		double minLineSize = this.atomicLineSize;
		if (this.blockHead) {
			minLineSize += this.textIndent;
			this.blockHead = false;
		}
		minLineSize *= this.columnCount;
		minLineSize += this.lineFrame;
		if (minLineSize > this.minLineSize) {
			this.minLineSize = minLineSize;
		}
		double maxLineSize = this.textIndent + this.maxStartFloatAdvance + this.maxEndFloatAdvance + this.lineAxis;
		maxLineSize *= this.columnCount;
		maxLineSize += this.lineFrame;
		if (maxLineSize > this.maxLineSize) {
			this.maxLineSize = maxLineSize;
		}
		this.atomicLineSize = 0;
		this.lineAxis = 0;
	}

	private void clearFloatAdvance(ClearMode clear) {
		switch (clear) {
		case ClearMode.BOTH:
			this.maxStartFloatAdvance = 0;
			this.maxEndFloatAdvance = 0;
			break;
		case ClearMode.START:
			this.maxStartFloatAdvance = 0;
			break;
		case ClearMode.END:
			this.maxEndFloatAdvance = 0;
			break;
		case ClearMode.NONE:
			break;
		default:
			throw new IllegalStateException();
		}
	}

	private double getCurrentLineHeight() {
		if (this.inlineStack.isEmpty()) {
			return this.builder.getFlowBox().getBlockParams().lineHeight;
		}
		InlineBox box = (InlineBox) this.inlineStack.get(this.inlineStack.size() - 1);
		return box.getInlinePos().lineHeight;
	}
}
