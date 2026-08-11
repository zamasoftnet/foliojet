package net.zamasoft.foliojet.xml.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.renderer.html.HtmlRenderer;
import org.htmlunit.cyberneko.HTMLConfiguration;
import org.htmlunit.cyberneko.filters.DefaultFilter;
import org.htmlunit.cyberneko.xerces.util.XMLAttributesImpl;
import org.htmlunit.cyberneko.xerces.xni.Augmentations;
import org.htmlunit.cyberneko.xerces.xni.NamespaceContext;
import org.htmlunit.cyberneko.xerces.xni.QName;
import org.htmlunit.cyberneko.xerces.xni.XMLAttributes;
import org.htmlunit.cyberneko.xerces.xni.XMLLocator;
import org.htmlunit.cyberneko.xerces.xni.XMLString;
import org.htmlunit.cyberneko.xerces.xni.XNIException;
import org.htmlunit.cyberneko.xerces.xni.XMLDocumentHandler;
import org.htmlunit.cyberneko.xerces.xni.parser.XMLDocumentSource;
import org.htmlunit.cyberneko.xerces.xni.parser.XMLInputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import net.zamasoft.balancer.ElementProps;
import net.zamasoft.balancer.TagBalancer;
import net.zamasoft.foliojet.ua.CompatibleMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.vocab.Foreign;
import net.zamasoft.zstream.resolver.Source;

