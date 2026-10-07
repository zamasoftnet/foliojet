package net.zamasoft.foliojet.css.value;

/**
 * A {@code flex-direction} keyword value (Flex F1a, 2026-08-02:
 * consult-codex-2026-08-02-flexbox.txt). Names correspond to {@code FlexDirection}
 * in the layout layer (mapped by BoxStyleMapper).
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
