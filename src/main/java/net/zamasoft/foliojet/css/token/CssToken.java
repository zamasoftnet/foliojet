package net.zamasoft.foliojet.css.token;

import java.util.List;
import java.util.Locale;

/**
 * A CSS declaration value token. An immutable value created by {@link Tokens} from the
 * parser's (ph-css) expressions; property handlers read it through {@link TokenStream}.
 */
public sealed interface CssToken {

	/** A unitless number. */
	record Num(double value, boolean integer) implements CssToken {
		public int intValue() {
			return (int) this.value;
		}

		public String toString() {
			return this.integer ? String.valueOf((int) this.value) : String.valueOf(this.value);
		}
	}

	/** A dimension with a unit. */
	record Dim(double value, Unit unit, String unitText) implements CssToken {
		public String toString() {
			return this.value + this.unitText;
		}
	}

	/** A percentage. */
	record Percent(double value) implements CssToken {
		public String toString() {
			return this.value + "%";
		}
	}

	/** An identifier. */
	record Ident(String name) implements CssToken {
		/** Compares case-insensitively. */
		public boolean is(String keyword) {
			return this.name.equalsIgnoreCase(keyword);
		}

		/** Returns the lowercased name. */
		public String lower() {
			return this.name.toLowerCase(Locale.ROOT);
		}

		public String toString() {
			return this.name;
		}
	}

	/** A quoted string. */
	record Str(String value) implements CssToken {
		public String toString() {
			return "\"" + this.value + "\"";
		}
	}

	/** A url() reference. */
	record Uri(String uri) implements CssToken {
		public String toString() {
			return "url(" + this.uri + ")";
		}
	}

	/** A function (rgb, counter, attr, -cssj-*, etc.). Arguments are tokens including comma separators. */
	record Func(String name, List<CssToken> args) implements CssToken {
		public boolean is(String functionName) {
			return this.name.equalsIgnoreCase(functionName);
		}

		public TokenStream argStream() {
			return new TokenStream(this.args);
		}

		public String toString() {
			StringBuilder buff = new StringBuilder(this.name).append('(');
			for (int i = 0; i < this.args.size(); ++i) {
				if (i > 0) {
					buff.append(' ');
				}
				buff.append(this.args.get(i));
			}
			return buff.append(')').toString();
		}
	}

	/**
	 * Grid line names {@code [name1 name2]} (2026-08-29). ph-css puts brackets in the
	 * expression tree as {@code CSSExpressionMemberLineNames}, but {@link Tokens}
	 * previously silently discarded them as unknown members. Thus
	 * {@code grid-template-columns: [full-start] 1fr [full-end]} was accepted as
	 * {@code 1fr}, leaving {@code grid-column: full-start / full-end} (line name references)
	 * unresolvable. Empty {@code []} yields an empty list.
	 */
	record LineNames(List<String> names) implements CssToken {
		public String toString() {
			return "[" + String.join(" ", this.names) + "]";
		}
	}

	/** unicode-range (retains text in U+xxxx form). */
	record UnicodeRange(String text) implements CssToken {
		public String toString() {
			return this.text;
		}
	}

	/**
	 * Delimiters and operators. PLUS/MINUS/TIMES are generated only when converting
	 * calc() to RPN (Tokens.convertCalc); arithmetic operators do not appear in ordinary tokenization.
	 */
	enum Op implements CssToken {
		COMMA, SLASH, PLUS, MINUS, TIMES;

		public String toString() {
			return switch (this) {
			case COMMA -> ",";
			case SLASH -> "/";
			case PLUS -> "+";
			case MINUS -> "-";
			case TIMES -> "*";
			};
		}
	}

	/** Special keywords. */
	enum Keyword implements CssToken {
		INHERIT, INITIAL, UNSET;

		public String toString() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
