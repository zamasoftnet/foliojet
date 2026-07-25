package net.zamasoft.foliojet.layout.box;

/**
 * ボックスのタイプです。
 */
public enum BoxType {
	PAGE, TEXT_BLOCK, LINE, INLINE, BLOCK, REPLACED, TABLE, TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_ROW_GROUP,
	TABLE_ROW, TABLE_CELL,
	/**
	 * 救済分割(visual rescue split)の断片です
	 * ({@code net.zamasoft.foliojet.layout.rescue.VisualRescueBox}。
	 * 2026-07-25追加、増分3。<b>まだ本番経路へ配線されていません</b>)。
	 *
	 * <p>
	 * 既存の型(特に{@code REPLACED})を偽装せず独立の型にしています。
	 * 偽装すると{@code getParams()}が{@code ReplacedParams}へ
	 * キャストされる類のClassCastExceptionが実行時まで見つからないためです
	 * (答申§2)。この型はレイアウト済みボックスから派生する短命な
	 * ページング状態であり、レシピ(LayoutSource)には入りません。
	 * </p>
	 */
	RESCUE;

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
