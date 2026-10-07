package net.zamasoft.foliojet.css.value;

/**
 * A value for {@code grid-column-start/end} or {@code grid-row-start/end}
 * (Grid G0). In addition to {@code auto}, integer line numbers (negative allowed, zero forbidden),
 * and {@code span positive-integer}, line names ({@code <custom-ident>}) are supported since 2026-08-29:
 * {@code name} alone, {@code N name} (the Nth line with that name), and
 * {@code span N name}. The layout layer ({@code GridLineNameResolver}) resolves line names
 * to numbers; this class only represents the syntax.
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridLineValue implements Value {
	/** {@code auto}. */
	public static final GridLineValue AUTO_VALUE = new GridLineValue(true, 0, false, null);

	private final boolean auto;

	/** Line number (non-span, nonzero) or span count (positive for spans). Zero for a name alone. */
	private final int number;

	private final boolean span;

	/** The line name (null if absent). */
	private final String name;

	private GridLineValue(final boolean auto, final int number, final boolean span, final String name) {
		this.auto = auto;
		this.number = number;
		this.span = span;
		this.name = name;
	}

	public static GridLineValue line(final int number) {
		return new GridLineValue(false, number, false, null);
	}

	public static GridLineValue span(final int count) {
		return new GridLineValue(false, count, true, null);
	}

	/** {@code name} alone (2026-08-29). */
	public static GridLineValue named(final String name) {
		return new GridLineValue(false, 0, false, name);
	}

	/** {@code N name} (2026-08-29). */
	public static GridLineValue line(final int number, final String name) {
		return new GridLineValue(false, number, false, name);
	}

	/** {@code span N name} (2026-08-29). */
	public static GridLineValue span(final int count, final String name) {
		return new GridLineValue(false, count, true, name);
	}

	public boolean isAuto() {
		return this.auto;
	}

	public boolean isSpan() {
		return this.span;
	}

	/** Whether a line name is present (2026-08-29). */
	public boolean isNamed() {
		return this.name != null;
	}

	/** A line name alone (only {@code <custom-ident>}; used to fill omitted grid-area components). */
	public boolean isNameOnly() {
		return this.name != null && !this.span && this.number == 0;
	}

	public String getName() {
		return this.name;
	}

	public int getNumber() {
		return this.number;
	}

	@Override
	public String toString() {
		if (this.auto) {
			return "auto";
		}
		final StringBuilder buff = new StringBuilder();
		if (this.span) {
			buff.append("span ");
		}
		if (this.number != 0) {
			buff.append(this.number);
		}
		if (this.name != null) {
			if (buff.length() > 0 && buff.charAt(buff.length() - 1) != ' ') {
				buff.append(' ');
			}
			buff.append(this.name);
		}
		return buff.toString();
	}
}
