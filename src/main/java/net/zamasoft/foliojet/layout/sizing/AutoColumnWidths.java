package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Resolves column widths for automatic layout (table-layout: auto). SPEC CSS 2.1 17.5.2.2.
 * Pairs with FixedColumnWidths. Accumulates colgroup specifications and cell measurements
 * (minimum/preferred/specified widths) using a column-type hierarchy (preferred &lt; absolute &lt; percent).
 * Distributes specified, minimum, and percentage widths of column spans (colspan) in three passes
 * to determine column arrays and the table's minimum and maximum line-axis sizes.
 * A pure accumulator that does not touch boxes; finish() transfers ownership of the result arrays
 * to the caller (P2-4: §5.2b table builder consolidation).
 *
 * @author MIYABE Tatsuhiko
 */
public final class AutoColumnWidths {
	/**
	 * Tolerance ratio for compressing excess min-content width to fit the type area
	 * (see resolve: if the sum of column minima is within this multiple of the available width,
	 * shrink proportionally to fit as before; otherwise, preserve column minima and let the whole table overflow).
	 */
	static final double MIN_OVERFLOW_TOLERANCE = 1.1;

	/** Column type: preferred width (derived from content). */
	public static final byte COLUMN_TYPE_DES = 0;
	/** Column type: absolute specification. */
	public static final byte COLUMN_TYPE_FIX = 1;
	/** Column type: percentage. */
	public static final byte COLUMN_TYPE_PCT = 2;

