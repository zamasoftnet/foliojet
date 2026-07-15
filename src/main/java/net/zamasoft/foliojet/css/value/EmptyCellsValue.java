package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 */
public enum EmptyCellsValue implements Value {
	SHOW_VALUE(Types.EMPTY_CELLS_SHOW),

	HIDE_VALUE(Types.EMPTY_CELLS_HIDE);

	private final byte emptyCells;

	private EmptyCellsValue(byte emptyCells) {
		this.emptyCells = emptyCells;
	}

	public byte getEmptyCells() {
		return this.emptyCells;
	}

	public String toString() {
		switch (this.emptyCells) {
		case Types.EMPTY_CELLS_SHOW:
			return "show";

		case Types.EMPTY_CELLS_HIDE:
			return "hide";

		default:
			throw new IllegalStateException();
		}
	}
}