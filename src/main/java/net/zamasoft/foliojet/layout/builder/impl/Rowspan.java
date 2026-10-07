package net.zamasoft.foliojet.layout.builder.impl;

import java.util.Comparator;

/**
 * Distribution request for a row span (rowspan) (input to the P2-2 shared kernel).
 */
public class Rowspan {
	/** Row index (zero-based) */
	public final int row;
	/** Number of spanned rows */
	public final int span;
	/** Minimum width */
	public double min;

	public Rowspan(int row, int span) {
		assert span >= 2;
		this.row = row;
		this.span = span;
	}

	public boolean equals(Object o) {
		Rowspan rowspan = (Rowspan) o;
		return this.row == rowspan.row && this.span == rowspan.span;
	}

	public int hashCode() {
		int h = this.row;
		h = 31 * h + this.span;
		return h;
	}

	public static final Comparator<Rowspan> SPAN_COMPARATOR = new Comparator<Rowspan>() {
		public int compare(Rowspan o1, Rowspan o2) {
			Rowspan span1 = (Rowspan) o1;
			Rowspan span2 = (Rowspan) o2;
			if (span1.span > span2.span) {
				return 1;
			}
			if (span1.span < span2.span) {
				return -1;
			}
			return 0;
		}
	};

}
