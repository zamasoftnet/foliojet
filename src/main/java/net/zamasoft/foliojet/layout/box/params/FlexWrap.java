package net.zamasoft.foliojet.layout.box.params;

/**
 * Flex container wrapping mode (Flex F1a, 2026-08-02). Corresponds by name to the CSS-side
 * {@code FlexWrapValue} (mapping in BoxStyleMapper).
 *
 * @author MIYABE Tatsuhiko
 */
public enum FlexWrap {
	NOWRAP, WRAP, WRAP_REVERSE;

	public boolean isWrap() {
		return this != NOWRAP;
	}
}
