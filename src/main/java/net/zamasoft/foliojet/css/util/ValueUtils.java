package net.zamasoft.foliojet.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.EmLengthValue;
import net.zamasoft.foliojet.css.value.ExLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.CSS3Value;
import net.zamasoft.foliojet.css.value.css3.ChLengthValue;
import net.zamasoft.foliojet.css.value.css3.RemLengthValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ValueUtils.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public final class ValueUtils {
	private ValueUtils() {
		// unused
	}

	/**
	 * 識別子キーワード(大文字小文字無視)であれば true を返します。
	 */
	public static boolean isKeyword(CssToken token, String keyword) {
		return token instanceof CssToken.Ident ident && ident.is(keyword);
	}

	/**
	 * auto であればtrueを返します。
	 */
	public static boolean isAuto(CssToken token) {
		return isKeyword(token, "auto");
	}

	/**
	 * none であればtrueを返します。
	 */
	public static boolean isNone(CssToken token) {
		return isKeyword(token, "none");
	}

	/**
	 * normal であればtrueを返します。
	 */
	public static boolean isNormal(CssToken token) {
		return isKeyword(token, "normal");
	}

	/**
	 * &lt;length&gt; を値に変換します。
	 */
	public static LengthValue toLength(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case EM:
				return EmLengthValue.create(dim.value());
			case EX:
				return ExLengthValue.create(dim.value());
			case REM:
				return RemLengthValue.create(dim.value());
			case CH:
				return ChLengthValue.create(dim.value());
			default:
				break;
			}
		}
		return toAbsoluteLength(ua, token);
	}

	/**
	 * 文字列表現を長さに変換します。
	 */
	public static LengthValue toLength(UserAgent ua, boolean legacy, String s) {
		try {
			s = s.toLowerCase().trim();
			if (s.endsWith("em")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return EmLengthValue.create(len);
			} else if (s.endsWith("ex")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return ExLengthValue.create(len);
			} else if (s.endsWith("ch")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return ChLengthValue.create(len);
			} else if (s.endsWith("rem")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 3));
				return RemLengthValue.create(len);
			} else {
				return toAbsoluteLength(ua, legacy, s);
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 文字列表現を長さに変換します。
	 */
	public static AbsoluteLengthValue toAbsoluteLength(UserAgent ua, boolean legacy, String s) {
		if (s == null) {
			return null;
		}
		s = s.toLowerCase().trim();
		try {
			if (s.endsWith("mm")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_MM);
			} else if (s.endsWith("cm")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_CM);
			} else if (s.endsWith("pt")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_PT);
			} else if (s.endsWith("px")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_PX);
			} else if (s.endsWith("pc")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_PC);
			} else if (s.endsWith("in")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_IN);
			} else {
				double len = NumberUtils.parseDouble(s);
				if (len == 0) {
					return AbsoluteLengthValue.ZERO;
				}
				if (legacy) {
					return AbsoluteLengthValue.create(ua, len, LengthValue.UNIT_PX);
				}
				return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * valueがEM_LENGTHかEX_LENGTHならstyleのフォント情報を基準に絶対長さに変換します。
	 */
	public static Value emExToAbsoluteLength(Value value, CSSStyle style) {
		switch (value.getValueType()) {
		case Value.TYPE_EM_LENGTH:
			EmLengthValue em = (EmLengthValue) value;
			value = em.toAbsoluteLength(style);
			break;

		case Value.TYPE_EX_LENGTH:
			ExLengthValue ex = (ExLengthValue) value;
			value = ex.toAbsoluteLength(style);
			break;

		case CSS3Value.TYPE_REM_LENGTH:
			RemLengthValue rem = (RemLengthValue) value;
			value = rem.toAbsoluteLength(style);
			break;

		case CSS3Value.TYPE_CH_LENGTH:
			ChLengthValue ch = (ChLengthValue) value;
			value = ch.toAbsoluteLength(style);
			break;
		}
		return value;
	}

	/**
	 * フォント相対長さ以外の &lt;length&gt; を値に変換します。
	 */
	public static AbsoluteLengthValue toAbsoluteLength(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Dim dim) {
			final short unit;
			switch (dim.unit()) {
			case IN:
				unit = LengthValue.UNIT_IN;
				break;
			case CM:
				unit = LengthValue.UNIT_CM;
				break;
			case MM:
				unit = LengthValue.UNIT_MM;
				break;
			case PT:
				unit = LengthValue.UNIT_PT;
				break;
			case PC:
				unit = LengthValue.UNIT_PC;
				break;
			case PX:
				unit = LengthValue.UNIT_PX;
				break;
			default:
				return null;
			}
			return AbsoluteLengthValue.create(ua, dim.value(), unit);
		}
		if (token instanceof CssToken.Num num && num.value() == 0) {
			return AbsoluteLengthValue.create(ua, 0, LengthValue.UNIT_PX);
		}
		return null;
	}

	/**
	 * &lt;percentage&gt; を値に変換します。
	 */
	public static PercentageValue toPercentage(CssToken token) {
		if (token instanceof CssToken.Percent percent) {
			return PercentageValue.create(percent.value());
		}
		return null;
	}

	/**
	 * &lt;number&gt; を値に変換します。
	 */
	public static RealValue toReal(CssToken token) {
		if (token instanceof CssToken.Num num) {
			return RealValue.create(num.value());
		}
		return null;
	}

	/**
	 * &lt;uri&gt; を値に変換します。
	 */
	public static URIValue toURI(UserAgent ua, URI baseURI, CssToken token) throws URISyntaxException {
		if (token instanceof CssToken.Uri uri) {
			return URIUtils.createURIValue(ua.getDocumentContext().getEncoding(), baseURI, uri.uri());
		}
		return null;
	}
}
