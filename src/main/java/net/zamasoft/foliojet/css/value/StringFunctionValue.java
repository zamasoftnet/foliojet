package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;

/**
 * GCPM {@code string(name[, first|start|last|first-except])}.
 *
 * @author MIYABE Tatsuhiko
 */
public class StringFunctionValue implements Value {
	private final String name;

	private final Mode mode;

	public StringFunctionValue(final String name, final Mode mode) {
		this.name = name;
		this.mode = mode;
	}

	public String getName() {
		return this.name;
	}

	/** The resolution policy for assignments within a page. */
	public Mode getMode() {
		return this.mode;
	}
}
