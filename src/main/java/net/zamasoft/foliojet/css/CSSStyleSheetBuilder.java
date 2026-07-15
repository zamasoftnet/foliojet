package net.zamasoft.foliojet.css;

import java.awt.Font;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSFontFaceRule;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSMediaQuery;
import com.helger.css.decl.CSSMediaRule;
import com.helger.css.decl.CSSPageRule;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSPageRuleMember;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;

import net.zamasoft.foliojet.css.parser.CSSException;
import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.foliojet.css.parser.SelectorConverter;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.FontFacePropertySet;
import net.zamasoft.foliojet.css.property.PagePropertySet;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.impl.css.property.CSSFontFamily;
import net.zamasoft.foliojet.impl.css.property.CSSFontStyle;
import net.zamasoft.foliojet.impl.css.property.FontWeight;
import net.zamasoft.foliojet.impl.css.property.css3.CSSUnicodeRange;
import net.zamasoft.foliojet.impl.css.property.css3.Src;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontManager;

/**
 * ph-cssで解析したスタイルシートからCSSStyleSheetを直接構築します。
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSStyleSheetBuilder {
	private static final Logger LOG = Logger.getLogger(CSSStyleSheetBuilder.class.getName());

	private static final int MAX_DEPTH = 10;

	private final UserAgent ua;

	/** スタイルシートのURIのスタック(importの深さ・循環検出)。 */
	private final List<URI> uriStack = new ArrayList<URI>();

	/** 直前に処理した@pageの擬似ページ(-cssj-page-contentが参照する。従来動作の踏襲)。 */
	private String pseudoPage;

	private CSSStyleSheet cssStyleSheet;

	public CSSStyleSheetBuilder(UserAgent ua) {
		this.ua = ua;
	}

	public void setCSSStyleSheet(CSSStyleSheet cssStyleSheet) {
		this.cssStyleSheet = cssStyleSheet;
	}

	public CSSStyleSheet getCSSStyleSheet() {
		return this.cssStyleSheet;
	}

	/**
	 * スタイルシートを解析して構築中のCSSStyleSheetに追加します。
	 */
	public void parse(InputSource source) throws IOException, CSSException {
		String css = read(source.getReader());
		css = css.replace("{literal}", "").replace("{/literal}", "");
		CascadingStyleSheet sheet = CSSReader.readFromStringReader(css, DeclarationParser.settings());
		if (sheet == null) {
			throw new CSSException("スタイルシートを解析できません");
		}
		final URI uri = URI.create(source.getURI());
		this.uriStack.add(uri);
		try {
			for (CSSImportRule importRule : sheet.getAllImportRules()) {
				this.importStyle(importRule.getLocationString(), toMediaTypes(importRule.getAllMediaQueries()), uri,
						source.getEncoding());
			}
			for (ICSSTopLevelRule rule : sheet.getAllRules()) {
				this.rule(rule, uri, true);
			}
		} finally {
			this.uriStack.remove(this.uriStack.size() - 1);
		}
	}

	private void rule(ICSSTopLevelRule rule, URI uri, boolean mediaOk) {
		if (rule instanceof CSSStyleRule styleRule) {
			if (!mediaOk) {
				return;
			}
			final List<Selector> selectors;
			try {
				selectors = SelectorConverter.convertList(styleRule.getAllSelectors());
			} catch (CSSException e) {
				// 解釈できないセレクタを含む規則は無視する
				return;
			}
			Declaration declaration = DeclarationParser.convert(styleRule.getAllDeclarations(), null,
					ElementPropertySet.getInstance(), this.ua, uri);
			this.cssStyleSheet.addRule(selectors, declaration);
		} else if (rule instanceof CSSMediaRule mediaRule) {
			// メディア判定は最内の@mediaが優先(従来動作の踏襲)
			boolean ok = false;
			for (CSSMediaQuery query : mediaRule.getAllMediaQueries()) {
				String medium = query.getMedium();
				if (medium == null) {
					medium = "all";
				}
				if (!query.isNot() && this.ua.is(medium.toLowerCase())) {
					ok = true;
					break;
				}
			}
			for (ICSSTopLevelRule inner : mediaRule.getAllRules()) {
				this.rule(inner, uri, ok);
			}
		} else if (rule instanceof CSSPageRule pageRule) {
			this.page(pageRule, uri, mediaOk);
		} else if (rule instanceof CSSFontFaceRule fontFaceRule) {
			// 従来動作の踏襲: @font-faceはメディアに関係なく登録する
			this.fontFace(fontFaceRule, uri);
		}
		// その他(@keyframes, @supports, @namespace, 未知のat-rule)は無視する
	}

	private static List<CSSDeclaration> pageDeclarations(CSSPageRule pageRule) {
		List<CSSDeclaration> declarations = new ArrayList<CSSDeclaration>();
		for (ICSSPageRuleMember member : pageRule.getAllMembers()) {
			if (member instanceof CSSDeclaration declaration) {
				declarations.add(declaration);
			}
			// ページマージンボックス(@top-center等)は現段階では未対応
		}
		return declarations;
	}

	private void page(CSSPageRule pageRule, URI uri, boolean mediaOk) {
		String name = null, pseudo = null;
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
				pseudo = selector.substring(colon + 1);
			}
		}
		if ("-cssj-page-content".equalsIgnoreCase(pseudo)) {
			if (mediaOk) {
				Declaration declaration = DeclarationParser.convert(pageDeclarations(pageRule), null,
						ElementPropertySet.getInstance(), this.ua, uri);
				this.cssStyleSheet.addPageContent(name, this.pseudoPage, declaration);
			}
			return;
		}
		if (name != null) {
			this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(), "名前つきページはサポートしていません");
			return;
		}
		if (mediaOk) {
			this.pseudoPage = pseudo;
			Declaration declaration = DeclarationParser.convert(pageDeclarations(pageRule), null,
					PagePropertySet.getInstance(), this.ua, uri);
			this.cssStyleSheet.addPage(pseudo, declaration);
		}
	}

	private void fontFace(CSSFontFaceRule fontFaceRule, URI uri) {
		Declaration decl = DeclarationParser.convert(fontFaceRule.getAllDeclarations(), null,
				FontFacePropertySet.getInstance(), this.ua, uri);
		if (decl == null) {
			return;
		}
		CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, null);
		decl.applyProperties(style);
		URI[] uris = Src.get(style);
		if (uris != null) {
			boolean missing = true;
			for (int i = 0; i < uris.length; ++i) {
				URI srcUri = uris[i];
				try {
					Source src = null;
					try {
						FontFace face;
						if (srcUri.getScheme() != null && srcUri.getScheme().equals("local-font")) {
							String name = srcUri.getSchemeSpecificPart();
							Font local = Font.decode(name);
							if (local == null) {
								continue;
							}
							face = new FontFace();
							face.local = local;
						} else {
							src = this.ua.resolve(srcUri);
							if (!src.exists()) {
								continue;
							}
							face = new FontFace();
							face.src = src;
						}

						face.fontFamily = CSSFontFamily.get(style);
						face.fontWeight = FontWeight.get(style);
						face.fontStyle = CSSFontStyle.get(style);
						face.unicodeRange = CSSUnicodeRange.get(style);
						FontManager fm = this.ua.getFontManager();
						fm.addFontFace(face);
						missing = false;
						break;
					} finally {
						if (src != null) {
							this.ua.release(src);
						}
					}
				} catch (Exception e) {
					LOG.log(Level.FINE, "Font error", e);
				}
			}
			if (missing) {
				this.ua.message(MessageCodes.WARN_MISSING_FONT_FILE, Arrays.asList(uris).toString());
			}
		}
	}

	private void importStyle(String href, String mediaTypes, URI baseURI, String encoding) {
		if (!this.ua.is(mediaTypes)) {
			return;
		}
		if (this.uriStack.size() > MAX_DEPTH) {
			this.ua.message(MessageCodes.WARN_DEEP_IMPORT, baseURI.toString(), String.valueOf(MAX_DEPTH));
			return;
		}
		URI uri;
		try {
			uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(), baseURI, href);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, href);
			return;
		}
		for (int i = 0; i < this.uriStack.size(); ++i) {
			if (this.uriStack.get(i).equals(uri)) {
				this.ua.message(MessageCodes.WARN_LOOP_IMPORT, baseURI.toString(), uri.toString());
				return;
			}
		}
		try {
			Source source = this.ua.resolve(uri);
			try {
				InputSource inputSource = XMLUtils.toCSSInputSource(source, encoding);
				this.parse(inputSource);
			} finally {
				this.ua.release(source);
			}
		} catch (CSSException e) {
			this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(), e.getMessage());
			LOG.log(Level.FINE, "CSS文法エラー", e);
		} catch (IOException e) {
			this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, uri.toString());
			LOG.log(Level.FINE, "CSS読み込みエラー", e);
		}
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
