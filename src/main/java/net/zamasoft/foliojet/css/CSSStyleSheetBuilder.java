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
import com.helger.css.decl.CSSLayerRule;
import com.helger.css.decl.CSSMediaExpression;
import com.helger.css.decl.CSSMediaQuery;
import com.helger.css.decl.CSSMediaRule;
import com.helger.css.decl.CSSPageMarginBlock;
import com.helger.css.decl.CSSPageRule;
import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CSSUnknownRule;
import com.helger.css.decl.CSSSupportsConditionDeclaration;
import com.helger.css.decl.CSSSupportsConditionNegation;
import com.helger.css.decl.CSSSupportsConditionNested;
import com.helger.css.decl.CSSSupportsRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ECSSSupportsConditionOperator;
import com.helger.css.decl.ICSSPageRuleMember;
import com.helger.css.decl.ICSSSupportsConditionMember;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;

import net.zamasoft.foliojet.css.counterstyle.CounterStyleDef;
import net.zamasoft.foliojet.css.counterstyle.CounterStyleParser;
import com.helger.css.writer.CSSWriterSettings;

import net.zamasoft.foliojet.css.parser.CSSException;
import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.foliojet.css.parser.SelectorConverter;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.FontFacePropertySet;
import net.zamasoft.foliojet.css.property.PagePropertySet;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontWeight;
import net.zamasoft.foliojet.css.impl.property.font.CSSUnicodeRange;
import net.zamasoft.foliojet.css.impl.property.font.Src;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
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

	private CSSStyleSheet cssStyleSheet;

	/** これから追加する規則のcascade origin。既定は文書(著者)スタイルシート。 */
	private Origin origin = Origin.AUTHOR;

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
	 * これから{@link #parse(InputSource)}する規則のcascade originを設定します。
	 * ユーザーエージェント既定スタイルシートを読み込む前後で切り替えて使用します。
	 *
	 * @param origin
	 */
	public void setOrigin(Origin origin) {
		this.origin = origin;
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
				this.rule(rule, uri, true, Rule.NO_LAYER, null);
			}
		} finally {
			this.uriStack.remove(this.uriStack.size() - 1);
		}
	}

	private void rule(ICSSTopLevelRule rule, URI uri, boolean mediaOk, int layer, String layerNamePrefix) {
		if (rule instanceof CSSStyleRule styleRule) {
			if (!mediaOk) {
				return;
			}
			this.styleRule(styleRule, uri, layer, null);
		} else if (rule instanceof CSSMediaRule mediaRule) {
			boolean ok = false;
			for (CSSMediaQuery query : mediaRule.getAllMediaQueries()) {
				if (this.evaluateMediaQuery(query)) {
					ok = true;
					break;
				}
			}
			// 外側の@media/@supportsの不一致は内側へ継承する(2026-07-19修正:
			// 以前は内側の判定だけで決まり、外側が不一致でも内側の@mediaが
			// 独立に一致すれば適用されてしまっていた)
			for (ICSSTopLevelRule inner : mediaRule.getAllRules()) {
				this.rule(inner, uri, mediaOk && ok, layer, layerNamePrefix);
			}
		} else if (rule instanceof CSSSupportsRule supportsRule) {
			boolean ok = this.evaluateSupports(supportsRule.getAllSupportConditionMembers(), uri, 0);
			for (ICSSTopLevelRule inner : supportsRule.getAllRules()) {
				this.rule(inner, uri, mediaOk && ok, layer, layerNamePrefix);
			}
		} else if (rule instanceof CSSPageRule pageRule) {
			this.page(pageRule, uri, mediaOk);
		} else if (rule instanceof CSSFontFaceRule fontFaceRule) {
			// 従来動作の踏襲: @font-faceはメディアに関係なく登録する
			this.fontFace(fontFaceRule, uri);
		} else if (rule instanceof CSSLayerRule layerRule) {
			this.layer(layerRule, uri, mediaOk, layerNamePrefix);
		} else if (rule instanceof CSSUnknownRule unknownRule) {
			// ph-cssは@counter-styleを未知のat-ruleとして本文ごと渡す
			if (mediaOk && "@counter-style".equalsIgnoreCase(unknownRule.getDeclaration())) {
				this.counterStyle(unknownRule);
			}
		}
		// その他(@keyframes, @namespace, 未知のat-rule)は無視する
	}

	/**
	 * 著者定義カウンタスタイルです({@code @counter-style}、2026-08-02——
	 * PLAN §2の5位。漢数字・いろは等の和文実需とWeb由来CSSの入力互換)。
	 *
	 * <p>
	 * ph-cssは本規則を{@link CSSUnknownRule}(名前・引数・本文の文字列)
	 * として渡すため、本文をダミーの規則へ包んで読み直し、宣言の並びを
	 * {@link CounterStyleParser}へ渡す。登録簿は{@code UAContext}にあり、
	 * {@code list-style-type: <name>}の側は名前からコードを引くだけなので
	 * 規則の出現順に依存しない。
	 * </p>
	 */
	private void counterStyle(final CSSUnknownRule rule) {
		final String name = rule.getParameterList();
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		final String body = rule.getBody();
		if (body == null) {
			return;
		}
		final CascadingStyleSheet sheet = CSSReader.readFromStringReader("*{" + body + "}",
				DeclarationParser.settings());
		if (sheet == null || sheet.getRuleCount() != 1
				|| !(sheet.getRuleAtIndex(0) instanceof CSSStyleRule holder)) {
			return;
		}
		final List<String[]> descriptors = new ArrayList<>();
		for (final CSSDeclaration declaration : holder.getAllDeclarations()) {
			descriptors.add(new String[] { declaration.getProperty(),
					declaration.getExpression().getAsCSSString(MEDIA_WRITER_SETTINGS, 0) });
		}
		final CounterStyleDef def = CounterStyleParser.parse(descriptors);
		if (def != null) {
			this.ua.getUAContext().getCounterStyles().define(name.trim(), def);
		}
	}

	/**
	 * スタイル規則です(CSS Nesting対応、2026-08-02——PLAN §2の4位。
	 * ph-css 8.2の入れ子ASTを平坦化する)。
	 *
	 * <p>
	 * 入れ子セレクタは**テキスト置換**で親と結合する: {@code &}は親セレクタ
	 * 文字列に置換、{@code &}なしは子孫結合({@code 親 子})。親がセレクタ
	 * リストのときは直積で展開する。仕様の{@code :is()}脱糖と違い固有性は
	 * 分岐ごとに評価される(Sass等のプリプロセッサと同じ挙動——記録済みの
	 * 簡略化)。入れ子の後の宣言(CSSNestedDeclarations)は同セレクタの
	 * 追加規則として出現順に登録され、カスケード順が保たれる。規則内に
	 * 入れ子になった条件規則(@media等)はサブセット外として無視する。
	 * </p>
	 */
	private void styleRule(final CSSStyleRule styleRule, final URI uri, final int layer,
			final List<String> parentSelectorTexts) {
		// 結合済みセレクタ文字列(入れ子の再帰用に常に計算する)
		final List<String> selfTexts = new ArrayList<>();
		for (final CSSSelector selector : styleRule.getAllSelectors()) {
			final String text = selector.getAsCSSString();
			if (parentSelectorTexts == null) {
				selfTexts.add(text);
			} else {
				for (final String parent : parentSelectorTexts) {
					selfTexts.add(combineNestedSelector(parent, text));
				}
			}
		}
		final List<Selector> selectors;
		try {
			if (parentSelectorTexts == null) {
				selectors = SelectorConverter.convertList(styleRule.getAllSelectors());
			} else {
				selectors = this.parseSelectorTexts(selfTexts);
			}
		} catch (final CSSException e) {
			// 解釈できないセレクタを含む規則は無視する(入れ子ごと)
			return;
		}
		if (selectors == null) {
			return;
		}
		if (styleRule.hasDeclarations()) {
			final Declaration declaration = DeclarationParser.convert(styleRule.getAllDeclarations(), null,
					ElementPropertySet.getInstance(), this.ua, uri);
			this.cssStyleSheet.addRule(selectors, declaration, this.origin, layer);
			this.collectSVGStyleRule(selfTexts, styleRule.getAllDeclarations());
		}
		for (final com.helger.css.decl.ICSSNestedRule nested : styleRule.getAllRules()) {
			if (nested instanceof CSSStyleRule nestedStyle) {
				this.styleRule(nestedStyle, uri, layer, selfTexts);
			} else if (nested instanceof com.helger.css.decl.CSSNestedDeclarations nestedDecls) {
				// 入れ子規則の後に現れた宣言——同セレクタで順序どおり追加
				if (nestedDecls.hasDeclarations()) {
					final Declaration declaration = DeclarationParser.convert(nestedDecls.getAllDeclarations(),
							null, ElementPropertySet.getInstance(), this.ua, uri);
					this.cssStyleSheet.addRule(selectors, declaration, this.origin, layer);
					this.collectSVGStyleRule(selfTexts, nestedDecls.getAllDeclarations());
				}
			}
			// 規則内の@media/@supports等はサブセット外(無視)
		}
	}

	/**
	 * インラインSVG向けの著者CSS部分集合の収集です(2026-08-07)。
	 * インラインSVGはBatikの独立文書として描かれ、HTML文書のスタイル
	 * シートが届かない(CSSクラスでfill/strokeを塗るアイコンシステムが
	 * 全部黒くなる)。そこでSVGプレゼンテーション系の宣言を含む規則
	 * だけを{@link net.zamasoft.foliojet.ua.DocumentContext}へ集め、
	 * SVG文書へ&lt;style&gt;注入してBatik側でカスケードさせる。
	 *
	 * <p>
	 * セレクタはBatikのCSS2世代のパーサが読める形(タグ・クラス・id・
	 * 子孫・{@code >}・{@code *})だけを通す。擬似クラスや属性セレクタ、
	 * エスケープ入りのクラス名は捨てる——SVG文書の中にはHTML側の祖先が
	 * 存在しないので、文脈を要するセレクタはどのみち正しく評価できない。
	 * 同じ理由で、HTML祖先を含む子孫セレクタは「一致しない」側へ倒れる
	 * (過剰適用はしない)。var()を含む宣言はBatikが解決できないので捨てる。
	 * </p>
	 */
	private void collectSVGStyleRule(final List<String> selectorTexts,
			final Iterable<CSSDeclaration> declarations) {
		if (this.origin != Origin.AUTHOR) {
			return;
		}
		List<SVGAuthorCss.Decl> decls = null;
		boolean hasCore = false;
		for (final CSSDeclaration d : declarations) {
			final String prop = d.getProperty().toLowerCase(java.util.Locale.ROOT);
			final boolean core = SVG_PAINT_PROPS.contains(prop);
			if (!core && !SVG_AUX_PROPS.contains(prop)) {
				continue;
			}
			hasCore |= core;
			// 値は生トークン列のまま持つ。var()はここでは解決できない
			// (要素の文脈が要る)ので、注入時まで遅延する(SVGAuthorCss参照)
			final List<CssToken> tokens = Tokens.fromExpression(d.getExpression());
			if (tokens.isEmpty()) {
				continue;
			}
			if (decls == null) {
				decls = new ArrayList<SVGAuthorCss.Decl>();
			}
			decls.add(new SVGAuthorCss.Decl(prop, tokens, d.isImportant()));
		}
		if (decls == null || !hasCore) {
			// SVG固有の描画プロパティを1つも含まない規則は持ち込まない。
			// color/display/font系はHTML汎用で、これらだけの規則まで拾うと
			// 実サイトでは数千規則になり(qiitaで6,234規則)、注入が肥大する
			// 上にBatikが読めない値(display:flex等)を引く確率が上がる
			return;
		}
		StringBuilder sels = null;
		for (final String text : selectorTexts) {
			final String t = text.trim();
			if (t.isEmpty() || !BATIK_SAFE_SELECTOR.matcher(t).matches()) {
				continue;
			}
			if (sels == null) {
				sels = new StringBuilder();
			} else {
				sels.append(',');
			}
			sels.append(t);
		}
		if (sels == null) {
			return;
		}
		this.ua.getDocumentContext().getSVGAuthorCss().addRule(new SVGAuthorCss.Rule(sels.toString(), decls));
	}

	/**
	 * インラインSVGへ持ち込む「SVG固有の描画プロパティ」。規則の採否は
	 * この集合を1つでも含むかで決める(collectSVGStyleRule参照)。
	 */
	private static final java.util.Set<String> SVG_PAINT_PROPS = java.util.Set.of( //
			"fill", "fill-opacity", "fill-rule", //
			"stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", //
			"stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset", //
			"stop-color", "stop-opacity", "opacity", //
			"clip-path", "clip-rule", "mask", "filter", //
			"marker-start", "marker-mid", "marker-end", //
			"text-anchor", "dominant-baseline", "baseline-shift");

	/**
	 * 採用された規則にだけ同乗させるHTML汎用プロパティ(継承・
	 * currentColor・可視性のため)。
	 */
	private static final java.util.Set<String> SVG_AUX_PROPS = java.util.Set.of( //
			"color", "display", "visibility", //
			"font-family", "font-size", "font-weight", "font-style", //
			"letter-spacing", "word-spacing");

	/** BatikのCSS2世代パーサへ安全に渡せるセレクタの形。 */
	private static final java.util.regex.Pattern BATIK_SAFE_SELECTOR = java.util.regex.Pattern
			.compile("[-_a-zA-Z0-9.#*>\\s]+");

	/** 入れ子セレクタの結合({@code &}=親置換、なければ子孫結合)。 */
	private static String combineNestedSelector(final String parent, final String child) {
		final String trimmed = child.trim();
		if (trimmed.indexOf('&') >= 0) {
			return trimmed.replace("&", parent);
		}
		return parent + " " + trimmed;
	}

	/** 結合済みセレクタ文字列群を再解析します(解釈不能はnull)。 */
	private List<Selector> parseSelectorTexts(final List<String> texts) throws CSSException {
		final CascadingStyleSheet sheet = CSSReader
				.readFromStringReader(String.join(",", texts) + "{}", DeclarationParser.settings());
		if (sheet == null || sheet.getRuleCount() != 1
				|| !(sheet.getRuleAtIndex(0) instanceof CSSStyleRule reparsed)) {
			return null;
		}
		return SelectorConverter.convertList(reparsed.getAllSelectors());
	}

	/**
	 * {@code @layer}(CSS Cascade Layers、2026-07-21新設)を処理します。
	 * ブロック形式({@code @layer name { ... }}・匿名{@code @layer { ... }})、
	 * 文形式({@code @layer a, b, c;}、規則を伴わずレイヤーの出現順だけを
	 * 確定する)の両方に対応する。ネストした{@code @layer}(レイヤー
	 * ブロックの中にさらに{@code @layer}がある場合)は、ドット結合した
	 * 完全名(例: 外側{@code a}・内側{@code b}なら{@code "a.b"})で
	 * 独立したレイヤーとして登録する(CSS Cascade Layersの入れ子命名と
	 * 同じ考え方)。{@code !important}によるレイヤー優先順位の反転は
	 * 未対応(docs/CSS-SUPPORT.md参照)。
	 */
	private void layer(CSSLayerRule layerRule, URI uri, boolean mediaOk, String layerNamePrefix) {
		final List<String> names = layerRule.getAllSelectors();
		if (layerRule.getAllRules().isEmpty()) {
			// 文形式(@layer a, b;)、または空ブロック(@layer a {})——
			// 規則を追加せず出現順だけを確定する
			for (String name : names) {
				this.cssStyleSheet.registerNamedLayer(qualifyLayerName(layerNamePrefix, name));
			}
			return;
		}
		// ブロック形式: 0個(匿名)か1個(名前つき)のはず
		final int childLayer;
		final String childPrefix;
		if (names.isEmpty()) {
			childLayer = this.cssStyleSheet.registerAnonymousLayer();
			childPrefix = null;
		} else {
			childPrefix = qualifyLayerName(layerNamePrefix, names.get(0));
			childLayer = this.cssStyleSheet.registerNamedLayer(childPrefix);
		}
		for (ICSSTopLevelRule inner : layerRule.getAllRules()) {
			this.rule(inner, uri, mediaOk, childLayer, childPrefix);
		}
	}

	private static String qualifyLayerName(String prefix, String name) {
		return prefix == null ? name : prefix + "." + name;
	}

	/**
	 * 1個の@mediaクエリ(メディア型+特性式の並び、暗黙にAND)を評価します。
	 * `not`が付く場合は全体を反転します(SPEC Media Queries)。
	 */
	private boolean evaluateMediaQuery(CSSMediaQuery query) {
		String medium = query.getMedium();
		if (medium == null) {
			medium = "all";
		}
		boolean result = this.ua.is(medium.toLowerCase());
		if (result) {
			for (CSSMediaExpression expression : query.getAllMediaExpressions()) {
				if (!this.evaluateMediaExpression(expression)) {
					result = false;
					break;
				}
			}
		}
		return query.isNot() ? !result : result;
	}

	private static final CSSWriterSettings MEDIA_WRITER_SETTINGS = new CSSWriterSettings();

	/**
	 * メディア特性式(`(min-width: 400px)`等)を評価します。ページ寸法は
	 * `output.page-width`/`output.page-height`プロパティで文書解析前に
	 * 静的に確定済みのため、先読みなしに1Pで評価できる。
	 * <p>
	 * ph-css 8.2.1はMedia Queries Level 3相当までしかパースできない
	 * (Level 4の`or`結合子・括弧なしの`not (...)`・range構文
	 * `(width &gt;= 400px)`は構文解析の時点で規則ごと無視される。
	 * docs/CSS-SUPPORT.md参照)。
	 * </p>
	 */
	private boolean evaluateMediaExpression(CSSMediaExpression expression) {
		String feature = expression.getFeature();
		if (feature == null) {
			return false;
		}
		feature = feature.toLowerCase(java.util.Locale.ROOT);
		if (feature.equals("orientation")) {
			String value = expression.getValue() != null
					? expression.getValue().getAsCSSString(MEDIA_WRITER_SETTINGS, 0).trim().toLowerCase(java.util.Locale.ROOT)
					: null;
			boolean landscape = this.resolvePageWidth() > this.resolvePageHeight();
			if ("landscape".equals(value)) {
				return landscape;
			}
			if ("portrait".equals(value)) {
				return !landscape;
			}
			return false;
		}
		if (expression.getValue() == null) {
			// 値なしのbooleanコンテキストクエリ(例: (color)、(monochrome))は未対応
			return false;
		}
		String valueText = expression.getValue().getAsCSSString(MEDIA_WRITER_SETTINGS, 0);
		AbsoluteLengthValue value = ValueUtils.toAbsoluteLength(this.ua, false, valueText);
		if (value == null) {
			value = this.mediaFontRelativeLength(valueText);
		}
		if (value == null) {
			return false;
		}
		double length = value.getLength();
		switch (feature) {
		case "width":
			return length == this.resolvePageWidth();
		case "min-width":
			return this.resolvePageWidth() >= length;
		case "max-width":
			return this.resolvePageWidth() <= length;
		case "height":
			return length == this.resolvePageHeight();
		case "min-height":
			return this.resolvePageHeight() >= length;
		case "max-height":
			return this.resolvePageHeight() <= length;
		default:
			// aspect-ratio等の未対応特性は保守的に不一致とする
			return false;
		}
	}

	/**
	 * メディアクエリのem/remを解決します。メディアクエリには要素の文脈が
	 * 無いため、どちらも<b>初期フォントサイズ</b>(medium)基準で静的に
	 * 解決できる(Media Queries Level 3 §6)。実サイトは
	 * {@code (min-width: 70rem)}のようにremで書くことがあり、ここで
	 * 落とすと@media全体が不成立になる(5ch.ioのサイドバーが
	 * display:noneのまま丸ごと消えた欠陥で実測)。ex/chは
	 * フォントメトリクスが要るため引き続き未対応(nullを返す)。
	 */
	private AbsoluteLengthValue mediaFontRelativeLength(String valueText) {
		String text = valueText.trim().toLowerCase(java.util.Locale.ROOT);
		String number;
		if (text.endsWith("rem")) {
			number = text.substring(0, text.length() - 3);
		} else if (text.endsWith("em")) {
			number = text.substring(0, text.length() - 2);
		} else {
			return null;
		}
		final double ratio;
		try {
			ratio = Double.parseDouble(number.trim());
		} catch (NumberFormatException e) {
			return null;
		}
		return AbsoluteLengthValue.create(this.ua,
				ratio * this.ua.getFontSize(net.zamasoft.foliojet.ua.AbsoluteFontSize.MEDIUM));
	}

	private Double pageWidth, pageHeight;

	private double resolvePageWidth() {
		if (this.pageWidth == null) {
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false,
					UAProps.OUTPUT_PAGE_WIDTH.getString(this.ua));
			this.pageWidth = length != null ? length.getLength() : 0;
		}
		return this.pageWidth;
	}

	private double resolvePageHeight() {
		if (this.pageHeight == null) {
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false,
					UAProps.OUTPUT_PAGE_HEIGHT.getString(this.ua));
			this.pageHeight = length != null ? length.getLength() : 0;
		}
		return this.pageHeight;
	}

	/**
	 * @supports条件式(and/or/notと括弧によるネスト)を評価します。CSS仕様上、
	 * 同一階層でand/orが混在することはない(混在させる場合は括弧が必須)ため、
	 * 左から畳み込むだけでよい。ネスト(括弧)は構文由来の深さ(手書きCSSの
	 * 入れ子段数)のため上限付きの再帰で扱う(calc()の関数ネストと同じ方針)。
	 */
	private boolean evaluateSupports(List<ICSSSupportsConditionMember> members, URI uri, int depth) {
		if (depth > MAX_DEPTH || members.isEmpty()) {
			return false;
		}
		Boolean result = null;
		ECSSSupportsConditionOperator pendingOp = null;
		for (ICSSSupportsConditionMember member : members) {
			if (member instanceof ECSSSupportsConditionOperator op) {
				pendingOp = op;
				continue;
			}
			boolean value = this.evaluateSupportsMember(member, uri, depth);
			if (result == null) {
				result = value;
			} else if (pendingOp == ECSSSupportsConditionOperator.OR) {
				result = result || value;
			} else {
				result = result && value;
			}
		}
		return result != null && result;
	}

	private boolean evaluateSupportsMember(ICSSSupportsConditionMember member, URI uri, int depth) {
		if (depth > MAX_DEPTH) {
			return false;
		}
		if (member instanceof CSSSupportsConditionDeclaration declMember) {
			CSSDeclaration declaration = declMember.getDeclaration();
			List<CssToken> tokens = Tokens.fromExpression(declaration.getExpression());
			return ElementPropertySet.getInstance().supports(declaration.getProperty(), tokens, this.ua, uri);
		}
		if (member instanceof CSSSupportsConditionNegation negation) {
			return !this.evaluateSupportsMember(negation.getSupportsMember(), uri, depth + 1);
		}
		if (member instanceof CSSSupportsConditionNested nested) {
			return this.evaluateSupports(nested.getAllMembers(), uri, depth + 1);
		}
		// selector()等、ph-css 8.2.1がそもそも解析できない構文は未対応(不一致)
		return false;
	}

	private static List<CSSDeclaration> pageDeclarations(CSSPageRule pageRule) {
		List<CSSDeclaration> declarations = new ArrayList<CSSDeclaration>();
		for (ICSSPageRuleMember member : pageRule.getAllMembers()) {
			if (member instanceof CSSDeclaration declaration) {
				declarations.add(declaration);
			}
			// ページマージンボックス(@top-center等)は page() が別途処理する
		}
		return declarations;
	}

	private void page(CSSPageRule pageRule, URI uri, boolean mediaOk) {
		if (!mediaOk) {
			return;
		}
		// 名前付きページN1a(consult-codex-2026-07-31-named-pages.txt Q1):
		// セレクタリスト全件を処理し、名前+複合擬似(chapter:first等)を
		// 構造化PageRuleへ。未対応の擬似(:blank等)はそのセレクタのみ無効
		final List<String> selectors = pageRule.getAllSelectors();
		final List<String> names = new ArrayList<String>();
		final List<Byte> masks = new ArrayList<Byte>();
		if (selectors.isEmpty()) {
			names.add(null);
			masks.add((byte) 0);
		} else {
			selector: for (final String selector : selectors) {
				String name = null;
				byte mask = 0;
				final String[] parts = selector.split(":", -1);
				if (!parts[0].isEmpty()) {
					name = parts[0];
				}
				for (int i = 1; i < parts.length; ++i) {
					final String pseudo = parts[i];
					if ("first".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_FIRST;
					} else if ("left".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_LEFT;
					} else if ("right".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_RIGHT;
					} else if ("-cssj-page-content".equalsIgnoreCase(pseudo)) {
						// 4で廃止された独自機能(3.xの@page :-cssj-page-content)
						this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(),
								"@page :-cssj-page-content は廃止されました。@page のマージンボックスを使用してください");
						continue selector;
					} else {
						this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(),
								"未対応のページ擬似クラスです: :" + pseudo);
						continue selector;
					}
				}
				names.add(name);
				masks.add(mask);
			}
		}
		if (names.isEmpty()) {
			return;
		}
		final Declaration declaration = DeclarationParser.convert(pageDeclarations(pageRule), null,
				PagePropertySet.getInstance(), this.ua, uri);
		for (int s = 0; s < names.size(); ++s) {
			final net.zamasoft.foliojet.css.PageRule rule = this.cssStyleSheet.addPageRule(names.get(s),
					masks.get(s), declaration);

			// ページマージンボックス(@top-center等、css-page-3 §7)
			for (ICSSPageRuleMember member : pageRule.getAllMembers()) {
				if (member instanceof CSSPageMarginBlock marginBlock) {
					final MarginBoxName box = MarginBoxName.fromSymbol(marginBlock.getPageMarginSymbol());
					if (box == null) {
						if (s == 0) {
							this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(),
									"未知のページマージンボックスです: " + marginBlock.getPageMarginSymbol());
						}
						continue;
					}
					Declaration boxDeclaration = DeclarationParser.convert(marginBlock.getAllDeclarations(), null,
							ElementPropertySet.getInstance(), this.ua, uri);
					this.cssStyleSheet.addPageRuleMarginBox(rule, box, boxDeclaration);
				}
			}
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
