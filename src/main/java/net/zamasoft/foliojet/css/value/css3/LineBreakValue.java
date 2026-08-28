package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

/**
 * {@code line-break}の値です(css-text-3 §5.2、2026-08-29新設)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum LineBreakValue implements Value {
	AUTO("auto"),

	LOOSE("loose"),

	NORMAL("normal"),

	STRICT("strict"),

	ANYWHERE("anywhere");

	private final String name;

	private LineBreakValue(final String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
