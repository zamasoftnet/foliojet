package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.pdfg2d.gc.font.UnicodeRange;
import net.zamasoft.pdfg2d.gc.font.UnicodeRangeList;
import net.zamasoft.foliojet.css.value.Value;

/**
 * Unicode-Range です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class UnicodeRangeValue implements Value {
	public static final UnicodeRangeValue EMPTY = new UnicodeRangeValue(new UnicodeRange[0]);

	private final UnicodeRangeList list;

	public UnicodeRangeValue(UnicodeRange[] includes) {
		this.list = new UnicodeRangeList(includes);
	}

	public UnicodeRangeList asUnicodeRangeList() {
		return this.list;
	}

	public boolean canDisplay(int c) {
		return this.list.canDisplay(c);
	}

	public boolean isEmpty() {
		return this.list.isEmpty();
	}

	@Override
	public String toString() {
		return this.list.toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof UnicodeRangeValue v && this.list.equals(v.list);
	}

	@Override
	public int hashCode() {
		return this.list.hashCode();
	}
}
