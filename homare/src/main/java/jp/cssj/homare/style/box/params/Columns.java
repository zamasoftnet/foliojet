package jp.cssj.homare.style.box.params;

import jp.cssj.homare.style.util.StyleUtils;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Columns.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Columns {
	public static final byte FILL_AUTO = 1;
	public static final byte FILL_BALANCE = 2;

	public final byte count;

	public final double width;

	public final double gap;

	public final Border rule;

	public final byte fill;

	public static final Columns NONE_COLUMNS = new Columns((byte) 0, StyleUtils.NONE, 0, Border.NONE_BORDER,
			FILL_BALANCE);

	public Columns(byte count, double width, double gap, Border rule, byte fill) {
		this.count = count;
		this.width = width;
		this.gap = gap;
		this.rule = rule;
		this.fill = fill;
	}

	public String toString() {
		return "count=" + this.count + "/width=" + this.width + "/gap=" + this.gap + "/rule=" + this.rule + "/fill="
				+ this.fill;
	}
}