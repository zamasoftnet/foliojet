package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlexContentAlignment;
import net.zamasoft.foliojet.layout.box.params.FlexItemSpec;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.RetainedFlex;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.sizing.FlexItemMetrics;
import net.zamasoft.foliojet.layout.sizing.FlexItemMetricsResolver;
import net.zamasoft.foliojet.layout.sizing.FlexLengthResolver;
import net.zamasoft.foliojet.layout.sizing.FlexLineBreaker;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.sizing.Sizing;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Coordinator for Flex construction (Flex F1d–F6, 2026-08-02 —
 * consult-codex-2026-08-02-flexbox.txt. Like {@code GridBuilder}, it is pushed onto
 * {@code DocumentBuilder.builderStack} but is not a {@code Builder}).
 * Opens a {@link FlexItemBox} and item builder (TwoPass recording) for each direct child,
 * then resolves §9.7 and places the items at Flex end.
 *
 * <p>
 * The bind structure is shared by row/column. {@link MainAxis} centralizes their main-axis
 * differences (logical accessors for Dimension/Insets/frame). The 2026-08-02 unification
 * replaced duplicate bindLines/bindColumn implementations with shared measurement
 * ({@link #buildMetrics}), line breaking ({@link #breakMainLines}), §9.7 application
 * ({@link #resolveMainSizes}), cross-axis distribution ({@link #distributeCross}), and
 * main-axis distribution ({@link MainDistribution}). The remaining differences are the
 * physical placement mapping (row uses line offset for the main axis and addFlow for cross;
 * column reverses them) and row-only measured cross sizes (post-bind stretch, §9.4, and
 * auto margins). {@link #placeRow}/{@link #placeColumn} handle these respectively.
 * </p>
 *
 * <p>
 * Content-dependent column basis (auto-height items) is permanently outside the subset
 * (F4c decision = consult-codex-2026-08-02-flexbox-f4c.txt). The classifier falls back
 * to a single column for the entire container (per-item fallback is prohibited).
 * </p>
 */
public final class FlexBuilder implements RetainedFlex, net.zamasoft.foliojet.layout.builder.ItemCoordinator {

	/** Total items constructed, not the number of TwoPass body records. */
	public static final AtomicLong FLEX_ITEM_RECORDS = new AtomicLong();

	/** Number of bound items (including fallback paths). */
	public static final AtomicLong FLEX_ITEM_BINDS = new AtomicLong();

	/** Number of empty anonymous items discarded. */
	public static final AtomicLong FLEX_ITEM_EMPTY_ANON_DROPS = new AtomicLong();

	/** Container fallbacks due to column basis:content (F4c: permanently outside the subset). */
	public static final AtomicLong FLEX_COLUMN_FALLBACKS_CONTENT_BASIS = new AtomicLong();

	/** Fallbacks due to column basis:auto + auto main size (requires content height) (F4c). */
	public static final AtomicLong FLEX_COLUMN_FALLBACKS_AUTO_MAIN = new AtomicLong();

	private final Builder host;

	/** Parent LayoutStack for item builders (the same instance as {@code host}). */
	private final LayoutStack hostStack;

	private final FlexBox flexBox;

	private final List<FlexItemContent> items = new ArrayList<>();

	/** Builder for the open item (element or anonymous), or null when none is open. */
	private TwoPassBlockBuilder openItemBuilder;

	private FlexItemBox openItemBox;

	private boolean openItemAnonymous;

	private FlexItemSpec openItemSpec = FlexItemSpec.DEFAULT;

	/** Original authored box for takeover (matches endBox; null for neutral/anonymous items). */
	private FlowBlockBox openItemSource;
	private long openItemAnchor = -1;

	/** Bind runs only once. */
	private boolean bound;

	FlexBuilder(final Builder host, final FlexBox flexBox) {
		this.host = host;
		this.hostStack = (LayoutStack) host;
		this.flexBox = flexBox;
	}

	@Override
	public net.zamasoft.foliojet.layout.box.IBox getItemHostBox() {
		return this.flexBox;
	}

	@Override
	public FlexBox getFlexBox() {
		return this.flexBox;
	}

	public boolean hasOpenElementItem() {
		return this.openItemBuilder != null && !this.openItemAnonymous;
	}

	public boolean hasOpenItem() {
		return this.openItemBuilder != null;
	}

	/** Returns whether {@code box} is the original box of the open takeover element item. */
	public boolean isElementItemSource(final Object box) {
		return this.openItemSource != null && this.openItemSource == box;
	}

	/** Params for a neutral item ({@link NeutralItemParams}). */
	private BlockParams itemParams() {
		return NeutralItemParams.of(this.flexBox.getFlexParams());
	}

	/**
	 * Opens an element item for a plain block direct child (takeover: the item box itself
	 * inherits the authored child's params/pos; the original outer box is not constructed.
	 * This is the recommendation's most important prototype requirement).
	 * The caller pushes the returned builder.
	 */
	public TwoPassBlockBuilder startElementItem(final FlowBlockBox source, final FlexItemSpec spec) {
		final TwoPassBlockBuilder builder = this.startItem(
				new FlexItemBox(source.getBlockParams(), (FlowPos) source.getPos()), false, spec);
		this.openItemSource = source;
		this.openItemAnchor = source.getSourceAnchor();
		return builder;
	}

	/**
	 * Authored dimensions taken over by a neutral wrapper (generalized from direct
	 * BlockParams passing on 2026-08-09: a replaced element's ReplacedParams is not BlockParams).
	 */
	public record NeutralTransfer(Dimension size, Dimension minSize, Dimension maxSize, BoxSizingMode boxSizing,
			Insets margin) {
		public static NeutralTransfer of(final BlockParams p) {
			return p == null ? null : new NeutralTransfer(p.size, p.minSize, p.maxSize, p.boxSizing, p.frame.margin);
		}

		/**
		 * Takeover from a replaced element (2026-08-09). Without this, the wrapper always has
		 * size:auto and the flex base size falls back to maxContent, the two-pass measurement.
		 * A replaced element's % width has no basis during two-pass measurement
		 * (LayoutUtils.calculateReplacedSize uses refWidth=NONE during twoPass), so an svg
		 * without intrinsic dimensions (viewBox only, width=100%) measures as 0. The item's
		 * main size is then 0 and its width collapses to 0 (the second half of the real bug
		 * that made NHK News navigation chevrons disappear). At bind time, the wrapper's resolved
		 * width serves as lineSize, the child's % basis.
		 */
		public static NeutralTransfer of(final net.zamasoft.foliojet.layout.box.params.ReplacedParams p) {
			return new NeutralTransfer(p.size, p.minSize, p.maxSize, p.boxSizing, p.frame.margin);
		}
	}

	/**
	 * Opens a neutral wrapper element item for a non-plain child (table, nested container, etc.).
	 * {@code sourceAnchor} is the authored child's anchor.
	 *
	 * <p>
	 * When non-null, {@code authored} contains the authored child's params.
	 * <b>Take over its line-axis size specifications (size/min/max) into the wrapper</b>
	 * (2026-08-08). The old wrapper always had size:auto, losing specifications such as
	 * {@code width:50%} on nested flex containers and collapsing to shrink-to-fit
	 * (the real bug that stacked the high-school baseball strip on the asahi.com home page
	 * at one-character width). Prevent double resolution in the child by treating it as
	 * fill (auto) via {@link FlexItemBox#markNeutralLineFill}; direct mutation of child params
	 * is unusable because item body replay (rematerialization) loses it. Keep the wrapper
	 * auto on the page axis so the child's specification applies (stretching only the wrapper
	 * would separate its background/frame from the child).
	 * </p>
	 */
	public TwoPassBlockBuilder startNeutralElementItem(final FlexItemSpec spec, final NeutralTransfer authored,
			final long sourceAnchor) {
		final BlockParams wrapper = this.itemParams();
		final boolean transfer = authored != null;
		if (transfer) {
			final boolean vertical = this.flow().isVertical();
			wrapper.size = lineOnly(authored.size(), vertical);
			wrapper.minSize = lineOnly(authored.minSize(), vertical);
			wrapper.maxSize = lineOnly(authored.maxSize(), vertical);
			wrapper.boxSizing = authored.boxSizing();
			// Auto margins absorb free space at the item (wrapper) level (§8.1),
			// so take over only auto edges into the wrapper (2026-08-09: the real bug where
			// a nested Bootstrap navbar .ml-auto container did not align to the right edge).
			// Auto margins left inside are harmless because the wrapper has no internal free space
			// (no double shift). Non-auto margins are visually equivalent inside, so leave them there.
			final Insets margin = authored.margin();
			if (margin.getTopType() == LengthType.AUTO || margin.getRightType() == LengthType.AUTO
					|| margin.getBottomType() == LengthType.AUTO || margin.getLeftType() == LengthType.AUTO) {
				wrapper.frame = RectFrame.create(
						Insets.create(0, 0, 0, 0,
								margin.getTopType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE,
								margin.getRightType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE,
								margin.getBottomType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE,
								margin.getLeftType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE),
						null, null, null);
			}
		}
		final FlexItemBox itemBox = new FlexItemBox(wrapper, new FlowPos());
		if (transfer) {
			itemBox.markNeutralLineFill();
		}
		final TwoPassBlockBuilder builder = this.startItem(itemBox, false, spec);
		this.openItemAnchor = sourceAnchor;
		return builder;
	}

	/** Dimension retaining only the line-axis component (page axis auto; line axis is height in vertical writing). */
	private static Dimension lineOnly(final Dimension d, final boolean vertical) {
		return vertical
				? Dimension.create(0, 0, d.getHeight(), d.getHeightRatio(), LengthType.AUTO, d.getHeightType())
				: Dimension.create(d.getWidth(), d.getWidthRatio(), 0, 0, d.getWidthType(), LengthType.AUTO);
	}

	/** Opens an anonymous item for direct text (reuses it if already open). */
	public TwoPassBlockBuilder requireAnonymousItem(final long sourceAnchor) {
		if (this.openItemBuilder != null && this.openItemAnonymous) {
			return null; // Already open (no need to push again)
		}
		final TwoPassBlockBuilder builder = this.startItem(new FlexItemBox(this.itemParams(), new FlowPos()), true,
				FlexItemSpec.DEFAULT);
		this.openItemAnchor = sourceAnchor;
		return builder;
	}

	private TwoPassBlockBuilder startItem(final FlexItemBox itemBox, final boolean anonymous,
			final FlexItemSpec spec) {
		assert this.openItemBuilder == null : "前のitemが閉じられていない";
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this.hostStack, itemBox);
		builder.tagRootKind(ContinuationStats.TwoPassRootKind.FLEX_ITEM);
		this.openItemBuilder = builder;
		this.openItemBox = itemBox;
		this.openItemAnonymous = anonymous;
		this.openItemSpec = spec;
		this.openItemSource = null;
		this.openItemAnchor = -1;
		return builder;
	}

	/** Finalizes the open item (recording completion point). Discards empty anonymous items. */
	public void itemClosed() {
		final TwoPassBlockBuilder builder = this.openItemBuilder;
		final FlexItemBox itemBox = this.openItemBox;
		final boolean anonymous = this.openItemAnonymous;
		final FlexItemSpec spec = this.openItemSpec;
		final boolean takeover = this.openItemSource != null;
		final long anchor = this.openItemAnchor;
		this.openItemBuilder = null;
		this.openItemBox = null;
		this.openItemAnonymous = false;
		this.openItemSpec = FlexItemSpec.DEFAULT;
		this.openItemSource = null;
		this.openItemAnchor = -1;
		if (anonymous && !builder.hasLayoutContent() && !itemBox.paintsAnything()) {
			FLEX_ITEM_EMPTY_ANON_DROPS.incrementAndGet();
			return;
		}
		FLEX_ITEM_RECORDS.incrementAndGet();
		builder.tagItemKind(anonymous, takeover);
		builder.sealBodyForRangeBind(anchor, anonymous
				? net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode.ANONYMOUS_CHILDREN
				: takeover
					? net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode.CHILDREN_ONLY
					: net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode.ROOTED_SUBTREE);
		this.items.add(new FlexItemContent(itemBox, builder, builder.getIntrinsicSizes(), anonymous, spec));
	}

	/**
	 * Flex end (F1f): passes the execution plan to the host. BlockBuilder calls
	 * {@link #bind} immediately; TwoPass retains it in the ownership ledger and binds
	 * after width resolution.
	 */
	public void finish() {
		assert this.openItemBuilder == null : "item未クローズでFlex終端に到達";
		this.host.addFlex(this);
	}

	// ------------------------------------------------------------------
	// Logical axes

	/** Container writing mode (basis for logical axis mapping — F6). */
	private WritingMode flow() {
		return this.flexBox.getFlexParams().flow;
	}

	/**
	 * Logical main-axis accessors (unified on 2026-08-02). The row main axis is the line axis;
	 * the column main axis is the page axis. Centralize axis selection for Dimension/Insets/
	 * border/automatic minimum checks here to share the bind structure.
	 * {@code marginBase} is the basis for resolving Insets percentages. CSS uses the inline
	 * size (container inner line-axis size) for both horizontal and vertical margins/padding.
	 */
	private final class MainAxis {
		final boolean mainIsLine;

		/** Percentage basis for main-axis dimensions (width/height). */
		final double mainBase;

		/** Percentage basis for margins/padding (always the container inner line-axis size). */
		final double marginBase;

		MainAxis(final boolean mainIsLine, final double mainBase, final double marginBase) {
			this.mainIsLine = mainIsLine;
			this.mainBase = mainBase;
			this.marginBase = marginBase;
		}

		double mainGap() {
			final FlexParams params = FlexBuilder.this.flexBox.getFlexParams();
			return this.mainIsLine ? params.columnGap : params.rowGap;
		}

		double crossGap() {
			final FlexParams params = FlexBuilder.this.flexBox.getFlexParams();
			return this.mainIsLine ? params.rowGap : params.columnGap;
		}

		/** Main-axis dimension (auto = NaN; resolve % against {@code mainBase}). */
		double mainValue(final Dimension size) {
			return this.mainIsLine ? lineValue(size, this.mainBase) : pageValue(size, this.mainBase);
		}

		/** Main-axis maximum size (none = +∞). */
		double mainMaxValue(final Dimension size) {
			final double value = this.mainValue(size);
			return Double.isNaN(value) ? Double.POSITIVE_INFINITY : Math.max(0, value);
		}

		/** Sum of main-axis borders and padding. */
		double mainFrame(final RectFrame frame) {
			return this.mainIsLine ? insetsLine(frame.padding, this.marginBase) + borderLine(frame)
					: insetsPage(frame.padding, this.marginBase) + borderPage(frame);
		}

		/** Sum of main-axis margins (auto counts as 0). */
		double mainMargin(final RectFrame frame) {
			return this.mainIsLine ? insetsLine(frame.margin, this.marginBase)
					: insetsPage(frame.margin, this.marginBase);
		}

		/** Checks main-axis min-size:auto (§4.5; FlexItemSpec records whether it was declared). */
		boolean minMainAuto(final FlexItemSpec spec) {
			final boolean vertical = FlexBuilder.this.flow().isVertical();
			return this.mainIsLine == !vertical ? spec.minWidthAuto() : spec.minHeightAuto();
		}
	}

	// ------------------------------------------------------------------
	// Bind structure (shared by row/column)

	/**
	 * Assembles Flex (converts all items to numeric values with FlexItemMetricsResolver,
	 * resolves main-axis sizes through §9.7 = FlexLengthResolver, and places them).
	 * Call while the host's active flow is this FlexBox (at DocumentBuilder FLOW end for
	 * live processing; likewise between StartFlow(FlexBox) and EndFlow for range replay).
	 */
	@Override
	public void bind(final Builder hostBuilder) {
		assert !this.bound : "Flexの二重bind";
		this.bound = true;
		final BlockBuilder target = (BlockBuilder) hostBuilder;
		final FlexParams params = this.flexBox.getFlexParams();
		final boolean mainIsLine = params.flexDirection.isRow();
		final double innerLine = this.flexBox.getLineSize();
		if (!mainIsLine && !this.columnMainResolvable(target)) {
			return; // Container fallback already applied (an item that cannot be measured)
		}
		// A column's main size is its specified height when absolute (the G5e technique); otherwise (stage 2 of
		// docs/design/column-flex-indefinite-main-design.md) it follows from the items below.
		final boolean definiteMain = mainIsLine
				|| params.size.getPageType(params.flow) == LengthType.ABSOLUTE;
		MainAxis axis = new MainAxis(mainIsLine,
				mainIsLine ? innerLine : definiteMain ? this.flexBox.getInnerPageExtent(params.flow) : Double.NaN,
				innerLine);
		// Column: the cross sizes come first, then the items whose main size depends on their content are measured
		// at them (2026-10-08; F4c had stacked such containers in one column).
		final double[] crossWidths = mainIsLine ? null : new double[this.items.size()];
		final double[] crossExtras = mainIsLine ? null : new double[this.items.size()];
		final double[] measuredMain = mainIsLine ? null
				: this.measureColumnItems(target, innerLine, !definiteMain, crossWidths, crossExtras);
		// Lines are collected in order-modified document order (css-flexbox-1 §9.3); a reverse main axis only
		// mirrors the items inside each line. Reversing the whole sequence before breaking put the last item on
		// the first line and could regroup the lines (fit sweep seed 11931726, 2026-10-07).
		final int[] ordered = this.visualOrder();
		final List<FlexItemMetrics> orderedMetrics = this.buildMetrics(ordered, axis, measuredMain);
		if (!definiteMain) {
			axis = new MainAxis(false, this.indefiniteColumnMain(orderedMetrics, axis), innerLine);
		}
		final List<FlexLineBreaker.Line> lines = this.breakMainLines(orderedMetrics, axis);
		final boolean reversed = params.flexDirection.isReverse();
		final int[] seq = reversed ? reverseWithinLines(ordered, lines) : ordered;
		final List<FlexItemMetrics> metrics = reversed ? reverseWithinLines(orderedMetrics, lines) : orderedMetrics;
		final double[] mainSizeByOriginal = this.resolveMainSizes(seq, metrics, lines, axis);
		if (mainIsLine) {
			this.placeRow(target, axis, seq, metrics, lines, mainSizeByOriginal);
		} else {
			this.placeColumn(target, axis, seq, metrics, lines, mainSizeByOriginal, crossWidths, crossExtras);
		}
		this.syncHostCursor(target, params);
	}

	/**
	 * Pre-scans whether every item whose column main-axis size depends on its content can be measured (2026-10-08).
	 * Until then any such item made the whole container fall back to one stacked column (F4c); now
	 * {@link FlexItemContent#measureMain} measures them, and only an item that cannot be replicated (a multi-column
	 * item) still falls back, for the entire container, without binding any body.
	 * Check the logical page axis: the main axis of a vertical-writing column is physical width.
	 */
	private boolean columnMainResolvable(final BlockBuilder target) {
		final boolean indefinite = this.flexBox.getFlexParams().size.getPageType(this.flow()) != LengthType.ABSOLUTE;
		for (final FlexItemContent item : this.items) {
			if (this.measuresContent(item, indefinite) && item.itemBox.newMeasureReplica() == null) {
				if (item.spec.basis().isContent()) {
					FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.incrementAndGet();
				} else {
					FLEX_COLUMN_FALLBACKS_AUTO_MAIN.incrementAndGet();
				}
				this.bindFallback(target);
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a column item's main size comes from its content: {@link FlexItemContent#hasContentMain}, or, in a
	 * container whose main size is indefinite, a percentage basis (which then behaves as {@code content}) with a
	 * page-axis size that is not absolute. A percentage page size of such a container behaves as {@code auto} too
	 * (an aspect-ratio thumbnail with {@code height: 100%} took no room, ourworldindata 2026-10-08).
	 */
	private boolean measuresContent(final FlexItemContent item, final boolean indefiniteMain) {
		if (item.hasContentMain(this.flow())) {
			return true;
		}
		return indefiniteMain && !(item.spec.basis().getSize() instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue)
				&& item.itemBox.getBlockParams().size.getPageType(this.flow()) != LengthType.ABSOLUTE;
	}

	/**
	 * Column cross sizes before line breaking (explicit width, stretch for a single nowrap column, otherwise
	 * fit-content) and the main sizes of the items that depend on their content, measured at those cross sizes
	 * (NaN for the others; 2026-10-08).
	 */
	private double[] measureColumnItems(final BlockBuilder target, final double innerLine,
			final boolean indefiniteMain, final double[] crossWidthByOriginal, final double[] itemCrossExtras) {
		final FlexParams params = this.flexBox.getFlexParams();
		final double[] measured = new double[this.items.size()];
		for (int oi = 0; oi < this.items.size(); ++oi) {
			final FlexItemContent item = this.items.get(oi);
			final BlockParams p = item.itemBox.getBlockParams();
			final RectFrame frame = p.frame;
			final double lineExtras = insetsLine(frame.margin, innerLine) + insetsLine(frame.padding, innerLine)
					+ borderLine(frame);
			final BoxAlignment align = this.resolveAlign(item, false);
			// For border-box, subtract the frame to obtain inner size (excludes margins)
			final double borderBoxAdjust = p.boxSizing == BoxSizingMode.BORDER_BOX
					? lineExtras - insetsLine(frame.margin, innerLine)
					: 0;
			final double crossWidth;
			if (p.size.getLineType(params.flow) != LengthType.AUTO) {
				// Explicit width
				crossWidth = this.clampCross(p, Math.max(0, lineValue(p.size, innerLine) - borderBoxAdjust),
						innerLine, borderBoxAdjust);
			} else if (align == BoxAlignment.STRETCH && !params.flexWrap.isWrap() && !this.crossAutoMargin(item)) {
				// Stretch for a single nowrap column fills the container inner size (not with an auto cross margin,
				// §9.4 step 11). Column stretch with wrap happens after column width resolution (placeColumn).
				crossWidth = this.clampCross(p, Math.max(0, innerLine - lineExtras), innerLine, borderBoxAdjust);
			} else {
				crossWidth = this.clampCross(p, Sizing.fitContent(item.sizes.minContent(), item.sizes.maxContent(),
						Math.max(0, innerLine - lineExtras)), innerLine, borderBoxAdjust);
			}
			crossWidthByOriginal[oi] = crossWidth;
			itemCrossExtras[oi] = lineExtras;
			measured[oi] = this.measuresContent(item, indefiniteMain)
					? item.measureMain(target, crossWidth, innerLine, params.flow)
					: Double.NaN;
		}
		return measured;
	}

	/**
	 * Clamps a column item's cross size (its content-box width) by its own min/max width (2026-10-08): a stretched or
	 * fit-content item stays within {@code max-width} (css-flexbox-1 §9.4 step 11). Until then bulma's launch banner
	 * text ({@code max-width: 20rem}, centered) ran across the whole container once column-flex stage 2 retained it.
	 *
	 * @param borderBoxAdjust the line-axis padding and border when the item is border-box (min/max are border-box)
	 */
	private double clampCross(final BlockParams p, final double crossWidth, final double innerLine,
			final double borderBoxAdjust) {
		double width = crossWidth;
		final double max = lineValue(p.maxSize, innerLine);
		if (!Double.isNaN(max)) {
			width = Math.min(width, max - borderBoxAdjust);
		}
		final double min = lineValue(p.minSize, innerLine);
		if (!Double.isNaN(min)) {
			width = Math.max(width, min - borderBoxAdjust);
		}
		return Math.max(0, width);
	}

	/**
	 * The used main size of a column whose own main size is indefinite (stage 2, 2026-10-08): the items' hypothetical
	 * outer main sizes and gaps, clamped by the container's absolute min/max main size. With wrap, a max lets lines
	 * break at it (Chrome does); without wrap, the items shrink to it (§9.7).
	 */
	private double indefiniteColumnMain(final List<FlexItemMetrics> metrics, final MainAxis axis) {
		final FlexParams params = this.flexBox.getFlexParams();
		double sum = metrics.size() > 1 ? axis.mainGap() * (metrics.size() - 1) : 0;
		for (final FlexItemMetrics m : metrics) {
			sum += m.hypotheticalMain() + m.outerMainExtra();
		}
		final double max = params.maxSize.getPageType(params.flow) == LengthType.ABSOLUTE
				? Math.max(0, params.maxSize.getPageLength(params.flow)) - pageFrameAdjust(params)
				: Double.POSITIVE_INFINITY;
		final double min = params.minSize.getPageType(params.flow) == LengthType.ABSOLUTE
				? Math.max(0, params.minSize.getPageLength(params.flow)) - pageFrameAdjust(params)
				: 0;
		return Math.max(Math.max(0, min), Math.min(sum, max));
	}

	/** The container's page-axis border+padding when box-sizing is border-box (min/max are border-box sizes then). */
	private double pageFrameAdjust(final FlexParams params) {
		return params.boxSizing == BoxSizingMode.BORDER_BOX
				? insetsPage(params.frame.padding, this.flexBox.getLineSize()) + borderPage(params.frame)
				: 0;
	}

	/** Converts all items in visual order to §9.7 inputs (main-axis measurements). */
	private List<FlexItemMetrics> buildMetrics(final int[] seq, final MainAxis axis, final double[] measuredMain) {
		final List<FlexItemMetrics> metrics = new ArrayList<>(seq.length);
		for (final int oi : seq) {
			final FlexItemContent item = this.items.get(oi);
			final BlockParams p = item.itemBox.getBlockParams();
			// For column, the intrinsic main size is the content height measured at the item's cross size
			// (2026-10-08); items that were not measured keep the simulated minPage (an F4b approximation).
			final double measured = measuredMain == null ? Double.NaN : measuredMain[oi];
			final double minContent = axis.mainIsLine ? item.sizes.minContent()
					: Double.isNaN(measured) ? item.sizes.minPage() : measured;
			final double maxContent = axis.mainIsLine ? item.sizes.maxContent()
					: Double.isNaN(measured) ? item.sizes.minPage() : measured;
			metrics.add(FlexItemMetricsResolver.resolve(new FlexItemMetricsResolver.Input(oi,
					item.spec.grow(), item.spec.shrink(), item.spec.basis(), axis.mainValue(p.size),
					axis.minMainAuto(item.spec) ? Double.NaN
							: Math.max(0, zeroIfNaN(axis.mainValue(p.minSize))),
					axis.mainMaxValue(p.maxSize), axis.mainFrame(p.frame), axis.mainMargin(p.frame),
					p.boxSizing == BoxSizingMode.BORDER_BOX, p.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE,
					minContent, maxContent, axis.mainBase)));
		}
		return metrics;
	}

	/** Breaks main-axis lines (rows for row, columns for column) (§9.3; nowrap is a single line). */
	private List<FlexLineBreaker.Line> breakMainLines(final List<FlexItemMetrics> metrics, final MainAxis axis) {
		if (this.flexBox.getFlexParams().flexWrap.isWrap()) {
			return FlexLineBreaker.breakLines(metrics, axis.mainBase, axis.mainGap());
		}
		if (this.items.isEmpty()) {
			return List.of();
		}
		return List.of(new FlexLineBreaker.Line(0, this.items.size()));
	}

	/** Resolves §9.7 per line, making used main-axis sizes accessible by source index. */
	private double[] resolveMainSizes(final int[] seq, final List<FlexItemMetrics> metrics,
			final List<FlexLineBreaker.Line> lines, final MainAxis axis) {
		final double[] byOriginal = new double[this.items.size()];
		for (final FlexLineBreaker.Line line : lines) {
			final double[] sizes = FlexLengthResolver.resolve(metrics.subList(line.from(), line.to()),
					axis.mainBase, axis.mainGap());
			for (int k = line.from(); k < line.to(); ++k) {
				byOriginal[seq[k]] = sizes[k - line.from()];
			}
		}
		return byOriginal;
	}

	/**
	 * Distributes main-axis free space (§9.5 + §8.1). Auto margins consume free space before
	 * justify-content (any auto margin disables justification; column excludes auto margins
	 * from its subset and passes {@code autoMargins}=0).
	 * Negative free space uses safe start (0); justify-content treats stretch as flex-start.
	 */
	private record MainDistribution(double leading, double between, double autoShare) {
	}

	private MainDistribution distributeMain(final double free, final int count, final int autoMargins,
			final double mainGap) {
		final FlexContentAlignment justify = this.flexBox.getFlexParams().justifyContent;
		if (autoMargins > 0) {
			return new MainDistribution(0, mainGap, free > 0 ? free / autoMargins : 0);
		}
		return new MainDistribution(justify.leadingOffset(free, count),
				mainGap + justify.betweenOffset(free, count), 0);
	}

	/**
	 * Distributes lines on the cross axis (§9.6, wrap only). normal/stretch adds equally to
	 * each line; other values use FlexContentAlignment arithmetic for leading/between space.
	 * {@code extents} holds line cross sizes (modified by addition).
	 */
	private record CrossDistribution(double leading, double between) {
	}

	private CrossDistribution distributeCross(final double[] extents, final double innerCross,
			final double crossGap) {
		final FlexParams params = this.flexBox.getFlexParams();
		double content = extents.length > 1 ? crossGap * (extents.length - 1) : 0;
		for (final double extent : extents) {
			content += extent;
		}
		double leading = 0;
		double between = extents.length > 1 ? crossGap : 0;
		final double freeCross = Math.max(0, innerCross - content);
		if (freeCross > 0 && extents.length > 0) {
			if (params.alignContent == FlexContentAlignment.STRETCH
					|| params.alignContent == FlexContentAlignment.NORMAL) {
				final double share = freeCross / extents.length;
				for (int i = 0; i < extents.length; ++i) {
					extents[i] += share;
				}
			} else {
				leading = params.alignContent.leadingOffset(freeCross, extents.length);
				between += params.alignContent.betweenOffset(freeCross, extents.length);
			}
		}
		return new CrossDistribution(leading, between);
	}

	/**
	 * Combines align-self:auto → align-items into the used value (§9.6). For wrap-reverse
	 * (cross-axis reversal), swap start/end. Plain start/end are treated as flex-start/end,
	 * since flex-* overwhelmingly dominates flex usage. Strict writing-mode-relative
	 * start/end is outside the subset.
	 */
	private BoxAlignment resolveAlign(final FlexItemContent item, final boolean crossReversed) {
		final BoxAlignment align = BoxAlignment.resolve(item.spec.alignSelf(),
				this.flexBox.getFlexParams().alignItems);
		if (crossReversed) {
			return align == BoxAlignment.START ? BoxAlignment.END
					: align == BoxAlignment.END ? BoxAlignment.START : align;
		}
		return align;
	}

	// ------------------------------------------------------------------
	// Placement (row/column reverse the physical mapping; only this part has two implementations)

	/**
	 * Places row items. Cross sizes are measured (item page extents after bind).
	 * Bind all items in source order with the §9.7 main sizes, then apply line cross size =
	 * maximum within the line, §9.4 (single nowrap line + definite cross size uses the
	 * container inner cross size as line height), align-content, stretch growth (takeover
	 * makes authored backgrounds follow), cross/main-axis auto margins, and wrap-reverse
	 * line order plus start/end reversal.
	 */
	private void placeRow(final BlockBuilder target, final MainAxis axis, final int[] seq,
			final List<FlexItemMetrics> metrics, final List<FlexLineBreaker.Line> lines,
			final double[] mainSizeByOriginal) {
		final FlexParams params = this.flexBox.getFlexParams();
		this.flexBox.markFlexLayout();
		// Bind in source order (F5a: preserve source order for Tagged PDF reading order and structure).
		for (int i = 0; i < this.items.size(); ++i) {
			this.items.get(i).bind(target, mainSizeByOriginal[i], axis.marginBase);
			FLEX_ITEM_BINDS.incrementAndGet();
		}
		// Main-axis placement per line (line offset). Cross placement follows line distribution.
		final double[] lineExtents = new double[lines.size()];
		for (int li = 0; li < lines.size(); ++li) {
			final FlexLineBreaker.Line line = lines.get(li);
			double lineUsed = axis.mainGap() * (line.count() - 1);
			int autoMargins = 0;
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				lineUsed += item.itemBox.getLineExtent(params.flow);
				lineExtents[li] = Math.max(lineExtents[li], item.itemBox.getPageExtent(params.flow));
				autoMargins += (this.mainMarginAuto(item, false) ? 1 : 0)
						+ (this.mainMarginAuto(item, true) ? 1 : 0);
			}
			final MainDistribution dist = this.distributeMain(axis.mainBase - lineUsed, line.count(),
					autoMargins, axis.mainGap());
			double lineCursor = dist.leading();
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				if (this.mainMarginAuto(item, false)) {
					lineCursor += dist.autoShare();
				}
				// The natural position includes the item margin, so offset accumulates preceding items.
				final double physicalLine = LayoutUtils.inlineToPhysical(params, axis.mainBase, lineCursor,
						lineCursor + item.itemBox.getLineExtent(params.flow));
				item.itemBox.setFlexLineOffset(physicalLine, params.flow.isVertical());
				lineCursor += item.itemBox.getLineExtent(params.flow)
						+ (this.mainMarginAuto(item, true) ? dist.autoShare() : 0)
						+ (k < line.to() - 1 ? dist.between() : 0);
			}
		}
		// Cross-axis line distribution (F3d). Provisionally resolve the total content cross size,
		// then obtain free space as the difference from the definite cross size
		// (getInnerPageExtent returns the specified height if present — the G5e technique).
		double content = lines.size() > 1 ? axis.crossGap() * (lines.size() - 1) : 0;
		for (final double extent : lineExtents) {
			content += extent;
		}
		this.flexBox.setPageAxis(content);
		final double innerCross = this.flexBox.getInnerPageExtent(params.flow);
		CrossDistribution dist = new CrossDistribution(0, lines.size() > 1 ? axis.crossGap() : 0);
		if (params.flexWrap.isWrap()) {
			dist = this.distributeCross(lineExtents, innerCross, axis.crossGap());
		} else if (lines.size() == 1 && innerCross > lineExtents[0]) {
			// §9.4: single line (nowrap) + definite cross size uses the container inner cross size as line height.
			lineExtents[0] = innerCross;
		}
		// Cross alignment (F3c: use line heights after distribution). wrap-reverse (F5c)
		// reverses the visual order of lines.
		final boolean crossReversed = params.flexWrap == FlexWrap.WRAP_REVERSE;
		double crossCursor = dist.leading();
		// **Record line boundaries for page breaks** (2026-08-07, Bug C). Store items in the same
		// order as addFlow so FlexBox.split can directly look up "which item belongs to which line"
		// without searching across containers (the same role as the
		// rows/cells lists in TableRowGroupBox).
		final List<FlexBox.Line> flexLines = new ArrayList<>(lines.size());
		final List<FlexItemBox> flexLineItems = new ArrayList<>(this.items.size());
		for (int v = 0; v < lines.size(); ++v) {
			final int li = crossReversed ? lines.size() - 1 - v : v;
			final FlexLineBreaker.Line line = lines.get(li);
			final double lineExtent = lineExtents[li];
			final double lineStart = crossCursor;
			final int lineStartFlow = flexLineItems.size();
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				final BoxAlignment align = this.resolveAlign(item, crossReversed);
				// Cross-axis auto margins precede align-self/stretch (§8.1, F3e):
				// auto at start aligns to the end; auto on both sides centers.
				final boolean crossStartAuto = this.crossMarginAuto(item, false);
				final boolean crossEndAuto = this.crossMarginAuto(item, true);
				double crossOffset = 0;
				if (crossStartAuto || crossEndAuto) {
					final double freeCross = Math.max(0,
							lineExtent - item.itemBox.getPageExtent(params.flow));
					crossOffset = crossStartAuto && crossEndAuto ? freeCross / 2
							: crossStartAuto ? freeCross : 0;
				} else if (align == BoxAlignment.STRETCH) {
					// Stretch only items with auto cross size to line height. The takeover design
					// makes authored backgrounds and frames follow unchanged (the F1d goal).
					if (item.itemBox.getBlockParams().size.getPageType(params.flow) == LengthType.AUTO) {
						final double deficit = lineExtent - item.itemBox.getPageExtent(params.flow);
						if (deficit > 0) {
							item.itemBox.setPageAxis(item.itemBox.getInnerPageExtent(params.flow) + deficit);
						}
					}
				} else {
					final double freeCross = lineExtent - item.itemBox.getPageExtent(params.flow);
					crossOffset = align == BoxAlignment.CENTER ? Math.max(0, freeCross / 2)
							: align == BoxAlignment.END ? Math.max(0, freeCross) : 0;
				}
				this.flexBox.getContainer().addFlow(item.itemBox, crossCursor + crossOffset);
				flexLineItems.add(item.itemBox);
			}
			flexLines.add(new FlexBox.Line(lineStartFlow, line.to() - line.from(), lineStart, lineExtent));
			crossCursor += lineExtent + (v < lines.size() - 1 ? dist.between() : 0);
		}
		if (!this.items.isEmpty()) {
			this.flexBox.setFlexLines(flexLines, flexLineItems);
		}
		this.flexBox.setPageAxis(this.items.isEmpty() ? 0 : Math.max(content, crossCursor));
	}

	/**
	 * Places column items. Cross sizes are computed in advance (the F4c recommendation's
	 * processing order): without reading body height, resolve column cross size as the
	 * maximum item explicit width/fit-content, align-content distribution, and stretch items
	 * following column width, then bind. After bind, impose the main size with setPageAxis
	 * (the §9.7 result takes precedence over specified height). Auto margins are outside
	 * the column subset. wrap-reverse reverses column order and item alignment start/end,
	 * symmetrically with row.
	 */
	private void placeColumn(final BlockBuilder target, final MainAxis axis, final int[] seq,
			final List<FlexItemMetrics> metrics, final List<FlexLineBreaker.Line> cols,
			final double[] mainSizeByOriginal, final double[] crossWidthByOriginal, final double[] itemCrossExtras) {
		final FlexParams params = this.flexBox.getFlexParams();
		this.flexBox.markFlexLayout();
		final double innerLine = axis.marginBase;
		final int count = this.items.size();
		// Column cross width = maximum item cross size (explicit width/stretch/fit-content, measureColumnItems)
		final double[] colCross = new double[cols.size()];
		for (int ci = 0; ci < cols.size(); ++ci) {
			final FlexLineBreaker.Line col = cols.get(ci);
			for (int k = col.from(); k < col.to(); ++k) {
				final int oi = seq[k];
				colCross[ci] = Math.max(colCross[ci], crossWidthByOriginal[oi] + itemCrossExtras[oi]);
			}
		}
		if (!params.flexWrap.isWrap() && cols.size() == 1) {
			// A single-line container's line is as wide as the container (§9.4 step 15), so align-items/align-self
			// center and end place narrower items across the whole width (2026-10-08; they stayed at the start)
			colCross[0] = Math.max(colCross[0], innerLine);
		}
		// Cross distribution of columns (wrap only) + stretch items following the resolved column width
		CrossDistribution dist = new CrossDistribution(0, cols.size() > 1 ? axis.crossGap() : 0);
		if (params.flexWrap.isWrap()) {
			dist = this.distributeCross(colCross, innerLine, axis.crossGap());
			for (int ci = 0; ci < cols.size(); ++ci) {
				final FlexLineBreaker.Line col = cols.get(ci);
				for (int k = col.from(); k < col.to(); ++k) {
					final int oi = seq[k];
					final FlexItemContent item = this.items.get(oi);
					final BlockParams p = item.itemBox.getBlockParams();
					if (this.resolveAlign(item, false) == BoxAlignment.STRETCH
							&& p.size.getLineType(params.flow) == LengthType.AUTO && !this.crossAutoMargin(item)) {
						final double borderBoxAdjust = p.boxSizing == BoxSizingMode.BORDER_BOX
								? itemCrossExtras[oi] - insetsLine(p.frame.margin, innerLine)
								: 0;
						crossWidthByOriginal[oi] = this.clampCross(p, Math.max(crossWidthByOriginal[oi],
								colCross[ci] - itemCrossExtras[oi]), innerLine, borderBoxAdjust);
					}
				}
			}
		}
		// Bind in source order (F5a: preserve source order for Tagged PDF reading order and structure).
		for (int i = 0; i < count; ++i) {
			this.items.get(i).bind(target, crossWidthByOriginal[i], axis.marginBase);
			FLEX_ITEM_BINDS.incrementAndGet();
		}
		// Place in visual order. Stack columns along the cross axis (wrap-reverse reverses column order).
		final boolean crossReversed = params.flexWrap == FlexWrap.WRAP_REVERSE;
		double crossCursor = dist.leading();
		double lastMainEnd = 0;
		// Item start offsets in a single column (nowrap), for synthesizing the page-axis ledger (below)
		final double[] singleColStarts = cols.size() == 1 ? new double[count] : null;
		double singleColLeading = 0;
		final List<FlexItemBox> singleColItems = cols.size() == 1 ? new ArrayList<>(count) : null;
		for (int v = 0; v < cols.size(); ++v) {
			final int ci = crossReversed ? cols.size() - 1 - v : v;
			final FlexLineBreaker.Line col = cols.get(ci);
			double used = axis.mainGap() * (col.count() - 1);
			for (int k = col.from(); k < col.to(); ++k) {
				used += mainSizeByOriginal[seq[k]] + metrics.get(k).outerMainExtra();
			}
			final MainDistribution main = this.distributeMain(axis.mainBase - used, col.count(), 0,
					axis.mainGap());
			double mainCursor = main.leading();
			singleColLeading = main.leading();
			for (int k = col.from(); k < col.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				// Resolve the main (page) size (the §9.7 result takes precedence over specified height).
				item.itemBox.setPageAxis(mainSizeByOriginal[seq[k]]);
				// Cross alignment (line axis): remaining space in the column + column start position.
				// wrap-reverse swaps start/end symmetrically with row (2026-08-02: removed
				// an asymmetry found while checking the unification).
				final double freeCross = Math.max(0, colCross[ci] - item.itemBox.getLineExtent(params.flow));
				final BoxAlignment align = this.resolveAlign(item, crossReversed);
				// Cross-axis auto margins (the line-axis ones of a column) precede align-self (§8.1, 2026-10-08;
				// tailwind's mx-auto button stayed at the start)
				final boolean crossStartAuto = this.mainMarginAuto(item, false);
				final boolean crossEndAuto = this.mainMarginAuto(item, true);
				final double crossOffset = crossStartAuto || crossEndAuto
						? (crossStartAuto && crossEndAuto ? freeCross / 2 : crossStartAuto ? freeCross : 0)
						: align == BoxAlignment.CENTER ? freeCross / 2
						: align == BoxAlignment.END ? freeCross : 0;
				final double logicalLine = crossCursor + crossOffset;
				final double physicalLine = LayoutUtils.inlineToPhysical(params, innerLine, logicalLine,
						logicalLine + item.itemBox.getLineExtent(params.flow));
				item.itemBox.setFlexLineOffset(physicalLine, params.flow.isVertical());
				this.flexBox.getContainer().addFlow(item.itemBox, mainCursor);
				if (singleColStarts != null) {
					singleColStarts[singleColItems.size()] = mainCursor;
					singleColItems.add(item.itemBox);
				}
				mainCursor += mainSizeByOriginal[seq[k]] + metrics.get(k).outerMainExtra()
						+ (k < col.to() - 1 ? main.between() : 0);
			}
			lastMainEnd = Math.max(lastMainEnd, mainCursor);
			crossCursor += colCross[ci] + (v < cols.size() - 1 ? dist.between() : 0);
		}
		this.flexBox.setPageAxis(count == 0 ? 0 : Math.max(axis.mainBase, lastMainEnd));
		// **Synthesize the page-axis ledger** (2026-08-18): a single column simply stacks items
		// on the page axis, so passing a ledger with one item per line lets the same
		// line splitting mechanism as row ({@code FlexBox.split}) work unchanged. Previously it was atomic
		// and fell back to rescue splitting (band clipping), slicing lines at band boundaries
		// in 37% of real documents (app-shell body flex). Storing line starts in the ledger
		// also handles main-axis alignment with leading>0 (center, etc.) directly (2026-08-19).
		if (singleColItems != null && !singleColItems.isEmpty()) {
			final List<FlexBox.Line> flexLines = new ArrayList<>(singleColItems.size());
			for (int k = 0; k < singleColItems.size(); ++k) {
				final double end = k + 1 < singleColItems.size() ? singleColStarts[k + 1]
						: singleColStarts[k] + singleColItems.get(k).getPageExtent(params.flow);
				flexLines.add(new FlexBox.Line(k, 1, singleColStarts[k], end - singleColStarts[k]));
			}
			this.flexBox.setFlexLines(flexLines, singleColItems);
		}
	}

	/**
	 * Single-column fallback (F4c, for content-dependent column basis).
	 * Stack items vertically as blocks filling the container inner size.
	 * Do not collapse margins.
	 */
	private void bindFallback(final BlockBuilder target) {
		final FlexParams params = this.flexBox.getFlexParams();
		this.flexBox.markFlexLayout();
		final double innerLine = this.flexBox.getLineSize();
		double pageCursor = 0;
		final List<FlexBox.Line> flexLines = new ArrayList<>(this.items.size());
		final List<FlexItemBox> flexLineItems = new ArrayList<>(this.items.size());
		for (final FlexItemContent item : this.items) {
			final RectFrame frame = item.itemBox.getBlockParams().frame;
			final double lineExtras = insetsLine(frame.margin, innerLine) + insetsLine(frame.padding, innerLine)
					+ borderLine(frame);
			item.bind(target, Math.max(0, innerLine - lineExtras), innerLine);
			FLEX_ITEM_BINDS.incrementAndGet();
			this.flexBox.getContainer().addFlow(item.itemBox, pageCursor);
			final double extent = item.itemBox.getPageExtent(params.flow);
			// Page-axis ledger with one item per line (2026-08-18, as synthesized in placeColumn).
			// The fallback path also stacks vertically, so the line splitting mechanism applies unchanged.
			flexLines.add(new FlexBox.Line(flexLineItems.size(), 1, pageCursor, extent));
			flexLineItems.add(item.itemBox);
			pageCursor += extent;
		}
		this.flexBox.setPageAxis(pageCursor);
		if (!flexLineItems.isEmpty()) {
			this.flexBox.setFlexLines(flexLines, flexLineItems);
		}
		this.syncHostCursor(target, params);
	}

	// ------------------------------------------------------------------
	// RetainedFlex (TwoPass host, absorption into parent range)

	/**
	 * Intrinsic content-box size contribution of the entire Flex (F1f: single-row
	 * approximation of §9.9). Line axis: min=Σ(item min-content + absolute frame portion),
	 * max=Σ(item max-content + same). For % frames, only the absolute portion counts because
	 * the basis is unresolved (a conservative approximation; bind resolves the width exactly).
	 * Page-axis min is the maximum item minPage (single line). Excludes the frame
	 * (the measurer's normal path adds it exactly once).
	 */
	@Override
	public IntrinsicSizes getIntrinsicSizes() {
		if (!this.flexBox.getFlexParams().flexDirection.isRow()) {
			return this.columnIntrinsicSizes();
		}
		double min = 0, max = 0, minPage = 0;
		boolean columnInflated = false;
		final boolean wrap = this.flexBox.getFlexParams().flexWrap.isWrap();
		for (final FlexItemContent item : this.items) {
			final BlockParams itemParams = item.itemBox.getBlockParams();
			final RectFrame frame = itemParams.frame;
			final double extra = insetsLine(frame.margin, 0) + insetsLine(frame.padding, 0) + borderLine(frame);
			// **Count dimensions declared by the item itself** (2026-08-05).
			// Previously, only content dimensions were added, ignoring these declarations, so an empty
			// item with `width:20pt` counted as 0. **Nested flex containers then had intrinsic
			// sizes accounting only for text**. From outside, this made
			// the main-axis free space negative, and default flex-shrink
			// collapsed inner items to width 0: the cause of "children disappear in nested flex".
			// Prioritize flex-basis as at bind time (FlexItemMetricsResolver).
			final double declared = declaredLineBase(item);
			double itemMin = item.sizes.minContent();
			double itemMax = item.sizes.maxContent();
			if (!Double.isNaN(declared)) {
				final double outer = itemParams.boxSizing == BoxSizingMode.BORDER_BOX
						? Math.max(declared, insetsLine(frame.padding, 0) + borderLine(frame))
						: declared + insetsLine(frame.padding, 0) + borderLine(frame);
				itemMin = Math.max(itemMin, outer - insetsLine(frame.padding, 0) - borderLine(frame));
				itemMax = Math.max(itemMax, outer - insetsLine(frame.padding, 0) - borderLine(frame));
			}
			// With wrap, min is the "largest item" (each item can wrap to a new line); with nowrap, use the sum.
			min = wrap ? Math.max(min, itemMin + extra) : min + itemMin + extra;
			max += itemMax + extra;
			minPage = Math.max(minPage, item.sizes.minPage());
			columnInflated |= item.sizes.columnInflated();
		}
		if (this.items.size() > 1) {
			final double gaps = this.flexBox.getFlexParams().columnGap * (this.items.size() - 1);
			max += gaps;
			if (!wrap) {
				min += gaps;
			}
		}
		return new IntrinsicSizes(min, max, minPage, columnInflated);
	}

	/**
	 * Intrinsic sizes of a column: the line axis is the cross axis, so the widest item decides min and max (§9.9.1;
	 * with wrap too, as Chrome sizes a column-wrap container by its largest item), counting the item's own width but
	 * not its flex-basis (a main-axis length). The page-axis min stacks the items and the gaps. Until 2026-10-08 the
	 * row sum applied: a column holding an image and a few lines measured as wide as all of them side by side, and a
	 * grid track sized by it pushed the card past the paper (frontiers-art, once column-flex stage 2 retained it).
	 */
	private IntrinsicSizes columnIntrinsicSizes() {
		double min = 0, max = 0, minPage = 0;
		boolean columnInflated = false;
		for (final FlexItemContent item : this.items) {
			final BlockParams itemParams = item.itemBox.getBlockParams();
			final RectFrame frame = itemParams.frame;
			final double extra = insetsLine(frame.margin, 0) + insetsLine(frame.padding, 0) + borderLine(frame);
			double itemMin = item.sizes.minContent();
			double itemMax = item.sizes.maxContent();
			final double declared = lineValue(itemParams.size, 0);
			if (!Double.isNaN(declared)) {
				final double inner = itemParams.boxSizing == BoxSizingMode.BORDER_BOX
						? Math.max(0, declared - insetsLine(frame.padding, 0) - borderLine(frame))
						: declared;
				itemMin = Math.max(itemMin, inner);
				itemMax = Math.max(itemMax, inner);
			}
			min = Math.max(min, itemMin + extra);
			max = Math.max(max, itemMax + extra);
			minPage += item.sizes.minPage();
			columnInflated |= item.sizes.columnInflated();
		}
		if (this.items.size() > 1) {
			minPage += this.flexBox.getFlexParams().rowGap * (this.items.size() - 1);
		}
		return new IntrinsicSizes(min, Math.max(min, max), minPage, columnInflated);
	}

	/**
	 * Line-axis base size <b>declared by the item itself</b> (NaN if indefinite).
	 * Prioritize {@code flex-basis} over {@code width}, in the same order as
	 * {@link FlexItemMetricsResolver}.
	 */
	private double declaredLineBase(final FlexItemContent item) {
		final net.zamasoft.foliojet.css.value.FlexBasisValue basis = item.spec.basis();
		if (!basis.isAuto() && !basis.isContent()
				&& basis.getSize() instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue length) {
			// The percentage basis is the container inner size, unresolved at this stage, so do not count it.
			return length.getLength();
		}
		return lineValue(item.itemBox.getBlockParams().size, 0);
	}

	/**
	 * Validation phase for converting the parent to a range (F1f; same structure as
	 * GridBuilder.collectAbsorbableItems, no side effects). Validate and list all item bodies
	 * as ordinary nested builders. Already bound items cannot be absorbed (fail closed).
	 */
	boolean collectAbsorbableItems(final LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final List<net.zamasoft.foliojet.layout.fragment.RangeHandle> outRanges,
			final Set<Long> ownedAbsoluteAnchors, final Set<TwoPassBlockBuilder> seen) {
		if (this.bound) {
			return false;
		}
		for (final FlexItemContent item : this.items) {
			if (!item.collectAbsorbable(log, fromId, toId, outRanges, ownedAbsoluteAnchors)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Physical-axis helpers (WritingMode handles orientation)

	/** Line-axis Dimension value (auto = NaN; resolve % against the reference size; height in vertical writing). */
	private double lineValue(final Dimension size, final double base) {
		return axisValue(size.getLineType(this.flow()), size.getLineLength(this.flow()),
				size.getLineRatio(this.flow()), base);
	}

	/** Page-axis Dimension value (auto = NaN; resolve % against the reference size; width in vertical writing). */
	private double pageValue(final Dimension size, final double base) {
		return axisValue(size.getPageType(this.flow()), size.getPageLength(this.flow()),
				size.getPageRatio(this.flow()), base);
	}

	/**
	 * Resolves dimension values.
	 *
	 * <p>
	 * <b>{@link LengthType#RELATIVE} (a pure percentage) stores the ratio in the value field</b>
	 * ({@code Length.create(ratio, RELATIVE)}). Only {@link LengthType#MIXED}
	 * (calc() combining an absolute length and a percentage) uses the ratio field.
	 *
	 * <p>
	 * Until 2026-08-03, this calculated "length + ratio × basis" without inspecting the type.
	 * As a result, <b>a flex item with {@code width: 66.66%} was read as "0.67 pt", raised
	 * to the automatic minimum size, and collapsed to min-content width</b>.
	 * Bootstrap 5 grids use {@code .row > * { width: 100% }} and
	 * {@code .col-N { width: X% }}, so <b>every Bootstrap document ended up with a type area
	 * that broke after every word</b>. Found in the first document of wave 0, which imported
	 * full-scale documents (PLAN §3). A sweep of 20 million documents never caught it:
	 * the generator does not specify % widths on flex items.
	 * Regression: files/unittest/3120-FLEXBOX/percentage-width.html.
	 */
	private static double axisValue(final LengthType type, final double length, final double ratio,
			final double base) {
		switch (type) {
		case AUTO:
			return Double.NaN;
		case ABSOLUTE:
			return length;
		case RELATIVE:
			return length * base;
		case MIXED:
			return length + ratio * base;
		default:
			throw new IllegalStateException(String.valueOf(type));
		}
	}

	private static double zeroIfNaN(final double value) {
		return Double.isNaN(value) ? 0 : value;
	}

	/** Line-axis Insets sum (absolute part + ratio × basis; auto = 0; top/bottom in vertical writing). */
	private double insetsLine(final Insets insets, final double base) {
		return insetsAxis(insets, base, this.flow().isVertical());
	}

	/** Page-axis Insets sum (absolute part + ratio × basis; auto = 0; left/right in vertical writing). */
	private double insetsPage(final Insets insets, final double base) {
		return insetsAxis(insets, base, !this.flow().isVertical());
	}

	/**
	 * Insets sum on the specified physical axis (horizontal=true: top/bottom; false: left/right).
	 *
	 * <p>
	 * Reading percentages has the same pitfall as {@link #axisValue}: {@code RELATIVE}
	 * stores the ratio in the value field. Flex items with {@code padding: 5%} were treated
	 * as 0.05 pt (fixed along with the width issue on 2026-08-03).
	 */
	private static double insetsAxis(final Insets insets, final double base, final boolean horizontal) {
		double sum = 0;
		if (horizontal) {
			sum += insetValue(insets.getTopType(), insets.getTop(), insets.getTopRatio(), base);
			sum += insetValue(insets.getBottomType(), insets.getBottom(), insets.getBottomRatio(), base);
		} else {
			sum += insetValue(insets.getLeftType(), insets.getLeft(), insets.getLeftRatio(), base);
			sum += insetValue(insets.getRightType(), insets.getRight(), insets.getRightRatio(), base);
		}
		return sum;
	}

	/** One edge of Insets (auto is 0 and contributes nothing to the sum). */
	private static double insetValue(final LengthType type, final double length, final double ratio,
			final double base) {
		if (type == LengthType.AUTO) {
			return 0;
		}
		return axisValue(type, length, ratio, base);
	}

	/** Sum of line-axis border widths (top/bottom in vertical writing). */
	private double borderLine(final RectFrame frame) {
		return this.flow().isVertical() ? frame.border.getTop().width + frame.border.getBottom().width
				: frame.border.getLeft().width + frame.border.getRight().width;
	}

	/** Sum of page-axis border widths (left/right in vertical writing). */
	private double borderPage(final RectFrame frame) {
		return this.flow().isVertical() ? frame.border.getLeft().width + frame.border.getRight().width
				: frame.border.getTop().width + frame.border.getBottom().width;
	}

	/** Returns whether a main-axis (line-axis) margin is auto (F3e; end = trailing; top/bottom in vertical writing). */
	private boolean mainMarginAuto(final FlexItemContent item, final boolean end) {
		final Insets margin = item.itemBox.getBlockParams().frame.margin;
		if (this.flow().isVertical()) {
			return (end ? margin.getBottomType() : margin.getTopType()) == LengthType.AUTO;
		}
		return (end ? margin.getRightType() : margin.getLeftType()) == LengthType.AUTO;
	}

	/** Whether a column item has an auto cross-axis margin (a line-axis one): it is not stretched (§9.4 step 11). */
	private boolean crossAutoMargin(final FlexItemContent item) {
		return this.mainMarginAuto(item, false) || this.mainMarginAuto(item, true);
	}

	/** Returns whether a cross-axis margin is auto (F3e; left/right in vertical writing). */
	private boolean crossMarginAuto(final FlexItemContent item, final boolean end) {
		final Insets margin = item.itemBox.getBlockParams().frame.margin;
		if (this.flow().isVertical()) {
			return (end ? margin.getRightType() : margin.getLeftType()) == LengthType.AUTO;
		}
		return (end ? margin.getBottomType() : margin.getTopType()) == LengthType.AUTO;
	}

	/**
	 * Returns item indexes in order-modified document order (ascending {@code order}, ties kept in recording
	 * order, §5.4) (F5a). Line breaking uses this order; a reverse main axis (F5b) then mirrors the items within
	 * each line ({@link #reverseWithinLines}), and the justify side is mirrored by the mapper's toFlexJustify.
	 * Binding stays in source order (keeps the Tagged PDF reading order and structure in source order, F5a).
	 */
	private int[] visualOrder() {
		final Integer[] seq = new Integer[this.items.size()];
		for (int i = 0; i < seq.length; ++i) {
			seq[i] = i;
		}
		Arrays.sort(seq,
				(x, y) -> Integer.compare(this.items.get(x).spec.order(), this.items.get(y).spec.order()));
		final int[] result = new int[seq.length];
		for (int i = 0; i < seq.length; ++i) {
			result[i] = seq[i];
		}
		return result;
	}

	/** Mirrors each line's range of {@code seq} in place on a copy (reverse main axis, 2026-10-07). */
	private static int[] reverseWithinLines(final int[] seq, final List<FlexLineBreaker.Line> lines) {
		final int[] result = seq.clone();
		for (final FlexLineBreaker.Line line : lines) {
			for (int k = line.from(); k < line.to(); ++k) {
				result[k] = seq[line.from() + line.to() - 1 - k];
			}
		}
		return result;
	}

	/** The same mirroring for the per-position metrics. */
	private static <T> List<T> reverseWithinLines(final List<T> list, final List<FlexLineBreaker.Line> lines) {
		final List<T> result = new ArrayList<>(list);
		for (final FlexLineBreaker.Line line : lines) {
			for (int k = line.from(); k < line.to(); ++k) {
				result.set(k, list.get(line.from() + line.to() - 1 - k));
			}
		}
		return result;
	}

	/** Synchronizes the host flow cursor (same structure as the end of GridBuilder.bind). */
	private void syncHostCursor(final BlockBuilder target, final FlexParams params) {
		final LayoutContext.Flow active = target.getFlow();
		assert active.box == this.flexBox : "Flex bindでactive flowがFlexではない: " + active.box;
		target.setPageAxis(active.pageAxis + this.flexBox.getInnerPageExtent(params.flow));
	}
}
