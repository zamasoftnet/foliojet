package net.zamasoft.foliojet.css.value;

/** The name of a running element whose style is determined at its original position. */
public record RunningPositionValue(String name) implements Value {
	public String getName() {
		return this.name;
	}
}
