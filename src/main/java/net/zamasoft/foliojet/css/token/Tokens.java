package net.zamasoft.foliojet.css.token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.helger.css.decl.CSSExpression;
import com.helger.css.decl.CSSExpressionMemberFunction;
import com.helger.css.decl.CSSExpressionMemberMath;
import com.helger.css.decl.CSSExpressionMemberTermSimple;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.ECSSExpressionOperator;
import com.helger.css.decl.ICSSExpressionMember;
import com.helger.css.writer.CSSWriterSettings;

/**
 * ph-css の式(CSSExpression)を {@link CssToken} 列に変換します。
 */
public final class Tokens {
	private static final CSSWriterSettings WRITER_SETTINGS = new CSSWriterSettings();

	private Tokens() {
		// utility
	}

	/**
	 * 式をトークン列に変換します。空の式は空リストを返します。
	 */
	public static List<CssToken> fromExpression(CSSExpression expression) {
		if (expression == null) {
			return Collections.emptyList();
		}
		List<CssToken> tokens = new ArrayList<CssToken>();
		for (ICSSExpressionMember member : expression.getAllMembers()) {
			CssToken token = convert(member);
			if (token != null) {
				tokens.add(token);
			}
		}
		return tokens;
	}

	private static CssToken convert(ICSSExpressionMember member) {
		if (member instanceof CSSExpressionMemberTermSimple term) {
			return convertTerm(term);
		}
		if (member instanceof CSSExpressionMemberTermURI uri) {
			return new CssToken.Uri(uri.getURIString());
		}
		if (member instanceof CSSExpressionMemberFunction function) {
			return new CssToken.Func(function.getFunctionName(), fromExpression(function.getExpression()));
		}
		if (member instanceof ECSSExpressionOperator op) {
			switch (op) {
			case COMMA:
				return CssToken.Op.COMMA;
			case SLASH:
				return CssToken.Op.SLASH;
			default:
				return new CssToken.Ident("=");
			}
		}
		if (member instanceof CSSExpressionMemberMath math) {
			// calc() 等の数式は未評価のまま渡す(未対応プロパティでは無効値として扱われる)
			return new CssToken.Func("calc",
					List.of(new CssToken.Str(math.getAsCSSString(WRITER_SETTINGS, 0))));
		}
		// 未知のメンバーは無視する
		return null;
	}

	private static CssToken convertTerm(CSSExpressionMemberTermSimple term) {
		String value = term.getValue().trim();
		if (term.isStringLiteral()) {
			return new CssToken.Str(unquote(value));
		}
		if (value.isEmpty()) {
			return null;
		}
		if (value.equalsIgnoreCase("inherit")) {
			return CssToken.Keyword.INHERIT;
		}
		if (value.equalsIgnoreCase("initial")) {
			return CssToken.Keyword.INITIAL;
		}
		if (value.equalsIgnoreCase("unset")) {
			return CssToken.Keyword.UNSET;
		}
		if (value.charAt(0) == '#') {
			CssToken color = parseHexColor(value);
			if (color != null) {
				return color;
			}
			return new CssToken.Ident(value);
		}
		CssToken number = parseNumber(value);
		if (number != null) {
			return number;
		}
		if (value.length() > 2 && (value.charAt(0) == 'U' || value.charAt(0) == 'u') && value.charAt(1) == '+') {
			return new CssToken.UnicodeRange(value);
		}
		return new CssToken.Ident(value);
	}

	private static CssToken parseHexColor(String value) {
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
		return new CssToken.Func("rgb", List.of(
				new CssToken.Num(r, true),
				new CssToken.Num(g, true),
				new CssToken.Num(b, true)));
	}

	private static CssToken parseNumber(String value) {
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
		final double number;
		try {
			number = Double.parseDouble(value.substring(0, unitStart));
		} catch (NumberFormatException e) {
			return null;
		}
		String unitText = value.substring(unitStart);
		if (unitText.isEmpty()) {
			return new CssToken.Num(number, !dot);
		}
		if (unitText.equals("%")) {
			return new CssToken.Percent(number);
		}
		// 単位がCSS識別子でない場合は数値として扱わない
		for (int i = 0; i < unitText.length(); ++i) {
			char c = Character.toLowerCase(unitText.charAt(i));
			if ((c < 'a' || c > 'z') && c != '-' && c != '_' && (c < '0' || c > '9')) {
				return null;
			}
		}
		return new CssToken.Dim(number, Unit.of(unitText), unitText);
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
