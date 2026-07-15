package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderValueUtils.java 1552 2018-04-26 01:43:24Z miyabe $
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
				return ua.getBorderWidth(UserAgent.BORDER_WIDTH_THIN);
			case "medium":
				return ua.getBorderWidth(UserAgent.BORDER_WIDTH_MEDIUM);
			case "thick":
				return ua.getBorderWidth(UserAgent.BORDER_WIDTH_THICK);
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
		LengthValue hr = ValueUtils.toLength(ua, first);
		if (hr == null) {
			return null;
		}
		LengthValue vr;
		if (tokens.hasNext()) {
			CssToken second = tokens.next();
			if (tokens.hasNext()) {
				return null;
			}
			vr = ValueUtils.toLength(ua, second);
			if (vr == null) {
				return null;
			}
		} else {
			vr = hr;
		}
		return BorderRadiusValue.create(hr, vr);
	}
}
