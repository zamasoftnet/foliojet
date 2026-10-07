package net.zamasoft.foliojet.css.property;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.Value;

/**
 * A property that cannot be decomposed.
 *
 * @author MIYABE Tatsuhiko
 */
public interface PrimitivePropertyInfo extends PropertyInfo {
	/**
	 * Returns true if the parent element's value is inherited by default.
	 *
	 * @return
	 */
	public boolean isInherited();

	/**
	 * Returns the default value if isInherit is false, or the root element's value if isInherit is true.
	 *
	 * @param style
	 * @return
	 */
	public Value getDefault(CSSStyle style);

	/**
	 * Returns the computed value.
	 *
	 * @param value
	 * @param style
	 * @return
	 */
	public Value getComputedValue(Value value, CSSStyle style);

	public PrimitivePropertyInfo getEffectiveInfo(CSSStyle style);
}