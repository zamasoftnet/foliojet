package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;

/**
 * The horizontal and vertical radii for one border-radius corner. Each component is
 * a {@code <length-percentage>} (percentages refer to the corresponding side's size
 * and resolve at rendering time).
 *
 * @author MIYABE Tatsuhiko
 */
public class BorderRadiusValue implements Value {
	public static final BorderRadiusValue ZERO_RADIUS = new BorderRadiusValue(AbsoluteLengthValue.ZERO,
			AbsoluteLengthValue.ZERO);

	public final QuantityValue hr, vr;

	public static BorderRadiusValue create(QuantityValue hr, QuantityValue vr) {
		if (hr.isZero() && vr.isZero()) {
			return ZERO_RADIUS;
		}
		return new BorderRadiusValue(hr, vr);
	}

	protected BorderRadiusValue(QuantityValue hr, QuantityValue vr) {
		this.hr = hr;
		this.vr = vr;
	}

}