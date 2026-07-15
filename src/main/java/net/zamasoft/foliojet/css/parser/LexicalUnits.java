package net.zamasoft.foliojet.css.parser;

import java.util.Locale;

import com.helger.css.decl.CSSExpression;
import com.helger.css.decl.CSSExpressionMemberFunction;
import com.helger.css.decl.CSSExpressionMemberMath;
import com.helger.css.decl.CSSExpressionMemberTermSimple;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.ECSSExpressionOperator;
import com.helger.css.decl.ICSSExpressionMember;
import com.helger.css.writer.CSSWriterSettings;

/**
 * ph-css の式(CSSExpression)を内部 LexicalUnit 連鎖に変換します。
 */
public final class LexicalUnits {
	private static final CSSWriterSettings WRITER_SETTINGS = new CSSWriterSettings();

	private LexicalUnits() {
		// utility
	}

	/**
	 * 式を LexicalUnit 連鎖に変換します。空の式は null を返します。
	 */
	public static LexicalUnit fromExpression(CSSExpression expression) {
		if (expression == null) {
			return null;
		}
		LexicalUnitImpl head = null, tail = null;
		for (ICSSExpressionMember member : expression.getAllMembers()) {
			LexicalUnitImpl lu = convert(member);
			if (lu == null) {
				continue;
			}
			if (head == null) {
				head = tail = lu;
			} else {
				tail = tail.append(lu);
			}
		}
		return head;
	}

	private static LexicalUnitImpl convert(ICSSExpressionMember member) {
		if (member instanceof CSSExpressionMemberTermSimple) {
			return convertTerm((CSSExpressionMemberTermSimple) member);
		}
		if (member instanceof CSSExpressionMemberTermURI) {
			return LexicalUnitImpl.string(LexicalUnit.SAC_URI,
					((CSSExpressionMemberTermURI) member).getURIString());
		}
		if (member instanceof CSSExpressionMemberFunction) {
			return convertFunction((CSSExpressionMemberFunction) member);
		}
		if (member instanceof ECSSExpressionOperator) {
			switch ((ECSSExpressionOperator) member) {
			case COMMA:
				return new LexicalUnitImpl(LexicalUnit.SAC_OPERATOR_COMMA);
			case SLASH:
				return new LexicalUnitImpl(LexicalUnit.SAC_OPERATOR_SLASH);
			default:
				return LexicalUnitImpl.string(LexicalUnit.SAC_IDENT, "=");
			}
		}
		if (member instanceof CSSExpressionMemberMath) {
			// calc() 等の数式は汎用関数として渡す(未対応プロパティでは無効値として扱われる)
			LexicalUnitImpl param = LexicalUnitImpl.string(LexicalUnit.SAC_IDENT,
					((CSSExpressionMemberMath) member).getAsCSSString(WRITER_SETTINGS, 0));
			return LexicalUnitImpl.function(LexicalUnit.SAC_FUNCTION, "calc", param);
		}
		// 未知のメンバーは無視する
		return null;
	}

	private static LexicalUnitImpl convertTerm(CSSExpressionMemberTermSimple term) {
		String value = term.getValue().trim();
		if (term.isStringLiteral()) {
			return LexicalUnitImpl.string(LexicalUnit.SAC_STRING_VALUE, unquote(value));
		}
		if (value.isEmpty()) {
			return null;
		}
		if (value.equalsIgnoreCase("inherit")) {
			return new LexicalUnitImpl(LexicalUnit.SAC_INHERIT);
		}
		if (value.charAt(0) == '#') {
			LexicalUnitImpl color = parseHexColor(value);
			if (color != null) {
				return color;
			}
			return LexicalUnitImpl.string(LexicalUnit.SAC_IDENT, value);
		}
		LexicalUnitImpl number = parseNumber(value);
		if (number != null) {
			return number;
		}
		if (value.length() > 2 && (value.charAt(0) == 'U' || value.charAt(0) == 'u') && value.charAt(1) == '+') {
			return LexicalUnitImpl.string(LexicalUnit.SAC_UNICODERANGE, value);
		}
		return LexicalUnitImpl.string(LexicalUnit.SAC_IDENT, value);
	}

	private static LexicalUnitImpl convertFunction(CSSExpressionMemberFunction function) {
		String name = function.getFunctionName();
		LexicalUnit params = fromExpression(function.getExpression());
		String lower = name.toLowerCase(Locale.ROOT);
		switch (lower) {
		case "rgb":
			return LexicalUnitImpl.function(LexicalUnit.SAC_RGBCOLOR, name, params);
		case "counter":
			return LexicalUnitImpl.function(LexicalUnit.SAC_COUNTER_FUNCTION, name, params);
		case "counters":
			return LexicalUnitImpl.function(LexicalUnit.SAC_COUNTERS_FUNCTION, name, params);
		case "rect":
			return LexicalUnitImpl.function(LexicalUnit.SAC_RECT_FUNCTION, name, params);
		case "attr": {
			String attrName = params != null && params.getLexicalUnitType() == LexicalUnit.SAC_IDENT
					? params.getStringValue()
					: (params != null ? params.getStringValue() : null);
			return LexicalUnitImpl.string(LexicalUnit.SAC_ATTR, attrName);
		}
		default:
			return LexicalUnitImpl.function(LexicalUnit.SAC_FUNCTION, name, params);
		}
	}

