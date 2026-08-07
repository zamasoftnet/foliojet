package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;

/**
 * @author MIYABE Tatsuhiko
 */
public final class BorderValueUtils {
	private BorderValueUtils() {
		// unused
	}

	/**
	 * &lt;border-width&gt; を値に変換します。
	 */
	public static LengthValue toBorderWidth(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "thin":
				return ua.getBorderWidth(BorderWidthKeyword.THIN);
			case "medium":
				return ua.getBorderWidth(BorderWidthKeyword.MEDIUM);
			case "thick":
				return ua.getBorderWidth(BorderWidthKeyword.THICK);
			default:
				return null;
			}
		}
		LengthValue length = ValueUtils.toLength(ua, token);
		if (length != null && length.isNegative()) {
			return null;
		}
		return length;
	}

	/**
	 * &lt;border-style&gt; を値に変換します。
	 */
	public static BorderStyleValue toBorderStyle(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "none":
				return BorderStyleValue.NONE_VALUE;
			case "hidden":
				return BorderStyleValue.HIDDEN_VALUE;
			case "dotted":
				return BorderStyleValue.DOTTED_VALUE;
			case "dashed":
				return BorderStyleValue.DASHED_VALUE;
			case "solid":
				return BorderStyleValue.SOLID_VALUE;
			case "double":
				return BorderStyleValue.DOUBLE_VALUE;
			case "groove":
				return BorderStyleValue.GROOVE_VALUE;
			case "ridge":
				return BorderStyleValue.RIDGE_VALUE;
			case "inset":
				return BorderStyleValue.INSET_VALUE;
			case "outset":
				return BorderStyleValue.OUTSET_VALUE;
			}
		}
		return null;
	}

	/**
	 * &lt;border-radius&gt;(水平半径 [垂直半径])を値に変換します。残りトークンを消費します。
	 */
	public static BorderRadiusValue toBorderRadius(UserAgent ua, TokenStream tokens) {
		CssToken first = tokens.next();
		if (first == null) {
			return null;
		}
		QuantityValue hr = toRadiusComponent(ua, first);
		if (hr == null) {
			return null;
		}
		QuantityValue vr;
		if (tokens.hasNext()) {
			CssToken second = tokens.next();
			if (tokens.hasNext()) {
				return null;
			}
			vr = toRadiusComponent(ua, second);
			if (vr == null) {
				return null;
			}
		} else {
			vr = hr;
		}
		return BorderRadiusValue.create(hr, vr);
	}

	/**
	 * border-radiusの半径成分({@code <length-percentage>})を値にします。
	 * パーセントは水平半径ならボックス幅・垂直半径なら高さ基準で、寸法確定後の
	 * 描画時に解決される({@code RectBorder.Radius#resolve})。longhand
	 * ({@link #toBorderRadius})とshorthand(BorderRadiusShorthand)の両方が使う。
	 */
	public static QuantityValue toRadiusComponent(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Percent) {
			return ValueUtils.toPercentage(token);
		}
		return ValueUtils.toLength(ua, token);
	}
}
