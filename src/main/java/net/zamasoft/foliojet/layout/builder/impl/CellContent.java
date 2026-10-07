package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/**
 * Cell content under construction (P2-2: the first part of the shared model for table builder
 * unification in §5.2b. Unifies the identically named inner classes of the OnePass/TwoPass
 * builders; they differed only in whether the constructor took colspan or read it from pos).
 *
 * <p>
 * The content is in one of the following four states (E-6 increment 5a added the third state
 * on 2026-07-24 — design consultation
 * §4.2/§4.3).
 * </p>
 * <ol>
 * <li>Measurement builder ({@link TwoPassBlockBuilder}, unresolved content)</li>
 * <li>Span continuation (second and subsequent rowspan rows = extended, resolved cell box)</li>
 * <li>Sealed body ({@link TwoPassBlockBuilder.DeferredBind}: IntrinsicSizes values +
 * SourceRange (+lease). The measurer is released when a Retained table cell closes)</li>
 * <li>Intrinsic sizes after MAIN ({@link IntrinsicSizes}; body ownership has ended)</li>
 * </ol>
 *
 * <p>Export the finalized body when the cell closes. Range ownership ends on MAIN bind,
 * absorption into the parent, or disposal at document end. Seal empty cells as Empty without a lease.
 * RangeOnlyInvariantTest checks that CELL_RANGE_SEALS balances the termination counters.</p>
 */
class CellContent {
	/** The root box is the same in every state. Do not allocate a root-box/body wrapper for each state. */
	private final TableCellBox cellBox;
	private Object cell;

	public final int rowspan, colspan;

	/**
	 * From a measurement builder (read colspan from pos).
	 */
	public CellContent(TwoPassBlockBuilder cellBuilder) {
		this(cellBuilder, ((TableCellBox) cellBuilder.getRootBox()).getTableCellPos().colspan);
	}

	/**
	 * From a measurement builder (explicit colspan, for truncating columns in fixed layout).
	 */
	public CellContent(TwoPassBlockBuilder cellBuilder, int colspan) {
		this.cellBox = (TableCellBox) cellBuilder.getRootBox();
		this.cell = cellBuilder;
		this.rowspan = this.cellBox.getTableCellPos().rowspan;
		this.colspan = colspan;
	}

	/**
	 * From a span continuation (resolved cell box).
	 */
	public CellContent(TableCellBox cell, int rowspan, int colspan) {
		assert rowspan >= 1;
		assert colspan >= 1;
		this.cellBox = cell;
		this.cell = cell;
		this.rowspan = rowspan;
		this.colspan = colspan;
	}

	/**
	 * Fills in cells joined by rowspan (P2-2 shared core. Unifies the identical algorithms
	 * in both builders; only the source of the lists differed).
	 * Consult the previous row's cells and add a continuation CellContent to the current
	 * row for each cell whose rowspan continues.
	 *
	 * @param cells      current row's cells (appended to)
	 * @param upperCells previous row's cells
	 */
	static void complementRowspan(final java.util.List<CellContent> cells, final java.util.List<CellContent> upperCells) {
		while (upperCells.size() > cells.size()) {
			final CellContent upperCell = upperCells.get(cells.size());
			if (upperCell.rowspan > 1) {
				for (int colspan = upperCell.colspan; colspan >= 1; --colspan) {
					cells.add(new CellContent(upperCell.getCellBox(), upperCell.rowspan - 1, colspan));
				}
			} else {
				break;
			}
		}
	}

	/**
	 * Computes the row baseline (maximum first ascent) (P2-4 shared core).
	 * Unifies three identical loops; span continuations count in their owning row.
	 */
	static double maxFirstAscent(final java.util.List<CellContent> cells) {
		double rowAscent = 0;
		for (int i = 0; i < cells.size(); ++i) {
			final CellContent cell = cells.get(i);
			if (cell.isExtended()) {
				continue;
			}
			final double firstAscent = cell.getCellBox().getFirstAscent();
			if (!net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(firstAscent) && firstAscent > rowAscent) {
				rowAscent = firstAscent;
			}
		}
		return rowAscent;
	}

	/**
	 * Applies row heights to cells (P2-5 (c) shared core; unifies three identical operations).
	 * For each cell that is not a continuation, set its page-axis size to the sum of the row
	 * heights in its span, then apply vertical alignment.
	 *
	 * @param cells     row's cells
	 * @param rowSizes  row heights (unit or row-group window)
	 * @param rowIndex  current row's position in the window
	 * @param rowAscent row baseline (NaN means already applied; skip it)
	 * @param vertical  true for vertical writing
	 */
	static void applyCellExtents(final java.util.List<CellContent> cells, final double[] rowSizes, final int rowIndex,
			final double rowAscent, final boolean vertical) {
		for (int k = 0; k < cells.size(); ++k) {
			final CellContent cell = cells.get(k);
			if (cell.isExtended()) {
				continue;
			}
			final TableCellBox cellBox = cell.getCellBox();
			double size = 0;
			final int rowspan = Math.min(rowSizes.length - rowIndex, cellBox.getTableCellPos().rowspan);
			for (int l = 0; l < rowspan; ++l) {
				size += rowSizes[rowIndex + l];
			}
			if (!Double.isNaN(rowAscent)) {
				cellBox.baseline(rowAscent);
			}
			if (vertical) {
				cellBox.setWidth(size);
			} else {
				cellBox.setHeight(size);
			}
			cellBox.verticalAlign();
		}
	}

