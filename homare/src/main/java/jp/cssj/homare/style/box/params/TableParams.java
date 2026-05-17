package jp.cssj.homare.style.box.params;

/**
 * テーブルのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableParams.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableParams extends BlockParams {
	public static final byte BORDER_SEPARATE = 0;

	public static final byte BORDER_COLLAPSE = 1;

	public static final byte LAYOUT_AUTO = 0;

	public static final byte LAYOUT_FIXED = 1;

	public double borderSpacingH, borderSpacingV;

	public byte borderCollapse = BORDER_SEPARATE;

	public byte layout = LAYOUT_AUTO;

	public byte getType() {
		return TYPE_TABLE;
	}

	public String toString() {
		return super.toString() + "[borderSpacingH=" + this.borderSpacingH + ",borderSpacingV=" + this.borderSpacingV
				+ ",borderCollapse=" + this.borderCollapse + ",layout=" + this.layout + "]";
	}
}
