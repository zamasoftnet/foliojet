package net.zamasoft.foliojet.css.value;

/**
 * {@code grid-auto-flow}の値です(css-grid-1 §7.7、2026-08-29)。
 * {@code [ row | column ] || dense}。
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridAutoFlowValue implements Value {
	public static final GridAutoFlowValue ROW = new GridAutoFlowValue(false, false);

	public static final GridAutoFlowValue COLUMN = new GridAutoFlowValue(true, false);

	public static final GridAutoFlowValue ROW_DENSE = new GridAutoFlowValue(false, true);

	public static final GridAutoFlowValue COLUMN_DENSE = new GridAutoFlowValue(true, true);

	private final boolean column, dense;

	private GridAutoFlowValue(final boolean column, final boolean dense) {
		this.column = column;
		this.dense = dense;
	}

	public static GridAutoFlowValue of(final boolean column, final boolean dense) {
		return column ? (dense ? COLUMN_DENSE : COLUMN) : (dense ? ROW_DENSE : ROW);
	}

	public boolean isColumn() {
		return this.column;
	}

	public boolean isDense() {
		return this.dense;
	}

	@Override
	public String toString() {
		return (this.column ? "column" : "row") + (this.dense ? " dense" : "");
	}
}