	/**
	 * Resolved result. Transfers array ownership to the caller
	 * (converted and consumed relative to the reference size during layout).
	 *
	 * @param mins        Minimum column widths
	 * @param specs       Specified column widths (absolute values or ratios, depending on type)
	 * @param desired     Preferred column widths
	 * @param types       Column types (COLUMN_TYPE_*)
	 * @param minLineSize Minimum table line-axis size (including frame)
	 * @param maxLineSize Maximum table line-axis size (including frame)
	 */
	public record Result(double[] mins, double[] specs, double[] desired, byte[] types, double minLineSize,
			double maxLineSize) {
		/**
		 * Determines the table's used line-axis size and column widths (CSS 2.1 17.5.2.2
		 * [Column widths influence the final table width as follows]).
		 * Without a specified size, tries to expand for percentage columns based on the maximum line-axis size.
		 * Clamps to the minimum/maximum table width, converts percentage specifications to absolute values
		 * (mutating specs), and distributes column widths with ColumnDistribution.
		 *
		 * @param specifiedLineSize Specified table size (LayoutUtils.NONE if absent)
		 * @param maxTableSize      Maximum available table size
		 * @param tableFrame        Table frame
		 * @param lineBorderSpacing Border spacing along the line axis
		 * @param separateBorders   True for separate borders
		 * @return Table size and column widths
		 */
		public Sized resolve(final double specifiedLineSize, final double maxTableSize, final double tableFrame,
				final double lineBorderSpacing, final boolean separateBorders) {
			final int columnCount = this.mins.length;
			double tableSize = specifiedLineSize;
			double[] columnSizes = new double[columnCount];
			if (columnCount == 0) {
				return new Sized(LayoutUtils.isNone(tableSize) ? 0 : tableSize, columnSizes);
			}
			if (LayoutUtils.isNone(tableSize)) {
				tableSize = this.maxLineSize;
				if (tableSize < maxTableSize && columnCount > 1) {
					// Expand the table for percentage widths.
					int pctCount = 0, effColumnCount = 0;
					double pctSum = 0, noPctDesiredSum = 0;
					double w = tableSize - tableFrame;
					for (int i = 0; i < columnCount; ++i) {
						double des = this.desired[i];
						if (this.types[i] != COLUMN_TYPE_PCT && des == 0) {
							continue;
						}
						++effColumnCount;
						if (this.types[i] != COLUMN_TYPE_PCT) {
							noPctDesiredSum += des;
							continue;
						}
						++pctCount;
						double pct = this.specs[i];
						pctSum += pct;
						if (pct != 1 && pct != 0) {
							w = Math.max(w, des / pct);
						} else {
							w = maxTableSize - tableFrame;
						}
						if (w >= maxTableSize - tableFrame) {
							break;
						}
					}
					if (pctCount != 0 && pctCount != effColumnCount) {
						if (pctSum != 1 && pctSum != 0) {
							w = Math.max(w, noPctDesiredSum / (1 - pctSum));
						} else if (noPctDesiredSum > 0) {
							w = maxTableSize - tableFrame;
						}
					}
					tableSize = w + tableFrame;
				}
			}
			// Note that minLineSize includes tableFrame.
			// Check the min-content width guarantee after truncating to the available width (2026-08-20).
			// Column-width consistency across fragments was verified: resolve runs once per table, and split
			// fragments share the resolved column widths, so the reason for the 2026-08-18 withdrawal
			// does not apply to the current structure. When min-content width exceeds the available width,
			// compressing columns makes content overlap adjacent columns and breaks layout. Preserve column
			// minima and let the whole table overflow along the line axis (CSS 2.2 17.5.2.2 / Chrome behavior).
			// However, compress small excesses within the tolerance ratio to fit the type area as before.
			// Cell padding absorbs the compression with essentially no overlap (measured on w3c-jlreq tables
			// with ratios of 1.02–1.09), giving better print quality than clipping a few pt of text at the
			// paper edge as Chrome does. This intentionally differs from Chrome as a print-quality decision.
			// Per the minimum guarantee for automatic table widths (2026-08-20).
			if (tableSize > maxTableSize) {
				tableSize = maxTableSize;
			}
			if (tableSize < this.minLineSize && (this.minLineSize <= maxTableSize
					|| this.minLineSize > maxTableSize * MIN_OVERFLOW_TOLERANCE)) {
				tableSize = this.minLineSize;
			}
			final double innerSize = tableSize - tableFrame;
			// Calculate percentage widths.
			final double refSize = separateBorders ? innerSize - columnCount * lineBorderSpacing : innerSize;
			for (int i = 0; i < columnCount; ++i) {
				if (this.types[i] != COLUMN_TYPE_PCT) {
					continue;
				}
				this.specs[i] *= refSize;
				if (separateBorders) {
					// Separate borders.
					this.specs[i] += lineBorderSpacing;
				}
			}

			// Distribute column widths (css-tables-3).
			double[] startSizes = this.mins;
			double minSum = 0;
			for (int i = 0; i < columnCount; ++i) {
				minSum += this.mins[i];
			}
			if (minSum > innerSize) {
				// Shrink proportionally if the sum of minimum widths exceeds the inner size (a safeguard
				// normally unreachable with tableSize>=minLineSize after the minimum guarantee;
				// only numerical cases with negative or degenerate sizes).
				startSizes = new double[columnCount];
				for (int i = 0; i < columnCount; ++i) {
					startSizes[i] = this.mins[i] * Math.max(0, innerSize) / minSum;
				}
			}
			final ColumnDistribution.ColumnType[] distTypes = new ColumnDistribution.ColumnType[columnCount];
			for (int i = 0; i < columnCount; ++i) {
				distTypes[i] = switch (this.types[i]) {
				case COLUMN_TYPE_FIX -> ColumnDistribution.ColumnType.CONSTRAINED;
				case COLUMN_TYPE_PCT -> ColumnDistribution.ColumnType.PERCENT;
				default -> ColumnDistribution.ColumnType.AUTO;
				};
			}
			columnSizes = ColumnDistribution.distribute(startSizes, this.specs, distTypes, innerSize);
			return new Sized(tableSize, columnSizes);
		}
	}

	/**
	 * Resolved table size.
	 *
	 * @param tableSize   Table's line-axis size (including frame)
	 * @param columnSizes Column widths
	 */
	public record Sized(double tableSize, double[] columnSizes) {
	}

	/** Accumulated column-span data. */
	private static final class Colspan {
		final int col, span;
		double min = 0;
		double pct = LayoutUtils.NONE;
		double fix = LayoutUtils.NONE;
		double des = 0;

