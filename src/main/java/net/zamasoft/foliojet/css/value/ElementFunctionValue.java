package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;

/** The name and resolution policy of a running element referenced from a margin box. */
public record ElementFunctionValue(String name, Mode mode) implements Value {
	public String getName() {
		return this.name;
	}

	public Mode getMode() {
		return this.mode;
	}
}
