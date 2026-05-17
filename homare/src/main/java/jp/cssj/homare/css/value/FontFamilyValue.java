package jp.cssj.homare.css.value;

import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;

/**
 * フォントファミリーです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FontFamilyValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FontFamilyValue implements Value {
	private static final long serialVersionUID = 0L;

	public static final FontFamilyValue SERIF = new FontFamilyValue(FontFamily.SERIF_VALUE);

	public static final FontFamilyValue SANS_SERIF = new FontFamilyValue(FontFamily.SANS_SERIF_VALUE);

	public static final FontFamilyValue CURSIVE = new FontFamilyValue(FontFamily.CURSIVE_VALUE);

	public static final FontFamilyValue FANTASY = new FontFamilyValue(FontFamily.FANTASY_VALUE);

	public static final FontFamilyValue MONOSPACE = new FontFamilyValue(FontFamily.MONOSPACE_VALUE);

	private final FontFamilyList list;

	public FontFamilyValue(FontFamily[] entries) {
		this.list = new FontFamilyList(entries);
	}

	public FontFamilyValue(FontFamily f1) {
		this.list = new FontFamilyList(f1);
	}

	public FontFamilyList asFontFamilyList() {
		return this.list;
	}

	public FontFamily get(int index) {
		return this.list.get(index);
	}

	public int getLength() {
		return this.list.getLength();
	}

	public short getValueType() {
		return TYPE_FONT_FAMILY;
	}

	@Override
	public String toString() {
		return this.list.toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof FontFamilyValue v && this.list.equals(v.list);
	}

	@Override
	public int hashCode() {
		return this.list.hashCode();
	}
}
