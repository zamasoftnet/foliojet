package net.zamasoft.foliojet.css.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.decl.CSSFontFaceRule;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSMediaQuery;
import com.helger.css.decl.CSSMediaRule;
import com.helger.css.decl.CSSPageRule;
import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSPageRuleMember;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;

/**
 * CSSパーサー。構文解析は ph-css が行い、解析結果を StyleSheetHandler のイベントに変換します。
 */
public class Parser {
	private StyleSheetHandler handler;

	private String defaultCharset;

	public void setDocumentHandler(StyleSheetHandler handler) {
		this.handler = handler;
	}

	public void setDefaultCharset(String defaultCharset) {
		this.defaultCharset = defaultCharset;
	}

	public String getDefaultCharset() {
		return this.defaultCharset;
	}

	private static CSSReaderSettings settings() {
		return new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
	}

	public void parseStyleSheet(InputSource source) throws IOException, CSSException {
		String css = read(source.getReader());
		css = css.replace("{literal}", "").replace("{/literal}", "");
		CascadingStyleSheet sheet = CSSReader.readFromStringReader(css, settings());
		if (sheet == null) {
			throw new CSSException("スタイルシートを解析できません");
		}
		this.handler.startDocument(source);
		try {
			for (CSSImportRule importRule : sheet.getAllImportRules()) {
				this.handler.importStyle(importRule.getLocationString(),
						toMediaTypes(importRule.getAllMediaQueries()));
			}
			for (ICSSTopLevelRule rule : sheet.getAllRules()) {
				this.walkRule(rule);
			}
		} finally {
			this.handler.endDocument(source);
		}
	}

	public void parseStyleDeclaration(InputSource source) throws IOException, CSSException {
		String css = read(source.getReader()).trim();
		CSSDeclarationList declarations = CSSReaderDeclarationList.readFromString(css, settings());
		if (declarations == null) {
			throw new CSSException("スタイル宣言を解析できません");
		}
		for (CSSDeclaration declaration : declarations.getAllDeclarations()) {
			this.property(declaration);
		}
	}

	private void walkRule(ICSSTopLevelRule rule) {
		if (rule instanceof CSSStyleRule) {
			CSSStyleRule styleRule = (CSSStyleRule) rule;
			final List<Selector> selectors;
			try {
				selectors = convertSelectors(styleRule.getAllSelectors());
			} catch (CSSException e) {
				// 解釈できないセレクタを含む規則は無視する
				return;
			}
			this.handler.startSelector(selectors);
			for (CSSDeclaration declaration : styleRule.getAllDeclarations()) {
				this.property(declaration);
			}
			this.handler.endSelector(selectors);
		} else if (rule instanceof CSSMediaRule) {
			CSSMediaRule mediaRule = (CSSMediaRule) rule;
			List<String> media = new ArrayList<String>();
			for (CSSMediaQuery query : mediaRule.getAllMediaQueries()) {
				String medium = query.getMedium();
				if (medium == null) {
					medium = "all";
				}
				media.add(query.isNot() ? "not " + medium : medium);
			}
			this.handler.startMedia(media);
			try {
				for (ICSSTopLevelRule inner : mediaRule.getAllRules()) {
					this.walkRule(inner);
				}
			} finally {
				this.handler.endMedia();
			}
		} else if (rule instanceof CSSPageRule) {
			CSSPageRule pageRule = (CSSPageRule) rule;
			String name = null, pseudoPage = null;
			List<String> selectors = pageRule.getAllSelectors();
			if (!selectors.isEmpty()) {
				String selector = selectors.get(0);
				int colon = selector.indexOf(':');
				if (colon == -1) {
					name = selector;
				} else {
					if (colon > 0) {
						name = selector.substring(0, colon);
					}
					pseudoPage = selector.substring(colon + 1);
				}
			}
			this.handler.startPage(name, pseudoPage);
			for (ICSSPageRuleMember member : pageRule.getAllMembers()) {
				if (member instanceof CSSDeclaration) {
					this.property((CSSDeclaration) member);
				}
				// ページマージンボックス(@top-center等)は現段階では未対応
			}
			this.handler.endPage(name, pseudoPage);
		} else if (rule instanceof CSSFontFaceRule) {
			this.handler.startFontFace();
			for (CSSDeclaration declaration : ((CSSFontFaceRule) rule).getAllDeclarations()) {
				this.property(declaration);
			}
			this.handler.endFontFace();
		}
		// その他(@keyframes, @supports, @namespace, 未知のat-rule)は無視する
	}

	private void property(CSSDeclaration declaration) {
		List<CssToken> tokens = Tokens.fromExpression(declaration.getExpression());
		if (tokens.isEmpty()) {
			return;
		}
		this.handler.property(declaration.getProperty(), tokens, declaration.isImportant());
	}

	private static List<Selector> convertSelectors(List<CSSSelector> selectors) throws CSSException {
		return SelectorConverter.convertList(selectors);
	}

	private static String toMediaTypes(List<CSSMediaQuery> queries) {
		StringBuilder buff = new StringBuilder();
		for (CSSMediaQuery query : queries) {
			String medium = query.getMedium();
			if (medium == null) {
				continue;
			}
			if (buff.length() > 0) {
				buff.append(' ');
			}
			buff.append(medium);
		}
		return buff.toString();
	}

	private static String read(Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[4096];
		for (int len = reader.read(buffer); len != -1; len = reader.read(buffer)) {
			builder.append(buffer, 0, len);
		}
		return builder.toString();
	}
}