		Colspan(int col, int span) {
			assert span >= 2;
			this.col = col;
			this.span = span;
		}

		public boolean equals(Object o) {
			Colspan colspan = (Colspan) o;
			return this.col == colspan.col && this.span == colspan.span;
		}

		public int hashCode() {
			return 31 * this.col + this.span;
		}

		static final Comparator<Colspan> SPAN_COMPARATOR = (span1, span2) -> Integer.compare(span1.span, span2.span);
	}

	private final double[] mins, specs, desired;
	private final byte[] types;
	private final Map<Colspan, Colspan> colspans = new HashMap<>();
	private final List<Colspan> colspanList = new ArrayList<>();

	public AutoColumnWidths(final int columnCount) {
		this.mins = new double[columnCount];
		this.specs = new double[columnCount];
		this.desired = new double[columnCount];
		this.types = new byte[columnCount];
	}

	/**
	 * Applies an absolute width specified by colgroup (to each column in the span).
	 */
	public void specFixed(final int col, final int span, final double fix) {
		for (int s = 0; s < span; ++s) {
			final int k = col + s;
			if (this.types[k] <= COLUMN_TYPE_FIX) {
				if (this.types[k] != COLUMN_TYPE_FIX) {
					this.types[k] = COLUMN_TYPE_FIX;
					this.specs[k] = 0;
				}
				this.specs[k] = Math.max(this.specs[k], fix);
			}
			this.desired[k] = Math.max(this.desired[k], fix);
		}
	}

	/**
	 * Applies a percentage width specified by colgroup (to each column in the span).
	 */
	public void specPercent(final int col, final int span, final double pct) {
		for (int s = 0; s < span; ++s) {
			final int k = col + s;
			if (this.types[k] <= COLUMN_TYPE_PCT) {
				if (this.types[k] != COLUMN_TYPE_PCT) {
					this.types[k] = COLUMN_TYPE_PCT;
					this.specs[k] = 0;
				}
				if (pct > this.specs[k]) {
					double pctDiff = pct - this.specs[k];
					this.specs[k] += pctDiff;
					this.desired[k] = 1; // Ensure PCT specifications are treated as having some content.
				}
			}
		}
	}

	/**
	 * Applies a colgroup min-size to the first column.
	 */
	public void colMin(final int col, final double minSize) {
		this.mins[col] = Math.max(minSize, this.mins[col]);
		this.desired[col] = Math.max(minSize, this.desired[col]);
	}

	/**
	 * Applies a colgroup max-size to the first column.
	 */
	public void colMax(final int col, final double maxSize) {
		this.mins[col] = Math.min(maxSize, this.mins[col]);
		if (this.types[col] == COLUMN_TYPE_FIX) {
			this.specs[col] = Math.min(maxSize, this.specs[col]);
			this.desired[col] = Math.min(maxSize, this.desired[col]);
		}
	}

