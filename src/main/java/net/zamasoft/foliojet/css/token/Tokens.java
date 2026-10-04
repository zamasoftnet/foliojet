package net.zamasoft.foliojet.css.token;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

import com.helger.css.decl.CSSExpression;
import com.helger.css.decl.CSSExpressionMemberFunction;
import com.helger.css.decl.CSSExpressionMemberLineNames;
import com.helger.css.decl.CSSExpressionMemberMath;
import com.helger.css.decl.CSSExpressionMemberMathProduct;
import com.helger.css.decl.CSSExpressionMemberMathUnitProduct;
import com.helger.css.decl.CSSExpressionMemberMathUnitSimple;
import com.helger.css.decl.CSSExpressionMemberTermSimple;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.ECSSExpressionOperator;
import com.helger.css.decl.ECSSMathOperator;
import com.helger.css.decl.ICSSExpressionMathMember;
import com.helger.css.decl.ICSSExpressionMember;

/**
 * ph-css の式(CSSExpression)を {@link CssToken} 列に変換します。
 */
public final class Tokens {
	private Tokens() {
		// utility
	}

	/**
	 * 関数呼び出しの入れ子段数の上限。{@code fromExpression}/{@code convert}は
	 * 関数の引数を再帰的にトークン化する(ph-cssの式木の構造上、この境界だけは
	 * 既存コードから再帰を使っている——CSS作者が実際に書いた構文の入れ子段数
	 * そのものであり、HTML文書のような外部データ由来の非有界な深さとは性質が
	 * 異なる)。calc()/min()/max()/clamp()の追加で入れ子が実際に踏まれやすく
	 * なったため、明示的な上限で安全弁を設ける(2026-07-19、外部レビューで
	 * 指摘)。
	 */
	private static final int MAX_NESTING_DEPTH = 64;

	/**
	 * 式をトークン列に変換します。空の式は空リストを返します。
	 */
	public static List<CssToken> fromExpression(CSSExpression expression) {
		return fromExpression(expression, 0);
	}

	static List<CssToken> fromExpression(CSSExpression expression, int depth) {
		if (expression == null) {
			return Collections.emptyList();
		}
		if (depth > MAX_NESTING_DEPTH) {
			// 深すぎる入れ子は無視する(安全弁。実用的なCSSでは到達しない)
			return Collections.emptyList();
		}
		List<CssToken> tokens = new ArrayList<CssToken>();
		for (ICSSExpressionMember member : expression.getAllMembers()) {
			CssToken token = convert(member, depth);
			if (token != null) {
				tokens.add(token);
			}
		}
		return tokens;
	}

