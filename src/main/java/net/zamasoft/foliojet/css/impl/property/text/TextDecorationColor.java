package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;

/**
 * {@code text-decoration-color}(css-text-decoration-3)です(2026-08-29新設)。
 *
 * <p>
 * 既定は{@code currentcolor}({@link KeywordValue#DEFAULT}番兵、border-color
 * と同じ)で、計算値でその要素の{@code color}へ解決します。描画側は
 * {@code AbstractTextParams.decorationColor}(nullなら文字色)で受けます。
 * 実サイト50件中10〜16件で{@code text-decoration: underline dotted #999}や
 * 個別指定として使われていました。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class TextDecorationColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextDecorationColor();

	/**
	 * 装飾線の色を返します。文字色と同じ(currentcolor)なら null。
	 * {@code transparent}は完全透明の色として返す(線を描かないのと同じ)。
	 */
	public static Color get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.TRANSPARENT) {
			return RGBAColor.create(0, 0, 0, 0);
		}
		if (value instanceof ColorValue color) {
			return color.getColor();
		}
		return null;
	}

	protected TextDecorationColor() {
		super("text-decoration-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		value = ValueUtils.emExToAbsoluteLength(value, style);
		if (value == KeywordValue.DEFAULT || value == KeywordValue.NONE) {
			return style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		final Value value = ColorValueUtils.toColorOrCurrent(ua, lu);
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
