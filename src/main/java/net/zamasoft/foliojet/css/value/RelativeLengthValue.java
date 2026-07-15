package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.impl.css.property.FontSize;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * フォント相対の長さ(em / ex / rem / ch)です。
 */
public final class RelativeLengthValue implements LengthValue {
	private final Unit unit;

	private final double value;

	private RelativeLengthValue(Unit unit, double value) {
		this.unit = unit;
		this.value = value;
	}

	public static RelativeLengthValue em(double value) {
		return new RelativeLengthValue(Unit.EM, value);
	}

	public static RelativeLengthValue ex(double value) {
		return new RelativeLengthValue(Unit.EX, value);
	}

	public static RelativeLengthValue rem(double value) {
		return new RelativeLengthValue(Unit.REM, value);
	}

	public static RelativeLengthValue ch(double value) {
		return new RelativeLengthValue(Unit.CH, value);
	}

	public Unit getUnit() {
		return this.unit;
	}

	public double getValue() {
		return this.value;
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		switch (this.unit) {
		case EM: {
			double fontSize = FontSize.get(style);
			return AbsoluteLengthValue.create(style.getUserAgent(), fontSize * this.value);
		}
		case REM: {
			double fontSize = FontSize.get(style.getRootStyle());
			return AbsoluteLengthValue.create(style.getUserAgent(), fontSize * this.value);
		}
		case EX:
		case CH: {
			// ch は x-height 近似(従来実装踏襲)
			UserAgent ua = style.getUserAgent();
			FontStyle fontStyle = style.getFontStyle();
			FontListMetrics flm = ua.getFontManager().getFontListMetrics(fontStyle);
			double xheight = flm.getMaxXHeight();
			return AbsoluteLengthValue.create(ua, xheight * this.value);
		}
		default:
			throw new IllegalStateException(this.unit.toString());
		}
	}

	public boolean isNegative() {
		return this.value < 0;
	}

	public boolean isZero() {
		return this.value == 0;
	}

	public String toString() {
		return this.value + this.unit.name().toLowerCase();
	}
}
