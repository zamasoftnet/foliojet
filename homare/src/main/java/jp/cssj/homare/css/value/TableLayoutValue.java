package jp.cssj.homare.css.value;

import jp.cssj.homare.style.box.params.TableParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TableLayoutValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableLayoutValue implements Value {
	public static final TableLayoutValue AUTO_VALUE = new TableLayoutValue(TableParams.LAYOUT_AUTO);

	public static final TableLayoutValue FIXED_VALUE = new TableLayoutValue(TableParams.LAYOUT_FIXED);

	private final byte tableLayout;

	private TableLayoutValue(byte tableLayout) {
		this.tableLayout = tableLayout;
	}

	public short getValueType() {
		return Value.TYPE_TABLE_LAYOUT;
	}

	public byte getTableLayout() {
		return this.tableLayout;
	}

	public String toString() {
		switch (this.getTableLayout()) {
		case TableParams.LAYOUT_AUTO:
			return "auto";
		case TableParams.LAYOUT_FIXED:
			return "fixed";
		default:
			throw new IllegalStateException();
		}
	}
}