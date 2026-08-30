package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * フォント相対の長さ(em / ex / rem / ch / lh / cap / rlh)です。
 */
public final class RelativeLengthValue implements LengthValue {
	private final Unit unit;

	private final double value;

	private RelativeLengthValue(Unit unit, double value) {
		this.unit = unit;
		this.value = value;
	}

	/** 単位を指定して生成します(calc()のフォント相対成分の解決に使う)。 */
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
			// ch は x-height 近似(従来実装踏襲)
			UserAgent ua = style.getUserAgent();
			FontStyle fontStyle = style.getFontStyle();
			FontListMetrics flm = ua.getFontManager().getFontListMetrics(fontStyle);
			double xheight = flm.getMaxXHeight();
			return AbsoluteLengthValue.create(ua, xheight * this.value);
		}
		case CAP: {
			// SPEC css-values-4: <b>第一</b>利用可能フォントのcap-height。
			// フォントソースは1/1000em単位で持つ(OpenTypeFontSourceは'H'の
			// グリフ実データから得る)。ex/chがリスト中の最大を採るのと違い
			// 先頭だけを見るのは仕様どおり——和文フォールバックのcap-heightは
			// 表意文字の高さ(ほぼ1em)になり、最大を採ると1emへ潰れる
			UserAgent ua = style.getUserAgent();
			FontListMetrics flm = ua.getFontManager().getFontListMetrics(style.getFontStyle());
			double fontSize = FontSize.get(style);
			double capRatio = flm.getLength() == 0 ? 0
					: flm.getFontMetrics(0).getFontSource().getCapHeight() / 1000.0;
			if (capRatio <= 0) {
				// メトリクスが取れないときはUAの既定比(AbstractFontSourceと同じ0.7)
				capRatio = 0.7;
			}
			return AbsoluteLengthValue.create(ua, fontSize * capRatio * this.value);
		}
		case LH: {
			// SPEC css-values-4: 自要素の計算済みline-height。line-height
			// 特性自身に書かれた場合の自己参照はLineHeight.getComputedValueが
			// 先に継承値基準で畳むため、ここへは到達しない
			double lineHeight = net.zamasoft.foliojet.css.impl.property.font.LineHeight.get(style);
			return AbsoluteLengthValue.create(style.getUserAgent(), lineHeight * this.value);
		}
		case RLH: {
			// SPEC css-values-4: 根要素の計算済みline-height。根は先に計算
			// されるので子孫からは安全に読める。根自身のline-heightに書かれた
			// 場合の自己参照はLineHeight.getComputedValueが畳むため到達しない
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
