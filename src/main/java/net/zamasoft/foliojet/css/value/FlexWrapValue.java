package net.zamasoft.foliojet.css.value;

/**
 * A {@code flex-wrap} keyword value (Flex F1a, 2026-08-02).
 * Names correspond to {@code FlexWrap} in the layout layer (mapped by BoxStyleMapper).
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
