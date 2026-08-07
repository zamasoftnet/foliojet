package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;

/**
 * border-radiusの1隅の半径(水平・垂直)です。各成分は
 * {@code <length-percentage>}(パーセントは対応する辺の寸法基準で、
 * 描画時に解決される)。
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