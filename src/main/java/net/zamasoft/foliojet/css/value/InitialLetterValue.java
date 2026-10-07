package net.zamasoft.foliojet.css.value;

/**
 * An {@code initial-letter} value (css-inline-3, added on 2026-08-20).
 *
 * @param lines the number of lines occupied by the drop cap (a real number of at least 1)
 * @param sink the number of lines to sink (1 through lines; lines=sink is a normal drop cap,
 *        sink=1 is a raised cap)
 * @author MIYABE Tatsuhiko
 */
public record InitialLetterValue(double lines, int sink) implements Value {

	@Override
	public String toString() {
		return this.lines + " " + this.sink;
	}
}
