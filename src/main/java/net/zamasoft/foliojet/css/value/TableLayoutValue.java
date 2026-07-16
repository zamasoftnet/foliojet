package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.TableParams;

/**
 * @author MIYABE Tatsuhiko
 */
public enum TableLayoutValue implements Value {
	AUTO_VALUE(TableParams.LAYOUT_AUTO),

	FIXED_VALUE(TableParams.LAYOUT_FIXED);

	private final byte tableLayout;

	private TableLayoutValue(byte tableLayout) {
		this.tableLayout = tableLayout;
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