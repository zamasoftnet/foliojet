package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/** A font-relative length (em / ex / rem / ch / lh / cap / rlh). */
public final class RelativeLengthValue implements LengthValue {
	private final Unit unit;

	private final double value;

	private RelativeLengthValue(Unit unit, double value) {
		this.unit = unit;
		this.value = value;
	}

	/** Creates a value with the specified unit (used to resolve font-relative components of calc()). */
	public static RelativeLengthValue of(Unit unit, double value) {
		return new RelativeLengthValue(unit, value);
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

	public static RelativeLengthValue lh(double value) {
		return new RelativeLengthValue(Unit.LH, value);
	}

	public static RelativeLengthValue cap(double value) {
		return new RelativeLengthValue(Unit.CAP, value);
	}

	public static RelativeLengthValue rlh(double value) {
		return new RelativeLengthValue(Unit.RLH, value);
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
			// ch uses an x-height approximation (retains the previous implementation).
			UserAgent ua = style.getUserAgent();
			FontStyle fontStyle = style.getFontStyle();
			FontListMetrics flm = ua.getFontManager().getFontListMetrics(fontStyle);
			double xheight = flm.getMaxXHeight();
			return AbsoluteLengthValue.create(ua, xheight * this.value);
		}
		case CAP: {
			// SPEC css-values-4: the cap-height of the <b>first</b> available font.
			// Font sources hold it in 1/1000 em units (OpenTypeFontSource obtains it from the actual
			// glyph data for 'H'). Unlike ex/ch, which take the maximum in the list,
			// checking only the first font follows the specification: the cap-height of a Japanese fallback
			// is the ideograph height (nearly 1 em), so taking the maximum reduces it to 1 em.
			UserAgent ua = style.getUserAgent();
			FontListMetrics flm = ua.getFontManager().getFontListMetrics(style.getFontStyle());
			double fontSize = FontSize.get(style);
			double capRatio = flm.getLength() == 0 ? 0
					: flm.getFontMetrics(0).getFontSource().getCapHeight() / 1000.0;
			if (capRatio <= 0) {
				// If metrics are unavailable, use the UA's default ratio (0.7, as in AbstractFontSource).
				capRatio = 0.7;
			}
			return AbsoluteLengthValue.create(ua, fontSize * capRatio * this.value);
		}
		case LH: {
			// SPEC css-values-4: this element's computed line-height. A self-reference in the
			// line-height property itself does not reach here: LineHeight.getComputedValue
			// first folds it using the inherited value as the reference.
			double lineHeight = net.zamasoft.foliojet.css.impl.property.font.LineHeight.get(style);
			return AbsoluteLengthValue.create(style.getUserAgent(), lineHeight * this.value);
		}
		case RLH: {
			// SPEC css-values-4: the root element's computed line-height. The root is computed first,
			// so descendants can read it safely. A self-reference in the root's own line-height
			// does not reach here because LineHeight.getComputedValue folds it.
			double lineHeight = net.zamasoft.foliojet.css.impl.property.font.LineHeight
					.get(style.getRootStyle());
			return AbsoluteLengthValue.create(style.getUserAgent(), lineHeight * this.value);
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
