package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.EmptyCellsMode;


/**
 * @author MIYABE Tatsuhiko
 */
public enum EmptyCellsValue implements Value {
	SHOW_VALUE(EmptyCellsMode.SHOW),

	HIDE_VALUE(EmptyCellsMode.HIDE);

	private final EmptyCellsMode emptyCells;

	private EmptyCellsValue(EmptyCellsMode emptyCells) {
		this.emptyCells = emptyCells;
	}

	public EmptyCellsMode getEmptyCells() {
		return this.emptyCells;
	}

	public String toString() {
		switch (this.emptyCells) {
		case EmptyCellsMode.SHOW:
			return "show";

		case EmptyCellsMode.HIDE:
			return "hide";

		default:
			throw new IllegalStateException();
		}
	}
}