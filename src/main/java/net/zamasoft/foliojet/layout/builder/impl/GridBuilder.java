package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.box.impl.RowContributionSink;
import net.zamasoft.foliojet.layout.box.impl.RowGeometryFinalizer;
import net.zamasoft.foliojet.layout.box.impl.RowSubgridLink;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.sizing.BasicGridTrackSizing;
import net.zamasoft.foliojet.layout.sizing.FixedGridLayout;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver;
import net.zamasoft.foliojet.layout.sizing.GridRowSizing;
import net.zamasoft.foliojet.layout.sizing.Sizing;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Coordinator for Grid construction (Grid G1b, 2026-07-31 —
 * consult-codex-2026-07-31-grid-g1.txt §1). Like {@code TableBuilder}, it is pushed onto
 * {@code DocumentBuilder.builderStack} but is not a {@code Builder}.
 * Opens a fixed-width {@link GridItemBox} and item builder for each direct child,
 * then places them at Grid end according to {@link FixedGridLayout}.
 *
 * <p>
 * G3a (consult-codex-2026-07-31-grid-g3.txt): record item bodies with
 * {@link TwoPassBlockBuilder}, then assemble them at Grid end in the order
 * "resolve widths → bind → measure row heights → place".
 * For fixed columns, widths remain unchanged from construction, so results match
 * G1 (direct construction). Intrinsic size snapshots prepare for auto/fr columns (G3b/c).
 * Side effect: Grid is inactive within TwoPass, so a Grid nested in an item falls back
 * to G0 (single column), to be restored by GridEvent in G3d1.
 * </p>
 *
 * <p>
 * G3d1 (RetainedGrid): with a BlockBuilder host, {@code finish()} →
 * {@code addGrid} immediately calls {@link #bind}. With a TwoPass host
 * (such as a float without width), the recording's {@code GridEvent} retains it.
 * Range replay after width resolution reconstructs the plan and uses the same
 * {@link #bind}. Intrinsic size contributions ({@link #getIntrinsicSizes}) are G3d2.
 * </p>
 *
 * <p>
 * Subset: fixed/auto/fr/minmax/etc. columns; auto rows (maximum actual item height in the row)
 * or fixed-height rows; source-order row auto-placement.
 * Eligibility is checked by {@link GridBuilderLifecycle#eligible}.
 * </p>
 *
 * <p>
 * <b>subgrid (css-grid-2, 2026-08-29/09-03)</b>: at bind time, a grid with
 * {@code grid-template-columns: subgrid} receives resolved widths, gaps, and line names
 * of the parent grid columns it spans from its immediate containing item
 * ({@link GridItemBox}) ({@link #resolveSubgrid}), then uses them as fixed tracks.
 * On the row axis, pass descendant item contributions back to the parent; after the
 * parent resolves its rows, finalize child placement, sizes, and the row ledger.
 * The child itself always stretches in the parent item, ignoring specified height and
 * align-self/align-content. Its own border/padding/margin affect edge contributions and
 * available row heights. <b>Cases that fall back to a single-auto-track approximation</b>:
 * (a) intrinsic size measurement of the child grid ({@link #getIntrinsicSizes}, during
 * recording before parent track resolution; only the parent contribution uses this
 * approximation, while final placement uses real tracks),
 * (b) the child grid is not directly under an item (wrapped in a div, anonymous item =
 * direct text, made inline, etc.: the host builder's context flow at bind time is not
 * GridItemBox, or another flow intervenes),
 * (c) the parent grid has not run track placement (G0 fallback for vertical writing),
 * (d) paths without parent column resolution ({@code subgrid} specified when the parent
 * is not {@code display:grid}; equivalent to {@code none} in the spec as well).
 * Vertical writing is excluded because eligibility falls back to G0. Parallel notes
 * inside a row subgrid cannot move from their page positions at bind time, so detect
 * them with 2823 and leave them at their original positions.
 * </p>
 */
public final class GridBuilder
		implements net.zamasoft.foliojet.layout.builder.RetainedGrid, net.zamasoft.foliojet.layout.builder.ItemCoordinator {

	/** Total items constructed, not the number of TwoPass body records. */
	public static final AtomicLong GRID_ITEM_RECORDS = new AtomicLong();

	/** Number of bound items. */
	public static final AtomicLong GRID_ITEM_BINDS = new AtomicLong();

	/** Number of empty anonymous items discarded (without consuming slots). */
	public static final AtomicLong GRID_ITEM_EMPTY_ANON_DROPS = new AtomicLong();

	/**
	 * Number of containers reverted to source-order placement due to unsupported explicit
	 * placement (G4b: no silent cap; do not make just one item auto — recommendation Q5).
	 */
	public static final AtomicLong GRID_PLACEMENT_FALLBACKS = new AtomicLong();

	private final Builder host;

	/** Parent LayoutStack for item builders (the same instance as {@code host}). */
	private final LayoutStack hostStack;

	private final GridBox gridBox;

	/**
	 * Column tracks (fixed/auto/fr/%/min-content/max-content).
	 * Resolved by {@link #placementPlan}, including auto-repeat expansion, implicit column
	 * completion, and collapsing trailing auto-fit tracks (2026-08-29).
	 * Until then, retain the template unchanged ({@link #sizingTracks} resolves percentages).
	 */
	private List<GridTrackListValue.TrackSize> tracks;

	/** Column-axis line names (zero-based line index → names, including implicit area names; 2026-08-29). */
	private List<List<String>> columnLines = List.of();

	/** Row-axis line names (zero-based line index → names, including implicit area names and implicit rows). */
	private List<List<String>> rowLines = List.of();

	/** Explicit row count (the greater of grid-template-rows and grid-template-areas; 2026-08-29). */
	private int explicitRows;

	/** Expanded explicit column count (basis for the grid-auto-columns cycle; 2026-08-29). */
	private int explicitColumns;

	/** Container line width when {@link #placementPlan} was resolved (checks whether to re-expand auto-repeat). */
	private double planAvailable = Double.NaN;

	private double columnGap, rowGap;

	/**
	 * Column tracks inherited from the parent for subgrid (set by {@link #resolveSubgrid}
	 * at bind time; null means use the normal template).
	 */
	private List<GridTrackListValue.TrackSize> subgridColumns;

	/** Line names for {@link #subgridColumns} (column count + 1 entries; parent names + own {@code subgrid [a]} names). */
	private List<List<String>> subgridColumnLines;

	/** Row tracks inherited from the parent for subgrid. Null means use the normal row template. */
	private List<GridTrackListValue.TrackSize> subgridRows;

	/** Line names for {@link #subgridRows} (row count + 1 entries). */
	private List<List<String>> subgridRowLines;

	/** One-time connection to the parent row subgrid. Null for a normal grid. */
	private RowSubgridLink rowSubgridLink;

	/** Synthetic item owning {@link #rowSubgridLink}. Final dimensions are also written here. */
	private GridItemBox rowSubgridOwner;

	/** Half the difference from the parent gap. Applied to inner item edges for both contributions and placement. */
	private double rowSubgridGapShim;

	/** Whether construction found a page-margin-note in this Grid subtree. */
	private boolean containsPageMarginNote;

	/** Finalization registrations for child row subgrids (coordinates are local rows of this Grid). */
	private record PendingRowFinalizer(int rowStart, int span, RowGeometryFinalizer finalizer) {
	}

	private final List<GridItemContent> items = new ArrayList<>();

	/** Builder for the open item (element or anonymous), or null when none is open. */
	private TwoPassBlockBuilder openItemBuilder;

	private GridItemBox openItemBox;

	private double openItemMinCap = -1;

	/** Whether the open item is anonymous (direct text). */
	private boolean openItemAnonymous;

	/** Explicit placement specification of the open item (G4a). */
	private GridItemSpec openItemSpec = GridItemSpec.AUTO;

	GridBuilder(final Builder host, final GridBox gridBox) {
		this.host = host;
		this.hostStack = (LayoutStack) host;
		this.gridBox = gridBox;
		final GridParams params = gridBox.getGridParams();
		// No template means a single implicit auto column (2026-08-09;
		// see GridBuilderLifecycle.eligible).
		this.tracks = params.templateColumns.isEmpty()
				? List.of(net.zamasoft.foliojet.css.value.GridTrackListValue.Auto.INSTANCE)
				: params.templateColumns;
		this.columnGap = params.columnGap;
		this.rowGap = params.rowGap;
	}

	public boolean hasOpenElementItem() {
		return this.openItemBuilder != null && !this.openItemAnonymous;
	}

	public boolean hasOpenItem() {
		return this.openItemBuilder != null;
	}

	/**
	 * Params for a synthetic item ({@link NeutralItemParams}); inherit Grid text attributes
	 * and reset frame, etc. to neutral.
	 * G3a addendum (recommendation Q1): also neutralize size properties so Grid's own
	 * width/min/max-width do not leak into item intrinsic sizes.
	 */
	private BlockParams itemParams() {
		return NeutralItemParams.of(this.gridBox.getGridParams());
	}

	/** Original authored box for takeover (matches endBox; null for neutral/anonymous items). */
	private net.zamasoft.foliojet.layout.box.impl.FlowBlockBox openItemSource;
	private long openItemAnchor = -1;

	/**
	 * Returns whether a takeover element item sourced from {@code box} is open
	 * (2026-08-29, G7; same structure as FlexBuilder.isElementItemSource).
	 */
	public boolean isElementItemSource(final net.zamasoft.foliojet.layout.box.IBox box) {
		return this.openItemSource != null && this.openItemSource == box;
	}

	/**
	 * Opens an element item through <b>takeover</b> (2026-08-29, G7).
	 *
	 * <p>
	 * Transfer the authored block's params/pos to {@link GridItemBox} and
	 * <b>do not construct the original outer box</b>. The item becomes the authored box itself,
	 * so backgrounds and frames follow stretching to row height. With a wrapper, stretch
	 * did not reach the inner child, making {@code grid-template-rows} and
	 * {@code grid-row: span} appear ineffective (user report A-7).
	 * Flex already solves this in the same way ({@link FlexBuilder#startElementItem}).
	 * </p>
	 */
	public TwoPassBlockBuilder startElementItem(final net.zamasoft.foliojet.layout.box.impl.FlowBlockBox source,
			final GridItemSpec spec, final double minContributionCap) {
		final GridItemBox itemBox = new GridItemBox(source.getBlockParams(), (FlowPos) source.getPos(), 0);
		itemBox.setTakeover(true);
		final TwoPassBlockBuilder builder = this.startItem(itemBox, false, spec, minContributionCap);
		this.openItemSource = source;
		this.openItemAnchor = source.getSourceAnchor();
		return builder;
	}

	/**
	 * Opens the next item (for an element). The caller pushes the returned builder.
	 * {@code minContributionCap} caps the min-content contribution
	 * (see {@link GridItemContent#minContributionCap}; negative = unlimited);
	 * {@code sourceAnchor} is the authored child's anchor.
	 */
	public TwoPassBlockBuilder startElementItem(final GridItemSpec spec, final double minContributionCap,
			final long sourceAnchor) {
		final TwoPassBlockBuilder builder = this.startItem(false, spec, minContributionCap);
		this.openItemAnchor = sourceAnchor;
		return builder;
	}

	/** Opens an anonymous item for direct text (reuses it if already open). */
	public TwoPassBlockBuilder requireAnonymousItem(final long sourceAnchor) {
		if (this.openItemBuilder != null && this.openItemAnonymous) {
			return null; // Already open (no need to push again)
		}
		final TwoPassBlockBuilder builder = this.startItem(true, GridItemSpec.AUTO, -1);
		this.openItemAnchor = sourceAnchor;
		return builder;
	}

	private TwoPassBlockBuilder startItem(final boolean anonymous, final GridItemSpec spec,
			final double minContributionCap) {
		// The width is provisional (auto columns are unresolved). Recording and measurement are width-independent;
		// setTrackWidth supplies the resolved width just before bind in finish() (G3b).
		return this.startItem(new GridItemBox(this.itemParams(), new FlowPos(), 0), anonymous, spec,
				minContributionCap);
	}

	private TwoPassBlockBuilder startItem(final GridItemBox itemBox, final boolean anonymous,
			final GridItemSpec spec, final double minContributionCap) {
		assert this.openItemBuilder == null : "前のitemが閉じられていない";
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this.hostStack, itemBox);
		// Distinguish binds originating from Grid/Flex items from TOPLEVEL.
		builder.tagRootKind(
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind.GRID_ITEM);
		this.openItemBuilder = builder;
		this.openItemBox = itemBox;
		this.openItemMinCap = minContributionCap;
		this.openItemAnonymous = anonymous;
		this.openItemSpec = spec;
		this.openItemAnchor = -1;
		return builder;
	}

	/**
	 * Finalizes the open item (recording completion point). Discard empty anonymous items
	 * (whitespace-only, etc.) without consuming a slot. Seal the authored range for element
	 * items, or the body within synthetic boundaries for anonymous items.
	 */
	public void itemClosed() {
		final TwoPassBlockBuilder builder = this.openItemBuilder;
		final GridItemBox itemBox = this.openItemBox;
		final boolean anonymous = this.openItemAnonymous;
		final GridItemSpec spec = this.openItemSpec;
		final boolean takeover = this.openItemSource != null;
		final long anchor = this.openItemAnchor;
		this.openItemBuilder = null;
		this.openItemBox = null;
		this.openItemAnonymous = false;
		this.openItemSpec = GridItemSpec.AUTO;
		this.openItemSource = null;
		this.openItemAnchor = -1;
		if (anonymous && !builder.hasLayoutContent() && !itemBox.paintsAnything()) {
			GRID_ITEM_EMPTY_ANON_DROPS.incrementAndGet();
			return;
		}
		GRID_ITEM_RECORDS.incrementAndGet();
		builder.tagItemKind(anonymous, takeover);
		builder.sealBodyForRangeBind(anchor, anonymous
				? net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode.ANONYMOUS_CHILDREN
				: takeover
					? net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode.CHILDREN_ONLY
					: net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode.ROOTED_SUBTREE);
		this.items.add(new GridItemContent(itemBox, builder, builder.getIntrinsicSizes(), anonymous, spec,
				this.openItemMinCap, takeover));
	}

	/**
	 * Grid end (G3d1): passes the execution plan to the host. BlockBuilder calls
	 * {@link #bind} immediately; TwoPass retains it in the ownership ledger and binds
	 * after width resolution.
	 */
	public void finish() {
		assert this.openItemBuilder == null : "item未クローズでGrid終端に到達";
		this.publishSubgridSource();
		this.host.addGrid(this);
	}

	@Override
	public net.zamasoft.foliojet.layout.box.IBox getItemHostBox() {
		return this.gridBox;
	}

	@Override
	public GridBox getGridBox() {
		return this.gridBox;
	}

	/** Resolved placement plan (G4b: always shared by getIntrinsicSizes and bind). */
	private GridPlacementResolver.Plan placementPlan;

	/**
	 * Resolves the placement plan exactly once (G4b, recommendation Q3). For unsupported
	 * explicit placement (implicit columns, negative rows, exceeding limits) or rowSpan&gt;1
	 * (planned for G4d), revert the entire container to G3 source-order placement
	 * (col=i%n, row=i/n). Making just one item auto would shift every subsequent item
	 * through occupancy/cursor state (the most important rule in recommendation Q5).
	 */
	private GridPlacementResolver.Plan placementPlan() {
		if (this.placementPlan != null) {
			return this.placementPlan;
		}
		final GridParams params = this.gridBox.getGridParams();
		final double available = Math.max(0, this.gridBox.getLineSize());
		this.planAvailable = available;
		// (1) Expand explicit columns (2026-08-29): repeat auto-repeat as many times as fit,
		// keeping line names alongside tracks. No template means a single implicit auto column.
		final List<GridTrackListValue.TrackSize> cols = new ArrayList<>();
		final List<List<String>> lines = new ArrayList<>();
		lines.add(new ArrayList<>());
		boolean autoFit = false;
		if (this.subgridColumns != null) {
			// subgrid: fixed tracks and line names inherited from the parent (2026-08-29)
			cols.addAll(this.subgridColumns);
			lines.clear();
			for (final List<String> names : this.subgridColumnLines) {
				lines.add(new ArrayList<>(names));
			}
			this.explicitColumns = cols.size();
		} else if (params.templateColumns.isEmpty()) {
			// Zero explicit columns: the first column is also implicit (uses grid-auto-columns size,
			// or auto as before if absent). This prevents only the first column from remaining auto
			// and taking the leftover space as column flow adds implicit columns.
			this.explicitColumns = 0;
			this.addImplicitColumn(cols, lines);
		} else {
			autoFit = expandTracks(params.templateColumns, params.columnLineNames, available, this.columnGap, cols,
					lines);
			this.explicitColumns = cols.size();
		}
		// (2) Column count and implicit line names (name-start/name-end) defined by grid-template-areas
		final net.zamasoft.foliojet.css.value.GridTemplateAreasValue areas = params.templateAreas;
		while (cols.size() < areas.getColumnCount()) {
			this.addImplicitColumn(cols, lines);
		}
		final List<List<String>> rowLines = new ArrayList<>();
		final List<List<String>> initialRowLines = this.subgridRowLines != null ? this.subgridRowLines
				: params.rowLineNames;
		for (final List<String> names : initialRowLines) {
			rowLines.add(new ArrayList<>(names));
		}
		while (rowLines.size() < areas.getRowCount() + 1) {
			rowLines.add(new ArrayList<>());
		}
		for (final net.zamasoft.foliojet.css.value.GridTemplateAreasValue.Area area : areas.getAreas()) {
			lines.get(area.columnStart()).add(area.name() + "-start");
			lines.get(area.columnEnd()).add(area.name() + "-end");
			rowLines.get(area.rowStart()).add(area.name() + "-start");
			rowLines.get(area.rowEnd()).add(area.name() + "-end");
		}
		this.explicitRows = this.subgridRows != null ? this.subgridRows.size()
				: Math.max(params.templateRows.size(), areas.getRowCount());
		// (3) Convert line names to numbers
		final List<GridItemSpec> specs = new ArrayList<>(this.items.size());
		for (final GridItemContent item : this.items) {
			specs.add(net.zamasoft.foliojet.layout.sizing.GridLineNameResolver.resolve(item.spec, lines, rowLines));
		}
		// (4) In row flow, add implicit columns for lines/spans referring beyond the explicit columns
		// (using grid-auto-columns sizes). Previously this failed closed and reverted to source-order
		// placement. Retain the rule against making just one item auto;
		// instead, add columns so the placement can resolve.
		if (!params.autoFlowColumn) {
			final int needed = Math.min(GridPlacementResolver.LIMIT, requiredColumns(specs));
			while (cols.size() < needed) {
				this.addImplicitColumn(cols, lines);
			}
		}
		GridPlacementResolver.Plan plan = null;
		final GridPlacementResolver.Result placement = this.rowSubgridLink == null
				? GridPlacementResolver.resolve(specs, cols.size(), this.explicitRows, params.autoFlowColumn,
						params.autoFlowDense)
				: GridPlacementResolver.resolve(specs, cols.size(), this.explicitRows, params.autoFlowColumn,
						params.autoFlowDense, this.rowSubgridLink.span());
		if (placement instanceof GridPlacementResolver.Result.Resolved resolved) {
			plan = resolved.plan(); // Handle rowSpan with GridRowSizing deficit distribution (G4d).
			// Implicit columns created by column flow
			while (cols.size() < plan.columnCount()) {
				this.addImplicitColumn(cols, lines);
			}
		}
		if (plan == null) {
			GRID_PLACEMENT_FALLBACKS.incrementAndGet();
			final int n = cols.size();
			final GridPlacementResolver.GridArea[] fallback = new GridPlacementResolver.GridArea[this.items.size()];
			for (int i = 0; i < fallback.length; ++i) {
				final int row = this.rowSubgridLink == null ? i / n
						: Math.min(this.rowSubgridLink.span() - 1, i / n);
				fallback[i] = new GridPlacementResolver.GridArea(i % n, row, 1, 1);
			}
			plan = new GridPlacementResolver.Plan(List.of(fallback), n, this.rowSubgridLink == null
					? Math.max((fallback.length + n - 1) / n, this.explicitRows)
					: this.rowSubgridLink.span());
		}
		// (5) auto-fit: collapse trailing tracks without items, including their gaps
		if (autoFit) {
			int used = 1;
			for (final GridPlacementResolver.GridArea area : plan.areas()) {
				used = Math.max(used, area.column() + area.columnSpan());
			}
			if (used < cols.size()) {
				cols.subList(used, cols.size()).clear();
				lines.subList(used + 1, lines.size()).clear();
				plan = new GridPlacementResolver.Plan(plan.areas(), used, plan.rowCount());
			}
		}
		this.tracks = List.copyOf(cols);
		this.columnLines = lines;
		while (rowLines.size() < plan.rowCount() + 1) {
			rowLines.add(new ArrayList<>());
		}
		this.rowLines = rowLines;
		this.placementPlan = plan;
		return plan;
	}

	/**
	 * Expands the explicit column template (2026-08-29). Repeat auto-repeat as many times
	 * as fit after subtracting other fixed-width tracks and gaps (at least once).
	 * If the reference width is unresolved, repeat once, as the spec requires for intrinsic
	 * size measurement.
	 *
	 * @return whether it includes auto-fit
	 */
	private static boolean expandTracks(final List<GridTrackListValue.TrackSize> template,
			final List<List<String>> templateLines, final double available, final double gap,
			final List<GridTrackListValue.TrackSize> cols, final List<List<String>> lines) {
		boolean autoFit = false;
		// Total fixed widths outside auto-repeat (remaining width for the repetition count)
		double fixedSum = 0;
		int fixedCount = 0;
		for (final GridTrackListValue.TrackSize t : template) {
			if (t instanceof GridTrackListValue.AutoRepeat) {
				continue;
			}
			// Spec (§7.2.3.2): count each track by its max size if definite,
			// otherwise its min size (for content-dependent tracks, count only the gap).
			fixedSum += definiteExtent(t, available);
			++fixedCount;
		}
		for (int i = 0; i < template.size(); ++i) {
			final GridTrackListValue.TrackSize t = template.get(i);
			if (i < templateLines.size()) {
				lines.get(lines.size() - 1).addAll(templateLines.get(i));
			}
			if (t instanceof GridTrackListValue.AutoRepeat repeat) {
				final int unitSize = repeat.unit().size();
				final double unitMin = repeat.unitMinLength() + repeat.unitMinRatio() * available;
				int reps = 1;
				if (available > 0 && unitMin > 0) {
					final double room = available - fixedSum - gap * fixedCount;
					reps = (int) Math.floor((room + gap) / (unitMin + gap * unitSize) + 1e-9);
					reps = Math.max(1, reps);
				}
				reps = Math.min(reps, Math.max(1, (net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.MAX_TRACKS
						- cols.size() - template.size()) / unitSize));
				autoFit |= repeat.fit();
				for (int r = 0; r < reps; ++r) {
					lines.get(lines.size() - 1).addAll(repeat.unitLineNames().get(0));
					for (int k = 0; k < unitSize; ++k) {
						cols.add(repeat.unit().get(k));
						lines.add(new ArrayList<>(repeat.unitLineNames().get(k + 1)));
					}
				}
				continue;
			}
			cols.add(t);
			lines.add(new ArrayList<>());
		}
		if (templateLines.size() > template.size()) {
			lines.get(lines.size() - 1).addAll(templateLines.get(template.size()));
		}
		return autoFit;
	}

	/**
	 * Definite track width used to determine the auto-repeat count (2026-08-29).
	 * Use the value for fixed lengths/percentages; for minmax(), use max if definite,
	 * otherwise min. Content-dependent sizes count as 0.
	 */
	private static double definiteExtent(final GridTrackListValue.TrackSize t, final double available) {
		if (t instanceof GridTrackListValue.Fixed f) {
			return f.length();
		}
		if (t instanceof GridTrackListValue.Percentage p) {
			return p.resolve(available);
		}
		if (t instanceof GridTrackListValue.MinMax m) {
			final double max = definiteExtent(m.max(), available);
			return m.max() instanceof GridTrackListValue.Fixed || m.max() instanceof GridTrackListValue.Percentage
					? max
					: definiteExtent(m.min(), available);
		}
		return 0;
	}

	/** Adds one implicit column using the grid-auto-columns cycle (auto if empty). */
	private void addImplicitColumn(final List<GridTrackListValue.TrackSize> cols, final List<List<String>> lines) {
		final List<GridTrackListValue.TrackSize> autoColumns = this.gridBox.getGridParams().autoColumns;
		final int implicitIndex = cols.size() - this.explicitColumns;
		cols.add(autoColumns.isEmpty() ? GridTrackListValue.Auto.INSTANCE
				: autoColumns.get(Math.max(0, implicitIndex) % autoColumns.size()));
		lines.add(new ArrayList<>());
	}

	/**
	 * Column count required by each item's column specification in row flow
	 * (positive line numbers and spans only; negative numbers are relative to the explicit
	 * end and do not count).
	 */
	private static int requiredColumns(final List<GridItemSpec> specs) {
		int needed = 1;
		for (final GridItemSpec spec : specs) {
			final net.zamasoft.foliojet.css.value.GridLineValue s = spec.columnStart(), e = spec.columnEnd();
			final int startLine = !s.isAuto() && !s.isSpan() && !s.isNamed() && s.getNumber() > 0 ? s.getNumber() : 0;
			final int endLine = !e.isAuto() && !e.isSpan() && !e.isNamed() && e.getNumber() > 0 ? e.getNumber() : 0;
			final int startSpan = s.isSpan() ? s.getNumber() : 0;
			final int endSpan = e.isSpan() ? e.getNumber() : 0;
			if (startLine > 0 && endLine > 0) {
				needed = Math.max(needed, Math.max(startLine, endLine) - 1);
			} else if (startLine > 0) {
				needed = Math.max(needed, startLine - 1 + Math.max(1, endSpan));
			} else if (endLine > 0) {
				needed = Math.max(needed, endLine - 1);
			} else {
				needed = Math.max(needed, Math.max(startSpan, endSpan));
			}
		}
		return needed;
	}

	/**
	 * Columns passed to track resolution (2026-08-29): convert % to absolute lengths
	 * using the container line width (unresolved reference width, as in intrinsic measurement,
	 * is treated as auto by {@code BasicGridTrackSizing}).
	 */
	private List<GridTrackListValue.TrackSize> sizingTracks(final double available) {
		if (!(available > 0)) {
			return this.tracks;
		}
		List<GridTrackListValue.TrackSize> resolved = null;
		for (int i = 0; i < this.tracks.size(); ++i) {
			final GridTrackListValue.TrackSize t = this.tracks.get(i);
			final GridTrackListValue.TrackSize r;
			if (t instanceof GridTrackListValue.Percentage p) {
				r = new GridTrackListValue.Fixed(p.resolve(available));
			} else if (t instanceof GridTrackListValue.MinMax m
					&& (m.min() instanceof GridTrackListValue.Percentage
							|| m.max() instanceof GridTrackListValue.Percentage)) {
				// Percentage on either side of minmax() (2026-08-29)
				r = new GridTrackListValue.MinMax(
						m.min() instanceof GridTrackListValue.Percentage p
								? new GridTrackListValue.Fixed(p.resolve(available))
								: m.min(),
						m.max() instanceof GridTrackListValue.Percentage p
								? new GridTrackListValue.Fixed(p.resolve(available))
								: m.max());
			} else {
				continue;
			}
			if (resolved == null) {
				resolved = new ArrayList<>(this.tracks);
			}
			resolved.set(i, r);
		}
		return resolved == null ? this.tracks : resolved;
	}

	/**
	 * Inherits subgrid column tracks and the row subgrid connection from the parent
	 * (css-grid-2, 2026-08-29/09-03). See "Cases that fall back to a single-auto-track
	 * approximation" in the class Javadoc.
	 *
	 * @param target bind destination (inherit only if its context flow is this item's GridItemBox
	 *               and only this GridBox is stacked above it)
	 * @return whether inheritance succeeded
	 */
	private boolean resolveSubgrid(final BlockBuilder target) {
		final GridParams params = this.gridBox.getGridParams();
		if (!params.columnsSubgrid && !params.rowsSubgrid) {
			return false;
		}
		if (target.getFlowCount() != 2 || !(target.getFlow(0).box instanceof GridItemBox item)
				|| target.getFlow(1).box != this.gridBox) {
			return false;
		}
		if (item.isTakeover()) {
			// A takeover item is the authored element itself. A grid inside it is
			// **not directly under the item**, so it cannot inherit parent columns (G7, 2026-08-29).
			// When items were wrappers, flow depth alone distinguished these cases.
			// Regression: #s3 in files/unittest/0500-grid/subgrid.html.
			return false;
		}
		final GridItemBox.SubgridTracks parent = item.getSubgridTracks();
		if (parent == null) {
			return false;
		}
		boolean rowsResolved = false;
		if (params.rowsSubgrid) {
			final RowSubgridLink link = parent.consumeRowSubgridLink();
			if (link != null) {
				this.rowSubgridLink = link;
				this.rowSubgridOwner = item;
				final double childGap = params.rowGapNormal ? link.parentRowGap() : this.rowGap;
				this.rowSubgridGapShim = (childGap - link.parentRowGap()) / 2;
				this.rowGap = childGap;
				final int span = link.span();
				final List<GridTrackListValue.TrackSize> rows = new ArrayList<>(span);
				for (int i = 0; i < span; ++i) {
					rows.add(GridTrackListValue.Auto.INSTANCE);
				}
				final List<List<String>> lines = new ArrayList<>(span + 1);
				for (int i = 0; i <= span; ++i) {
					final List<String> names = new ArrayList<>(link.rowLineNames().get(i));
					if (i < params.rowLineNames.size()) {
						names.addAll(params.rowLineNames.get(i));
					}
					lines.add(names);
				}
				this.subgridRows = List.copyOf(rows);
				this.subgridRowLines = lines;
				rowsResolved = true;
				if (this.containsPageMarginNote && target.getPageContext() != null) {
					final String detailKey = "2823.subgrid-rows-margin-note";
					final net.zamasoft.foliojet.ua.UserAgent ua = target.getPageContext().getPageGenerator()
							.getUserAgent();
					if (ua.getUAContext().getReportedIneffectiveCombinationDetails().add(detailKey)) {
						ua.message(net.zamasoft.foliojet.message.MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION,
								"float", net.zamasoft.foliojet.message.MessageCodeUtils.detail(detailKey));
					}
				}
			}
		}
		boolean columnsResolved = false;
		if (params.columnsSubgrid) {
			final double[] widths = parent.columnWidths();
			final int span = widths.length;
			final double line = Math.max(0, this.gridBox.getLineSize());
			final List<GridTrackListValue.TrackSize> cols = new ArrayList<>(span);
			if (span == 1) {
				cols.add(new GridTrackListValue.Fixed(line));
			} else {
				// Own border/padding/margin intrude into the first and last tracks.
				// Compute the trailing side as "parent area width − leading edge − own content width"
				// (when justify-self is not stretch and the item is narrower than its area, the last
				// track shrinks by that amount, keeping inner lines aligned with the parent).
				final double startInset = this.gridBox.getFrame().getFrameLineStart(params.flow);
				double sum = parent.columnGap() * (span - 1);
				for (final double w : widths) {
					sum += w;
				}
				final double endInset = sum - startInset - line;
				for (int i = 0; i < span; ++i) {
					double w = widths[i];
					if (i == 0) {
						w -= startInset;
					}
					if (i == span - 1) {
						w -= endInset;
					}
					cols.add(new GridTrackListValue.Fixed(Math.max(0, w)));
				}
			}
			final List<List<String>> lines = new ArrayList<>(span + 1);
			for (int i = 0; i <= span; ++i) {
				final List<String> names = new ArrayList<>(parent.columnLineNames().get(i));
				if (i < params.columnLineNames.size()) {
					names.addAll(params.columnLineNames.get(i));
				}
				lines.add(names);
			}
			this.subgridColumns = cols;
			this.subgridColumnLines = lines;
			this.columnGap = parent.columnGap();
			this.tracks = List.copyOf(cols);
			columnsResolved = true;
		}
		return columnsResolved || rowsResolved;
	}

	/** Records the presence of parallel notes so 2823 can be reported if this becomes a row subgrid. */
	public void notePageMarginNote() {
		this.containsPageMarginNote = true;
	}

	/**
	 * Template size of row r (explicit rows use {@code grid-template-rows}; implicit rows
	 * cycle through {@code grid-auto-rows}; null if absent = content height; 2026-08-29).
	 */
	private GridTrackListValue.TrackSize rowTrack(final int r) {
		final GridParams params = this.gridBox.getGridParams();
		if (this.subgridRows != null && r < this.subgridRows.size()) {
			return this.subgridRows.get(r);
		}
		if (this.subgridRows != null) {
			return null;
		}
		if (r < params.templateRows.size()) {
			return params.templateRows.get(r);
		}
		if (params.autoRows.isEmpty()) {
			return null;
		}
		return params.autoRows.get((r - params.templateRows.size()) % params.autoRows.size());
	}

	private static boolean hasAutoRepeat(final List<GridTrackListValue.TrackSize> template) {
		for (final GridTrackListValue.TrackSize t : template) {
			if (t instanceof GridTrackListValue.AutoRepeat) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Item's authored line-axis size (content-box); auto/unspecified is NaN (G7, 2026-08-29).
	 * For {@code box-sizing: border-box}, subtract the frame to obtain the inner size.
	 */
	private static double authoredLineSize(final GridItemBox itemBox,
			final net.zamasoft.foliojet.layout.box.params.WritingMode flow,
			final double areaWidth) {
		final BlockParams p = itemBox.getBlockParams();
		final double value;
		switch (p.size.getLineType(flow)) {
		case ABSOLUTE:
			value = p.size.getLineLength(flow);
			break;
		case RELATIVE:
			value = p.size.getLineLength(flow) * areaWidth;
			break;
		case MIXED:
			value = p.size.getLineLength(flow) + p.size.getLineRatio(flow) * areaWidth;
			break;
		default:
			return Double.NaN;
		}
		final double borderBoxAdjust = p.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
				? itemBox.getFrame().getBorderLineExtent(flow)
				: 0;
		return Math.max(0, value - borderBoxAdjust);
	}

	/** Fixed height of row r (fixed, or % with a resolved basis; otherwise NONE for content height). */
	private double fixedRowHeight(final int r) {
		final GridTrackListValue.TrackSize track = this.rowTrack(r);
		if (track instanceof GridTrackListValue.Fixed f) {
			return f.length();
		}
		if (track instanceof GridTrackListValue.Percentage p && this.gridBox.isSpecifiedPageSize()) {
			return p.resolve(this.gridBox.getInnerPageExtent(this.gridBox.getGridParams().flow));
		}
		return net.zamasoft.foliojet.layout.util.LayoutUtils.NONE;
	}

	/**
	 * Retains only row heights resolved before item bind. Leave unresolved rows as NONE
	 * and do not pass them as seeds to post-bind {@code GridRowSizing.resolve}.
	 */
	private double[] preResolvedRowHeights(final GridPlacementResolver.Plan plan) {
		final double[] heights = new double[Math.max(1, plan.rowCount())];
		java.util.Arrays.fill(heights, net.zamasoft.foliojet.layout.util.LayoutUtils.NONE);
		for (int r = 0; r < heights.length; ++r) {
			heights[r] = this.fixedRowHeight(r);
		}
		return heights;
	}

	/** Whether every item has rowSpan=1 (G6 row splitting eligibility). */
	private static boolean allSingleRowSpan(final GridPlacementResolver.Plan plan, final int count) {
		for (int i = 0; i < count; ++i) {
			if (plan.areas().get(i).rowSpan() != 1) {
				return false;
			}
		}
		return true;
	}

	/** Whether row numbers are nondecreasing in source order (G6). */
	private static boolean isRowMajor(final GridPlacementResolver.Plan plan, final int count) {
		int prevRow = -1;
		for (int i = 0; i < count; ++i) {
			final int r = plan.areas().get(i).row();
			if (r < prevRow) {
				return false;
			}
			prevRow = r;
		}
		return true;
	}

	/**
	 * Whether any items overlap in grid areas (G6: exclude overlaps because reordering flow
	 * registration would violate the specified paint order = document order).
	 */
	private static boolean hasOverlap(final GridPlacementResolver.Plan plan, final int count) {
		for (int a = 0; a < count; ++a) {
			final GridPlacementResolver.GridArea x = plan.areas().get(a);
			for (int b = a + 1; b < count; ++b) {
				final GridPlacementResolver.GridArea y = plan.areas().get(b);
				final boolean rowsMeet = x.row() < y.row() + y.rowSpan() && y.row() < x.row() + x.rowSpan();
				final boolean colsMeet = x.column() < y.column() + y.columnSpan()
						&& y.column() < x.column() + x.columnSpan();
				if (rowsMeet && colsMeet) {
					return true;
				}
			}
		}
		return false;
	}

	/** Column contribution of each item based on the plan (G4d, including spans). */
	private List<BasicGridTrackSizing.ItemContribution> columnContributions(
			final GridPlacementResolver.Plan plan) {
		return this.columnContributions(plan, -1);
	}

	/**
	 * @param inflatedCap cap for item min-content inflated by the column count
	 *                    ({@code columnInflated}); negative means uncapped.
	 *                    During track resolution, pass the Grid container content-box line width.
	 *                    Columns can shrink, so the inflated minimum need not be honored
	 *                    (same reason as the clamp in AbstractStaticBlockBox).
	 *                    On 2026-08-22, sweep seed 1879802 had a multi-column table in a grid
	 *                    whose min-content expanded the track to 2.7 times the sheet size,
	 *                    drawing the second column outside the sheet. Intrinsic measurement
	 *                    does not cap it, but propagates the flag upward
	 */
	private List<BasicGridTrackSizing.ItemContribution> columnContributions(
			final GridPlacementResolver.Plan plan, final double inflatedCap) {
		final List<BasicGridTrackSizing.ItemContribution> contributions = new ArrayList<>(this.items.size());
		for (int i = 0; i < this.items.size(); ++i) {
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			final GridItemContent item = this.items.get(i);
			final GridItemBox.SubgridSource source = item.takeover ? null : item.itemBox.getSubgridSource();
			if (source != null) {
				// A column subgrid directly under the item: its items size the tracks it spans (css-grid-2 §9,
				// 2026-10-09). As one spanning item it reached only the fr tracks, and auto tracks stayed at zero.
				expandSubgrid(source, area.column(), area.columnSpan(),
						this.columnLines.subList(area.column(), area.column() + area.columnSpan() + 1), contributions);
				continue;
			}
			final double[] c = this.lineContribution(item, inflatedCap);
			contributions.add(new BasicGridTrackSizing.ItemContribution(area.column(), area.columnSpan(), c[0], c[1]));
		}
		return contributions;
	}

	/** The min/max column contribution of an item (as a single item; see {@link #columnContributions}). */
	private double[] lineContribution(final GridItemContent item, final double inflatedCap) {
		// Override the automatic minimum size (see GridItemContent.minContributionCap).
		double itemMin = item.minContributionCap >= 0
				? Math.min(item.sizes.minContent(), item.minContributionCap)
				: item.sizes.minContent();
		if (inflatedCap >= 0 && item.sizes.columnInflated() && itemMin > inflatedCap) {
			itemMin = inflatedCap;
		}
		double itemMax = item.sizes.maxContent();
		if (item.takeover) {
			// In takeover, the authored root's frame and declared width lie outside the recorded body
			// (G7, 2026-08-29). Unless added back, a sized item has its track resolved
			// to the narrow content-only size (observed in D of place-shorthand).
			final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.gridBox.getGridParams().flow;
			final BlockParams ip = item.itemBox.getBlockParams();
			final double declared = ip.size.getLineType(flow) == LengthType.ABSOLUTE
					? ip.size.getLineLength(flow)
					: Double.NaN;
			final double extras = item.itemBox.getFrame().getBorderLineExtent(flow);
			if (!Double.isNaN(declared)) {
				final double used = ip.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? Math.max(declared, extras)
						: declared + extras;
				itemMin = used;
				itemMax = used;
			} else {
				itemMin += extras;
				itemMax += extras;
			}
		}
		return new double[] { itemMin, itemMax };
	}

	/**
	 * Adds the contributions of a column subgrid's items to the tracks [start, start + span) of this grid
	 * (css-grid-2 §9, 2026-10-09). The items are placed again in the subgrid's real column count (its span here),
	 * with this grid's line names plus its own; items beyond its columns are clamped into them. The subgrid's own
	 * margin, border and padding count as extra margin on the items at its edges. A nested column subgrid is
	 * expanded the same way into the columns its item spans.
	 *
	 * @param lines this grid's line names for the span (span + 1 entries)
	 */
	private static void expandSubgrid(final GridItemBox.SubgridSource source, final int start, final int span,
			final List<List<String>> lines, final List<BasicGridTrackSizing.ItemContribution> out) {
		final List<GridItemBox.SubgridCell> cells = source.cells();
		if (cells.isEmpty() || span <= 0) {
			if (span > 0) {
				final double frame = source.startInset() + source.endInset();
				out.add(new BasicGridTrackSizing.ItemContribution(start, span, frame, frame));
			}
			return;
		}
		final List<List<String>> merged = new ArrayList<>(span + 1);
		for (int i = 0; i <= span; ++i) {
			final List<String> names = new ArrayList<>(i < lines.size() ? lines.get(i) : List.of());
			if (i < source.lineNames().size()) {
				names.addAll(source.lineNames().get(i));
			}
			merged.add(names);
		}
		final List<List<String>> rowLines = List.of(List.of());
		final List<GridItemSpec> specs = new ArrayList<>(cells.size());
		for (final GridItemBox.SubgridCell cell : cells) {
			specs.add(net.zamasoft.foliojet.layout.sizing.GridLineNameResolver.resolve(cell.spec(), merged, rowLines));
		}
		List<GridPlacementResolver.GridArea> areas = null;
		final GridPlacementResolver.Result placement = GridPlacementResolver.resolve(specs, span,
				source.explicitRows(), source.autoFlowColumn(), source.dense());
		if (placement instanceof GridPlacementResolver.Result.Resolved resolved) {
			areas = resolved.plan().areas();
		}
		final int end = start + span;
		for (int k = 0; k < cells.size(); ++k) {
			final GridItemBox.SubgridCell cell = cells.get(k);
			int column = areas == null ? k % span : Math.min(areas.get(k).column(), span - 1);
			final int columnSpan = areas == null ? 1 : Math.max(1, Math.min(areas.get(k).columnSpan(), span - column));
			column += start;
			final int first = out.size();
			if (cell.nested() != null) {
				expandSubgrid(cell.nested(), column, columnSpan,
						merged.subList(column - start, column - start + columnSpan + 1), out);
			} else {
				out.add(new BasicGridTrackSizing.ItemContribution(column, columnSpan, cell.min(), cell.max()));
			}
			// The subgrid's own frame is extra margin on what touches its edges.
			for (int j = first; j < out.size(); ++j) {
				final BasicGridTrackSizing.ItemContribution c = out.get(j);
				double extra = 0;
				if (c.column() == start) {
					extra += source.startInset();
				}
				if (c.column() + c.span() == end) {
					extra += source.endInset();
				}
				if (extra != 0) {
					out.set(j, new BasicGridTrackSizing.ItemContribution(c.column(), c.span(), c.minContent() + extra,
							c.maxContent() + extra));
				}
			}
		}
	}

	/**
	 * Registers this grid's column contributions on the item it sits directly under when its columns are a subgrid
	 * (2026-10-09; see {@link GridItemBox.SubgridSource}). Same condition as {@link #resolveSubgrid}: the host is the
	 * item's body with only this grid open above the item, and the item is not the authored element itself.
	 */
	private void publishSubgridSource() {
		final GridParams params = this.gridBox.getGridParams();
		if (!params.columnsSubgrid || !(this.host instanceof TwoPassBlockBuilder body)
				|| !(body.getRootBox() instanceof GridItemBox item) || item.isTakeover() || body.getFlowDepth() != 2
				|| body.getFlowBox() != this.gridBox) {
			return;
		}
		final List<GridItemBox.SubgridCell> cells = new ArrayList<>(this.items.size());
		for (final GridItemContent child : this.items) {
			final GridItemBox.SubgridSource nested = child.takeover ? null : child.itemBox.getSubgridSource();
			final double[] c = this.lineContribution(child, -1);
			cells.add(new GridItemBox.SubgridCell(child.spec, c[0], c[1], nested));
		}
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = this.gridBox.getFrame();
		item.setSubgridSource(new GridItemBox.SubgridSource(cells, params.columnLineNames,
				frame.getFrameLineStart(params.flow), frame.getFrameLineEnd(params.flow), params.autoFlowColumn,
				params.autoFlowDense, params.rowsSubgrid ? 0 : Math.max(params.templateRows.size(),
						params.templateAreas.getRowCount())));
	}

	/**
	 * Intrinsic content-box size contribution of the entire Grid (G3d2, recommendation Q2/G3d2).
	 * Line axis: min=gap+Σ(fixed length|maximum item min-content in the column),
	 * max=gap+Σ(fixed length|maximum item max-content in the column) (for both auto/fr;
	 * the fr max-content contribution comes from content). Page-axis min is the sum of
	 * each row's maximum item minPage + rowGap. Excludes the frame
	 * (the measurer's normal path adds it exactly once).
	 */
	@Override
	public IntrinsicSizes getIntrinsicSizes() {
		final GridPlacementResolver.Plan plan = this.placementPlan();
		final BasicGridTrackSizing.Intrinsics line = BasicGridTrackSizing.intrinsics(
				this.sizingTracks(this.gridBox.getLineSize()), this.columnContributions(plan), this.columnGap);
		boolean columnInflated = false;
		final double[] rowMinPage = new double[Math.max(1, plan.rowCount())];
		for (int i = 0; i < this.items.size(); ++i) {
			final GridItemContent item = this.items.get(i);
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			// Approximate rowSpan by equal distribution to each row (a rough equivalent of deficit distribution;
			// GridRowSizing resolves actual heights precisely after bind).
			final double perRow = item.sizes.minPage() / area.rowSpan();
			for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
				rowMinPage[r] = Math.max(rowMinPage[r], perRow);
			}
			columnInflated |= item.sizes.columnInflated();
		}
		double minPage = plan.rowCount() > 1 ? this.rowGap * (plan.rowCount() - 1) : 0;
		for (int r = 0; r < rowMinPage.length; ++r) {
			// Use the specified heights of fixed-height explicit and implicit rows (2026-08-29).
			final double fixed = this.fixedRowHeight(r);
			minPage += net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(fixed) ? rowMinPage[r] : fixed;
		}
		return new IntrinsicSizes(line.min(), line.max(), minPage, columnInflated);
	}

	/**
	 * Validation phase for converting the parent to a range (Grid G3d3:
	 * consult-codex-2026-07-31-grid-g3.txt Q3, G3d3; no side effects). Validate and list
	 * all item bodies as ordinary nested builders. If an item body contains leases for
	 * sealed children (floats, etc.), this recursion proves containment in the parent range.
	 * An already bound Grid cannot be absorbed (structurally unreachable, but fail closed).
	 */
	boolean collectAbsorbableItems(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId,
			final long toId, final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final List<net.zamasoft.foliojet.layout.fragment.RangeHandle> outRanges,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		if (this.bound) {
			return false;
		}
		for (final GridItemContent item : this.items) {
			if (!item.collectAbsorbable(log, fromId, toId, outRanges, ownedAbsoluteAnchors)) {
				return false;
			}
		}
		return true;
	}

	/** Row-group sizes within a row subgrid, with frame-adjusted starts. */
	private static double rowAreaExtent(final GridPlacementResolver.GridArea area, final double[] rowHeights,
			final double[] rowStarts) {
		final int last = area.row() + area.rowSpan() - 1;
		return rowStarts[last] + rowHeights[last] - rowStarts[area.row()];
	}

	/**
	 * Passes this Grid's local row slice to a child finalizer. When called from within a
	 * row subgrid, also apply the current gap shim exactly once to that item's inner edges.
	 */
	private static void runRowFinalizers(final List<PendingRowFinalizer> finalizers, final double[] rowHeights,
			final double[] rowStarts, final double rowGap, final double gapShim) {
		for (final PendingRowFinalizer pending : finalizers) {
			final double[] heights = new double[pending.span()];
			final double[] starts = new double[pending.span()];
			final double baseShim = pending.rowStart() == 0 ? 0 : gapShim;
			final double base = rowStarts[pending.rowStart()] + baseShim;
			for (int i = 0; i < starts.length; ++i) {
				final int row = pending.rowStart() + i;
				final double startShim = row == 0 ? 0 : gapShim;
				final double endShim = row + 1 == rowHeights.length ? 0 : gapShim;
				heights[i] = Math.max(0, rowHeights[row] - startShim - endShim);
				starts[i] = i == 0 ? 0 : rowStarts[row] + startShim - base;
			}
			pending.finalizer().finalizeRows(heights, starts, rowGap);
		}
	}

	/**
	 * Passes descendant contributions of this row subgrid up one level. Add frame/gap
	 * exactly once at this boundary. For empty edge rows, duplicate the nearest occupied
	 * row's contribution with an expanded span.
	 */
	private void forwardRowSubgridContributions(final List<GridRowSizing.Contribution> local) {
		final RowSubgridLink link = this.rowSubgridLink;
		final int rowCount = link.span();
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = this.gridBox.getFrame();
		final double startFrame = frame.getFramePageStart(this.gridBox.getGridParams().flow);
		final double endFrame = frame.getFramePageEnd(this.gridBox.getGridParams().flow);
		if (local.isEmpty()) {
			// If completely empty, use only the frame. Do not create child gaps or hypothetical items.
			link.sink().contribute(0, rowCount, Math.max(0, startFrame + endFrame));
			return;
		}

		final List<GridRowSizing.Contribution> expanded = new ArrayList<>(local);
		int firstOccupied = rowCount, lastOccupied = -1;
		for (final GridRowSizing.Contribution contribution : local) {
			firstOccupied = Math.min(firstOccupied, contribution.row());
			lastOccupied = Math.max(lastOccupied, contribution.row() + contribution.span() - 1);
		}
		if (firstOccupied > 0) {
			for (final GridRowSizing.Contribution contribution : local) {
				if (contribution.row() <= firstOccupied
						&& contribution.row() + contribution.span() > firstOccupied) {
					expanded.add(new GridRowSizing.Contribution(0,
							contribution.row() + contribution.span(), contribution.extent()));
				}
			}
		}
		if (lastOccupied < rowCount - 1) {
			for (final GridRowSizing.Contribution contribution : local) {
				if (contribution.row() <= lastOccupied
						&& contribution.row() + contribution.span() > lastOccupied) {
					expanded.add(new GridRowSizing.Contribution(contribution.row(),
							rowCount - contribution.row(), contribution.extent()));
				}
			}
		}
		for (final GridRowSizing.Contribution contribution : expanded) {
			double extent = contribution.extent();
			if (contribution.row() == 0) {
				extent += startFrame;
			} else {
				extent += this.rowSubgridGapShim;
			}
			if (contribution.row() + contribution.span() == rowCount) {
				extent += endFrame;
			} else {
				extent += this.rowSubgridGapShim;
			}
			link.sink().contribute(contribution.row(), contribution.span(), Math.max(0, extent));
		}
	}

	/** After parent rows resolve, finalizes row subgrid direct items, grandchildren, then the row ledger. */
	private void finalizeRowSubgrid(final GridPlacementResolver.Plan plan, final FixedGridLayout layout,
			final double contentX, final double[] itemXOffsets, final BoxAlignment[] aligns,
			final double[] boundExtents, final boolean[] rowSubgridItems,
			final List<PendingRowFinalizer> childFinalizers, final double[] parentHeights,
			final double[] parentStarts, final double parentRowGap) {
		final GridParams params = this.gridBox.getGridParams();
		assert Double.doubleToLongBits(parentRowGap) == Double
				.doubleToLongBits(this.rowSubgridLink.parentRowGap()) : "row subgridの親gapが不一致";
		final int count = this.items.size();
		final double startFrame = this.gridBox.getFrame().getFramePageStart(params.flow);
		final double endFrame = this.gridBox.getFrame().getFramePageEnd(params.flow);
		final double[] rowHeights = parentHeights.clone();
		if (rowHeights.length == 1) {
			rowHeights[0] = Math.max(0, rowHeights[0] - startFrame - endFrame);
		} else {
			rowHeights[0] = Math.max(0, rowHeights[0] - startFrame);
			rowHeights[rowHeights.length - 1] = Math.max(0,
					rowHeights[rowHeights.length - 1] - endFrame);
		}
		final double[] rowStarts = new double[rowHeights.length];
		for (int r = 0; r < rowHeights.length; ++r) {
			rowStarts[r] = r == 0 ? 0 : parentStarts[r] - startFrame;
		}
		final int lastRow = rowHeights.length - 1;
		final double ownerOuter = parentStarts[lastRow] + parentHeights[lastRow];
		final double cursor = Math.max(0, ownerOuter - startFrame - endFrame);
		this.gridBox.setExactUsedPageSize(cursor);
		final double ownerFrame = this.rowSubgridOwner.getFrame().getFramePageExtent(params.flow);
		this.rowSubgridOwner.setExactUsedPageSize(Math.max(0, ownerOuter - ownerFrame));

		final boolean ledgerEligible = count > 0 && allSingleRowSpan(plan, count);
		final Integer[] order = new Integer[count];
		for (int i = 0; i < count; ++i) {
			order[i] = i;
		}
		boolean rowMajor = ledgerEligible && isRowMajor(plan, count);
		if (ledgerEligible && !rowMajor && !hasOverlap(plan, count)) {
			java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> plan.areas().get(i).row()));
			rowMajor = true;
		}
		final double[] yOffsets = new double[count];
		for (int idx = 0; idx < count; ++idx) {
			final int i = order[idx];
			final GridItemBox itemBox = this.items.get(i).itemBox;
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			final double logicalLine = contentX + layout.columnStart(area.column()) + itemXOffsets[i];
			itemBox.setGridLineOffset(LayoutUtils.inlineToPhysical(params, this.gridBox.getLineSize(), logicalLine,
					logicalLine + itemBox.getLineExtent(params.flow)));
			final double startShim = area.row() == 0 ? 0 : this.rowSubgridGapShim;
			final double endShim = area.row() + area.rowSpan() == rowHeights.length ? 0
					: this.rowSubgridGapShim;
			final double areaHeight = Math.max(0,
					rowAreaExtent(area, rowHeights, rowStarts) - startShim - endShim);
			if (!rowSubgridItems[i] && aligns[i] == BoxAlignment.STRETCH
					&& areaHeight > itemBox.getPageExtent(params.flow)
					&& itemBox.getBlockParams().size.getPageType(params.flow) == LengthType.AUTO) {
				final double deficit = areaHeight - itemBox.getPageExtent(params.flow);
				itemBox.setPageAxis(itemBox.getInnerPageExtent(params.flow) + deficit);
			}
			final double free = Math.max(0, areaHeight - itemBox.getPageExtent(params.flow));
			final double yOffset = startShim + (aligns[i] == BoxAlignment.CENTER ? free / 2
					: aligns[i] == BoxAlignment.END ? free : 0);
			yOffsets[i] = yOffset;
			this.gridBox.getContainer().addFlow(itemBox, rowStarts[area.row()] + yOffset);
		}

		// Register direct items at their final positions, then resolve exact grandchild sizes and geometry.
		runRowFinalizers(childFinalizers, rowHeights, rowStarts, this.rowGap, this.rowSubgridGapShim);
		for (int i = 0; i < count; ++i) {
			boundExtents[i] = this.items.get(i).itemBox.getPageExtent(params.flow);
		}
		if (rowMajor) {
			final double[] ledgerRowStarts = new double[rowStarts.length];
			final double[] ledgerRowHeights = new double[rowHeights.length];
			for (int r = 0; r < rowStarts.length; ++r) {
				final double startShim = r == 0 ? 0 : this.rowSubgridGapShim;
				final double endShim = r + 1 == rowStarts.length ? 0 : this.rowSubgridGapShim;
				ledgerRowStarts[r] = rowStarts[r] + startShim;
				ledgerRowHeights[r] = Math.max(0, rowHeights[r] - startShim - endShim);
			}
			final List<GridBox.Row> gridRows = new ArrayList<>();
			final List<GridItemBox> gridRowItems = new ArrayList<>(count);
			int rowStartFlow = 0;
			double itemsEnd = 0;
			int currentRow = plan.areas().get(order[0]).row();
			for (int idx = 0; idx < count; ++idx) {
				final int i = order[idx];
				final int r = plan.areas().get(i).row();
				if (r != currentRow) {
					gridRows.add(new GridBox.Row(rowStartFlow, idx - rowStartFlow,
							ledgerRowStarts[currentRow], ledgerRowHeights[currentRow], itemsEnd));
					rowStartFlow = idx;
					itemsEnd = 0;
					currentRow = r;
				}
				final double startShim = r == 0 ? 0 : this.rowSubgridGapShim;
				itemsEnd = Math.max(itemsEnd, yOffsets[i] - startShim
						+ this.items.get(i).itemBox.paintedPageExtent(params.flow));
				gridRowItems.add(this.items.get(i).itemBox);
			}
			gridRows.add(new GridBox.Row(rowStartFlow, count - rowStartFlow, ledgerRowStarts[currentRow],
					ledgerRowHeights[currentRow], itemsEnd));
			this.gridBox.setGridRows(gridRows, gridRowItems);
		}
		this.rowSubgridLink = null;
		this.rowSubgridOwner = null;
	}

	/** Bind the execution plan only once. */
	private boolean bound;

	/**
	 * Assembles Grid (G3a: resolve widths → bind → measure row heights → place).
	 * Add all items to the Grid container at track coordinates, and synchronize the Grid
	 * inner height and host flow cursor (independent item builders do not advance the parent
	 * cursor; without synchronization, subsequent blocks overlap — a correction in the
	 * G1 recommendation). Call while the host's active flow is this GridBox (at DocumentBuilder
	 * FLOW end for live processing; likewise between StartFlow(GridBox) and EndFlow for range replay).
	 */
	@Override
	public void bind(final Builder hostBuilder) {
		assert !this.bound : "Gridの二重bind";
		this.bound = true;
		// Enable the atomic contract (G6): a G0 fallback grid that does not run track placement
		// is not treated as atomic (see PageAtomicBox.isPageAtomicNow).
		this.gridBox.markTrackLayout();
		final BlockBuilder target = (BlockBuilder) hostBuilder;
		final GridParams params = this.gridBox.getGridParams();
		if (this.resolveSubgrid(target)) {
			// Discard the single-auto plan made during intrinsic measurement and place using parent lines
			// (2026-08-29; G4b plan sharing applies only within the same set of tracks).
			this.placementPlan = null;
		}
		if (this.placementPlan != null && this.planAvailable != Math.max(0, this.gridBox.getLineSize())
				&& hasAutoRepeat(params.templateColumns)) {
			// The auto-repeat count depends on container width (2026-08-29). Rebuild the plan made during
			// intrinsic measurement (unresolved width = one repetition) at bind after width resolution.
			// The spec also uses distinct counts: once for measurement at indefinite width, as many as fit
			// at definite width. The plan sharing rule (G4b) applies only within the same width.
			this.placementPlan = null;
		}
		final GridPlacementResolver.Plan plan = this.placementPlan();
		final double[] preResolvedRowHeights = this.preResolvedRowHeights(plan);
		// Track width resolution (G3b/c): use plan-based column contributions (G4b),
		// resolving fixed=specified length, auto=base/growth limit+stretch, fr=find-fr.
		// The reference width is the Grid container content-box line width
		// (after shrink-to-fit resolution on the TwoPass path).
		// G5c: justify-content used value. Positional values (start/center/end)
		// disable leftover stretch for auto columns and use that space as the track-group offset.
		final BoxAlignment justifyContent = BoxAlignment.resolve(BoxAlignment.AUTO, params.justifyContent);
		final double[] widths = BasicGridTrackSizing.resolve(this.sizingTracks(this.gridBox.getLineSize()),
				this.columnContributions(plan, Math.max(0, this.gridBox.getLineSize())),
				this.gridBox.getLineSize(), this.columnGap, justifyContent == BoxAlignment.STRETCH);
		final FixedGridLayout layout = new FixedGridLayout(widths, this.columnGap, this.rowGap);
		double trackLineExtent = this.columnGap * (this.tracks.size() - 1);
		for (final double w : widths) {
			trackLineExtent += w;
		}
		final double freeLine = Math.max(0, this.gridBox.getLineSize() - trackLineExtent);
		final double contentX = justifyContent == BoxAlignment.CENTER ? freeLine / 2
				: justifyContent == BoxAlignment.END ? freeLine : 0;
		// G5b: resolve each item’s justify used value, bind width, and line-axis offset
		// for all items before bind (fallback after binding some items is impossible —
		// recommendation Q3). stretch=area width (current); start/center/end=fit-content width
		// (min-content floor: max(min, min(area, max))) + free space × {0,0.5,1}.
		// Clamp negative free space to 0 (safe for print: overflow on the start side).
		final int count = this.items.size();
		final double[] itemWidths = new double[count];
		final double[] itemXOffsets = new double[count];
		final BoxAlignment[] aligns = new BoxAlignment[count];
		for (int i = 0; i < count; ++i) {
			final GridItemContent item = this.items.get(i);
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			double areaWidth = this.columnGap * (area.columnSpan() - 1);
			for (int c = area.column(); c < area.column() + area.columnSpan(); ++c) {
				areaWidth += widths[c];
			}
			final BoxAlignment justify = BoxAlignment.resolve(item.spec.justifySelf(), params.justifyItems);
			aligns[i] = BoxAlignment.resolve(item.spec.alignSelf(), params.alignItems);
			// A takeover item (G7, 2026-08-29) has **its own frame**. areaWidth is the margin-box
			// width, while setTrackWidth takes the inner size, so subtract the frame before
			// passing it. Resolve frame %/em to used sizes here too (using the grid area as the basis).
			double lineExtras = 0;
			if (item.takeover) {
				final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = item.itemBox.getFrame();
				net.zamasoft.foliojet.layout.util.LayoutUtils.computePaddings(frame.padding, frame.frame.padding,
						areaWidth);
				net.zamasoft.foliojet.layout.util.LayoutUtils.computeMarginsAutoToZero(frame.margin,
						frame.frame.margin, areaWidth);
				lineExtras = frame.getFrameLineExtent(params.flow);
			}
			final double innerArea = Math.max(0, areaWidth - lineExtras);
			// Explicit width takes precedence over stretch (css-grid §6.6). With wrappers,
			// the inner child applied it itself; it is now lost unless handled here.
			final double authoredLine = authoredLineSize(item.itemBox, params.flow, innerArea);
			if (!Double.isNaN(authoredLine)) {
				itemWidths[i] = authoredLine;
			} else if (justify == BoxAlignment.STRETCH) {
				itemWidths[i] = innerArea;
			} else {
				itemWidths[i] = Sizing.fitContent(item.sizes.minContent(), item.sizes.maxContent(), innerArea);
			}
			final double freeLineInArea = Math.max(0, areaWidth - (itemWidths[i] + lineExtras));
			itemXOffsets[i] = justify == BoxAlignment.CENTER ? freeLineInArea / 2
					: justify == BoxAlignment.END ? freeLineInArea : 0;
			assert itemWidths[i] >= 0 && !Double.isNaN(itemWidths[i]) : "不正なitem幅: " + itemWidths[i];
		}
		// Resolve width → bind body. The PageAtomicBox contract ensures all binds complete while
		// the Grid flow is active (no page breaks run).
		final double[] extents = new double[count];
		final boolean[] rowSubgridItems = new boolean[count];
		final List<GridRowSizing.Contribution> rowContributions = new ArrayList<>();
		final List<PendingRowFinalizer> rowFinalizers = new ArrayList<>();
		final RootBuilder pageContext = target.getPageContext();
		if (this.rowSubgridLink != null && pageContext != null) {
			pageContext.beginRowSubgridBind();
		}
		try {
			for (int i = 0; i < count; ++i) {
				final int itemIndex = i;
				final GridItemContent item = this.items.get(i);
				// Pass column tracks and a temporary link that converts row contributions to parent coordinates once.
				final GridPlacementResolver.GridArea area = plan.areas().get(i);
				final RowContributionSink sink = new RowContributionSink() {
					@Override
					public void contribute(final int row, final int span, final double extent) {
						if (row < 0 || span <= 0 || row + span > area.rowSpan()) {
							throw new IllegalArgumentException("row subgrid contribution: " + row + "/" + span);
						}
						rowContributions.add(new GridRowSizing.Contribution(area.row() + row, span, extent));
					}

					@Override
					public void whenRowsResolved(final RowGeometryFinalizer finalizer) {
						if (rowSubgridItems[itemIndex]) {
							throw new IllegalStateException("row subgrid finalizerの二重登録");
						}
						rowSubgridItems[itemIndex] = true;
						aligns[itemIndex] = BoxAlignment.STRETCH;
						rowFinalizers.add(new PendingRowFinalizer(area.row(), area.rowSpan(), finalizer));
					}
				};
				final RowSubgridLink link = new RowSubgridLink(area.row(), area.rowSpan(), this.rowGap,
						this.rowLines.subList(area.row(), area.row() + area.rowSpan() + 1), sink);
				item.itemBox.setSubgridTracks(new GridItemBox.SubgridTracks(
						java.util.Arrays.copyOfRange(widths, area.column(), area.column() + area.columnSpan()),
						this.columnGap,
						this.columnLines.subList(area.column(), area.column() + area.columnSpan() + 1),
						this.rowGap, this.rowLines.subList(area.row(), area.row() + area.rowSpan() + 1), link));
				item.bind(target, itemWidths[i]);
				GRID_ITEM_BINDS.incrementAndGet();
				extents[i] = item.itemBox.getPageExtent(params.flow);
				// Do not leave closures on persistent boxes for items that were not row subgrids either.
				item.itemBox.getSubgridTracks().consumeRowSubgridLink();
			}
		} finally {
			if (this.rowSubgridLink != null && pageContext != null) {
				pageContext.endRowSubgridBind();
			}
		}

		if (this.rowSubgridLink != null) {
			for (int i = 0; i < count; ++i) {
				if (!rowSubgridItems[i]) {
					final GridPlacementResolver.GridArea area = plan.areas().get(i);
					rowContributions.add(new GridRowSizing.Contribution(area.row(), area.rowSpan(), extents[i]));
				}
			}
			this.forwardRowSubgridContributions(rowContributions);
			final boolean[] finalized = new boolean[1];
			this.rowSubgridLink.sink().whenRowsResolved((rowHeights, rowStarts, inheritedGap) -> {
				if (finalized[0]) {
					throw new IllegalStateException("row subgrid finalizerの二重実行");
				}
				finalized[0] = true;
				this.finalizeRowSubgrid(plan, layout, contentX, itemXOffsets, aligns, extents,
						rowSubgridItems, rowFinalizers, rowHeights, rowStarts, inheritedGap);
			});
			final LayoutContext.Flow active = target.getFlow();
			assert active.box == this.gridBox : "Grid bindでactive flowがGridではない: " + active.box;
			return;
		}
		// Row height resolution (G4d: includes rowSpan deficit distribution via GridRowSizing).
		// Empty rows have height 0 but retain adjacent rowGap, as required by the spec for gutters.
		List<GridPlacementResolver.GridArea> sizingAreas = plan.areas();
		double[] sizingExtents = extents;
		int ordinaryCount = 0;
		for (final boolean rowSubgrid : rowSubgridItems) {
			if (!rowSubgrid) {
				++ordinaryCount;
			}
		}
		if (ordinaryCount != count) {
			final List<GridPlacementResolver.GridArea> ordinaryAreas = new ArrayList<>(ordinaryCount);
			final double[] ordinaryExtents = new double[ordinaryCount];
			int ordinary = 0;
			for (int i = 0; i < count; ++i) {
				if (!rowSubgridItems[i]) {
					ordinaryAreas.add(plan.areas().get(i));
					ordinaryExtents[ordinary++] = extents[i];
				}
			}
			sizingAreas = ordinaryAreas;
			sizingExtents = ordinaryExtents;
		}
		final double[] rowHeights = GridRowSizing.resolve(sizingAreas, sizingExtents, plan.rowCount(), this.rowGap,
				rowContributions);
		// Fixed-height rows (absolute lengths or % with a resolved basis in grid-template-rows/grid-auto-rows)
		// stay at that height (2026-08-29; taller content overflows the item,
		// as specified). auto/fr/min-content, etc. keep content height;
		// in an auto-height Grid, fr rows are equivalent to auto.
		final boolean[] fixedRow = new boolean[rowHeights.length];
		for (int r = 0; r < rowHeights.length; ++r) {
			final double fixed = preResolvedRowHeights[r];
			if (!net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(fixed)) {
				rowHeights[r] = fixed;
				fixedRow[r] = true;
			}
		}
		// G5e: align-content, free space in a Grid with explicit height. Distribute content first,
		// then apply item self alignment (using adjusted row heights).
		double trackPageExtent = plan.rowCount() > 1 ? this.rowGap * (plan.rowCount() - 1) : 0;
		for (final double h : rowHeights) {
			trackPageExtent += h;
		}
		double contentY = 0;
		if (!this.items.isEmpty()) {
			this.gridBox.setPageAxis(trackPageExtent);
			final double freePage = Math.max(0,
					this.gridBox.getInnerPageExtent(params.flow) - trackPageExtent);
			if (freePage > 0) {
				final BoxAlignment alignContent = BoxAlignment.resolve(BoxAlignment.AUTO, params.alignContent);
				int stretchable = 0;
				for (final boolean fixed : fixedRow) {
					if (!fixed) {
						++stretchable;
					}
				}
				if (alignContent == BoxAlignment.STRETCH && stretchable > 0) {
					// Distribute equally to auto rows (including empty rows; do not stretch fixed-height rows — 2026-08-29).
					final double share = freePage / stretchable;
					for (int r = 0; r < rowHeights.length; ++r) {
						if (!fixedRow[r]) {
							rowHeights[r] += share;
						}
					}
				} else if (alignContent != BoxAlignment.STRETCH) {
					contentY = alignContent == BoxAlignment.CENTER ? freePage / 2
							: alignContent == BoxAlignment.END ? freePage : 0;
				}
			}
		}
		final double[] rowStarts = new double[rowHeights.length];
		double cursor = contentY;
		for (int r = 0; r < rowHeights.length; ++r) {
			rowStarts[r] = cursor;
			cursor += rowHeights[r];
			if (r < rowHeights.length - 1) {
				cursor += this.rowGap;
			}
		}
		// After parent rows resolve, finalize child subgrids before placing direct items.
		runRowFinalizers(rowFinalizers, rowHeights, rowStarts, this.rowGap, 0);
		for (int i = 0; i < count; ++i) {
			extents[i] = this.items.get(i).itemBox.getPageExtent(params.flow);
		}
		// G5d: page-axis offset from the align used value (area = spanned rows + inner gaps).
		// stretch uses a top-aligned approximation compatible with current behavior;
		// true used-height stretch follows later. Clamp negative free space to 0.
		// Check row splitting eligibility (G6) and reorder flow registration to row-major order if needed.
		// The ledger (GridBox.Row) represents rows as contiguous ranges in the flow list,
		// so registration must be row-major. For explicit placement whose source order is not row-major
		// (gigazine.net .content: an item pinned to the first row by grid-row appears later
		// in the DOM), reorder to row-major **only if items do not overlap**.
		// Use a stable sort to preserve source order within rows. CSS specifies document order
		// for painting overlapping items, so grids with overlaps retain their order and
		// fall back to atomic as before (preserves the explicit-overlap regression).
		final boolean ledgerEligible = !this.items.isEmpty() && contentY == 0
				&& allSingleRowSpan(plan, count);
		Integer[] order = new Integer[count];
		for (int i = 0; i < count; ++i) {
			order[i] = i;
		}
		boolean rowMajor = ledgerEligible && isRowMajor(plan, count);
		if (ledgerEligible && !rowMajor && !hasOverlap(plan, count)) {
			java.util.Arrays.sort(order,
					java.util.Comparator.comparingInt(i -> plan.areas().get(i).row()));
			rowMajor = true;
		}
		final double[] itemPageEnds = new double[count];
		for (int idx = 0; idx < count; ++idx) {
			final int i = order[idx];
			final GridItemBox itemBox = this.items.get(i).itemBox;
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			final double logicalLine = contentX + layout.columnStart(area.column()) + itemXOffsets[i];
			itemBox.setGridLineOffset(LayoutUtils.inlineToPhysical(params, this.gridBox.getLineSize(), logicalLine,
					logicalLine + itemBox.getLineExtent(params.flow)));
			double areaHeight = this.rowGap * (area.rowSpan() - 1);
			for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
				areaHeight += rowHeights[r];
			}
			// Default stretch: stretch the item to the height of its row (the spanned range for spanning items)
			// (G7, user report A-7 on 2026-08-29). Takeover makes the item the authored box
			// itself, so its background/frame stretch here. This is symmetric with column-side
			// justify: stretch setting itemWidths=areaWidth.
			if (aligns[i] == BoxAlignment.STRETCH && areaHeight > extents[i]
					&& itemBox.getBlockParams().size.getPageType(params.flow) == LengthType.AUTO) {
				// Add the deficit to the **inner size** (codex finding). Using areaHeight directly
				// as content height would add padding/borders on top for framed items.
				final double deficit = areaHeight - itemBox.getPageExtent(params.flow);
				itemBox.setPageAxis(itemBox.getInnerPageExtent(params.flow) + deficit);
			}
			final double free = Math.max(0, areaHeight - extents[i]);
			final double yOffset = aligns[i] == BoxAlignment.CENTER ? free / 2
					: aligns[i] == BoxAlignment.END ? free : 0;
			this.gridBox.getContainer().addFlow(itemBox, rowStarts[area.row()] + yOffset);
			// For the row splitting (G6) slack check: the end **actually painted** by an item relative to row start
			// (2026-08-29, G7). Without a background/frame, use the content end; otherwise use
			// the whole stretched box. Splitting the latter as whitespace loses the continued background.
			itemPageEnds[i] = yOffset + itemBox.paintedPageExtent(params.flow);
		}
		// A grid without items keeps its explicit rows (2026-10-08, css-grid-1 §7.1: explicit tracks exist without
		// items; Chrome gives an empty grid-template-rows: 50px grid 50px). Implicit rows exist only for items, so
		// count the explicit ones only (column flow keeps one row for placement).
		double pageExtent = cursor;
		if (this.items.isEmpty()) {
			pageExtent = 0;
			for (int r = 0; r < Math.min(rowHeights.length, this.explicitRows); ++r) {
				pageExtent += rowHeights[r] + (r > 0 ? this.rowGap : 0);
			}
		}
		this.gridBox.setPageAxis(pageExtent);
		// **Record row boundaries for page breaks** (2026-08-10, G6 row splitting;
		// same structure as FlexBuilder.placeRow). Only applies when flow order (= source order) is
		// contiguous row-major, all items have rowSpan=1, and align-content has no leading space
		// (includes vertical writing — 2026-10-05. Previously excluded, which caused a vertical-writing
		// grid too large for a page to move intact to the next page). Without a ledger, GridBox.split
		// is not called; the existing PageAtomicBox atomic path (move intact/visual rescue)
		// applies, so failing this eligibility gate cannot make behavior worse than before.
		if (rowMajor) {
			final java.util.List<GridBox.Row> gridRows = new ArrayList<>();
			final java.util.List<GridItemBox> gridRowItems = new ArrayList<>(count);
			int rowStartFlow = 0;
			double itemsEnd = 0;
			int currentRow = plan.areas().get(order[0]).row();
			for (int idx = 0; idx < count; ++idx) {
				final int i = order[idx];
				final int r = plan.areas().get(i).row();
				if (r != currentRow) {
					gridRows.add(new GridBox.Row(rowStartFlow, idx - rowStartFlow, rowStarts[currentRow],
							rowHeights[currentRow], itemsEnd));
					rowStartFlow = idx;
					itemsEnd = 0;
					currentRow = r;
				}
				itemsEnd = Math.max(itemsEnd, itemPageEnds[i]);
				gridRowItems.add(this.items.get(i).itemBox);
			}
			gridRows.add(new GridBox.Row(rowStartFlow, count - rowStartFlow, rowStarts[currentRow],
					rowHeights[currentRow], itemsEnd));
			this.gridBox.setGridRows(gridRows, gridRowItems);
		}
		final LayoutContext.Flow active = target.getFlow();
		assert active.box == this.gridBox : "Grid bindでactive flowがGridではない: " + active.box;
		target.setPageAxis(active.pageAxis + this.gridBox.getInnerPageExtent(params.flow));
	}
}
