package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;

/** A &lt;length&gt; value. {@link net.zamasoft.foliojet.css.token.Unit} represents the unit. */
public interface LengthValue extends Value, QuantityValue {
	public boolean isZero();

	public boolean isNegative();

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style);
}
