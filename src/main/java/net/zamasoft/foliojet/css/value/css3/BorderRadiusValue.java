package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;

/**
 * Unicode-Range です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class BorderRadiusValue implements Value {
	public static final BorderRadiusValue ZERO_RADIUS = new BorderRadiusValue(AbsoluteLengthValue.ZERO,
			AbsoluteLengthValue.ZERO);

	public final LengthValue hr, vr;

	public static BorderRadiusValue create(LengthValue hr, LengthValue vr) {
		if (hr.isZero() && vr.isZero()) {
			return ZERO_RADIUS;
		}
		return new BorderRadiusValue(hr, vr);
	}

	protected BorderRadiusValue(LengthValue hr, LengthValue vr) {
		this.hr = hr;
		this.vr = vr;
	}

}