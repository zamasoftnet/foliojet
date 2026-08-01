package net.zamasoft.foliojet.css.value;

/**
 * {@code flex-direction}のキーワード値です(Flex F1a、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt)。layout側の{@code FlexDirection}と
 * 同名対応(マッピングはBoxStyleMapper)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum FlexDirectionValue implements Value {
	ROW("row"), ROW_REVERSE("row-reverse"), COLUMN("column"), COLUMN_REVERSE("column-reverse");

	private final String text;

	private FlexDirectionValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
