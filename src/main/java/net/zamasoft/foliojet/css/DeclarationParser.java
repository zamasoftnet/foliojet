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
 * ph-cssで解析した宣言列をDeclarationに変換します。
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
	 * 閉じられていないコメントを入力終端で暗黙に閉じます(2026-08-18、
	 * 利用者バグ報告)。
	 *
	 * <p>
	 * CSS Syntax Level 3の「Consume comments」は、コメント中に入力が
	 * 尽きた場合をparse errorとしつつ<b>そこでコメントを終えて処理を
	 * 継続する</b>と定める(=直前までの規則は有効)。ところがph-cssの
	 * 字句解析器(JavaCC)は未閉鎖コメントでTokenMgrErrorになり、
	 * {@code CSSReader}がnullを返して<b>シート全体が破棄</b>されていた。
	 * ph-css側では回復できないため、入力を渡す前にここで検査して
	 * 終端が未閉鎖コメント内なら{@code "*&#47;"}を補う。
	 * </p>
	 *
	 * <p>
	 * 文字列(""/'')内・{@code url(}〜{@code )}内の{@code /*}はコメント
	 * 開始ではない(未引用のurlトークン内ではコメントは認識されない)ため、
	 * その状態を追跡して誤検出しない。
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
					++i; // エスケープ(次の1文字を読み飛ばす。\改行も同じ扱いで足りる)
				} else if (c == quote || c == '\n') {
					// 改行は不正文字列の終端(bad-string)——状態だけ戻す
					state = NORMAL;
				}
				break;
			case URL:
				if (c == '\\') {
					++i;
				} else if (c == '"' || c == '\'') {
					// 引用付きurl("...")はSTRINGとして続きを読む(閉じ括弧は
					// NORMALに戻ってから消費されるが、コメント判定には影響しない)
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
	 * style属性文字列→ph-cssのASTのキャッシュ。生成された文書は同一の
	 * インラインスタイルを大量に繰り返すことが多く(e-Gov法令HTMLは
	 * 4.2万属性で異なり値はわずか)、ph-cssの起動が変換時間の1割を
	 * 占めていた(2026-08-09のランダムポーズ実測)。ASTは以後読み取り
	 * 専用で扱うため共有できる。ここで止めてDeclarationまでキャッシュ
	 * しないのは、プロパティ解釈が文書URI(url()の相対解決)に依存する
	 * ため。
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
	 * インラインスタイル(style属性)を解析してintoに追記します。
	 *
	 * @param into 追記先。nullなら必要時に新規作成。
	 * @return 解析結果(1つも解釈できなければinto)
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
	 * ph-cssの宣言列をintoに追記します。
	 *
	 * @param into 追記先。nullなら必要時に新規作成。
	 * @return 1つも解釈できなければinto(nullのままの場合あり)
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
