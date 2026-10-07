package net.zamasoft.foliojet.css;

import java.net.URI;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import net.zamasoft.foliojet.css.parser.CSSException;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.property.PropertySet;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Converts a declaration sequence parsed by ph-css to a Declaration.
 */
final class DeclarationParser {
	private DeclarationParser() {
		// utility
	}

	static CSSReaderSettings settings() {
		return new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
	}

	/**
	 * Implicitly closes an unclosed comment at the end of input (2026-08-18,
	 * user bug report).
	 *
	 * <p>
	 * CSS Syntax Level 3's "Consume comments" specifies that input ending
	 * inside a comment is a parse error, but <b>ends the comment there
	 * and continues processing</b> (so preceding rules remain valid). However, the ph-css
	 * lexer (JavaCC) raised TokenMgrError on an unclosed comment,
	 * making {@code CSSReader} return null and <b>discard the entire sheet</b>.
	 * Because ph-css cannot recover, check before passing the input
	 * and append {@code "*&#47;"} if it ends inside an unclosed comment.
	 * </p>
	 *
	 * <p>
	 * {@code /*} inside strings (""/'') or between {@code url(} and {@code )} does not
	 * start a comment (comments are not recognized inside unquoted URL tokens),
	 * so track those states to avoid false positives.
	 * </p>
	 */
	static String closeUnterminatedComment(final String css) {
		final int NORMAL = 0, COMMENT = 1, STRING = 2, URL = 3;
		int state = NORMAL;
		char quote = 0;
		for (int i = 0; i < css.length(); ++i) {
			final char c = css.charAt(i);
			switch (state) {
			case NORMAL:
				if (c == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') {
					state = COMMENT;
					++i;
				} else if (c == '"' || c == '\'') {
					state = STRING;
					quote = c;
				} else if ((c == 'u' || c == 'U') && css.regionMatches(true, i, "url(", 0, 4)) {
					state = URL;
					i += 3;
				}
				break;
			case COMMENT:
				if (c == '*' && i + 1 < css.length() && css.charAt(i + 1) == '/') {
					state = NORMAL;
					++i;
				}
				break;
			case STRING:
				if (c == '\\') {
					++i; // Escape (skip the next character; the same handling suffices for a backslash followed by a newline)
				} else if (c == quote || c == '\n') {
					// A newline ends an invalid string (bad-string); just reset the state.
					state = NORMAL;
				}
				break;
			case URL:
				if (c == '\\') {
					++i;
				} else if (c == '"' || c == '\'') {
					// Continue reading a quoted url("...") as STRING (the closing parenthesis is
					// consumed after returning to NORMAL, but does not affect comment detection).
					state = STRING;
					quote = c;
				} else if (c == ')') {
					state = NORMAL;
				}
				break;
			}
		}
		return state == COMMENT ? css + "*/" : css;
	}

	/**
	 * Cache from style attribute strings to ph-css ASTs. Generated documents often
	 * repeat the same inline styles extensively (e-Gov legal HTML has 42,000 attributes
	 * but few distinct values), and starting ph-css accounted for one tenth
	 * of conversion time (measured by random pauses on 2026-08-09). ASTs can be shared
	 * because subsequent access is read-only. Stop caching at this level rather than
	 * caching Declaration, because property interpretation depends on the document URI
	 * (relative resolution of url()).
	 */
	private static final int INLINE_CACHE_LIMIT = 4096;
	private static final java.util.Map<String, CSSDeclarationList> INLINE_CACHE = java.util.Collections
			.synchronizedMap(new java.util.LinkedHashMap<String, CSSDeclarationList>(256, 0.75f, true) {
				private static final long serialVersionUID = 1L;

				protected boolean removeEldestEntry(java.util.Map.Entry<String, CSSDeclarationList> eldest) {
					return this.size() > INLINE_CACHE_LIMIT;
				}
			});

	/**
	 * Parses an inline style (style attribute) and appends it to into.
	 *
	 * @param into destination; if null, created as needed
	 * @return the parse result (into if no declarations can be interpreted)
	 */
	static Declaration parseInline(String css, Declaration into, PropertySet propertySet, UserAgent ua, URI uri)
			throws CSSException {
		CSSDeclarationList declarations = INLINE_CACHE.get(css);
		if (declarations == null) {
			declarations = CSSReaderDeclarationList.readFromString(closeUnterminatedComment(css), settings());
			if (declarations == null) {
				throw new CSSException("スタイル宣言を解析できません");
			}
			INLINE_CACHE.put(css, declarations);
		}
		return convert(declarations.getAllDeclarations(), into, propertySet, ua, uri);
	}

	/**
	 * Appends ph-css declarations to into.
	 *
	 * @param into destination; if null, created as needed
	 * @return into if no declarations can be interpreted (may remain null)
	 */
	static Declaration convert(List<CSSDeclaration> declarations, Declaration into, PropertySet propertySet,
			UserAgent ua, URI uri) {
		for (CSSDeclaration declaration : declarations) {
			List<CssToken> tokens = Tokens.fromExpression(declaration.getExpression());
			if (tokens.isEmpty()) {
				continue;
			}
			Property property = propertySet.parseDeclaration(declaration.getProperty(), tokens, ua, uri,
					declaration.isImportant());
			if (property == null) {
				continue;
			}
			if (into == null) {
				into = new Declaration();
			}
			into.addProperty(property);
		}
		return into;
	}
}
