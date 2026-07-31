package net.zamasoft.foliojet.css.value;

/**
 * {@code grid-column-start/end}・{@code grid-row-start/end}の1値です
 * (Grid G0)。初期サブセットは{@code auto}・整数線番号(負可・0不可)・
 * {@code span 正整数}のみ(named lineは非対応)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridLineValue implements Value {
	/** {@code auto}。 */
	public static final GridLineValue AUTO_VALUE = new GridLineValue(true, 0);

	/** span指定ならtrue…ではなく、auto判定用。 */
	private final boolean auto;

	/** 線番号(非span、非0)またはspan数(span時、正)。 */
	private final int number;

	private final boolean span;

	private GridLineValue(final boolean auto, final int number) {
		this.auto = auto;
		this.number = number;
		this.span = false;
	}

	private GridLineValue(final int number, final boolean span) {
		this.auto = false;
		this.number = number;
		this.span = span;
	}

	public static GridLineValue line(final int number) {
		return new GridLineValue(number, false);
	}

	public static GridLineValue span(final int count) {
		return new GridLineValue(count, true);
	}

	public boolean isAuto() {
		return this.auto;
	}

	public boolean isSpan() {
		return this.span;
	}

	public int getNumber() {
		return this.number;
	}

	@Override
	public String toString() {
		if (this.auto) {
			return "auto";
		}
		return this.span ? "span " + this.number : String.valueOf(this.number);
	}
}
