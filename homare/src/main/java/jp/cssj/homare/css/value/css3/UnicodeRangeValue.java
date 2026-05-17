package jp.cssj.homare.css.value.css3;

import net.zamasoft.pdfg2d.gc.font.UnicodeRange;
import net.zamasoft.pdfg2d.gc.font.UnicodeRangeList;

/**
 * Unicode-Range です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: UnicodeRangeValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class UnicodeRangeValue implements CSS3Value {
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

	public short getValueType() {
		return TYPE_UNICODE_RANGE;
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
