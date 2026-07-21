package net.zamasoft.foliojet.css.value;

/**
 * {@code string-set}の1エントリ({@code <ident> <value>+}の1組)。
 * {@code parts}は{@link StringValue}/{@link CounterValue}/
 * {@link CountersValue}/{@link AttrValue}/{@link ContentFunctionValue}
 * の混在リスト。
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
