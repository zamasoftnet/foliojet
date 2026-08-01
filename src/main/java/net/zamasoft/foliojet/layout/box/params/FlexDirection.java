package net.zamasoft.foliojet.layout.box.params;

/**
 * Flexコンテナの主軸方向です(Flex F1a、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2)。CSS側の
 * {@code FlexDirectionValue}と同名対応(マッピングはBoxStyleMapper)。
 * 物理軸への写像はF4aのFlexAxesが担う。
 *
 * @author MIYABE Tatsuhiko
 */
public enum FlexDirection {
	ROW, ROW_REVERSE, COLUMN, COLUMN_REVERSE;

	public boolean isRow() {
		return this == ROW || this == ROW_REVERSE;
	}

	public boolean isReverse() {
		return this == ROW_REVERSE || this == COLUMN_REVERSE;
	}
}
