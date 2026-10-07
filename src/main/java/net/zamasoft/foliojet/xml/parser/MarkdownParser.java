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

import net.zamasoft.balancer.TagBalancer;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.zstream.resolver.Source;

/**
 * Parses Markdown documents with CommonMark (Markdown).
 *
 * <p>
 * Builds a syntax tree directly from the Markdown reader ({@code parseReader}, without
 * creating a source String), then walks the tree and <b>emits XNI events directly</b>
 * to {@link TagBalancer} (2026-08-10, owner decision). Uses neither an intermediate HTML
 * string, retokenization of it, nor pipes and threads, following this product's standard
 * event-to-event relay approach. The only remaining buffer is the CommonMark syntax tree
 * (unavoidable given the library's structure).
 * </p>
 *
 * <p>
 * <b>Raw HTML fragments</b> within Markdown remain strings, so tokenize only those fragments
 * with NekoHTML's scanner and relay their events to <b>the same TagBalancer</b>.
 * Centralizing the open-element stack in the balancer correctly balances {@code <div>}
 * and {@code </div>} even across separate fragments, and shares exactly the same implicit
 * closing rules (e.g., div closes p) as HTML input.
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
		// The document context defaults to ISO-8859-1. Set it even for the UTF-8 fallback,
		// or percent-encoding URIs containing non-ASCII characters (such as input.default-stylesheet)
		// fails with UnmappableCharacterException.
		ua.getDocumentContext().setEncoding(encoding);

		// Do not inject the default style (markdown-ua.css) when the user specifies
		// a custom style sheet through input.default-stylesheet (2026-08-10,
		// owner decision). The default is only an "A4 report for users who specify nothing".
		// For custom designs such as books, leaving it underneath leaks through
		// p{line-height} and page numbers, leading to competing overrides.
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
	 * Default Markdown style (A4 report, markdown-ua.css). Markdown has no presentation specification,
	 * so FolioJet provides an A4 report design by default, injected as {@code <style>} in head.
	 * Do not inject anything when {@code input.default-stylesheet} is specified.
	 * XSLT joining (join.xslt) extracts only body, so this does not affect document builds such as manuals.
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
	 * Extracts raw HTML {@code <style>} elements to hoist them into head (2026-08-10).
	 * If left in body, streaming construction has already opened the body box,
	 * so properties affecting body/html themselves, such as {@code writing-mode: vertical-rl},
	 * cannot apply retroactively (observed with Markdown manuscripts for books in vertical writing).
	 *
	 * <p>
	 * {@code <style>} occurs only as a raw HTML <b>block</b> (CommonMark type-1 HTML blocks,
	 * extending through the closing tag), so scanning only {@link HtmlBlock} nodes avoids
	 * a full-text scan. Remove a block from the tree if extraction leaves only whitespace.
	 * Append styles after the default style in occurrence order, preserving the rule that
	 * later document styles override earlier ones.
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

	// ---------------------------------------------------------------- String API

	public static String toHtml(String markdown) {
		return toHtml(markdown, true);
	}

	/**
	 * Converts Markdown source to an HTML document string that CopperPDF can interpret.
	 * A public API for Markdown-to-HTML conversion without a CopperPDF session
	 * (e.g., document-generation build tools). The conversion pipeline ({@link #parse})
	 * connects XNI events directly without an HTML string, so this is a separate path for tools
	 * (sharing the syntax tree and style hoisting).
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

	// ---------------------------------------------------------------- XNI bridge

	/**
	 * An emitter of XNI events from the syntax tree. Event flow is
	 * {@code emitter/fragment scanner → foreign filter → TagBalancer → SAX conversion →
	 * XMLHandler}; everything from the filter onward is shared with HTML input ({@link HTMLParser}).
	 */
	private static final class EventBridge {

		private final UserAgent ua;
		private final XMLHandler xmlHandler;
		private final TagBalancer balancer;
		/** Head of the chain (foreign content filter). Send all events here. */
		private final XMLDocumentHandler head;
		/** Raw HTML fragment tokenizer (created lazily and shared across fragments). */
		private HTMLConfiguration fragmentScanner;

		/** A fragment containing just one closing tag (common in Markdown inline raw HTML). */
		private static final Pattern LONE_END_TAG = Pattern
				.compile("\s*</\s*([a-zA-Z][a-zA-Z0-9]*)\s*>\s*");

		/** Fragment scanner output (supplied with each fragment's source text). */
		private FragmentRelay fragmentRelay;

		EventBridge(final UserAgent ua, final XMLHandler xmlHandler) {
			this.ua = ua;
			this.xmlHandler = xmlHandler;
			this.balancer = new TagBalancer();
			final boolean changeDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);
			// HTMLParser's foreign content filter (HTML5 namespaces for math/svg) + ElementProps switch in standards mode
			final DefaultFilter foreign = new ForeignContentFilter(ua, this.balancer, changeDefaultNamespace);
			foreign.setDocumentHandler(this.balancer);
			this.balancer.setDocumentSource(foreign);
			this.balancer.setDocumentHandler(new XniToSax(xmlHandler));
			this.head = foreign;
		}

		void emit(final Node document, final String hoistedStyles, final boolean defaultStyle)
				throws XNIException, SAXException {
			this.head.startDocument(null, "UTF-8", null, null);
			// XHTML1.0 Strict DOCTYPE: for standards-mode detection and relaxed named entity
			// reference handling during XSLT joining (join.xslt) (a contract since the HTMLParser implementation).
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

		// ------------------------------------------------------------ Syntax tree traversal

		private void emitNode(final Node node, final boolean tight) throws XNIException {
			if (node instanceof final Text text) {
				this.aozoraRuby(text.getLiteral());
			} else if (node instanceof SoftLineBreak) {
				this.characters("\n");
			} else if (node instanceof HardLineBreak) {
				this.empty("br");
			} else if (node instanceof final Paragraph p) {
				// Do not create paragraph tags inside tight lists (CommonMark convention).
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
				// No output
			} else {
				// For unknown nodes (future extensions), traverse only children.
				this.emitChildren(node, tight);
			}
		}

		private void emitChildren(final Node parent, final boolean tight) throws XNIException {
			for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
				this.emitNode(child, tight);
			}
		}

		/** Image alt text (concatenated plain text of child nodes). */
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

		// ------------------------------------------------------------ Event emission

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
		 * Expands Aozora Bunko ruby notation ({@code 漢字《かんじ》}, {@code ｜任意の語《よみ》})
		 * into ruby elements (2026-08-11, owner request).
		 *
		 * <p>
		 * Japanese manuscripts in vertical writing need direct ruby notation.
		 * Writing HTML {@code <ruby>} by hand every time is too cumbersome for manuscripts,
		 * so accept Aozora Bunko notation as a Markdown extension. Follow the original rules:
		 * </p>
		 * <ul>
		 * <li>If a sequence of kanji immediately precedes {@code 《}–{@code 》}, use it as the base text
		 * ({@code 狼狽《ろうばい》}). Count 々, ヶ, 〆, and CJK Unified Ideographs
		 * (including Extension A) as kanji.</li>
		 * <li>With {@code ｜} (full-width vertical bar), text from there to {@code 《} is the base
		 * ({@code ｜生前退位《せいぜんたいい》}, {@code ｜1970年《いちきゅうななまるねん》}).
		 * Use this for non-kanji base text.</li>
		 * <li>Emit {@code 《...》} without identifiable base text literally,
		 * to avoid breaking manuscripts that use these as quotation marks.</li>
		 * </ul>
		 *
		 * <p>
		 * Expansion produces HTML {@code <ruby>}, so CSS can adjust ruby presentation
		 * (books specify font size on {@code ruby > rt}).
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
				// Determine the base text range.
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
				// Emit 《》 as ordinary text if there is no base text, the reading is empty,
				// or **the reading is not kana and no ｜ is present**, rather than making it ruby.
				// Since 《》 also brackets book titles and similar text, recognizing only kana readings
				// as ruby avoids confusion (explicit ｜ allows any reading
				// to become ruby).
				if (baseStart < 0 || baseStart == start || reading.isEmpty()
						|| (bar < pos && !isKana(reading))) {
					this.characters(literal.substring(pos, end + 1));
					pos = end + 1;
					continue;
				}
				// Emit text preceding the base as ordinary text (omit ｜).
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

		/** Whether the reading contains only kana (including long vowel marks, middle dots, voiced marks, etc.). */
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

		/** Characters counted as Aozora Bunko ruby base text (kanji, 々, ヶ, 〆). */
		private static boolean isKanji(final char c) {
			return (c >= '一' && c <= '鿿') || (c >= '㐀' && c <= '䶿') || c == '々'
					|| c == 'ヶ' || c == '〆' || c == '〇';
		}

		private void characters(final String text) throws XNIException {
			if (!text.isEmpty()) {
				this.head.characters(new XMLString(text), null);
			}
		}

		// ------------------------------------------------------------ Raw HTML fragments

		/**
		 * Tokenizes raw HTML fragments with NekoHTML's scanner, drops document-level events
		 * (such as startDocument), and relays the rest to the same chain.
		 * The scanner only tokenizes (the shared TagBalancer balances tags),
		 * using the same settings as HTMLParser (preserved element/attribute names and CDATA sections).
		 */
		private void scanFragment(final String literal) throws XNIException {
			if (literal == null || literal.isEmpty()) {
				return;
			}
			final Matcher lone = LONE_END_TAG.matcher(literal);
			if (lone.matches()) {
				// **Do not pass closing-tag-only fragments through the scanner** (2026-08-11).
				// Each fragment is parsed independently, so the built-in balancer sees
				// an orphan closing tag without a matching start tag and silently discards it.
				// This dropped {@code </rt>}, causing ruby text to swallow subsequent body text.
				// The shared TagBalancer holds the open-element stack,
				// so explicitly emitting and forwarding the event here is correct.
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
				// Cannot occur with StringReader.
				throw new XNIException(e);
			}
		}

		/**
		 * A filter that removes document-level fragment scanner events and forwards the rest to
		 * the shared chain. Drops startDocument/endDocument/doctype/xmlDecl and <b>the html, head,
		 * and body elements themselves</b>: this fork (neko-htmlunit) synthesizes these in the scanner.
		 * Passing them through makes each fragment's "end body" close the outer document's body
		 * in TagBalancer, leaving subsequent content outside body.
		 * Markdown raw HTML blocks are body content by definition, so html/head/body within fragments
		 * have no structural meaning even if their tags are explicitly present.
		 */
		private static final class FragmentRelay extends DefaultFilter {
			/** Remaining count of closing tags actually present in this fragment's source. */
			private final java.util.Map<String, Integer> writtenEnds = new java.util.HashMap<>();

			private static final Pattern END_TAG = Pattern.compile("</\s*([a-zA-Z][a-zA-Z0-9]*)");

			/**
			 * Supplies the source text of the next fragment to relay.
			 *
			 * <p>
			 * <b>The scanner automatically closes elements still open at the fragment's end.</b>
			 * This fork's (neko-htmlunit) default configuration includes a balancer after the scanner,
			 * with no way to detach it (the scanner-only constructor is package-private).
			 * Passing everything through turns a fragment containing only {@code <ruby>} into
			 * an empty element that opens and immediately closes, leaving subsequent base and ruby text
			 * outside {@code ruby}. Ruby thus flowed into body text as plain characters
			 * (observed in a book in vertical writing, 2026-08-11).
			 * The <b>shared {@link TagBalancer}</b> balances across fragments,
			 * so discard closing tags absent from the source here.
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
					// A closing tag supplied by the scanner (absent from source). Leave balancing
					// to the shared balancer and do not forward it.
					return;
				}
				this.writtenEnds.put(key, remaining - 1);
				super.endElement(element, augs);
			}

			public void startDocument(XMLLocator locator, String encoding, NamespaceContext nscontext,
					Augmentations augs) throws XNIException {
				// Do not forward per-fragment document starts.
			}

			public void xmlDecl(String version, String encoding, String standalone, Augmentations augs)
					throws XNIException {
				// Do not forward.
			}

			public void doctypeDecl(String root, String publicId, String systemId, Augmentations augs)
					throws XNIException {
				// Do not forward.
			}

			public void endDocument(Augmentations augs) throws XNIException {
				// Do not forward.
			}
		}

		// ------------------------------------------------------------ XNI→SAX

		/**
		 * The final stage converting TagBalancer output (XNI) to {@link XMLHandler}
		 * (SAX ContentHandler + LexicalHandler). AbstractSAXParser handles this in the HTML path;
		 * here, a small converter for only the required events suffices.
		 */
		private static final class XniToSax implements XMLDocumentHandler {

			private final XMLHandler handler;
			private XMLDocumentSource source;

			XniToSax(final XMLHandler handler) {
				this.handler = handler;
			}

			/**
			 * Resolves namespaces.
			 *
			 * <p>
			 * <b>Namespace assignment must match the HTML path</b> (2026-08-11).
			 * NekoHTML's SAXParser has a namespace binder after the filter chain;
			 * it places unprefixed elements in the default HTML namespace (XHTML) before passing them to SAX.
			 * This bridge lacks that binder, so unmodified events arrive without namespaces,
			 * <b>disabling all HTML-specific processing</b>.
			 * {@code HTMLCodes.code()} returns {@code ANY} outside the XHTML namespace,
			 * so ruby ({@code ruby}/{@code rt}) flowed into body text as ordinary inline characters
			 * (observed in a book in vertical writing). Foreign content (SVG/MathML) already has
			 * namespaces from the upstream filter, so pass it through unchanged.
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
				// Unnecessary
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
