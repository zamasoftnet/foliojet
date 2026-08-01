package net.zamasoft.foliojet.css.value;

/**
 * {@code flex-wrap}のキーワード値です(Flex F1a、2026-08-02)。
 * layout側の{@code FlexWrap}と同名対応(マッピングはBoxStyleMapper)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum FlexWrapValue implements Value {
	NOWRAP("nowrap"), WRAP("wrap"), WRAP_REVERSE("wrap-reverse");

	private final String text;

	private FlexWrapValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
