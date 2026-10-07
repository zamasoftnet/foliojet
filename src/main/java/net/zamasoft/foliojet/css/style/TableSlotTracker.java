package net.zamasoft.foliojet.css.style;

import java.util.Arrays;

/**
 * Tracks columns occupied by rowspans from earlier rows using HTML table placement rules (2026-09-29).
 *
 * <p>
 * The table builder treats each row's cell-sequence indexes as columns, carrying over
 * rowspans from above with {@code CellContent.complementRowspan} only at contiguous
 * positions after the end of the cell sequence. If a short row has an empty column
 * between its last cell and a continuing rowspan, the farther rowspan is not carried
 * to this or later rows. Cells in subsequent rows were then placed left of their grid
 * columns, overlapping spanning cells (a byproduct of 36M sweep classification,
 * copperpdf4 triage §13).
 * </p>
 *
 * <p>
 * Filling empty columns with <b>empty anonymous cells</b> makes the builder's existing
 * carryover match the grid. Anonymous cells bypass the cascade (inherited and initial
 * values only), so have no border, margin, or background and draw nothing, like empty
 * columns. This class only detects empty columns; it does not affect box dimensions or
 * placement. Like the builder, it does not carry rowspans across row-group boundaries.
 * </p>
 *
 * <p>
 * <b>Limitation</b>: for fixed-layout tables, the Incremental builder discards cells beyond
 * the column count set by the first row (an existing limitation that also loses their content).
 * This class does not know about that truncation, so it may add anonymous cells before
 * rowspans of discarded cells. The only effect is one extra invisible empty cell in that
 * row (an empty row gains {@code border-spacing} height; codex review, 2026-09-29).
 * </p>
 */
final class TableSlotTracker {
	/**
	 * Table element style. Aligns anonymous cell writing direction with the table
	 * (inheriting the row's could make cells orthogonal).
	 */
	final net.zamasoft.foliojet.css.CSSStyle table;

	TableSlotTracker(final net.zamasoft.foliojet.css.CSSStyle table) {
		this.table = table;
	}

	/** For each column, number of rows after this one still occupied by a cell above. */
	private int[] carry = new int[8];
	/** Columns occupied in this row (continuations from above and this row's cells). */
	private boolean[] occupied = new boolean[8];
	/** Maximum column continuing from above at this row's start, plus one. */
	private int carriedEnd = 0;
	/** Column at which to start searching for this row's next cell. */
	private int cursor = 0;
	private boolean inRow = false;

	/** Start of a row group. Rowspans do not cross groups. */
	void beginRowGroup() {
		Arrays.fill(this.carry, 0);
	}

	void beginRow() {
		this.inRow = true;
		this.cursor = 0;
		this.carriedEnd = 0;
		for (int c = 0; c < this.carry.length; ++c) {
			this.occupied[c] = this.carry[c] > 0;
			if (this.occupied[c]) {
				--this.carry[c];
				this.carriedEnd = c + 1;
			}
		}
	}

	/** Places this row's cell in the first unoccupied column. */
	void placeCell(final int colspan, final int rowspan) {
		if (!this.inRow) {
			return;
		}
		while (this.cursor < this.occupied.length && this.occupied[this.cursor]) {
			++this.cursor;
		}
		this.ensureCapacity(this.cursor + colspan);
		for (int k = 0; k < colspan; ++k) {
			this.occupied[this.cursor + k] = true;
			this.carry[this.cursor + k] = Math.max(this.carry[this.cursor + k], rowspan - 1);
		}
		this.cursor += colspan;
	}

	/**
	 * Whether an empty column exists after this row's cells and before a continuing rowspan.
	 * If so, the next anonymous cell placed with {@link #placeCell}(1, 1) occupies that column.
	 */
	boolean hasGapBeforeCarried() {
		if (!this.inRow) {
			return false;
		}
		int c = this.cursor;
		while (c < this.carriedEnd && this.occupied[c]) {
			++c;
		}
		return c < this.carriedEnd;
	}

	void endRow() {
		this.inRow = false;
	}

	private void ensureCapacity(final int size) {
		if (size > this.carry.length) {
			final int length = Math.max(size, this.carry.length * 2);
			this.carry = Arrays.copyOf(this.carry, length);
			this.occupied = Arrays.copyOf(this.occupied, length);
		}
	}
}
