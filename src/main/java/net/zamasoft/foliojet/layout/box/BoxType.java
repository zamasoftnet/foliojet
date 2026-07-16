package net.zamasoft.foliojet.layout.box;

/**
 * ボックスのタイプです。
 */
public enum BoxType {
	PAGE, TEXT_BLOCK, LINE, INLINE, BLOCK, REPLACED, TABLE, TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_ROW_GROUP,
	TABLE_ROW, TABLE_CELL;

	/**
	 * テーブル内部要素(列グループ・列・行グループ・行・セル)であればtrueを返します。
	 */
	public boolean isTableInternal() {
		return switch (this) {
		case TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_ROW_GROUP, TABLE_ROW, TABLE_CELL -> true;
		default -> false;
		};
	}
}