/**
 * CommonMark(Markdown)によりMarkdown文書を解析します。
 *
 * <p>
 * Markdown原文をリーダから直接構文木にし({@code parseReader}——原文の
 * Stringは作らない)、構文木を歩きながら<b>XNIイベントを直接発行</b>して
 * {@link TagBalancer}へ流します(2026-08-10、オーナー裁定)。中間HTML
 * 文字列も、その再トークナイズも、パイプ+スレッドも使わない——この
 * プロダクトの常道である「イベントからイベントへの中継」に揃える。
 * 残るバッファはCommonMarkの構文木一つだけ(ライブラリの構造上不可避)。
 * </p>
 *
 * <p>
 * Markdown中の<b>生HTML断片</b>は文字列のままなので、断片だけNekoHTMLの
 * スキャナで字句化し、そのイベントを<b>同じTagBalancer</b>へ中継する。
 * 開いた要素のスタックがバランサに一元化されるため、{@code <div>}と
 * {@code </div>}が別々の断片に割れていても正しく釣り合い、暗黙閉じ
 * (pがdivで閉じる等)の解釈もHTML入力と完全に共通になる。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class MarkdownParser implements Parser {

	public void parse(final UserAgent ua, final Source source, XMLHandler xmlHandler) throws SAXException, IOException {
		String encoding = source.getEncoding();
		if (encoding == null) {
			encoding = "UTF-8";
		}
		// 文書コンテキストの既定はISO-8859-1——UTF-8フォールバック時も
		// 設定しないと、非ASCIIを含むURI(input.default-stylesheet等)の
		// %エンコードがUnmappableCharacterExceptionで落ちる
		ua.getDocumentContext().setEncoding(encoding);

		// 既定スタイル(markdown-ua.css)は、利用者がinput.default-stylesheetで
		// 自前のスタイルシートを指定している場合は注入しない(2026-08-10、
		// オーナー裁定)。既定はあくまで「何も指定しない人のためのA4レポート」
		// であり、書籍などデザインを自分で設計する利用では下敷きに残ると
		// p{line-height}やノンブルが透けて上書き合戦になるため
		final boolean defaultStyle = UAProps.INPUT_DEFAULT_STYLESHEET.getString(ua) == null;

		final Node document;
		try (Reader reader = openReader(source, encoding)) {
			document = newParser().parseReader(reader);
		}
		final String hoisted = extractStyles(document);

		try {
			new EventBridge(ua, xmlHandler).emit(document, hoisted, defaultStyle);
		} catch (final XNIException e) {
			throw new SAXException(e.getMessage(), e);
		}
	}

	private static final java.util.List<org.commonmark.Extension> EXTENSIONS = java.util.List
			.of(org.commonmark.ext.gfm.tables.TablesExtension.create());

	private static org.commonmark.parser.Parser newParser() {
		return org.commonmark.parser.Parser.builder().extensions(EXTENSIONS)
				.inlineParserFactory(CjkFriendlyInlineParser::new).build();
	}

	/**
	 * Markdown既定スタイル(A4レポート、markdown-ua.css)。Markdownには表示仕様が
	 * ないため、FolioJetの既定としてA4レポート向けのデザインを与える。head内の
	 * {@code <style>}として注入する。{@code input.default-stylesheet}指定時は
	 * まったく注入しない。XSLT結合(join.xslt)はbodyだけを取り出すため、
	 * マニュアル等のドキュメントビルドには影響しない。
	 */
	private static final String DEFAULT_STYLE = loadDefaultStyle();

	private static String loadDefaultStyle() {
		try (InputStream in = MarkdownParser.class
				.getResourceAsStream("/net/zamasoft/foliojet/css/html/markdown-ua.css");
				Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return readAll(reader);
		} catch (IOException e) {
			throw new IllegalStateException("markdown-ua.css を読み込めません", e);
		}
	}

	/**
	 * 生HTMLの{@code <style>}をheadへ巻き上げるための抽出です(2026-08-10)。
	 * body内に残すと、ストリーミング構築ではbodyのボックスが既に開いている
	 * ため、body/html自身に効くプロパティ——{@code writing-mode: vertical-rl}
	 * 等——が遡って適用されない(縦書き書籍のMarkdown原稿で実測)。
	 *
	 * <p>
	 * {@code <style>}は生HTMLの<b>ブロック</b>としてのみ現れる(CommonMarkの
	 * type-1生HTMLブロックで、終了タグまでが一つのブロックになる)ため、
	 * 構文木の{@link HtmlBlock}だけを走査すれば全文走査は要らない。抽出後の
	 * ブロックが空白だけになったら木から外す。既定スタイルの後ろへ出現順で
	 * 連結するので「文書側のstyleが後勝ちで上書きする」という約束は不変。
	 * </p>
	 */
	private static final Pattern STYLE_BLOCK = Pattern.compile("<style\\b[^>]*>(.*?)</style\\s*>",
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	private static String extractStyles(final Node document) {
		final StringBuilder hoisted = new StringBuilder();
		Node node = document.getFirstChild();
		while (node != null) {
			final Node next = node.getNext();
			if (node instanceof final HtmlBlock block) {
				final String literal = block.getLiteral();
				if (literal != null) {
					final Matcher m = STYLE_BLOCK.matcher(literal);
					if (m.find()) {
						final StringBuilder rest = new StringBuilder();
						int last = 0;
						do {
							rest.append(literal, last, m.start());
							hoisted.append(m.group(1));
							last = m.end();
						} while (m.find());
						rest.append(literal, last, literal.length());
						if (rest.toString().isBlank()) {
							node.unlink();
						} else {
							block.setLiteral(rest.toString());
						}
					}
				}
			}
			node = next;
		}
		return hoisted.toString();
	}

	// ---------------------------------------------------------------- 文字列API

	public static String toHtml(String markdown) {
		return toHtml(markdown, true);
	}

	/**
	 * Markdown原文を、CopperPDFが解釈できるHTML文書(文字列)へ変換します。
	 * CopperPDFのセッションを介さずMarkdown→HTML変換だけを行いたい場合
	 * (文書生成のビルドツール等)向けの公開API。変換パイプライン
	 * ({@link #parse})はXNIイベント直結でHTML文字列を経由しないため、
	 * こちらはツール用の独立経路である(構文木・style巻き上げは共通)。
	 */
	public static String toHtml(String markdown, boolean defaultStyle) {
		final Node document = newParser().parse(markdown);
		final String hoisted = extractStyles(document);
		final StringBuilder out = new StringBuilder();
		out.append(
				"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">")
				.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>")
				.append("<style type=\"text/css\">").append(defaultStyle ? DEFAULT_STYLE : "").append("</style>")
				.append("<style type=\"text/css\">").append(hoisted).append("</style>").append("</head><body>");
		final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).build();
		renderer.render(document, out);
		out.append("</body></html>");
		return out.toString();
	}

	private static Reader openReader(Source source, String encoding) throws IOException {
		if (source.isReader()) {
			return source.getReader();
		}
		return new InputStreamReader(source.getInputStream(), encoding);
	}

	private static String readAll(Reader reader) throws IOException {
		StringBuilder buff = new StringBuilder();
		char[] buffer = new char[4096];
		for (int len = reader.read(buffer); len != -1; len = reader.read(buffer)) {
			buff.append(buffer, 0, len);
		}
		return buff.toString();
	}

	// ---------------------------------------------------------------- XNIブリッジ

	/**
	 * 構文木→XNIイベントの発行器です。イベントの流れは
	 * {@code emitter/断片スキャナ → foreignフィルタ → TagBalancer → SAX変換 →
	 * XMLHandler}で、HTML入力({@link HTMLParser})とfilter以降を完全に共有する。
	 */
	private static final class EventBridge {

		private final UserAgent ua;
		private final XMLHandler xmlHandler;
		private final TagBalancer balancer;
		/** チェーンの先頭(foreign contentフィルタ)。全イベントはここへ入れる。 */
		private final XMLDocumentHandler head;
		/** 生HTML断片の字句化器(遅延生成、断片間で共有)。 */
		private HTMLConfiguration fragmentScanner;

		/** 終了タグ1個だけの断片(Markdownのインライン生HTMLで普通に現れる)。 */
		private static final Pattern LONE_END_TAG = Pattern
				.compile("\s*</\s*([a-zA-Z][a-zA-Z0-9]*)\s*>\s*");

		/** 断片スキャナの出口(断片ごとに原文を教える)。 */
		private FragmentRelay fragmentRelay;

		EventBridge(final UserAgent ua, final XMLHandler xmlHandler) {
			this.ua = ua;
			this.xmlHandler = xmlHandler;
			this.balancer = new TagBalancer();
			final boolean changeDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);
			// HTMLParserと同じforeign contentフィルタ(math/svgへHTML5の名前空間を
			// 与える)+標準モードでのElementProps切替。詳細な設計判断は
			// HTMLParser.parse内の同型フィルタのコメントを参照
			final DefaultFilter foreign = new DefaultFilter() {
				private boolean firstElement = true;
				private String foreignURI = null;
				private int foreignDepth = 0;

				private void applyForeign(QName element) {
					if (this.foreignDepth == 0) {
						if (element.getUri() == null) {
							final String uri = Foreign.uriOf(element.getLocalpart());
							if (uri == null) {
								return;
							}
							this.foreignURI = uri;
							element.setUri(uri);
						} else if (Foreign.is(element.getUri())) {
							this.foreignURI = element.getUri();
						} else {
							return;
						}
					} else if (element.getUri() == null) {
						element.setUri(this.foreignURI);
					}
					++this.foreignDepth;
				}

				public void startElement(QName element, XMLAttributes attributes, Augmentations augs)
						throws XNIException {
					this.applyForeign(element);
					if (!changeDefaultNamespace && !Foreign.is(element.getUri())) {
						if (element.getUri() != null
								&& (element.getPrefix() == null || element.getPrefix().length() == 0)) {
							element.setUri(null);
						}
					}
					super.startElement(element, attributes, augs);
					if (this.firstElement && element.getLocalpart().equalsIgnoreCase("body")) {
						if (ua.getDocumentContext().getCompatibleMode() == CompatibleMode.STRICT) {
							EventBridge.this.balancer.setElementProps(ElementProps.getElementProps("html4.xml"));
						}
						this.firstElement = false;
					}
				}

				public void endElement(QName element, Augmentations augs) throws XNIException {
					if (this.foreignDepth > 0) {
						if (element.getUri() == null) {
							element.setUri(this.foreignURI);
						}
						if (--this.foreignDepth == 0) {
							this.foreignURI = null;
						}
					}
					if (!changeDefaultNamespace && !Foreign.is(element.getUri())) {
						if (element.getUri() != null
								&& (element.getPrefix() == null || element.getPrefix().length() == 0)) {
							element.setUri(null);
						}
					}
					super.endElement(element, augs);
				}

				public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs)
						throws XNIException {
					final int depth = this.foreignDepth;
					final String uri = this.foreignURI;
					this.applyForeign(element);
					this.foreignDepth = depth;
					this.foreignURI = uri;
					if (!changeDefaultNamespace && !Foreign.is(element.getUri())) {
						if (element.getUri() != null
								&& (element.getPrefix() == null || element.getPrefix().length() == 0)) {
							element.setUri(null);
						}
					}
					super.emptyElement(element, attributes, augs);
				}
			};
			foreign.setDocumentHandler(this.balancer);
			this.balancer.setDocumentSource(foreign);
			this.balancer.setDocumentHandler(new XniToSax(xmlHandler));
			this.head = foreign;
		}

		void emit(final Node document, final String hoistedStyles, final boolean defaultStyle)
				throws XNIException, SAXException {
			this.head.startDocument(null, "UTF-8", null, null);
			// XHTML1.0 StrictのDOCTYPE——標準モード判定と、XSLT結合(join.xslt)時の
			// 名前付き実体参照の緩和規定のため(HTMLParser時代からの約束)
			this.head.doctypeDecl("html", "-//W3C//DTD XHTML 1.0 Strict//EN",
					"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd", null);
			this.start("html");
			this.start("head");
			this.startElement("meta", "charset", "UTF-8");
			this.end("meta");
			this.startElement("style", "type", "text/css");
			if (defaultStyle) {
				this.characters(DEFAULT_STYLE);
			}
			this.end("style");
			this.startElement("style", "type", "text/css");
			if (!hoistedStyles.isEmpty()) {
				this.characters(hoistedStyles);
			}
			this.end("style");
			this.end("head");
			this.start("body");
			this.emitChildren(document, false);
			this.end("body");
			this.end("html");
			this.head.endDocument(null);
		}

		// ------------------------------------------------------------ 構文木の走査

		private void emitNode(final Node node, final boolean tight) throws XNIException {
			if (node instanceof final Text text) {
				this.aozoraRuby(text.getLiteral());
			} else if (node instanceof SoftLineBreak) {
				this.characters("\n");
			} else if (node instanceof HardLineBreak) {
				this.empty("br");
			} else if (node instanceof final Paragraph p) {
				// タイトなリストの中では段落タグを作らない(CommonMarkの慣例)
				if (tight) {
					this.emitChildren(p, tight);
				} else {
					this.start("p");
					this.emitChildren(p, tight);
					this.end("p");
				}
			} else if (node instanceof final Heading h) {
				final String name = "h" + h.getLevel();
				this.start(name);
				this.emitChildren(h, tight);
				this.end(name);
			} else if (node instanceof final Emphasis em) {
				this.start("em");
				this.emitChildren(em, tight);
				this.end("em");
			} else if (node instanceof final StrongEmphasis strong) {
				this.start("strong");
				this.emitChildren(strong, tight);
				this.end("strong");
			} else if (node instanceof final Code code) {
				this.start("code");
				this.characters(code.getLiteral());
				this.end("code");
			} else if (node instanceof final Link link) {
				final XMLAttributesImpl atts = new XMLAttributesImpl();
				addAttribute(atts, "href", link.getDestination());
				if (link.getTitle() != null) {
					addAttribute(atts, "title", link.getTitle());
				}
				this.start("a", atts);
				this.emitChildren(link, tight);
				this.end("a");
			} else if (node instanceof final Image image) {
				final XMLAttributesImpl atts = new XMLAttributesImpl();
				addAttribute(atts, "src", image.getDestination());
				addAttribute(atts, "alt", altText(image));
				if (image.getTitle() != null) {
					addAttribute(atts, "title", image.getTitle());
				}
				this.empty("img", atts);
			} else if (node instanceof final BlockQuote quote) {
				this.start("blockquote");
				this.emitChildren(quote, false);
				this.end("blockquote");
			} else if (node instanceof final BulletList list) {
				this.start("ul");
				this.emitChildren(list, list.isTight());
				this.end("ul");
			} else if (node instanceof final OrderedList list) {
				final XMLAttributesImpl atts = new XMLAttributesImpl();
				final Integer start = list.getStartNumber();
				if (start != null && start != 1) {
					addAttribute(atts, "start", String.valueOf(start));
				}
				this.start("ol", atts);
				this.emitChildren(list, list.isTight());
				this.end("ol");
			} else if (node instanceof final ListItem item) {
				this.start("li");
				this.emitChildren(item, tight);
				this.end("li");
			} else if (node instanceof final FencedCodeBlock fence) {
				this.start("pre");
				final XMLAttributesImpl atts = new XMLAttributesImpl();
				final String info = fence.getInfo();
				if (info != null && !info.isEmpty()) {
					final int space = info.indexOf(' ');
					addAttribute(atts, "class", "language-" + (space < 0 ? info : info.substring(0, space)));
				}
				this.start("code", atts);
				this.characters(fence.getLiteral());
				this.end("code");
				this.end("pre");
			} else if (node instanceof final IndentedCodeBlock code) {
				this.start("pre");
				this.start("code");
				this.characters(code.getLiteral());
				this.end("code");
				this.end("pre");
			} else if (node instanceof ThematicBreak) {
				this.empty("hr");
			} else if (node instanceof final HtmlBlock html) {
				this.scanFragment(html.getLiteral());
			} else if (node instanceof final HtmlInline html) {
				this.scanFragment(html.getLiteral());
			} else if (node instanceof final TableBlock table) {
				this.start("table");
				this.emitChildren(table, tight);
				this.end("table");
			} else if (node instanceof final TableHead thead) {
				this.start("thead");
				this.emitChildren(thead, tight);
				this.end("thead");
			} else if (node instanceof final TableBody tbody) {
				this.start("tbody");
				this.emitChildren(tbody, tight);
				this.end("tbody");
			} else if (node instanceof final TableRow row) {
				this.start("tr");
				this.emitChildren(row, tight);
				this.end("tr");
			} else if (node instanceof final TableCell cell) {
				final String name = cell.isHeader() ? "th" : "td";
				final XMLAttributesImpl atts = new XMLAttributesImpl();
				final TableCell.Alignment align = cell.getAlignment();
				if (align != null) {
					addAttribute(atts, "align", switch (align) {
					case LEFT -> "left";
					case CENTER -> "center";
					case RIGHT -> "right";
					});
				}
				this.start(name, atts);
				this.emitChildren(cell, tight);
				this.end(name);
			} else if (node instanceof LinkReferenceDefinition) {
				// 出力なし
			} else {
				// 未知のノード(将来の拡張)は子だけたどる
				this.emitChildren(node, tight);
			}
		}

		private void emitChildren(final Node parent, final boolean tight) throws XNIException {
			for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
				this.emitNode(child, tight);
			}
		}

		/** 画像のalt文字列(子ノードのプレーンテキスト連結)。 */
		private static String altText(final Node node) {
			final StringBuilder buff = new StringBuilder();
			collectText(node, buff);
			return buff.toString();
		}

		private static void collectText(final Node node, final StringBuilder buff) {
			for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
				if (child instanceof final Text text) {
					buff.append(text.getLiteral());
				} else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
					buff.append('\n');
				} else {
					collectText(child, buff);
				}
			}
		}

		// ------------------------------------------------------------ イベント発行

		private static QName name(final String name) {
			return new QName(null, name, name, null);
		}

		private static void addAttribute(final XMLAttributesImpl atts, final String name, final String value) {
			atts.addAttribute(name(name), "CDATA", value);
		}

		private void start(final String name) throws XNIException {
			this.head.startElement(name(name), new XMLAttributesImpl(), null);
		}

		private void start(final String name, final XMLAttributes atts) throws XNIException {
			this.head.startElement(name(name), atts, null);
		}

		private void startElement(final String name, final String attName, final String attValue) throws XNIException {
			final XMLAttributesImpl atts = new XMLAttributesImpl();
			addAttribute(atts, attName, attValue);
			this.head.startElement(name(name), atts, null);
		}

		private void end(final String name) throws XNIException {
			this.head.endElement(name(name), null);
		}

		private void empty(final String name) throws XNIException {
			this.head.emptyElement(name(name), new XMLAttributesImpl(), null);
		}

		private void empty(final String name, final XMLAttributes atts) throws XNIException {
			this.head.emptyElement(name(name), atts, null);
		}

		/**
		 * 青空文庫式のルビ記法({@code 漢字《かんじ》}・{@code ｜任意の語《よみ》})を
		 * ルビの要素へ展開します(2026-08-11、オーナー要望)。
		 *
		 * <p>
		 * 縦組みの日本語では原稿にルビを直接書けることが要る。HTMLの
		 * {@code <ruby>}を毎回手で書くのは原稿として重すぎるので、
		 * 青空文庫の記法をMarkdownの拡張として受ける。規則は本家に合わせる:
		 * </p>
		 * <ul>
		 * <li>{@code 《}〜{@code 》}の直前が漢字の連なりなら、それを親文字にする
		 * ({@code 狼狽《ろうばい》})。漢字には々・ヶ・〆と、CJK統合漢字
		 * (拡張Aを含む)を数える</li>
		 * <li>{@code ｜}(全角縦棒)があれば、そこから{@code 《}までが親文字
		 * ({@code ｜生前退位《せいぜんたいい》}、{@code ｜1970年《いちきゅうななまるねん》})。
		 * 漢字以外を親文字にしたいときに使う</li>
		 * <li>親文字が見つからない{@code 《...》}は、そのままの文字として出す
		 * ——引用符として使う原稿を壊さないため</li>
		 * </ul>
		 *
		 * <p>
		 * 展開先はHTMLの{@code <ruby>}なので、ルビの体裁はCSSで調整できる
		 * (書籍では{@code ruby > rt}に文字寸法を指定している)。
		 * </p>
		 */
		private void aozoraRuby(final String literal) throws XNIException {
			if (literal == null || literal.isEmpty()) {
				return;
			}
			final int open = literal.indexOf('《');
			if (open < 0) {
				this.characters(literal);
				return;
			}
			int pos = 0;
			while (pos < literal.length()) {
				final int start = literal.indexOf('《', pos);
				if (start < 0) {
					break;
				}
				final int end = literal.indexOf('》', start + 1);
				if (end < 0) {
					break;
				}
				// 親文字の範囲を決める
				int baseStart = -1;
				final int bar = literal.lastIndexOf('｜', start);
				if (bar >= pos) {
					baseStart = bar + 1;
				} else {
					int i = start;
					while (i > pos && isKanji(literal.charAt(i - 1))) {
						--i;
					}
					if (i < start) {
						baseStart = i;
					}
				}
				final String reading = literal.substring(start + 1, end);
				// 親文字が無い《》、読みが空の《》、それに**｜が無いのに
				// 読みが仮名でない**《》は、ルビにせず普通の文字として出す。
				// 《》は書名などの括弧としても使われるので、仮名の読みだけを
				// ルビと見なすことで取り違えを避ける(｜を書けば何でもルビに
				// できる)
				if (baseStart < 0 || baseStart == start || reading.isEmpty()
						|| (bar < pos && !isKana(reading))) {
					this.characters(literal.substring(pos, end + 1));
					pos = end + 1;
					continue;
				}
				// 親文字の前までを普通の文字として出す(｜は出さない)
				final int plainEnd = bar >= pos ? bar : baseStart;
				this.characters(literal.substring(pos, plainEnd));
				this.start("ruby");
				this.characters(literal.substring(baseStart, start));
				this.start("rt");
				this.characters(reading);
				this.end("rt");
				this.end("ruby");
				pos = end + 1;
			}
			if (pos < literal.length()) {
				this.characters(literal.substring(pos));
			}
		}

		/** 読みが仮名だけか(長音符・中黒・濁点等を含む)。 */
		private static boolean isKana(final String text) {
			for (int i = 0; i < text.length(); ++i) {
				final char c = text.charAt(i);
				final boolean kana = (c >= 'ぁ' && c <= 'ゟ') || (c >= '゠' && c <= 'ヿ')
						|| c == 'ー' || c == '・' || c == '･';
				if (!kana) {
					return false;
				}
			}
			return true;
		}

		/** 青空文庫式ルビの親文字に数える文字(漢字・々・ヶ・〆)。 */
		private static boolean isKanji(final char c) {
			return (c >= '一' && c <= '鿿') || (c >= '㐀' && c <= '䶿') || c == '々'
					|| c == 'ヶ' || c == '〆' || c == '〇';
		}

		private void characters(final String text) throws XNIException {
			if (!text.isEmpty()) {
				this.head.characters(new XMLString(text), null);
			}
		}

		// ------------------------------------------------------------ 生HTML断片

		/**
		 * 生HTML断片をNekoHTMLスキャナで字句化し、文書レベルのイベント
		 * (startDocument等)を落として同じチェーンへ中継します。スキャナは
		 * 字句化だけを行い(タグの釣り合いは共有のTagBalancerが取る)、
		 * 設定はHTMLParserと同じ(要素名そのまま・属性名そのまま・CDATA区間)。
		 */
		private void scanFragment(final String literal) throws XNIException {
			if (literal == null || literal.isEmpty()) {
				return;
			}
			final Matcher lone = LONE_END_TAG.matcher(literal);
			if (lone.matches()) {
				// **終了タグだけの断片はスキャナに通さない**(2026-08-11)。
				// 断片ごとに独立した解析になるため、内蔵バランサから見ると
				// 対応する開始タグが無い迷子の終了タグで、黙って捨てられる
				// ——{@code </rt>}が消えてルビ文字が後続の本文まで飲み込んで
				// いた。開いた要素のスタックは共有のTagBalancerが持っているので、
				// ここは自分でイベントを起こして渡すのが正しい
				this.end(lone.group(1));
				return;
			}
			if (this.fragmentScanner == null) {
				final HTMLConfiguration config = new HTMLConfiguration();
				config.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
				config.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");
				config.setFeature("http://cyberneko.org/html/features/scanner/cdata-sections", true);
				this.fragmentRelay = new FragmentRelay(this.head);
				config.setDocumentHandler(this.fragmentRelay);
				this.fragmentScanner = config;
			}
			this.fragmentRelay.beginFragment(literal);
			try {
				this.fragmentScanner
						.parse(new XMLInputSource(null, null, null, new StringReader(literal), "UTF-8"));
			} catch (final IOException e) {
				// StringReaderからは起きない
				throw new XNIException(e);
			}
		}

		/**
		 * 断片スキャナのイベントから文書レベルのものを落とし、残りを共有
		 * チェーンへ渡すフィルタです。落とすのはstartDocument/endDocument/
		 * doctype/xmlDeclに加えて<b>html・head・body要素そのもの</b>——
		 * このfork(neko-htmlunit)はスキャナ自身がこれらを合成するため、
		 * 素通しすると断片ごとの「end body」が外側の文書のbodyを
		 * TagBalancer上で閉じてしまい、以降の内容がbodyの外に落ちる。
		 * Markdownの生HTMLブロックは定義上body内容なので、断片の中の
		 * html/head/bodyはタグが実在しても骨組みとしては意味を持たない。
		 */
		private static final class FragmentRelay extends DefaultFilter {
			/** この断片の原文に実際に書かれている終了タグの残り個数。 */
			private final java.util.Map<String, Integer> writtenEnds = new java.util.HashMap<>();

			private static final Pattern END_TAG = Pattern.compile("</\s*([a-zA-Z][a-zA-Z0-9]*)");

			/**
			 * 次に流す断片の原文を教えます。
			 *
			 * <p>
			 * <b>スキャナは断片の終わりで開いたままの要素を勝手に閉じる</b>
			 * ——このfork(neko-htmlunit)の既定構成はスキャナの後ろにバランサを
			 * 含んでおり、切り離す手段が無い(スキャナ単体の構築子は
			 * package-private)。素通しすると{@code <ruby>}だけの断片が
			 * 「開いてすぐ閉じる」空要素になり、続く親文字とルビ文字が
			 * {@code ruby}の外へ落ちる——ルビがただの文字列として本文へ
			 * 流れ込んでいた(2026-08-11、縦組み書籍で実測)。断片をまたぐ
			 * 釣り合いは<b>共有の{@link TagBalancer}</b>が取るので、ここでは
			 * 原文に書かれていない終了タグを落とす。
			 * </p>
			 */
			void beginFragment(final String literal) {
				this.writtenEnds.clear();
				final Matcher m = END_TAG.matcher(literal);
				while (m.find()) {
					this.writtenEnds.merge(m.group(1).toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
				}
			}

			FragmentRelay(final XMLDocumentHandler next) {
				this.setDocumentHandler(next);
			}

			private static boolean skeleton(final QName element) {
				final String name = element.getLocalpart();
				return "html".equalsIgnoreCase(name) || "head".equalsIgnoreCase(name)
						|| "body".equalsIgnoreCase(name);
			}

			public void startElement(QName element, XMLAttributes attributes, Augmentations augs)
					throws XNIException {
				if (!skeleton(element)) {
					super.startElement(element, attributes, augs);
				}
			}

			public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs)
					throws XNIException {
				if (!skeleton(element)) {
					super.emptyElement(element, attributes, augs);
				}
			}

			public void endElement(QName element, Augmentations augs) throws XNIException {
				if (skeleton(element)) {
					return;
				}
				final String key = element.getLocalpart().toLowerCase(java.util.Locale.ROOT);
				final Integer remaining = this.writtenEnds.get(key);
				if (remaining == null || remaining <= 0) {
					// スキャナが補った終了タグ(原文に無い)。共有のバランサに
					// 任せるので流さない
					return;
				}
				this.writtenEnds.put(key, remaining - 1);
				super.endElement(element, augs);
			}

			public void startDocument(XMLLocator locator, String encoding, NamespaceContext nscontext,
					Augmentations augs) throws XNIException {
				// 断片ごとの文書開始は流さない
			}

			public void xmlDecl(String version, String encoding, String standalone, Augmentations augs)
					throws XNIException {
				// 流さない
			}

			public void doctypeDecl(String root, String publicId, String systemId, Augmentations augs)
					throws XNIException {
				// 流さない
			}

			public void endDocument(Augmentations augs) throws XNIException {
				// 流さない
			}
		}

		// ------------------------------------------------------------ XNI→SAX

		/**
		 * TagBalancerの出力(XNI)を{@link XMLHandler}(SAXのContentHandler+
		 * LexicalHandler)へ変換する尾部です。HTML経路ではAbstractSAXParserが
		 * この役を担っている——ここでは必要なイベントだけの小さな変換で足りる。
		 */
		private static final class XniToSax implements XMLDocumentHandler {

			private final XMLHandler handler;
			private XMLDocumentSource source;

			XniToSax(final XMLHandler handler) {
				this.handler = handler;
			}

			/**
			 * 名前空間を解決します。
			 *
			 * <p>
			 * <b>名前空間の付与はHTML経路と揃えなければならない</b>
			 * (2026-08-11)。NekoHTMLのSAXParserは、フィルタ連鎖の後ろに
			 * 名前空間バインダを持っていて、接頭辞のない要素をHTMLの既定
			 * 名前空間(XHTML)へ入れてからSAXへ渡す。このブリッジには
			 * そのバインダが無いため、素のままだと名前空間なしで届き、
			 * <b>HTML固有の処理が丸ごと効かなくなる</b>——
			 * {@code HTMLCodes.code()}がXHTML名前空間でなければ
			 * {@code ANY}を返すので、ルビ({@code ruby}/{@code rt})が
			 * ただのインライン文字列として本文へ流れ込んでいた
			 * (縦組み書籍で実測)。foreign content(SVG/MathML)は
			 * 上流のフィルタが名前空間を入れているのでそのまま通す。
			 * </p>
			 */
			private static String uri(final QName name) {
				final String uri = name.getUri();
				return uri == null || uri.isEmpty() ? net.zamasoft.foliojet.xml.vocab.XHTML.URI : uri;
			}

			public void startDocument(XMLLocator locator, String encoding, NamespaceContext nscontext,
					Augmentations augs) throws XNIException {
				try {
					this.handler.startDocument();
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void xmlDecl(String version, String encoding, String standalone, Augmentations augs) {
				// 不要
			}

			public void doctypeDecl(String root, String publicId, String systemId, Augmentations augs)
					throws XNIException {
				try {
					this.handler.startDTD(root, publicId, systemId);
					this.handler.endDTD();
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void comment(XMLString text, Augmentations augs) throws XNIException {
				try {
					final String s = text.toString();
					this.handler.comment(s.toCharArray(), 0, s.length());
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void processingInstruction(String target, XMLString data, Augmentations augs) throws XNIException {
				try {
					this.handler.processingInstruction(target, data == null ? "" : data.toString());
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void startElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
				try {
					this.handler.startElement(uri(element), element.getLocalpart(), element.getRawname(),
							saxAttributes(attributes));
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
				this.startElement(element, attributes, augs);
				this.endElement(element, augs);
			}

			public void endElement(QName element, Augmentations augs) throws XNIException {
				try {
					this.handler.endElement(uri(element), element.getLocalpart(), element.getRawname());
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void characters(XMLString text, Augmentations augs) throws XNIException {
				try {
					final String s = text.toString();
					this.handler.characters(s.toCharArray(), 0, s.length());
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void startCDATA(Augmentations augs) throws XNIException {
				try {
					this.handler.startCDATA();
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void endCDATA(Augmentations augs) throws XNIException {
				try {
					this.handler.endCDATA();
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void endDocument(Augmentations augs) throws XNIException {
				try {
					this.handler.endDocument();
				} catch (final SAXException e) {
					throw new XNIException(e);
				}
			}

			public void setDocumentSource(XMLDocumentSource source) {
				this.source = source;
			}

			public XMLDocumentSource getDocumentSource() {
				return this.source;
			}

			private static org.xml.sax.Attributes saxAttributes(final XMLAttributes attributes) {
				final AttributesImpl atts = new AttributesImpl();
				if (attributes != null) {
					for (int i = 0; i < attributes.getLength(); ++i) {
						final String uri = attributes.getURI(i);
						atts.addAttribute(uri == null ? "" : uri, attributes.getLocalName(i), attributes.getQName(i),
								attributes.getType(i), attributes.getValue(i));
					}
				}
				return atts;
			}
		}
	}
}