	private static LexicalUnitImpl parseHexColor(String value) {
		String hex = value.substring(1);
		int r, g, b;
		try {
			if (hex.length() == 3) {
				r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
				g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
				b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
			} else if (hex.length() == 6) {
				r = Integer.parseInt(hex.substring(0, 2), 16);
				g = Integer.parseInt(hex.substring(2, 4), 16);
				b = Integer.parseInt(hex.substring(4, 6), 16);
			} else {
				return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
		LexicalUnitImpl red = LexicalUnitImpl.number(LexicalUnit.SAC_INTEGER, r, null);
		red.append(new LexicalUnitImpl(LexicalUnit.SAC_OPERATOR_COMMA))
				.append(LexicalUnitImpl.number(LexicalUnit.SAC_INTEGER, g, null))
				.append(new LexicalUnitImpl(LexicalUnit.SAC_OPERATOR_COMMA))
				.append(LexicalUnitImpl.number(LexicalUnit.SAC_INTEGER, b, null));
		return LexicalUnitImpl.function(LexicalUnit.SAC_RGBCOLOR, "rgb", red);
	}

	private static LexicalUnitImpl parseNumber(String value) {
		int unitStart = value.length();
		boolean digit = false, dot = false;
		for (int i = 0; i < value.length(); ++i) {
			char c = value.charAt(i);
			if ((c == '+' || c == '-') && i == 0) {
				continue;
			}
			if (c >= '0' && c <= '9') {
				digit = true;
				continue;
			}
			if (c == '.' && !dot) {
				dot = true;
				continue;
			}
			unitStart = i;
			break;
		}
		if (!digit) {
			return null;
		}
		String numberPart = value.substring(0, unitStart);
		String unit = value.substring(unitStart).toLowerCase(Locale.ROOT);
		final float number;
		try {
			number = Float.parseFloat(numberPart);
		} catch (NumberFormatException e) {
			return null;
		}
		switch (unit) {
		case "":
			return LexicalUnitImpl.number(dot ? LexicalUnit.SAC_REAL : LexicalUnit.SAC_INTEGER, number, null);
		case "%":
			return LexicalUnitImpl.number(LexicalUnit.SAC_PERCENTAGE, number, "%");
		case "em":
			return LexicalUnitImpl.number(LexicalUnit.SAC_EM, number, unit);
		case "ex":
			return LexicalUnitImpl.number(LexicalUnit.SAC_EX, number, unit);
		case "rem":
			return LexicalUnitImpl.number(LexicalUnit.SAC_REM, number, unit);
		case "ch":
			return LexicalUnitImpl.number(LexicalUnit.SAC_CH, number, unit);
		case "px":
			return LexicalUnitImpl.number(LexicalUnit.SAC_PIXEL, number, unit);
		case "in":
			return LexicalUnitImpl.number(LexicalUnit.SAC_INCH, number, unit);
		case "cm":
			return LexicalUnitImpl.number(LexicalUnit.SAC_CENTIMETER, number, unit);
		case "mm":
			return LexicalUnitImpl.number(LexicalUnit.SAC_MILLIMETER, number, unit);
		case "pt":
			return LexicalUnitImpl.number(LexicalUnit.SAC_POINT, number, unit);
		case "pc":
			return LexicalUnitImpl.number(LexicalUnit.SAC_PICA, number, unit);
		case "deg":
			return LexicalUnitImpl.number(LexicalUnit.SAC_DEGREE, number, unit);
		case "grad":
			return LexicalUnitImpl.number(LexicalUnit.SAC_GRADIAN, number, unit);
		case "rad":
			return LexicalUnitImpl.number(LexicalUnit.SAC_RADIAN, number, unit);
		case "ms":
			return LexicalUnitImpl.number(LexicalUnit.SAC_MILLISECOND, number, unit);
		case "s":
			return LexicalUnitImpl.number(LexicalUnit.SAC_SECOND, number, unit);
		case "hz":
			return LexicalUnitImpl.number(LexicalUnit.SAC_HERTZ, number, unit);
		case "khz":
			return LexicalUnitImpl.number(LexicalUnit.SAC_KILOHERTZ, number, unit);
		default:
			// 単位がCSS識別子でない場合は数値として扱わない
			for (int i = 0; i < unit.length(); ++i) {
				char c = unit.charAt(i);
				if ((c < 'a' || c > 'z') && c != '-' && c != '_' && (c < '0' || c > '9')) {
					return null;
				}
			}
			return LexicalUnitImpl.number(LexicalUnit.SAC_DIMENSION, number, unit);
		}
	}

	private static String unquote(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
				value = value.substring(1, value.length() - 1);
			}
		}
		if (value.indexOf('\\') == -1) {
			return value;
		}
		StringBuilder buff = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); ++i) {
			char c = value.charAt(i);
			if (c != '\\' || i + 1 >= value.length()) {
				buff.append(c);
				continue;
			}
			char next = value.charAt(i + 1);
			if (isHexDigit(next)) {
				// CSSの16進エスケープ(最大6桁+空白1つ)
				int end = i + 1;
				while (end < value.length() && end - i <= 6 && isHexDigit(value.charAt(end))) {
					++end;
				}
				buff.appendCodePoint(Integer.parseInt(value.substring(i + 1, end), 16));
				if (end < value.length() && value.charAt(end) == ' ') {
					++end;
				}
				i = end - 1;
			} else {
				buff.append(next);
				++i;
			}
		}
		return buff.toString();
	}

	private static boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}
}
