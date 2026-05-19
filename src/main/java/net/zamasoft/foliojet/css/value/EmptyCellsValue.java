package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: EmptyCellsValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class EmptyCellsValue implements Value {
	public static final EmptyCellsValue SHOW_VALUE = new EmptyCellsValue(Types.EMPTY_CELLS_SHOW);

	public static final EmptyCellsValue HIDE_VALUE = new EmptyCellsValue(Types.EMPTY_CELLS_HIDE);

	private final byte emptyCells;

	private EmptyCellsValue(byte emptyCells) {
		this.emptyCells = emptyCells;
	}

	public short getValueType() {
		return TYPE_EMPTY_CELLS;
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