	public boolean isExtended() {
		return this.cell instanceof TableCellBox;
	}

	/**
	 * Returns the measurement builder. Since E-6 increment 5a, Retained table cells can
	 * seal ({@link #sealForRangeBind()}) and release the builder on close, so the Retained
	 * path must use {@link #getIntrinsicSizes()}/
	 * {@link #bind(BlockBuilder)} instead (current callers are only Incremental tables
	 * during column measurement).
	 */
	public TwoPassBlockBuilder getBuilder() {
		return (TwoPassBlockBuilder) this.cell;
	}

	/** Exports the finalized body on cell close and releases the measurement builder. */
	void sealForRangeBind() {
		this.sealForRangeBind(false);
	}

	void sealForRangeBind(final boolean sliceText) {
		if (!(this.cell instanceof TwoPassBlockBuilder builder)) {
			return;
		}
		builder.sealCellBodyForRangeBind(sliceText);
		final TwoPassBlockBuilder.DeferredBind body = builder.detachDeferredBind();
		this.cell = body;
		if (body.handle() != null) body.handle().markCell();
	}

	/**
	 * Intrinsic sizes read by column width calculation and orthogonal cell sizing
	 * (copies of existing IntrinsicMeasurer values). The measurer is immutable after close,
	 * so the values at seal time equal those previously read at bind time.
	 */
	public net.zamasoft.foliojet.layout.sizing.IntrinsicSizes getIntrinsicSizes() {
		if (this.cell instanceof IntrinsicSizes sizes) return sizes;
		if (this.cell instanceof TwoPassBlockBuilder.DeferredBind body) {
			return body.sizes();
		}
		return this.getBuilder().getIntrinsicSizes();
	}

	/** Lays out the finalized body. Laying it out while unresolved violates the contract. */
	public void bind(final BlockBuilder cellBindBuilder) {
		if (this.cell instanceof IntrinsicSizes) throw new IllegalStateException("消費済みセル本文のbind");
		if (!(this.cell instanceof TwoPassBlockBuilder.DeferredBind body)) {
			throw this.getBuilder().invariant("未sealセルのbind");
		}
		body.bind(cellBindBuilder);
		if (net.zamasoft.foliojet.layout.fragment.ReplayIntent.current()
				== net.zamasoft.foliojet.layout.fragment.ReplayIntent.MAIN) {
			this.cell = body.sizes();
		}
	}

	/**
	 * Returns the DeferredBind of the sealed body (E-6 increment 5b-1, for the table Pass B
	 * measurement primitive {@link CellPassBMeasurer}). Returns null for unsealed
	 * (still measuring) and extended cells (outside Pass B).
	 */
	TwoPassBlockBuilder.DeferredBind rangeBody() {
		return this.cell instanceof TwoPassBlockBuilder.DeferredBind body && body.handle() != null ? body : null;
	}

	/**
	 * Absorbs the cell into the parent's range representation (table absorption = commit phase
	 * of codex increment 5, 2026-07-30). Process only sealed cells: release the DeferredBind
	 * lease, account for the cell's SUBSUMED termination, and reduce retained state to the
	 * actual cell reference (equivalent to extended; the bind path skips it via {@code isExtended}).
	 * For unsealed (still measuring) cell builders, the validation phase
	 * ({@code TwoPassBlockBuilder.collectAbsorbableSelf}) lists them directly for absorption,
	 * and the commit phase calls {@code subsumeIntoParentRange}, so this is a no-op here.
	 * Extended cells are also a no-op because the actual cell handles them.
	 */
	void abandonForParentRange() {
		if (this.cell instanceof TwoPassBlockBuilder.DeferredBind body) {
			body.abandonForParentRange();
			this.cell = this.cellBox;
		}
	}

	/**
	 * Read access for the table absorption validation phase (no side effects) to check range
	 * containment of sealed cells (codex increment 5). Returns null unless the body is sealed.
	 */
	TwoPassBlockBuilder.DeferredBind sealedBodyOrNull() {
		return this.cell instanceof TwoPassBlockBuilder.DeferredBind body ? body : null;
	}

	/**
	 * Returns the unsealed (still measuring) cell builder (for table absorption validation).
	 * Returns null for sealed and extended cells.
	 */
	TwoPassBlockBuilder unsealedBuilderOrNull() {
		return this.cell instanceof TwoPassBlockBuilder builder ? builder : null;
	}

	/** Only range bodies and empty bodies support scratch measurement. */
	boolean isPassBMeasurable() {
		return this.cell instanceof TwoPassBlockBuilder.DeferredBind body
				&& (body.handle() != null || body.isEmpty())
				&& this.getCellBox().canMeasureReplica();
	}

	public TableCellBox getCellBox() {
		return this.cellBox;
	}
}
