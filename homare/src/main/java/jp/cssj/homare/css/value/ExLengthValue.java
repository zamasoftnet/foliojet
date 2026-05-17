package jp.cssj.homare.css.value;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ExLengthValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ExLengthValue implements LengthValue {
	private static final ExLengthValue ZERO_VALUE = new ExLengthValue(0);

	private final double value;

	public static ExLengthValue create(double value) {
		if (value == 0) {
			return ZERO_VALUE;
		}
		return new ExLengthValue(value);
	}

	private ExLengthValue(double value) {
		this.value = value;
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		FontStyle fontStyle = style.getFontStyle();
		FontListMetrics flm = ua.getFontManager().getFontListMetrics(fontStyle);
		double xheight = flm.getMaxXHeight();
		return AbsoluteLengthValue.create(ua, xheight * this.value);
	}

	public short getValueType() {
		return Value.TYPE_EX_LENGTH;
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
		return this.value + "ex";
	}
}