	/**
	 * Accumulates cell measurements. Applies non-spanning cells immediately through the column-type
	 * hierarchy; stores spanning cells in Colspan buckets for distribution in the three passes of finish().
	 *
	 * @param col  First column (zero-based)
	 * @param span Number of spanned columns
	 * @param min  Minimum width (including frame)
	 * @param des  Preferred width (including frame)
	 * @param type Specification type (COLUMN_TYPE_*)
	 * @param spec Specified width (absolute value or ratio, depending on type; same as des for DES)
	 */
	public void cell(final int col, final int span, final double min, double des, final byte type, final double spec) {
		if (span == 1) {
			// No column span.
			this.mins[col] = Math.max(this.mins[col], min);
			switch (type) {
			case COLUMN_TYPE_DES:
				if (this.types[col] == COLUMN_TYPE_DES) {
					this.specs[col] = Math.max(this.specs[col], spec);
				}
				break;
			case COLUMN_TYPE_FIX:
				if (this.types[col] <= COLUMN_TYPE_FIX) {
					if (this.types[col] != COLUMN_TYPE_FIX) {
						this.types[col] = COLUMN_TYPE_FIX;
						this.specs[col] = 0;
					}
					this.specs[col] = Math.max(this.specs[col], spec);
					this.desired[col] = Math.max(this.mins[col], this.specs[col]);
				} else {
					des = Math.max(des, spec);
				}
				break;
			case COLUMN_TYPE_PCT:
				if (this.types[col] <= COLUMN_TYPE_PCT) {
					if (this.types[col] != COLUMN_TYPE_PCT) {
						this.types[col] = COLUMN_TYPE_PCT;
						this.specs[col] = 0;
					}
					if (spec > this.specs[col]) {
						double pctDiff = spec - this.specs[col];
						this.specs[col] += pctDiff;
					}
				}
				break;
			default:
				throw new IllegalStateException();
			}
			if (this.types[col] != COLUMN_TYPE_FIX) {
				this.desired[col] = Math.max(this.desired[col], des);
			}
		} else {
			// Column span.
			Colspan key = new Colspan(col, span);
			Colspan colspan = this.colspans.get(key);
			if (colspan == null) {
				this.colspans.put(key, key);
				this.colspanList.add(key);
				colspan = key;
			}
			colspan.min = Math.max(colspan.min, min);
			colspan.des = Math.max(colspan.des, des);
			switch (type) {
			case COLUMN_TYPE_DES:
				break;
			case COLUMN_TYPE_FIX:
				if (LayoutUtils.isNone(colspan.fix)) {
					colspan.fix = spec;
				} else {
					colspan.fix = Math.max(colspan.fix, spec);
				}
				break;
			case COLUMN_TYPE_PCT:
				double pctDiff;
				if (LayoutUtils.isNone(colspan.pct)) {
					pctDiff = spec;
					colspan.pct = 0;
				} else {
					pctDiff = spec - colspan.pct;
				}
				if (pctDiff > 0) {
					colspan.pct += pctDiff;
				}
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}

	/**
	 * Returns the number of distinct accumulated (start column, colspan) constraints
	 * (Uspan, O(C²) in the worst case). E-6 increment 1 (2026-07-24): a read-only accessor for
	 * measurements used to choose spill thresholds and targets. Does not affect behavior.
	 */
	public int colspanConstraintCount() {
		return this.colspanList.size();
	}

	/**
	 * Performs three-pass column-span distribution (specified/minimum/percentage widths)
	 * and percentage limiting, then returns the result.
	 *
	 * @param tableFrame Table frame (added to minimum and maximum line-axis sizes)
	 */
	public Result finish(final double tableFrame) {
		// Apply colspan.
		Collections.sort(this.colspanList, Colspan.SPAN_COMPARATOR);
		for (int i = 0; i < this.colspanList.size(); ++i) {
			final Colspan colspan = this.colspanList.get(i);
			// Distribute automatic/fixed widths.
			boolean fix = !LayoutUtils.isNone(colspan.fix);
			double spec = fix ? colspan.fix : colspan.des;
			double desSum = 0;
			int noFixCount = 0, effCount = 0;
			double noFixDesiredSum = 0;
			for (int s = 0; s < colspan.span; ++s) {
				int k = colspan.col + s;
				double des = this.desired[k];
				if (des == 0) {
					continue;
				}
				++effCount;
				desSum += des;
				if (this.types[k] == COLUMN_TYPE_FIX) {
					continue;
				}
				++noFixCount;
				noFixDesiredSum += des;
			}
			// If all columns have zero width, do not ignore zero-width columns.
			if (effCount == 0) {
				noFixDesiredSum = 0;
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					double des = this.desired[k];
					++effCount;
					desSum += des;
					if (this.types[k] == COLUMN_TYPE_FIX) {
						continue;
					}
					++noFixCount;
					noFixDesiredSum += des;
				}
			}
			if (noFixCount == 0 && !fix) {
				// If all widths were originally fixed, do not apply automatic widths.
				continue;
			}
			if (spec > desSum) {
				if (effCount == 0) {
					effCount = colspan.span;
				}
				if (noFixCount == 0) {
					noFixDesiredSum = desSum;
				}
				double rem = spec - desSum;
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					if (effCount != colspan.span && this.desired[k] == 0) {
						continue;
					}
					if (noFixCount != 0 && this.types[k] == COLUMN_TYPE_FIX) {
						continue;
					}
					double diff;
					if (noFixDesiredSum > 0) {
						diff = rem * this.desired[k] / noFixDesiredSum;
					} else {
						diff = rem / colspan.span;
					}
					this.desired[k] += diff;
					if (this.types[k] == COLUMN_TYPE_PCT) {
						continue;
					}
					this.specs[k] = Math.max(this.mins[k], this.desired[k]);
				}
			}
		}
		for (int i = 0; i < this.colspanList.size(); ++i) {
			final Colspan colspan = this.colspanList.get(i);
			// Distribute minimum widths.
			double minSum = 0, desSum = 0, diffSum = 0;
			for (int s = 0; s < colspan.span; ++s) {
				int k = colspan.col + s;
				double min = this.mins[k];
				double des = this.desired[k];
				minSum += min;
				desSum += des;
				diffSum += (des - min);
			}
			if (colspan.min > minSum) {
				double rem = colspan.min - minSum;
				if (diffSum > 0) {
					double dist = Math.min(rem, diffSum);
					for (int s = 0; s < colspan.span; ++s) {
						int k = colspan.col + s;
						double min = this.mins[k];
						double des = this.desired[k];
						double diff = dist * (des - min) / diffSum;
						min = this.mins[k] += diff;
						if (this.types[k] == COLUMN_TYPE_DES) {
							this.specs[k] = Math.max(min, this.specs[k]);
						}
					}
					rem -= dist;
				}
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					double des = this.desired[k];
					double diff;
					if (desSum > 0) {
						diff = rem * des / desSum;
					} else {
						diff = rem / colspan.span;
					}
					double min = (this.mins[k] += diff);
					this.desired[k] = Math.max(min, this.desired[k]);
					if (this.types[k] != COLUMN_TYPE_DES) {
						continue;
					}
					this.specs[k] = Math.max(min, this.specs[k]);
				}
			}
		}
		for (int i = 0; i < this.colspanList.size(); ++i) {
			final Colspan colspan = this.colspanList.get(i);
			// Distribute percentage widths in proportion to des.
			if (LayoutUtils.isNone(colspan.pct)) {
				continue;
			}
			double spec = colspan.pct;
			double pctSum = 0;
			int nonPctCount = 0;
			double nonPctSum = 0, desSum = 0;
			for (int s = 0; s < colspan.span; ++s) {
				int k = colspan.col + s;
				double des = this.desired[k];
				desSum += des;
				if (this.types[k] == COLUMN_TYPE_PCT) {
					pctSum += this.specs[k];
					continue;
				}
				++nonPctCount;
				nonPctSum += des;
			}
			if (spec > pctSum) {
				double rem = spec - pctSum;
				if (nonPctCount == 0) {
					nonPctCount = colspan.span;
					nonPctSum = desSum;
				}
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					if (nonPctCount != colspan.span && this.types[k] == COLUMN_TYPE_PCT) {
						continue;
					}
					double diff;
					if (nonPctSum > 0) {
						diff = rem * this.desired[k] / nonPctSum;
					} else {
						diff = rem / nonPctCount;
					}
					if (this.types[k] == COLUMN_TYPE_PCT) {
						this.specs[k] += diff;
					} else {
						this.types[k] = COLUMN_TYPE_PCT;
						this.specs[k] = diff;
					}
				}
			}
		}

		// Calculate minimum/maximum widths and limit percentage widths.
		double minLineSize = 0, maxLineSize = 0;
		double pctRem = 1;
		for (int i = 0; i < this.mins.length; ++i) {
			minLineSize += this.mins[i];
			if (this.types[i] == COLUMN_TYPE_PCT) {
				this.specs[i] = Math.min(pctRem, this.specs[i]);
				pctRem -= this.specs[i];
			}
			maxLineSize += Math.max(this.mins[i], this.desired[i]);
		}
		minLineSize += tableFrame;
		maxLineSize += tableFrame;
		return new Result(this.mins, this.specs, this.desired, this.types, minLineSize, maxLineSize);
	}
}
