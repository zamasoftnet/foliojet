package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * &lt;length&gt; の値です。単位は {@link net.zamasoft.foliojet.css.token.Unit} で表します。
 */
public interface LengthValue extends Value, QuantityValue {
	public boolean isZero();

	public boolean isNegative();

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style);
}
