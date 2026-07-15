package net.zamasoft.foliojet.css.token;

import java.util.List;
import java.util.Locale;

/**
 * CSS宣言値のトークンです。パーサー(ph-css)の式から {@link Tokens} が生成する不変値で、
 * プロパティハンドラは {@link TokenStream} を通してこれを読み取ります。
 */
public sealed interface CssToken {

	/** 単位のない数値。 */
	record Num(double value, boolean integer) implements CssToken {
		public int intValue() {
			return (int) this.value;
		}

		public String toString() {
			return this.integer ? String.valueOf((int) this.value) : String.valueOf(this.value);
		}
	}

	/** 単位付きの寸法。 */
	record Dim(double value, Unit unit, String unitText) implements CssToken {
		public String toString() {
			return this.value + this.unitText;
		}
	}

	/** パーセント値。 */
	record Percent(double value) implements CssToken {
		public String toString() {
			return this.value + "%";
		}
	}

	/** 識別子。 */
	record Ident(String name) implements CssToken {
		/** 大文字小文字を無視して比較します。 */
		public boolean is(String keyword) {
			return this.name.equalsIgnoreCase(keyword);
		}

		/** 小文字化した名前を返します。 */
		public String lower() {
			return this.name.toLowerCase(Locale.ROOT);
		}

		public String toString() {
			return this.name;
		}
	}

	/** 引用符つき文字列。 */
	record Str(String value) implements CssToken {
		public String toString() {
			return "\"" + this.value + "\"";
		}
	}

	/** url() 参照。 */
	record Uri(String uri) implements CssToken {
		public String toString() {
			return "url(" + this.uri + ")";
		}
	}

	/** 関数(rgb、counter、attr、-cssj-* など)。引数はコンマ区切りを含むトークン列。 */
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

	/** unicode-range(U+xxxx 形式のテキストを保持)。 */
	record UnicodeRange(String text) implements CssToken {
		public String toString() {
			return this.text;
		}
	}

	/** 区切り記号。 */
	enum Op implements CssToken {
		COMMA, SLASH;

		public String toString() {
			return this == COMMA ? "," : "/";
		}
	}

	/** 特別なキーワード。 */
	enum Keyword implements CssToken {
		INHERIT;

		public String toString() {
			return "inherit";
		}
	}
}
