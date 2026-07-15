package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.TableParams;

/**
 * @author MIYABE Tatsuhiko
 */
public enum BorderCollapseValue implements Value {
	SEPARATE_VALUE(TableParams.BORDER_SEPARATE),

	COLLAPSE_VALUE(TableParams.BORDER_COLLAPSE);

	private final byte borderCollapse;

	private BorderCollapseValue(byte borderCollapse) {
		this.borderCollapse = borderCollapse;
	}

	public byte getBorderCollapse() {
		return this.borderCollapse;
	}

	public String toString() {
		switch (this.borderCollapse) {
		case TableParams.BORDER_COLLAPSE:
			return "collapse";

		case TableParams.BORDER_SEPARATE:
			return "separate";

		default:
			throw new IllegalStateException();
		}
	}
}