package net.zamasoft.foliojet.css.value;

/**
 * {@code initial-letter}の値です(css-inline-3、2026-08-20新設)。
 *
 * @param lines ドロップキャップの占有行数(1以上の実数)
 * @param sink 沈み行数(1〜lines。lines=sinkが通常のドロップキャップ、
 *        sink=1はraised cap)
 * @author MIYABE Tatsuhiko
 */
public record InitialLetterValue(double lines, int sink) implements Value {

	@Override
	public String toString() {
		return this.lines + " " + this.sink;
	}
}
