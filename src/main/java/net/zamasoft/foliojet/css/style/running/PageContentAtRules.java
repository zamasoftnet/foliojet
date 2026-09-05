package net.zamasoft.foliojet.css.style.running;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJPageContent;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.ua.UserAgent;

/** ph-cssが捨てる@page内のlegacy規則を救い、宣言全体を不変の一ノードへ写します。 */
public final class PageContentAtRules {
	private static final String RULE = "@-cssj-page-content";
	private record Identifier(String value, int end) {
	}

	private PageContentAtRules() {
	}

	public static RunningTemplate synthesize(final UserAgent ua, final String name, final byte pages,
			final Declaration declaration) {
		final CSSStyle style = CSSStyle.getCSSStyle(ua, null, CSSElement.BEFORE);
		if (declaration != null) {
			declaration.applyProperties(style);
		}
		style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
		style.set(CSSJPageContent.INFO_NAME, new StringValue(name), CSSStyle.MODE_IMPORTANT);
		style.set(CSSJPageContent.INFO_PAGES, IntegerValue.create(pages), CSSStyle.MODE_IMPORTANT);
		final StyleSnapshot snapshot = StyleSnapshot.capture(style);
		if (snapshot.imageUris().size() > RunningCapture.MAX_IMAGE_REFERENCES) {
			throw new IllegalArgumentException("image references");
		}
		return new RunningTemplate(name, pages, true,
				List.of(new RunningTemplate.Start(snapshot, null), new RunningTemplate.End()),
				(int) snapshot.textBytes(), snapshot.imageUris().size());
	}

	/**
	 * @pageの直下だけを同じ親の規則列へ引き上げます。@media/@supports/@layer/@containerの
	 * 条件と規則順は維持します。文字列、コメント、escape、関数、角括弧、その他のブロックは
	 * 不透明な字句として飛ばします。不完全なブロックは書き換えません。
	 */
	public static String preprocess(final String css) {
		return rules(css, 0, css.length(), 0);
	}

	private static String rules(final String css, final int from, final int to, final int depth) {
		if (depth >= 64) {
			return css.substring(from, to);
		}
		final StringBuilder result = new StringBuilder();
		int start = from;
		while (start < to) {
			final int delimiter = delimiter(css, start, to);
			if (delimiter == to) {
				result.append(css, start, to);
				break;
			}
			if (css.charAt(delimiter) == ';') {
				result.append(css, start, delimiter + 1);
				start = delimiter + 1;
				continue;
			}
			final int end = blockEnd(css, delimiter, to);
			if (end < 0) {
				result.append(css, start, to);
				break;
			}
			final String header = header(css, start, delimiter);
			final Identifier keyword = keyword(header);
			result.append(css, start, delimiter + 1);
			final List<String> lifted = new ArrayList<String>();
			if ("@page".equals(keyword.value())) {
				final String mask = mask(header.substring(keyword.end()));
				result.append(mask == null ? css.substring(delimiter + 1, end)
						: page(css, delimiter + 1, end, mask, lifted));
			} else if (List.of("@media", "@supports", "@layer", "@container").contains(keyword.value())) {
				result.append(rules(css, delimiter + 1, end, depth + 1));
			} else {
				result.append(css, delimiter + 1, end);
			}
			result.append('}');
			lifted.forEach(result::append);
			start = end + 1;
		}
		return result.toString();
	}

