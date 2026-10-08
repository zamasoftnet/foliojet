package net.zamasoft.foliojet.css.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Rewrites the at-rule preludes that ph-css 8.2.1 cannot read into forms it can, before the stylesheet is parsed
 * (2026-10-09). ph-css's error recovery does not stop at the broken at-rule: inside an {@code @media} or
 * {@code @layer} block it drops every rule of that block, before and after, and after a broken statement it drops
 * the rule that follows.
 *
 * <ul>
 * <li>{@code @supports} conditions {@code selector()}, {@code font-tech()} and {@code font-format()} become
 * {@code (-foliojet-supports:1)} or {@code (-foliojet-supports:0)} ({@link #MARKER}), which
 * {@code CSSStyleSheetBuilder} evaluates to true or false. {@code selector(<complex-selector>)} is true when the
 * selector parses and every part of it is known (CSS Conditional 4: non-forgiving parsing, one complex selector);
 * {@code font-tech()} and {@code font-format()} are false (an unknown condition). ja.wikipedia's Vector skin starts an
 * {@code @media screen} block with {@code @supports not selector(:focus-visible)}, so the rule that hides the menus was
 * lost and every dropdown printed open.</li>
 * <li>{@code @layer} names: ph-css has no grammar for the dotted names of nested layers ({@code @layer a.b {...}},
 * {@code @layer a.b, c.d;}). The dots become {@code \.}, which ph-css reads as part of one identifier;
 * {@link #layerNamePath} splits the name there again. daisyui's utilities ({@code @layer daisyui.l1.l2}) and
 * Docusaurus's theme ({@code @layer docusaurus.theme-classic}) were lost, with the outer layer around them. An escaped
 * dot written in the source ({@code a\.b}) is read as a separator too.</li>
 * </ul>
 *
 * <p>
 * Strings, comments, escapes and everything outside these preludes are copied unchanged.
 * </p>
 */
public final class AtRulePreludeRewriter {
	/** The property name of the replacement condition for {@code @supports} functions. */
	public static final String MARKER = "-foliojet-supports";

	private AtRulePreludeRewriter() {
		// utility
	}

	/**
	 * Rewrites every {@code @supports} and {@code @layer} prelude.
	 *
	 * @param css               a stylesheet
	 * @param selectorSupported tells whether one complex selector (the argument of {@code selector()}, trimmed) is
	 *                          supported
	 * @return the stylesheet, the same instance when nothing changes
	 */
	public static String rewrite(final String css, final Predicate<String> selectorSupported) {
		if (css.indexOf('@') == -1) {
			return css;
		}
		final String lower = css.toLowerCase(Locale.ROOT);
		if (!lower.contains("@supports") && !lower.contains("@layer")) {
			return css;
		}
		final StringBuilder out = new StringBuilder(css.length());
		final int n = css.length();
		int i = 0;
		boolean changed = false;
		while (i < n) {
			final char c = css.charAt(i);
			if (c == '/' && i + 1 < n && css.charAt(i + 1) == '*') {
				final int end = commentEnd(css, i);
				out.append(css, i, end);
				i = end;
			} else if (c == '"' || c == '\'') {
				final int end = stringEnd(css, i);
				out.append(css, i, end);
				i = end;
			} else if (c == '\\') {
				final int end = Math.min(n, i + 2);
				out.append(css, i, end);
				i = end;
			} else if (c == '@' && atKeyword(css, i, "supports")) {
				out.append(css, i, i + 9);
				i += 9;
				final int end = preludeEnd(css, i);
				final String prelude = css.substring(i, end);
				final String rewritten = rewriteSupports(prelude, selectorSupported);
				changed |= !rewritten.equals(prelude);
				out.append(rewritten);
				i = end;
			} else if (c == '@' && atKeyword(css, i, "layer")) {
				out.append(css, i, i + 6);
				i += 6;
				final int end = preludeEnd(css, i);
				final String prelude = css.substring(i, end);
				final String rewritten = rewriteLayer(prelude);
				changed |= !rewritten.equals(prelude);
				out.append(rewritten);
				i = end;
			} else {
				out.append(c);
				++i;
			}
		}
		return changed ? out.toString() : css;
	}

	/**
	 * Splits a layer name as ph-css returns it (raw, after {@link #rewrite}) into the names of the layer and its
	 * ancestors, outermost first, with CSS escapes resolved: {@code "a\.b\.c"} gives {@code [a, b, c]}.
	 */
	public static List<String> layerNamePath(final String raw) {
		final List<String> path = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < raw.length(); ++i) {
			if (raw.charAt(i) == '\\' && i + 1 < raw.length()) {
				if (raw.charAt(i + 1) == '.') {
					path.add(unescape(raw.substring(start, i).trim()));
					start = i + 2;
				}
				++i;
			}
		}
		path.add(unescape(raw.substring(start).trim()));
		return path;
	}

	/** Resolves CSS escapes ({@code \41 }, {@code \.}) in an identifier. */
	static String unescape(final String s) {
		if (s.indexOf('\\') == -1) {
			return s;
		}
		final StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); ++i) {
			final char c = s.charAt(i);
			if (c != '\\' || i + 1 >= s.length()) {
				out.append(c);
				continue;
			}
			int j = i + 1;
			while (j < s.length() && j < i + 7 && Character.digit(s.charAt(j), 16) != -1) {
				++j;
			}
			if (j > i + 1) {
				final int cp = Integer.parseInt(s.substring(i + 1, j), 16);
				out.appendCodePoint(cp == 0 || cp > Character.MAX_CODE_POINT || (cp >= 0xD800 && cp <= 0xDFFF)
						? 0xFFFD : cp);
				if (j < s.length() && Character.isWhitespace(s.charAt(j))) {
					++j;
				}
				i = j - 1;
			} else {
				out.append(s.charAt(i + 1));
				++i;
			}
		}
		return out.toString();
	}

	private static boolean atKeyword(final String css, final int at, final String name) {
		final int end = at + 1 + name.length();
		return css.regionMatches(true, at + 1, name, 0, name.length())
				&& (end >= css.length() || !isNameChar(css.charAt(end)));
	}

	/** Rewrites the function conditions of one {@code @supports} prelude. */
	private static String rewriteSupports(final String prelude, final Predicate<String> selectorSupported) {
		final StringBuilder out = new StringBuilder(prelude.length());
		final int n = prelude.length();
		int i = 0;
		while (i < n) {
			final char c = prelude.charAt(i);
			if (c == '/' && i + 1 < n && prelude.charAt(i + 1) == '*') {
				final int end = commentEnd(prelude, i);
				out.append(prelude, i, end);
				i = end;
				continue;
			}
			if (c == '"' || c == '\'') {
				final int end = stringEnd(prelude, i);
				out.append(prelude, i, end);
				i = end;
				continue;
			}
			if (isNameStart(c) && (i == 0 || !isNameChar(prelude.charAt(i - 1)))) {
				int j = i;
				while (j < n && isNameChar(prelude.charAt(j))) {
					++j;
				}
				final String name = prelude.substring(i, j).toLowerCase(Locale.ROOT);
				if (j < n && prelude.charAt(j) == '('
						&& (name.equals("selector") || name.equals("font-tech") || name.equals("font-format"))) {
					final int close = closingParen(prelude, j);
					final String argument = prelude.substring(j + 1, Math.max(j + 1, close - 1)).trim();
					final boolean value = name.equals("selector") && !argument.isEmpty()
							&& !hasTopLevelComma(argument) && selectorSupported.test(argument);
					out.append('(').append(MARKER).append(':').append(value ? '1' : '0').append(')');
					i = close;
					continue;
				}
				out.append(prelude, i, j);
				i = j;
				continue;
			}
			out.append(c);
			++i;
		}
		return out.toString();
	}

	/** Escapes the dots of one {@code @layer} prelude (outside strings, comments and escapes). */
	private static String rewriteLayer(final String prelude) {
		if (prelude.indexOf('.') == -1) {
			return prelude;
		}
		final StringBuilder out = new StringBuilder(prelude.length() + 8);
		final int n = prelude.length();
		int i = 0;
		while (i < n) {
			final char c = prelude.charAt(i);
			if (c == '/' && i + 1 < n && prelude.charAt(i + 1) == '*') {
				final int end = commentEnd(prelude, i);
				out.append(prelude, i, end);
				i = end;
			} else if (c == '"' || c == '\'') {
				final int end = stringEnd(prelude, i);
				out.append(prelude, i, end);
				i = end;
			} else if (c == '\\') {
				final int end = Math.min(n, i + 2);
				out.append(prelude, i, end);
				i = end;
			} else {
				if (c == '.') {
					out.append('\\');
				}
				out.append(c);
				++i;
			}
		}
		return out.toString();
	}

	/** The index after the prelude: the first "{", ";" or "}" outside parentheses, strings and comments. */
	private static int preludeEnd(final String css, int i) {
		final int n = css.length();
		int depth = 0;
		while (i < n) {
			final char c = css.charAt(i);
			if (c == '/' && i + 1 < n && css.charAt(i + 1) == '*') {
				i = commentEnd(css, i);
				continue;
			}
			if (c == '"' || c == '\'') {
				i = stringEnd(css, i);
				continue;
			}
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == '(') {
				++depth;
			} else if (c == ')') {
				if (depth > 0) {
					--depth;
				}
			} else if (depth == 0 && (c == '{' || c == ';' || c == '}')) {
				return i;
			}
			++i;
		}
		return n;
	}

	/** The index after the parenthesis that closes the one at {@code open} (the end when it is not closed). */
	private static int closingParen(final String s, final int open) {
		final int n = s.length();
		int depth = 0;
		int i = open;
		while (i < n) {
			final char c = s.charAt(i);
			if (c == '"' || c == '\'') {
				i = stringEnd(s, i);
				continue;
			}
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == '(') {
				++depth;
			} else if (c == ')' && --depth == 0) {
				return i + 1;
			}
			++i;
		}
		return n;
	}

	private static boolean hasTopLevelComma(final String s) {
		int depth = 0;
		for (int i = 0; i < s.length(); ++i) {
			final char c = s.charAt(i);
			if (c == '"' || c == '\'') {
				i = stringEnd(s, i) - 1;
			} else if (c == '\\') {
				++i;
			} else if (c == '(' || c == '[') {
				++depth;
			} else if (c == ')' || c == ']') {
				--depth;
			} else if (c == ',' && depth == 0) {
				return true;
			}
		}
		return false;
	}

	/** The index after the comment starting at {@code i} (the end when it is not closed). */
	private static int commentEnd(final String s, final int i) {
		final int end = s.indexOf("*/", i + 2);
		return end == -1 ? s.length() : end + 2;
	}

	/** The index after the string starting at {@code i} (a newline or the end also ends it). */
	private static int stringEnd(final String s, final int i) {
		final char quote = s.charAt(i);
		int j = i + 1;
		while (j < s.length()) {
			final char c = s.charAt(j);
			if (c == '\\') {
				j += 2;
				continue;
			}
			if (c == quote) {
				return j + 1;
			}
			if (c == '\n') {
				return j;
			}
			++j;
		}
		return s.length();
	}

	private static boolean isNameStart(final char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '-' || c >= 0x80;
	}

	private static boolean isNameChar(final char c) {
		return isNameStart(c) || (c >= '0' && c <= '9');
	}
}
