package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.ua.NamedStringState;

/**
 * GCPM {@code string(name[, first|start|last|first-except])}。
 *
 * @author MIYABE Tatsuhiko
 */
public class StringFunctionValue implements Value {
	private final String name;

	private final byte mode;

	public StringFunctionValue(String name, byte mode) {
		this.name = name;
		this.mode = mode;
	}

	public String getName() {
		return this.name;
	}

	/** {@link NamedStringState#FIRST}/{@link NamedStringState#START}/{@link NamedStringState#LAST}/{@link NamedStringState#FIRST_EXCEPT} */
	public byte getMode() {
		return this.mode;
	}
}
