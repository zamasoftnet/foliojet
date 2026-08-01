package net.zamasoft.foliojet.layout.box.params;

/**
 * Flexコンテナの折り返しモードです(Flex F1a、2026-08-02)。CSS側の
 * {@code FlexWrapValue}と同名対応(マッピングはBoxStyleMapper)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum FlexWrap {
	NOWRAP, WRAP, WRAP_REVERSE;

	public boolean isWrap() {
		return this != NOWRAP;
	}
}
