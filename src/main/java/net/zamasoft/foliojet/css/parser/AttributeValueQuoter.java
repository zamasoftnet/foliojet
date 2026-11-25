package net.zamasoft.foliojet.css.parser;

import java.util.Locale;
import java.util.Set;

/**
 * Quotes the unquoted attribute selector values that ph-css 8.2.1 cannot read, before the stylesheet is parsed
 * (2026-11-25). ph-css (8.4.1 too) lexes {@code inherit}, {@code and}, {@code not}, {@code only}, {@code from},
 * {@code to}, {@code or}, {@code true} and {@code false}, in any case, as keyword tokens, and an attribute selector's
 * value must be an identifier or a string: {@code [data-active=false]} was a syntax error, which dropped the rule, and
 * inside {@code @media} let the rules after it out of the block. Minifiers (cssnano) take the quotes off
 * {@code [aria-hidden="true"]}; Le Monde's {@code .ds-popin[data-active=false]{display:none}} left its full-page menu
 * over the article.
 *
 * <p>
 * An identifier and a string are the same value in an attribute selector, so {@code [x=true]} becomes
 * {@code [x="true"]}. Only these nine words are quoted: a stylesheet ph-css could read before is returned unchanged.
 * Strings, comments and escapes are copied as they are.
 * </p>
 */
public final class AttributeValueQuoter {
	/** The identifiers ph-css lexes as keywords (ParserCSS30Constants). */
	private static final Set<String> KEYWORDS = Set.of("inherit", "and", "not", "only", "from", "to", "or", "true",
			"false");

	private AttributeValueQuoter() {
		// utility
	}

	/**
	 * @param css a stylesheet
	 * @return the stylesheet with those values quoted, the same instance when there are none
	 */
	public static String quote(final String css) {
		if (css.indexOf('[') == -1) {
			return css;
		}
		StringBuilder out = null;
		final int n = css.length();
		int copied = 0;
		int i = 0;
		while (i < n) {
			final char c = css.charAt(i);
			if (c == '/' && i + 1 < n && css.charAt(i + 1) == '*') {
				i = AtRulePreludeRewriter.commentEnd(css, i);
			} else if (c == '"' || c == '\'') {
				i = AtRulePreludeRewriter.stringEnd(css, i);
			} else if (c == '\\') {
				i += 2;
			} else if ((c == 'u' || c == 'U') && css.regionMatches(true, i, "url(", 0, 4)
					&& (i == 0 || !AtRulePreludeRewriter.isNameChar(css.charAt(i - 1)))) {
				// An unquoted URL may hold brackets (url(img[x=true].png)); a quoted one is a string
				int j = skipSpaces(css, i + 4);
				if (j < n && (css.charAt(j) == '"' || css.charAt(j) == '\'')) {
					i += 4;
					continue;
				}
				while (j < n && css.charAt(j) != ')') {
					j += css.charAt(j) == '\\' ? 2 : 1;
				}
				i = j + 1;
			} else if (c == '[') {
				final int value = keywordValue(css, i + 1);
				if (value == -1) {
					++i;
					continue;
				}
				int end = value;
				while (end < n && AtRulePreludeRewriter.isNameChar(css.charAt(end))) {
					++end;
				}
				if (out == null) {
					out = new StringBuilder(n + 8);
				}
				out.append(css, copied, value).append('"').append(css, value, end).append('"');
				copied = end;
				i = end;
			} else {
				++i;
			}
		}
		if (out == null) {
			return css;
		}
		return out.append(css, copied, n).toString();
	}

	/**
	 * The index of the value of the attribute selector whose name starts at {@code i} (after the "["), when the value
	 * is one of the keywords, unquoted and followed by "]" or a case modifier; otherwise -1.
	 */
	private static int keywordValue(final String css, int i) {
		final int n = css.length();
		i = skipSpaces(css, i);
		// [ns|name], [*|name], [|name], [name]
		final int nameStart = i;
		while (i < n && (AtRulePreludeRewriter.isNameChar(css.charAt(i)) || css.charAt(i) == '|'
				|| css.charAt(i) == '*' && i == nameStart)) {
			if (css.charAt(i) == '|' && i + 1 < n && css.charAt(i + 1) == '=') {
				break;
			}
			++i;
		}
		if (i == nameStart) {
			return -1;
		}
		i = skipSpaces(css, i);
		if (i < n && "~|^$*".indexOf(css.charAt(i)) != -1) {
			++i;
		}
		if (i >= n || css.charAt(i) != '=') {
			return -1;
		}
		i = skipSpaces(css, i + 1);
		final int value = i;
		while (i < n && AtRulePreludeRewriter.isNameChar(css.charAt(i))) {
			++i;
		}
		if (i == value || !KEYWORDS.contains(css.substring(value, i).toLowerCase(Locale.ROOT))) {
			return -1;
		}
		// "]", or a modifier (i, s) and "]"; not an escape or anything else that continues the value
		int j = skipSpaces(css, i);
		if (j < n && j > i && (css.charAt(j) == 'i' || css.charAt(j) == 'I' || css.charAt(j) == 's'
				|| css.charAt(j) == 'S')) {
			j = skipSpaces(css, j + 1);
		}
		return j < n && css.charAt(j) == ']' ? value : -1;
	}

	private static int skipSpaces(final String css, int i) {
		while (i < css.length() && Character.isWhitespace(css.charAt(i))) {
			++i;
		}
		return i;
	}
}
