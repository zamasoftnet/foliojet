package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.TableParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderCollapseValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderCollapseValue implements Value {
	public static final BorderCollapseValue SEPARATE_VALUE = new BorderCollapseValue(TableParams.BORDER_SEPARATE);

	public static final BorderCollapseValue COLLAPSE_VALUE = new BorderCollapseValue(TableParams.BORDER_COLLAPSE);

	private final byte borderCollapse;

	private BorderCollapseValue(byte borderCollapse) {
		this.borderCollapse = borderCollapse;
	}

	public short getValueType() {
		return Value.TYPE_BORDER_COLLAPSE;
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