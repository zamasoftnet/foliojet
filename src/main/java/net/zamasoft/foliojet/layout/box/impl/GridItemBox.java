package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * A synthetic wrapper for a grid item (Grid G1b, 2026-07-31 --
 * consult-codex-2026-07-31-grid-g1.txt §2).
 *
 * <p>
 * Width is fixed to the track width (determined before construction). The inherited
 * {@code offsetX} ({@link #setGridLineOffset}) supplies the line-axis track position,
 * affecting all three drawing paths: background/border, normal content, and text clipping.
 * As a synthetic box, it is not exposed to the source protocol (not recorded or replayed;
 * replay deterministically synthesizes it again from the same child events).
 * </p>
 */
public class GridItemBox extends FlowBlockBox {

	/**
	 * Parent grid tracks spanned by this item (for subgrid, css-grid-2, 2026-08-29).
	 * The parent's {@code GridBuilder.bind} sets these just before binding the item body.
	 * A grid with {@code grid-template-columns: subgrid} directly under the item inherits them
	 * as its own tracks.
	 *
	 * @param columnWidths    resolved widths of spanned columns (source order, span entries)
	 * @param columnGap       parent's column gap
	 * @param columnLineNames names of spanned column lines (span+1 entries; parent's explicit names
	 *                        + implicit names from areas)
	 * @param rowGap          parent's row gap
	 * @param rowLineNames    names of spanned row lines (span+1 entries)
	 * @param link            temporary row-subgrid connection; null when unused
	 */
	public static final class SubgridTracks {
		private final double[] columnWidths;
		private final double columnGap;
		private final java.util.List<java.util.List<String>> columnLineNames;
		private final double rowGap;
		private final java.util.List<java.util.List<String>> rowLineNames;
		private RowSubgridLink link;

		/** Compatibility constructor for previous callers that passed only the column axis. */
		public SubgridTracks(final double[] columnWidths, final double columnGap,
				final java.util.List<java.util.List<String>> columnLineNames, final double rowGap) {
			this(columnWidths, columnGap, columnLineNames, rowGap, java.util.List.of(), null);
		}

		public SubgridTracks(final double[] columnWidths, final double columnGap,
				final java.util.List<java.util.List<String>> columnLineNames, final double rowGap,
				final java.util.List<java.util.List<String>> rowLineNames,
				final RowSubgridLink link) {
			this.columnWidths = columnWidths;
			this.columnGap = columnGap;
			this.columnLineNames = columnLineNames;
			this.rowGap = rowGap;
			this.rowLineNames = rowLineNames;
			this.link = link;
		}

		public double[] columnWidths() {
			return this.columnWidths;
		}

		public double columnGap() {
			return this.columnGap;
		}

		public java.util.List<java.util.List<String>> columnLineNames() {
			return this.columnLineNames;
		}

		public double rowGap() {
			return this.rowGap;
		}

		public java.util.List<java.util.List<String>> rowLineNames() {
			return this.rowLineNames;
		}

		/** An unconsumed temporary connection. null after consumption. */
		public synchronized RowSubgridLink link() {
			return this.link;
		}

		/**
		 * Retrieves the temporary connection only once, detaching the sink closure from the persistent box.
		 * Returns null on subsequent calls.
		 */
		public synchronized RowSubgridLink consumeRowSubgridLink() {
			final RowSubgridLink consumed = this.link;
			this.link = null;
			return consumed;
		}
	}

	/**
	 * What a column subgrid directly under this item contributes to the parent's column sizing (css-grid-2 §9,
	 * 2026-10-09): an immutable copy taken when the subgrid's recording ends, so the parent sizes its tracks from
	 * the subgrid's items instead of the subgrid as one spanning item (whose contribution reached only fr tracks
	 * and left auto tracks at zero, piling the cells up). No live builder is kept: builders are rebuilt from the
	 * source on replay.
	 *
	 * @param cells          the subgrid's items in source order
	 * @param lineNames      the subgrid's own line names ({@code subgrid [a] [b]}), added to the parent's
	 * @param startInset     the subgrid's own margin, border and padding at the line start
	 * @param endInset       the same at the line end
	 * @param autoFlowColumn {@code grid-auto-flow: column}
	 * @param dense          {@code grid-auto-flow: dense}
	 * @param explicitRows   the subgrid's explicit row count (0 when its rows are a subgrid too)
	 */
	public record SubgridSource(java.util.List<SubgridCell> cells, java.util.List<java.util.List<String>> lineNames,
			double startInset, double endInset, boolean autoFlowColumn, boolean dense, int explicitRows) {
		public SubgridSource {
			cells = java.util.List.copyOf(cells);
			lineNames = lineNames.stream().map(java.util.List::copyOf).toList();
		}
	}

	/**
	 * One item of a {@link SubgridSource}: its placement (line names unresolved) and its column contribution, already
	 * settled for takeover, explicit width and the minimum cap; or, for an item hosting a column subgrid itself,
	 * that subgrid's source, whose cells take its place.
	 */
	public record SubgridCell(net.zamasoft.foliojet.layout.box.params.GridItemSpec spec, double min, double max,
			SubgridSource nested) {
	}

	private SubgridTracks subgridTracks;

	private SubgridSource subgridSource;

	/** Registers the column contributions of the subgrid directly under this item (its {@code GridBuilder.finish}). */
	public void setSubgridSource(final SubgridSource source) {
		this.subgridSource = source;
	}

	/** The column contributions of the subgrid directly under this item; null when there is none. */
	public SubgridSource getSubgridSource() {
		return this.subgridSource;
	}

	public GridItemBox(final BlockParams params, final FlowPos pos, final double trackWidth) {
		super(params, pos);
		if (params.flow.isVertical()) {
			this.height = trackWidth;
		} else {
			this.width = trackWidth;
		}
		this.markSpecifiedPageAxisFromSize();
	}

	/**
	 * {@code GridBuilder} injects grid item dimensions, so they bypass the {@code calculateSize}
	 * branch that sets {@code specifiedPageAxis} (G7, 2026-08-29; same reason as the corresponding
	 * method in {@code FlexItemBox}). Without the flag, cross-page remainder calculation
	 * ({@code FragmentState.of}) mistakenly sees "no specified size" and the continuation resolves
	 * the full specified height again (page 2 of row-split-carry became 91 pt instead of 56 pt).
	 * <b>Set it in the continuation-fragment constructor too</b>, since splitting again over
	 * three or more pages would otherwise reproduce the problem.
	 */
	private void markSpecifiedPageAxisFromSize() {
		this.specifiedPageAxis = this.size
				.getPageType(this.getBlockParams().flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
	}

	/** Sets the spanned parent tracks (parent's {@code GridBuilder.bind}, 2026-08-29). */
	public void setSubgridTracks(final SubgridTracks tracks) {
		this.subgridTracks = tracks;
	}

	/** The spanned parent tracks (null before parent track placement or for a parent in G0 degradation). */
	public SubgridTracks getSubgridTracks() {
		return this.subgridTracks;
	}

	/**
	 * Sets the track size finalized by a row subgrid exactly, without constraints from
	 * authored height/min/max-height or aspect-ratio (2026-09-03).
	 */
	public final void setExactUsedPageSize(final double pageSize) {
		this.restoreContentExtent(Math.max(0, pageSize));
		this.minPageAxis = 0;
		this.maxPageAxis = Double.MAX_VALUE;
		this.specifiedPageAxis = false;
	}

	/**
	 * Sets the line-axis track start position (origin at the grid container's inner edge).
	 *
	 * <p>
	 * Also save the same value in {@code baseOffsetX} (2026-08-06).
	 * This is the basis on which {@code AbstractContainerBox.resolveRelativeOffset} adds
	 * the {@code position:relative} offset. Without it, that method overwrites {@code offsetX}
	 * by assignment and loses grid placement (same reason as FlexItemBox.setFlexLineOffset).
	 * </p>
	 */
	public void setGridLineOffset(final double lineOffset) {
		if (this.getBlockParams().flow.isVertical()) {
			this.baseOffsetY = lineOffset;
			this.offsetY = lineOffset;
		} else {
			this.baseOffsetX = lineOffset;
			this.offsetX = lineOffset;
		}
	}

	/**
	 * Reads the line-axis position set by {@link #setGridLineOffset}
	 * (2026-08-10, for grid row splitting).
	 *
	 * <p>
	 * {@code fragmentRecipe} creates a new remainder {@link GridItemBox} for a forced split
	 * across a row, so it does not inherit the line-axis position. After splitting, the caller
	 * must read the original value here and apply {@link #setGridLineOffset} again to the remainder
	 * (for the same reason as {@code FlexItemBox.getFlexLineOffset}).
	 * </p>
	 */
	public double getGridLineOffset() {
		return this.getBlockParams().flow.isVertical() ? this.baseOffsetY : this.baseOffsetX;
	}

	/**
	 * Sets the finalized track width (Grid G3a: called just before bind.
	 * For fixed columns, the same value as at construction; auto/fr columns receive
	 * the value resolved in G3b/c).
	 */
	public void setTrackWidth(final double trackWidth) {
		if (this.getBlockParams().flow.isVertical()) {
			this.height = trackWidth;
		} else {
			this.width = trackWidth;
		}
	}

	/**
	 * Whether this is a takeover item (the authored box itself became the item box)
	 * (G7, 2026-08-29). Needed for subgrid's "direct child of an item" check: with takeover,
	 * this box is the authored element itself, so a grid inside it is <b>not directly under
	 * the item</b> (when wrappers were used, flow depth alone could distinguish this).
	 */
	private boolean takeover;

	public void setTakeover(final boolean takeover) {
		this.takeover = takeover;
	}

	public boolean isTakeover() {
		return this.takeover;
	}

	protected GridItemBox(final BlockParams params, final FlowPos pos,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame,
			final net.zamasoft.foliojet.layout.box.content.Container container) {
		super(params, pos, size, minSize, frame, container);
		this.markSpecifiedPageAxisFromSize();
	}

	/**
	 * <b>Creates continuation fragments with the same type</b> (2026-08-05).
	 *
	 * <p>
	 * {@link FlowBlockBox#fragmentRecipe()} directly uses {@code new FlowBlockBox(...)},
	 * so <b>without an override, continuation fragments become plain blocks</b>.
	 * {@code ContinuationValidator} detects the type mismatch and <b>stops the entire conversion</b>.
	 * This caused {@code ecma262} in real-world corpus wave 23 (the ECMAScript specification,
	 * a 7.5 MB single page) to fail after producing 2.9 MB of output.
	 * Only {@code MulticolumnBlockBox} had an override.
	 * </p>
	 */
	@Override
	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		// Carry the **used size after track resolution**, not the specified size, along the line axis
		// to continuation fragments (2026-08-10, G6 row splitting). Width is auto (the track width
		// is injected by setTrackWidth), so carrying it unchanged makes continuation restyle
		// reconstruction (startFlowBlock.calculateSize) resolve it again to the containing width
		// = the full grid width, turning the fragment background into a full-grid-width strip
		// (observed on page2 of row-split-carry; the same mechanism as the 2026-08-08 fix
		// in FlexItemBox.fragmentRecipe). Capture by value because recipes must not retain this.
		final boolean vertical = params.flow.isVertical();
		final double usedTrack = (vertical ? this.height : this.width)
				+ (params.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? this.frame.getBorderLineExtent(params.flow)
						: 0);
		return (state, container) -> {
			final net.zamasoft.foliojet.layout.box.params.Dimension ns = state.nextSize();
			final net.zamasoft.foliojet.layout.box.params.Dimension sized = vertical
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(ns.getWidth(), ns.getWidthRatio(),
							usedTrack, 0, ns.getWidthType(),
							net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: net.zamasoft.foliojet.layout.box.params.Dimension.create(usedTrack, 0, ns.getHeight(),
							ns.getHeightRatio(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE,
							ns.getHeightType());
			return new GridItemBox(params, pos, sized, state.nextMinSize(), state.nextFrame(), container);
		};
	}
}
