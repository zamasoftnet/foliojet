package net.zamasoft.foliojet.css.token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A cursor over a token sequence. Provides reading helpers to simplify value parsing
 * in property handlers. Helpers consume tokens only on a match as a general rule.
 */
public final class TokenStream {
	private final List<CssToken> tokens;

	private int pos = 0;

	public TokenStream(List<CssToken> tokens) {
		this.tokens = tokens;
	}

	/** Total token count (regardless of position). */
	public int size() {
		return this.tokens.size();
	}

	/** Whether unread tokens remain. */
	public boolean hasNext() {
		return this.pos < this.tokens.size();
	}

	/** The next token without consuming it, or null if absent. */
	public CssToken peek() {
		return this.pos < this.tokens.size() ? this.tokens.get(this.pos) : null;
	}

	/** Consumes and returns the next token, or null if absent. */
	public CssToken next() {
		return this.pos < this.tokens.size() ? this.tokens.get(this.pos++) : null;
	}

	/** Current position. */
	public int position() {
		return this.pos;
	}

	/** Restores the position (for undoing lookahead). */
	public void rewind(int position) {
		this.pos = position;
	}

	/** Whether the entire value is a standalone inherit. */
	public boolean isInherit() {
		return this.tokens.size() == 1 && this.tokens.get(0) == CssToken.Keyword.INHERIT;
	}

	/**
	 * Returns the corresponding {@link net.zamasoft.foliojet.css.value.KeywordValue}
	 * if the entire value is a standalone CSS-wide keyword (inherit/initial/unset).
	 * Otherwise returns null.
	 */
	public net.zamasoft.foliojet.css.value.KeywordValue globalKeyword() {
		if (this.tokens.size() != 1) {
			return null;
		}
		CssToken token = this.tokens.get(0);
		if (token == CssToken.Keyword.INHERIT) {
			return net.zamasoft.foliojet.css.value.KeywordValue.INHERIT;
		}
		if (token == CssToken.Keyword.INITIAL) {
			return net.zamasoft.foliojet.css.value.KeywordValue.INITIAL;
		}
		if (token == CssToken.Keyword.UNSET) {
			return net.zamasoft.foliojet.css.value.KeywordValue.UNSET;
		}
		return null;
	}

	/** Consumes and returns the next token's name if it is an identifier; otherwise null. */
	public String ident() {
		if (this.peek() instanceof CssToken.Ident ident) {
			++this.pos;
			return ident.name();
		}
		return null;
	}

	/** Consumes the next token and returns true if it is the given keyword (case-insensitive). */
	public boolean eat(String keyword) {
		if (this.peek() instanceof CssToken.Ident ident && ident.is(keyword)) {
			++this.pos;
			return true;
		}
		return false;
	}

	/** Consumes the next token and returns true if it is a comma. */
	public boolean eatComma() {
		if (this.peek() == CssToken.Op.COMMA) {
			++this.pos;
			return true;
		}
		return false;
	}

	/** Consumes the next token and returns true if it is a slash. */
	public boolean eatSlash() {
		if (this.peek() == CssToken.Op.SLASH) {
			++this.pos;
			return true;
		}
		return false;
	}

	/** Consumes and returns the next token if it is a number; otherwise null. */
	public CssToken.Num number() {
		if (this.peek() instanceof CssToken.Num num) {
			++this.pos;
			return num;
		}
		return null;
	}

	/** Consumes and returns the next token if it is a string; otherwise null. */
	public String string() {
		if (this.peek() instanceof CssToken.Str str) {
			++this.pos;
			return str.value();
		}
		return null;
	}

	/** Consumes and returns the next token if it is a function with the given name; otherwise null. */
	public CssToken.Func func(String name) {
		if (this.peek() instanceof CssToken.Func func && func.is(name)) {
			++this.pos;
			return func;
		}
		return null;
	}

	/**
	 * Consumes the remaining tokens and returns them split at commas.
	 * Commas themselves are excluded. Empty groups are omitted.
	 */
	public List<TokenStream> splitComma() {
		List<TokenStream> groups = new ArrayList<TokenStream>();
		List<CssToken> current = new ArrayList<CssToken>();
		while (this.hasNext()) {
			CssToken token = this.next();
			if (token == CssToken.Op.COMMA) {
				if (!current.isEmpty()) {
					groups.add(new TokenStream(current));
					current = new ArrayList<CssToken>();
				}
			} else {
				current.add(token);
			}
		}
		if (!current.isEmpty()) {
			groups.add(new TokenStream(current));
		}
		return groups;
	}

	/** Consumes and returns the remaining token sequence without commas. */
	public List<CssToken> restIgnoringCommas() {
		List<CssToken> result = new ArrayList<CssToken>();
		while (this.hasNext()) {
			CssToken token = this.next();
			if (token != CssToken.Op.COMMA) {
				result.add(token);
			}
		}
		return result;
	}

	public static TokenStream empty() {
		return new TokenStream(Collections.emptyList());
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		for (int i = 0; i < this.tokens.size(); ++i) {
			if (i > 0) {
				buff.append(' ');
			}
			buff.append(this.tokens.get(i));
		}
		return buff.toString();
	}
}