	private static String page(final String css, final int from, final int to, final String mask,
			final List<String> lifted) {
		final StringBuilder result = new StringBuilder();
		int start = from;
		while (start < to) {
			final int delimiter = delimiter(css, start, to);
			if (delimiter == to) {
				result.append(css, start, to);
				break;
			}
			if (css.charAt(delimiter) == ';') {
				result.append(css, start, delimiter + 1);
				start = delimiter + 1;
				continue;
			}
			final int end = blockEnd(css, delimiter, to);
			if (end < 0) {
				result.append(css, start, to);
				break;
			}
			final String header = header(css, start, delimiter);
			final Identifier keyword = keyword(header);
			if (RULE.equals(keyword.value())) {
				lifted.add("\n" + RULE + header.substring(keyword.end()) + mask + " " + css.substring(delimiter, end + 1));
				// 通常の@page宣言の文字列を触らず、行番号もできるだけ保つ。
				for (int i = start; i <= end; ++i) {
					final char c = css.charAt(i);
					result.append(c == '\n' || c == '\r' ? c : ' ');
				}
			} else {
				result.append(css, start, end + 1);
			}
			start = end + 1;
		}
		return result.toString();
	}

	private static String header(final String css, final int from, final int to) {
		final String text = uncomment(css.substring(from, to));
		// escapeに属する末尾の空白は残し、独立した空白だけを除く。
		final int start = whitespaceEnd(text, 0);
		int end = start;
		for (int i = start; i < text.length();) {
			if (whitespace(text.charAt(i))) {
				++i;
			} else {
				i = skip(text, i, text.length());
				end = i;
			}
		}
		return text.substring(start, end);
	}

	private static Identifier keyword(final String header) {
		final Identifier ident = header.startsWith("@") ? identifier(header, 1) : null;
		return ident == null ? new Identifier("", 0)
				: new Identifier("@" + ident.value().toLowerCase(Locale.ROOT), ident.end());
	}

	private static String mask(final String selectors) {
		final var masks = new java.util.LinkedHashSet<String>();
		boolean all = false;
		int i = whitespaceEnd(selectors, 0);
		if (i == selectors.length()) {
			return "";
		}
		while (true) {
			final Identifier name = identifier(selectors, i);
			if (name != null) {
				i = whitespaceEnd(selectors, name.end());
			}
			boolean pseudo = false;
			while (i < selectors.length() && selectors.charAt(i) == ':') {
				final Identifier ident = identifier(selectors, whitespaceEnd(selectors, i + 1));
				if (ident == null) {
					return null;
				}
				final String value = ident.value().toLowerCase(Locale.ROOT);
				if (!List.of("first", "left", "right", "single").contains(value)) {
					return null;
				}
				masks.add(value);
				pseudo = true;
				i = whitespaceEnd(selectors, ident.end());
			}
			if (name == null && !pseudo) {
				return null;
			}
			all |= !pseudo;
			if (i == selectors.length()) {
				break;
			}
			if (selectors.charAt(i) != ',') {
				return null;
			}
			i = whitespaceEnd(selectors, i + 1);
		}
		return all || masks.isEmpty() ? "" : " " + String.join(" ", masks);
	}

	/** CSS Syntax 3 §4.3.9/11: 識別子全体を消費し、escapeを復号します。 */
	private static Identifier identifier(final String css, final int from) {
		if (from >= css.length()) {
			return null;
		}
		final int first = css.codePointAt(from);
		if (!nameStart(first) && !validEscape(css, from, css.length())) {
			if (first != '-' || from + 1 >= css.length()) {
				return null;
			}
			final int next = css.codePointAt(from + 1);
			if (next != '-' && !nameStart(next) && !validEscape(css, from + 1, css.length())) {
				return null;
			}
		}
		final StringBuilder value = new StringBuilder();
		int i = from;
		while (i < css.length()) {
			final int c = css.codePointAt(i);
			if (nameStart(c) || c == '-' || c >= '0' && c <= '9') {
				value.appendCodePoint(normalize(c));
				i += Character.charCount(c);
			} else if (validEscape(css, i, css.length())) {
				i = escapeEnd(css, i, css.length(), value);
			} else {
				break;
			}
		}
		return new Identifier(value.toString(), i);
	}

	private static boolean nameStart(final int c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_' || c >= 0x80 || c == 0;
	}

	private static boolean newline(final char c) {
		return c == '\n' || c == '\r' || c == '\f';
	}

