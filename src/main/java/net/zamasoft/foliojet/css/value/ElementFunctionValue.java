package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;

/** マージンボックスから参照する running 要素の名前と解決方針です。 */
public record ElementFunctionValue(String name, Mode mode) implements Value {
	public String getName() {
		return this.name;
	}

	public Mode getMode() {
		return this.mode;
	}
}
