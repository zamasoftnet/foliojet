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
	 * インラインスタイル(style属性)を解析してintoに追記します。
	 *
	 * @param into 追記先。nullなら必要時に新規作成。
	 * @return 解析結果(1つも解釈できなければinto)
	 */
	static Declaration parseInline(String css, Declaration into, PropertySet propertySet, UserAgent ua, URI uri)
			throws CSSException {
		CSSDeclarationList declarations = CSSReaderDeclarationList.readFromString(css, settings());
		if (declarations == null) {
			throw new CSSException("スタイル宣言を解析できません");
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
