package net.zamasoft.foliojet.css.token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * トークン列のカーソルです。プロパティハンドラの値解釈を単純化するための
 * 読み取りヘルパーを提供します。ヘルパーは「マッチした場合のみ消費」が原則です。
 */
public final class TokenStream {
	private final List<CssToken> tokens;

	private int pos = 0;

	public TokenStream(List<CssToken> tokens) {
		this.tokens = tokens;
	}

	/** 全トークン数(位置に関係なく)。 */
	public int size() {
		return this.tokens.size();
	}

	/** 未読トークンがあるか。 */
	public boolean hasNext() {
		return this.pos < this.tokens.size();
	}

	/** 次のトークン(消費しない)。無ければ null。 */
	public CssToken peek() {
		return this.pos < this.tokens.size() ? this.tokens.get(this.pos) : null;
	}

	/** 次のトークンを消費して返す。無ければ null。 */
	public CssToken next() {
		return this.pos < this.tokens.size() ? this.tokens.get(this.pos++) : null;
	}

	/** 現在位置。 */
	public int position() {
		return this.pos;
	}

	/** 位置を戻す(先読みの取り消し用)。 */
	public void rewind(int position) {
		this.pos = position;
	}

	/** 値全体が単独の inherit か。 */
	public boolean isInherit() {
		return this.tokens.size() == 1 && this.tokens.get(0) == CssToken.Keyword.INHERIT;
	}

	/**
	 * 値全体が単独のCSS全体キーワード(inherit/initial/unset)なら対応する
	 * {@link net.zamasoft.foliojet.css.value.KeywordValue} を返します。
	 * それ以外は null。
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

	/** 次が識別子ならその名前を消費して返す。それ以外は null。 */
	public String ident() {
		if (this.peek() instanceof CssToken.Ident ident) {
			++this.pos;
			return ident.name();
		}
		return null;
	}

	/** 次が指定キーワード(大文字小文字無視)なら消費して true。 */
	public boolean eat(String keyword) {
		if (this.peek() instanceof CssToken.Ident ident && ident.is(keyword)) {
			++this.pos;
			return true;
		}
		return false;
	}

	/** 次がコンマなら消費して true。 */
	public boolean eatComma() {
		if (this.peek() == CssToken.Op.COMMA) {
			++this.pos;
			return true;
		}
		return false;
	}

	/** 次がスラッシュなら消費して true。 */
	public boolean eatSlash() {
		if (this.peek() == CssToken.Op.SLASH) {
			++this.pos;
			return true;
		}
		return false;
	}

	/** 次が数値なら消費して返す。それ以外は null。 */
	public CssToken.Num number() {
		if (this.peek() instanceof CssToken.Num num) {
			++this.pos;
			return num;
		}
		return null;
	}

	/** 次が文字列なら消費して返す。それ以外は null。 */
	public String string() {
		if (this.peek() instanceof CssToken.Str str) {
			++this.pos;
			return str.value();
		}
		return null;
	}

	/** 次が指定名の関数なら消費して返す。それ以外は null。 */
	public CssToken.Func func(String name) {
		if (this.peek() instanceof CssToken.Func func && func.is(name)) {
			++this.pos;
			return func;
		}
		return null;
	}

	/**
	 * 残りのトークンをコンマで分割して返します(消費します)。
	 * コンマ自体は結果に含まれません。空のグループは除かれます。
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

	/** 残りのトークンからコンマを除いた列を消費して返します。 */
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
