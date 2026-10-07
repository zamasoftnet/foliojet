package net.zamasoft.foliojet.css.value;

/**
 * One {@code string-set} entry (a single {@code <ident> <value>+} pair).
 * {@code parts} is a mixed list of {@link StringValue}/{@link CounterValue}/
 * {@link CountersValue}/{@link AttrValue}/{@link ContentFunctionValue}.
 *
 * @author MIYABE Tatsuhiko
 */
public class StringSetEntryValue implements Value {
	private final String name;

	private final Value[] parts;

	public StringSetEntryValue(String name, Value[] parts) {
		this.name = name;
		this.parts = parts;
	}

	public String getName() {
		return this.name;
	}

	public Value[] getParts() {
		return this.parts;
	}
}
