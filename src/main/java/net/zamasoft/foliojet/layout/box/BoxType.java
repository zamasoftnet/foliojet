package net.zamasoft.foliojet.layout.box;

/**
 * The box type.
 */
public enum BoxType {
	PAGE, TEXT_BLOCK, LINE, INLINE, BLOCK, REPLACED, TABLE, TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_ROW_GROUP,
	TABLE_ROW, TABLE_CELL,
	/**
	 * A visual rescue split fragment
	 * ({@code net.zamasoft.foliojet.layout.rescue.VisualRescueBox};
	 * added 2026-07-25, increment 3. <b>Not yet wired into the production path</b>).
	 *
	 * <p>
	 * Uses a distinct type rather than impersonating an existing one (especially {@code REPLACED}).
	 * Impersonation would leave ClassCastExceptions, such as casting {@code getParams()} to
	 * {@code ReplacedParams}, undetected until runtime (recommendation §2).
	 * This type is short-lived pagination state derived from an already laid-out box
	 * and does not enter the recipe (LayoutSource).
	 * </p>
	 */
	RESCUE;

	/**
	 * Returns true for internal table elements (column groups, columns, row groups, rows, and cells).
	 */
	public boolean isTableInternal() {
		return switch (this) {
		case TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_ROW_GROUP, TABLE_ROW, TABLE_CELL -> true;
		default -> false;
		};
	}
}
