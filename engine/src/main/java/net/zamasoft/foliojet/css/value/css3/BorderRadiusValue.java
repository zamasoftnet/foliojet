package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;

/**
 * Unicode-Range です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderRadiusValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderRadiusValue implements CSS3Value {
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

	public short getValueType() {
		return TYPE_RADIUS;
	}
}