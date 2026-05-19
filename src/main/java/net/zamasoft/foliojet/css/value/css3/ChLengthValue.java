package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ExLengthValue.java 1034 2013-10-23 05:51:57Z miyabe $
 */
public class ChLengthValue implements LengthValue {
	private static final ChLengthValue ZERO_VALUE = new ChLengthValue(0);

	private final double value;

	public static ChLengthValue create(double value) {
		if (value == 0) {
			return ZERO_VALUE;
		}
		return new ChLengthValue(value);
	}

	private ChLengthValue(double value) {
		this.value = value;
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		// TODO
		UserAgent ua = style.getUserAgent();
		FontStyle fontStyle = style.getFontStyle();
		FontListMetrics flm = ua.getFontManager().getFontListMetrics(fontStyle);
		double xheight = flm.getMaxXHeight();
		return AbsoluteLengthValue.create(ua, xheight * this.value);
	}

	public short getValueType() {
		return CSS3Value.TYPE_CH_LENGTH;
	}

	public short getUnitType() {
		return LengthValue.UNIT_FR;
	}

	public boolean isNegative() {
		return this.value < 0;
	}

	public boolean isZero() {
		return this.value == 0;
	}

	public String toString() {
		return this.value + "ch";
	}
}