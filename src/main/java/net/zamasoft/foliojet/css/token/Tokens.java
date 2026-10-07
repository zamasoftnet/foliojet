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
 * Converts ph-css expressions (CSSExpression) to {@link CssToken} sequences.
 */
public final class Tokens {
	private Tokens() {
		// utility
	}

	/**
	 * Maximum function-call nesting depth. {@code fromExpression}/{@code convert}
	 * recursively tokenize function arguments (the structure of ph-css expression trees
	 * requires the existing recursion at this boundary: this is the syntax nesting depth
	 * actually written by the CSS author, unlike unbounded depth from external data such
	 * as HTML documents). Adding calc()/min()/max()/clamp() made nesting more likely in
	 * practice, so impose an explicit limit as a safeguard (noted in an external review,
	 * 2026-07-19).
	 */
	private static final int MAX_NESTING_DEPTH = 64;

	/**
	 * Converts an expression to tokens. Returns an empty list for an empty expression.
	 */
	public static List<CssToken> fromExpression(CSSExpression expression) {
		return fromExpression(expression, 0);
	}

	static List<CssToken> fromExpression(CSSExpression expression, int depth) {
		if (expression == null) {
			return Collections.emptyList();
		}
		if (depth > MAX_NESTING_DEPTH) {
			// Ignore excessively deep nesting (safeguard; practical CSS does not reach it)
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
			// Grid line names [a b] (2026-08-29; previously discarded as unknown members:
			// see CssToken.LineNames Javadoc)
			return new CssToken.LineNames(List.copyOf(lineNames.getAllMembers()));
		}
		if (member instanceof CSSExpressionMemberMath math) {
			List<CssToken> rpn = convertCalc(math, depth);
			// Treat all of calc() as invalid if any term cannot be interpreted (still return CssToken.Func
			// but with empty arguments, so CalcValueUtils handles it as unevaluable).
			return new CssToken.Func("calc", rpn != null ? rpn : Collections.emptyList());
		}
		// Ignore unknown members
		return null;
	}

	/**
	 * Converts the calc() expression tree (ph-css {@link CSSExpressionMemberMath}) to
	 * {@link CssToken} tokens in reverse Polish notation (RPN).
	 * <p>
	 * {@link CSSExpressionMemberMath} (sum level), {@link CSSExpressionMemberMathProduct}
	 * (product level), {@link CSSExpressionMemberMathUnitProduct} (product explicitly
	 * grouped by parentheses), and nested {@link CSSExpressionMemberMath} (nested calc())
	 * all have the same structure: a flat [operand, operator, operand, ...] member sequence.
	 * A single iterative loop ({@link Frame} on an explicit stack) can therefore convert
	 * infix to postfix notation (no recursion within this method; {@link Frame} replaces
	 * the Java call stack). If a function call (var(), min(), etc.) occurs as a calc() term,
	 * treat the function itself as one opaque leaf (CssToken.Func). Delegate conversion
	 * of its arguments to the existing {@link #fromExpression}: ordinary function arguments
	 * do not form deep expression trees like calc(), so keep the existing recursion.
	 * </p>
	 *
	 * @return null if conversion fails (an unknown term is present)
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

	/** One explicit-stack frame used by {@link #convertCalc} (equivalent to one recursive call level). */
	private static final class Frame {
		final Iterator<? extends ICSSExpressionMathMember> it;
		final List<CssToken> out = new ArrayList<CssToken>();
		CssToken.Op pendingOp;

		Frame(Iterator<? extends ICSSExpressionMathMember> it) {
			this.it = it;
		}
	}

	/** Merges the postfix sequence for one operand completed by a child frame into the parent frame's output. */
	private static void receiveOperand(Frame frame, List<CssToken> operandRpn) {
		frame.out.addAll(operandRpn);
		if (frame.pendingOp != null) {
			frame.out.add(frame.pendingOp);
			frame.pendingOp = null;
		}
	}

	/** Converts a calc() expression-tree leaf (numeric term or function-call term) to {@link CssToken}. */
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
	 * Hex colors. Accepts CSS Color 4 <b>four/eight-digit forms (with alpha)</b> as well as
	 * three/six digits (2026-08-29). {@code #RRGGBBAA} is widely used on real sites;
	 * dropping it previously invalidated entire background/text color declarations.
	 */
	private static CssToken parseHexColor(String value) {
		final int[] rgba = hexOctets(value.substring(1));
		if (rgba == null) {
			return null;
		}
		if (rgba[3] >= 0) {
			// A real-number alpha value is read as 0–1 (ColorValueUtils.toColorComponent)
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
	 * Converts hex color digits (3/4/6/8 digits excluding #) to {r, g, b, a} in 0–255.
	 * Without alpha digits, a is -1; invalid length or characters yield null.
	 * For 3/4 digits, multiply each digit by 17 (#abc = #aabbcc).
	 *
	 * <p>
	 * Single definition for CSS tokens and HTML attribute values ({@code bgcolor}, etc.,
	 * {@code ColorValueUtils.parseRGBHexColor}) (2026-10-04: the attribute path did not
	 * multiply three-digit values by 17, making {@code bgcolor="#fff"} almost black).
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
		// Do not treat as a number if the unit is not a CSS identifier
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
	 * Decodes CSS escapes (CSS Syntax's "escaped code point"). Hex escapes have at most six
	 * digits and consume one following whitespace delimiter; zero, surrogates, and out-of-range
	 * values become U+FFFD. For other characters, simply remove {@code \}.
	 *
	 * <p>
	 * Single definition for strings, identifiers, and selectors (2026-10-04: three copies
	 * handled out-of-range values and whitespace after escapes differently;
	 * {@code "\FFFFFF"} and selector {@code .\110000} threw exceptions).
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

	/** CSS whitespace (including CR/FF before newline normalization). */
	private static boolean isWhiteSpace(final char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
	}
}
