package net.zamasoft.foliojet.layout.box.params;

/**
 * Main-axis direction of a Flex container (Flex F1a, 2026-08-02;
 * consult-codex-2026-08-02-flexbox.txt Q2). Corresponds by name to the CSS-side
 * {@code FlexDirectionValue} (mapping in BoxStyleMapper).
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
