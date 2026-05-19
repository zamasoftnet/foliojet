package net.zamasoft.foliojet.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
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
import net.zamasoft.foliojet.css.parser.LexicalUnit;
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
	 * auto であればtrueを返します。
	 * 
	 * @param lu
	 * @return
	 */
	public static boolean isAuto(LexicalUnit lu) {
		return (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT && lu.getStringValue().equalsIgnoreCase("auto"));
	}

	/**
	 * none であればtrueを返します。
	 * 
	 * @param lu
	 * @return
	 */
	public static boolean isNone(LexicalUnit lu) {
		return (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT && lu.getStringValue().equalsIgnoreCase("none"));
	}

	/**
	 * normal であればtrueを返します。
	 * 
	 * @param lu
	 * @return
	 */
	public static boolean isNormal(LexicalUnit lu) {
		return (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT && lu.getStringValue().equalsIgnoreCase("normal"));
	}

	/**
	 * &lt;length&gt; を値に変換します。
	 * 
	 * @param ua
	 * @param lu
	 * @return
	 */
	public static LengthValue toLength(UserAgent ua, LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_EM:
			return EmLengthValue.create(lu.getFloatValue());

		case LexicalUnit.SAC_EX:
			return ExLengthValue.create(lu.getFloatValue());

		case LexicalUnit.SAC_REM:
			return RemLengthValue.create(lu.getFloatValue());

		case LexicalUnit.SAC_CH:
			return ChLengthValue.create(lu.getFloatValue());

		default:
			return toAbsoluteLength(ua, lu);
		}
	}

	/**
	 * 文字列表現を長さに変換します。
	 * 
	 * @param ua
	 * @param legacy
	 * @param s
	 * @return
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
	 * 
	 * @param ua
	 * @param legacy
	 * @param s
	 * @return
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
	 * 
	 * @param value
	 * @param style
	 * @return
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
	 * 
	 * @param ua
	 * @param lu
	 * @return
	 */
	public static AbsoluteLengthValue toAbsoluteLength(UserAgent ua, LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_INCH:
		case LexicalUnit.SAC_CENTIMETER:
		case LexicalUnit.SAC_MILLIMETER:
		case LexicalUnit.SAC_POINT:
		case LexicalUnit.SAC_PICA:
		case LexicalUnit.SAC_PIXEL:
			return AbsoluteLengthValue.create(ua, lu.getFloatValue(), luType);

		case LexicalUnit.SAC_INTEGER: {
			int val = lu.getIntegerValue();
			if (val == 0) {
				return AbsoluteLengthValue.create(ua, val, LengthValue.UNIT_PX);
			}
			return null;
		}

		case LexicalUnit.SAC_REAL: {
			double val = lu.getFloatValue();
			if (val == 0) {
				return AbsoluteLengthValue.create(ua, val, LengthValue.UNIT_PX);
			}
			return null;
		}

		default:
			return null;
		}
	}

	/**
	 * &lt;percentage&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static PercentageValue toPercentage(LexicalUnit lu) {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_PERCENTAGE) {
			return PercentageValue.create(lu.getFloatValue());
		}
		return null;

	}

	/**
	 * &lt;number&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static RealValue toReal(LexicalUnit lu) {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_REAL) {
			return RealValue.create(lu.getFloatValue());
		} else if (lu.getLexicalUnitType() == LexicalUnit.SAC_INTEGER) {
			return RealValue.create(lu.getIntegerValue());
		} else {
			return null;
		}
	}

	/**
	 * &lt;uri&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static URIValue toURI(UserAgent ua, URI baseURI, LexicalUnit lu) throws URISyntaxException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_URI) {
			String href = lu.getStringValue();
			return URIUtils.createURIValue(ua.getDocumentContext().getEncoding(), baseURI, href);
		}
		return null;

	}
}