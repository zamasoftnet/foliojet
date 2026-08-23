package net.zamasoft.foliojet.css.value;

/** CSS Rubyの{@code ruby-position}値。 */
public enum RubyPositionValue implements Value {
	ALTERNATE("alternate", true, true, false),
	OVER("over", false, true, false),
	UNDER("under", false, false, false),
	ALTERNATE_OVER("alternate over", true, true, false),
	ALTERNATE_UNDER("alternate under", true, false, false),
	INTER_CHARACTER("inter-character", false, true, true);

	private final String text;
	private final boolean alternate;
	private final boolean startsOver;
	private final boolean interCharacter;

	private RubyPositionValue(final String text, final boolean alternate, final boolean startsOver,
			final boolean interCharacter) {
		this.text = text;
		this.alternate = alternate;
		this.startsOver = startsOver;
		this.interCharacter = interCharacter;
	}

	public boolean isOver(final int level) {
		return this.alternate ? this.startsOver == ((level & 1) == 0) : this.startsOver;
	}

	public boolean isInterCharacter() {
		return this.interCharacter;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