	private static boolean whitespace(final char c) {
		return c == ' ' || c == '\t' || newline(c);
	}

	private static int whitespaceEnd(final String css, int i) {
		while (i < css.length() && whitespace(css.charAt(i))) {
			++i;
		}
		return i;
	}

	private static boolean validEscape(final String css, final int from, final int to) {
		return from < to && css.charAt(from) == '\\' && (from + 1 == to || !newline(css.charAt(from + 1)));
	}

	private static int normalize(final int c) {
		return c == 0 || c > Character.MAX_CODE_POINT || c >= 0xd800 && c <= 0xdfff ? 0xfffd : c;
	}

	private static int hex(final char c) {
		return c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10
				: c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
	}

	/** §4.3.7。CRLFは前処理後の一つの改行として消費し、元の文字列は保ちます。 */
	private static int escapeEnd(final String css, final int from, final int to, final StringBuilder value) {
		int i = from + 1;
		int code = 0xfffd;
		if (i < to) {
			if (hex(css.charAt(i)) >= 0) {
				code = 0;
				for (int count = 0; count < 6 && i < to && hex(css.charAt(i)) >= 0; ++count) {
					code = code * 16 + hex(css.charAt(i++));
				}
				if (i < to && whitespace(css.charAt(i))) {
					i += css.charAt(i) == '\r' && i + 1 < to && css.charAt(i + 1) == '\n' ? 2 : 1;
				}
			} else {
				code = css.codePointAt(i);
				i += Character.charCount(code);
			}
		}
		if (value != null) {
			value.appendCodePoint(normalize(code));
		}
		return i;
	}

	private static String uncomment(final String text) {
		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < text.length();) {
			if (text.startsWith("/*", i)) {
				final int end = text.indexOf("*/", i + 2);
				i = end < 0 ? text.length() : end + 2;
				result.append(' ');
			} else {
				final int end = skip(text, i, text.length());
				result.append(text, i, end);
				i = end;
			}
		}
		return result.toString();
	}

	private static int delimiter(final String css, final int from, final int to) {
		for (int i = from; i < to;) {
			final char c = css.charAt(i);
			if (c == '{' || c == ';') {
				return i;
			}
			i = skip(css, i, to);
		}
		return to;
	}

	private static int blockEnd(final String css, final int open, final int to) {
		int depth = 1;
		for (int i = open + 1; i < to;) {
			final char c = css.charAt(i);
			if (c == '{') {
				++depth;
			} else if (c == '}' && --depth == 0) {
				return i;
			}
			i = skip(css, i, to);
		}
		return -1;
	}

	/** 一つの文字列/コメント/escape/括弧群を、括弧の深さによる再帰なしで飛ばします。 */
	private static int skip(final String css, final int from, final int to) {
		int i = from;
		int nesting = 0;
		do {
			final char c = css.charAt(i++);
			if (c == '\\') {
				if (validEscape(css, i - 1, to)) {
					i = escapeEnd(css, i - 1, to, null);
				}
			} else if (c == '/' && i < to && css.charAt(i) == '*') {
				final int end = css.indexOf("*/", i + 1);
				i = end < 0 ? to : Math.min(end + 2, to);
			} else if (c == '\'' || c == '"') {
				while (i < to) {
					final char quoted = css.charAt(i++);
					if (quoted == '\\') {
						if (i < to && newline(css.charAt(i))) {
							i += css.charAt(i) == '\r' && i + 1 < to && css.charAt(i + 1) == '\n' ? 2 : 1;
						} else {
							i = escapeEnd(css, i - 1, to, null);
						}
					} else if (quoted == c || newline(quoted)) {
						break;
					}
				}
			} else if (c == '(' || c == '[') {
				++nesting;
			} else if (c == ')' || c == ']') {
				--nesting;
			}
		} while (nesting > 0 && i < to);
		return i;
	}
}
