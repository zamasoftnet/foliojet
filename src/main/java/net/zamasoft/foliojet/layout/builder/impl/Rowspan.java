package net.zamasoft.foliojet.layout.builder.impl;

import java.util.Comparator;

/**
 * 行結合(rowspan)の分配要求です(P2-2 共有核の入力)。
 */
public class Rowspan {
	/** 行番号(0オリジン) */
	public final int row;
	/** 結合される行の数 */
	public final int span;
	/** 最小幅 */
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