	private static CssToken convert(ICSSExpressionMember member, int depth) {
		if (member instanceof CSSExpressionMemberTermSimple term) {
			return convertTerm(term);
		}
		if (member instanceof CSSExpressionMemberTermURI uri) {
			return new CssToken.Uri(uri.getURIString());
		}
		if (member instanceof CSSExpressionMemberFunction function) {
			return new CssToken.Func(function.getFunctionName(), fromExpression(function.getExpression(), depth + 1));
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
		if (member instanceof CSSExpressionMemberLineNames lineNames) {
			// Gridの行名[a b](2026-08-29。従来は未知メンバーとして捨てていた
			// ——CssToken.LineNamesのjavadoc参照)
			return new CssToken.LineNames(List.copyOf(lineNames.getAllMembers()));
		}
		if (member instanceof CSSExpressionMemberMath math) {
			List<CssToken> rpn = convertCalc(math, depth);
			// 解釈できない項を含む場合はcalc()全体を無効値として扱う(CssToken.Func自体は
			// 返すが空引数にし、CalcValueUtils側で「評価不能」として処理する)
			return new CssToken.Func("calc", rpn != null ? rpn : Collections.emptyList());
		}
		// 未知のメンバーは無視する
		return null;
	}

	/**
	 * calc() の数式木(ph-cssの{@link CSSExpressionMemberMath})を、逆ポーランド記法
	 * (RPN)の{@link CssToken}列に変換します。
	 * <p>
	 * {@link CSSExpressionMemberMath}(和のレベル)・{@link CSSExpressionMemberMathProduct}
	 * (積のレベル)・{@link CSSExpressionMemberMathUnitProduct}(丸括弧で明示的に
	 * グループ化された積)・入れ子の{@link CSSExpressionMemberMath}(ネストしたcalc())は
	 * いずれも「[被演算項, 演算子, 被演算項, ...]」という平坦なメンバー列を持つ点で
	 * 同型であるため、単一の反復ループ(明示的スタック上の{@link Frame})で
	 * 中置記法から後置記法への変換ができる(このメソッド自身の呼び出し中に
	 * 再帰は使わない。{@link Frame}がJavaの呼び出しスタックの代わりを果たす)。
	 * 関数呼び出し(var()・min()等)がcalc()の項として現れる場合は、その関数自体を
	 * 1つの不透明な項(CssToken.Func)として葉ノード扱いする(関数の引数自体の変換は
	 * 既存の{@link #fromExpression}に委譲する——通常の関数引数はcalc()のような
	 * 深い数式木にはならないため、既存コードの再帰は変更しない)。
	 * </p>
	 *
	 * @return 変換に失敗した場合(未知の項がある場合)はnull
	 */
	private static List<CssToken> convertCalc(CSSExpressionMemberMath math, int depth) {
		Deque<Frame> stack = new ArrayDeque<Frame>();
		stack.push(new Frame(math.getAllMembers().iterator()));
		while (true) {
			Frame frame = stack.peek();
			if (!frame.it.hasNext()) {
				List<CssToken> result = frame.out;
				stack.pop();
				if (stack.isEmpty()) {
					return result;
				}
				receiveOperand(stack.peek(), result);
				continue;
			}
			ICSSExpressionMathMember member = frame.it.next();
			if (member instanceof ECSSMathOperator op) {
				CssToken.Op mapped;
				switch (op) {
				case PLUS:
					mapped = CssToken.Op.PLUS;
					break;
				case MINUS:
					mapped = CssToken.Op.MINUS;
					break;
				case MULTIPLY:
					mapped = CssToken.Op.TIMES;
					break;
				case DIVIDE:
					mapped = CssToken.Op.SLASH;
					break;
				default:
					return null;
				}
				frame.pendingOp = mapped;
				continue;
			}
			if (member instanceof CSSExpressionMemberMathProduct product) {
				stack.push(new Frame(product.getAllMembers().iterator()));
				continue;
			}
			if (member instanceof CSSExpressionMemberMathUnitProduct paren) {
				stack.push(new Frame(paren.getProduct().getAllMembers().iterator()));
				continue;
			}
			if (member instanceof CSSExpressionMemberMath nestedMath) {
				stack.push(new Frame(nestedMath.getAllMembers().iterator()));
				continue;
			}
			CssToken leaf = convertMathLeaf(member, depth);
			if (leaf == null) {
				return null;
			}
			receiveOperand(frame, java.util.Collections.singletonList(leaf));
		}
	}

	/** {@link #convertCalc}が使う、明示的スタック上の1フレーム(再帰呼び出し1段分に相当)。 */
	private static final class Frame {
		final Iterator<? extends ICSSExpressionMathMember> it;
		final List<CssToken> out = new ArrayList<CssToken>();
		CssToken.Op pendingOp;

		Frame(Iterator<? extends ICSSExpressionMathMember> it) {
			this.it = it;
		}
	}

	/** 子フレームで完成した1つの被演算項の後置記法列を、親フレームの出力へ合流させる。 */
	private static void receiveOperand(Frame frame, List<CssToken> operandRpn) {
		frame.out.addAll(operandRpn);
		if (frame.pendingOp != null) {
			frame.out.add(frame.pendingOp);
			frame.pendingOp = null;
		}
	}

	/** calc()数式木の葉(数値項または関数呼び出し項)を{@link CssToken}に変換する。 */
	private static CssToken convertMathLeaf(ICSSExpressionMathMember member, int depth) {
		if (member instanceof CSSExpressionMemberMathUnitSimple simple) {
			return parseNumber(simple.getText().trim());
		}
		if (member instanceof CSSExpressionMemberFunction function) {
			return new CssToken.Func(function.getFunctionName(), fromExpression(function.getExpression(), depth + 1));
		}
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

	/**
	 * 16進の色。3桁・6桁に加えて、CSS Color 4の<b>4桁・8桁(透明度つき)</b>
	 * を受ける(2026-08-29)。{@code #RRGGBBAA}は実サイトで広く使われ、
	 * 落とすと背景色・文字色の宣言が丸ごと無効になっていた。
	 */
	private static CssToken parseHexColor(String value) {
		final int[] rgba = hexOctets(value.substring(1));
		if (rgba == null) {
			return null;
		}
		if (rgba[3] >= 0) {
			// 実数表記のアルファは0〜1として読まれる(ColorValueUtils.toColorComponent)
			return new CssToken.Func("rgba", List.of(
					new CssToken.Num(rgba[0], true),
					new CssToken.Num(rgba[1], true),
					new CssToken.Num(rgba[2], true),
					new CssToken.Num(rgba[3] / 255.0, false)));
		}
		return new CssToken.Func("rgb", List.of(
				new CssToken.Num(rgba[0], true),
				new CssToken.Num(rgba[1], true),
				new CssToken.Num(rgba[2], true)));
	}

	/**
	 * 16進の色の桁(# を除いた 3・4・6・8 桁)を 0〜255 の {r, g, b, a} にします。透明度の桁が無ければ a は -1、
	 * 桁数か字が違えば null。3・4 桁は各桁を 17 倍する(#abc = #aabbcc)。
	 *
	 * <p>
	 * CSS の字句とHTMLの属性値({@code bgcolor} など、{@code ColorValueUtils.parseRGBHexColor})の唯一の定義
	 * (2026-10-04。属性値の側は 3 桁を 17 倍せず、{@code bgcolor="#fff"} がほぼ黒になっていた)。
	 * </p>
	 */
	public static int[] hexOctets(final String hex) {
		final int n = hex.length();
		final int width = n == 3 || n == 4 ? 1 : n == 6 || n == 8 ? 2 : 0;
		if (width == 0) {
			return null;
		}
		final int[] rgba = { 0, 0, 0, -1 };
		for (int i = 0; i * width < n; ++i) {
			int v = 0;
			for (int j = 0; j < width; ++j) {
				final char c = hex.charAt(i * width + j);
				if (!HexFormat.isHexDigit(c)) {
					return null;
				}
				v = v * 16 + HexFormat.fromHexDigit(c);
			}
			rgba[i] = width == 1 ? v * 17 : v;
		}
		return rgba;
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
		return unescape(value);
	}

	/**
	 * CSS のエスケープを解きます(CSS Syntax の「エスケープされた符号位置」)。16 進は最大 6 桁で、続く空白 1 つを区切り
	 * として消費し、0・サロゲート・範囲外は U+FFFD にする。ほかの字は {@code \} を外してそのまま。
	 *
	 * <p>
	 * 文字列・識別子・セレクタの唯一の定義(2026-10-04。3 か所の写しが範囲外の値・エスケープの後ろの空白を別々に扱い、
	 * {@code "\FFFFFF"} やセレクタの {@code .\110000} は例外になっていた)。
	 * </p>
	 */
	public static String unescape(final String s) {
		if (s.indexOf('\\') < 0) {
			return s;
		}
		final int len = s.length();
		final StringBuilder buf = new StringBuilder(len);
		for (int i = 0; i < len; ++i) {
			final char c = s.charAt(i);
			if (c != '\\' || i + 1 >= len) {
				buf.append(c);
				continue;
			}
			int end = i + 1;
			while (end < len && end - i <= 6 && HexFormat.isHexDigit(s.charAt(end))) {
				++end;
			}
			if (end == i + 1) {
				buf.append(s.charAt(++i));
				continue;
			}
			final int codePoint = Integer.parseInt(s.substring(i + 1, end), 16);
			buf.appendCodePoint(codePoint == 0 || codePoint > Character.MAX_CODE_POINT
					|| codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE ? 0xFFFD
							: codePoint);
			if (end < len && isWhiteSpace(s.charAt(end))) {
				++end;
			}
			i = end - 1;
		}
		return buf.toString();
	}

	/** CSS の空白(改行の正規化の前の CR・FF を含む)です。 */
	private static boolean isWhiteSpace(final char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
	}
}
