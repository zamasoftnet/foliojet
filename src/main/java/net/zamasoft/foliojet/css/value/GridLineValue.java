package net.zamasoft.foliojet.css.value;

/**
 * {@code grid-column-start/end}・{@code grid-row-start/end}の1値です
 * (Grid G0)。{@code auto}・整数線番号(負可・0不可)・{@code span 正整数}
 * に加え、2026-08-29から線名({@code <custom-ident>})を持てる:
 * {@code name}単独・{@code N name}(N番目のその名の線)・
 * {@code span N name}。線名の数値化はレイアウト側
 * ({@code GridLineNameResolver})が行う——ここは構文の写しに徹する。
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridLineValue implements Value {
	/** {@code auto}。 */
	public static final GridLineValue AUTO_VALUE = new GridLineValue(true, 0, false, null);

	private final boolean auto;

	/** 線番号(非span、非0)またはspan数(span時、正)。線名単独のときは0。 */
	private final int number;

	private final boolean span;

	/** 線名(無ければnull)。 */
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

	/** {@code name}単独(2026-08-29)。 */
	public static GridLineValue named(final String name) {
		return new GridLineValue(false, 0, false, name);
	}

	/** {@code N name}(2026-08-29)。 */
	public static GridLineValue line(final int number, final String name) {
		return new GridLineValue(false, number, false, name);
	}

	/** {@code span N name}(2026-08-29)。 */
	public static GridLineValue span(final int count, final String name) {
		return new GridLineValue(false, count, true, name);
	}

	public boolean isAuto() {
		return this.auto;
	}

	public boolean isSpan() {
		return this.span;
	}

	/** 線名を持つか(2026-08-29)。 */
	public boolean isNamed() {
		return this.name != null;
	}

	/** 線名単独({@code <custom-ident>}だけ。grid-areaの省略補完の判定用)。 */
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
