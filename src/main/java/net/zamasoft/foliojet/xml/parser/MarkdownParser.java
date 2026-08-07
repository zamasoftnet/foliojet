package net.zamasoft.foliojet.xml.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.commonmark.node.Node;
import org.commonmark.renderer.html.HtmlRenderer;
import org.xml.sax.SAXException;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceValidity;
import net.zamasoft.zstream.resolver.util.UnknownSourceValidity;

/**
 * CommonMark(Markdown)によりMarkdown文書を解析します。
 * <p>
 * Markdown原文をHTMLへ変換し(CommonMarkはブロック内の生HTMLをそのまま
 * 通過させる仕様のため、これによりMarkdown中に埋め込まれた生HTMLもあわせて
 * 扱える)、既存の{@link HTMLParser}に委譲します。CSSの既定スタイル
 * (html-ua.css)・セレクタ・その他のHTML処理はすべてHTMLParserの経路を
 * 共有するため、Markdown専用の実装は原文の変換のみです。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class MarkdownParser implements Parser {
	public void parse(final UserAgent ua, final Source source, XMLHandler xmlHandler) throws SAXException, IOException {
		String encoding = source.getEncoding();
		if (encoding != null) {
			ua.getDocumentContext().setEncoding(encoding);
		} else {
			encoding = "UTF-8";
		}

		final String markdown = read(source, encoding);
		final String html = toHtml(markdown);

		final Source htmlSource = new StringHtmlSource(source.getURI(), html);
		new HTMLParser().parse(ua, htmlSource, xmlHandler);
	}

	/**
	 * Markdown原文を、CopperPDFが解釈できるHTML文書(文字列)へ変換します。
	 * <p>
	 * CommonMarkはブロック内の生HTMLをそのまま通過させる仕様のため、Markdown中に
	 * 埋め込まれた生HTMLもあわせてそのまま出力に含まれる。{@link #parse}が内部的に
	 * 使う変換そのものであり、CopperPDFのセッションを介さずMarkdown→HTML変換だけを
	 * 行いたい場合(文書生成のビルドツール等)にも利用できるよう公開している。
	 *
	 * @param markdown Markdown原文
	 * @return `&lt;html&gt;...&lt;/html&gt;` 形式のHTML文書文字列(UTF-8を前提とした
	 *         `meta charset` を含む)
	 */
	private static final java.util.List<org.commonmark.Extension> EXTENSIONS = java.util.List
			.of(org.commonmark.ext.gfm.tables.TablesExtension.create());

	/**
	 * Markdown既定スタイル(A4レポート、markdown-ua.css)。Markdownには表示仕様が
	 * ないため、FolioJetの既定としてA4レポート向けのデザインを与える。head内の
	 * {@code <style>}として注入するので、文書側の生HTMLの{@code <style>}(bodyに
	 * 現れる)が後勝ちで個別に上書きできる。XSLT結合(join.xslt)はbodyだけを
	 * 取り出すため、マニュアル等のドキュメントビルドには影響しない。
	 * CSS側は山括弧・アンパサンド禁止(XHTML要素内容として埋め込むため)。
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

	public static String toHtml(String markdown) {
		final org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder().extensions(EXTENSIONS)
				.inlineParserFactory(CjkFriendlyInlineParser::new).build();
		final Node document = parser.parse(markdown);
		final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).build();
		final String body = renderer.render(document);

		// DOCTYPEはbareな<!DOCTYPE html>ではなくXHTML1.0 StrictのSYSTEM識別子付きにする。
		// これにより、XSLT結合(join.xslt/Saxonのdocument())時にXML宣言済み外部DTDが
		// 存在する文書として扱われ、MathML用の名前付き実体参照(&PlusMinus;等、HTML5
		// パーサーの内蔵テーブルには含まれるがXHTML1.0 DTD自体には無い)を含む文書でも
		// 「外部サブセット未読のため未定義実体はエラーにしない」というXML 1.0仕様の
		// 緩和規定(WFC: Entity Declared)が働き、SAXParseExceptionにならない
		// (2026-07-19、4910_mathml.mdの&PlusMinus;/&InvisibleTimes;で実際に発生した)。
		return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><style type=\"text/css\">"
				+ DEFAULT_STYLE + "</style></head><body>" + body + "</body></html>";
	}

	private static String read(Source source, String encoding) throws IOException {
		if (source.isReader()) {
			try (Reader reader = source.getReader()) {
				return readAll(reader);
			}
		}
		try (InputStream in = source.getInputStream(); Reader reader = new InputStreamReader(in, encoding)) {
			return readAll(reader);
		}
	}

	private static String readAll(Reader reader) throws IOException {
		StringBuilder buff = new StringBuilder();
		char[] buffer = new char[4096];
		for (int len = reader.read(buffer); len != -1; len = reader.read(buffer)) {
			buff.append(buffer, 0, len);
		}
		return buff.toString();
	}

	/**
	 * Markdownから変換したHTML文字列を、既存のHTMLParserへ渡すためのメモリ内Sourceです。
	 * 元のMarkdownソースのURIを引き継ぐため、相対リンク・画像パスは元の場所を基準に解決されます。
	 */
	private static class StringHtmlSource implements Source {
		private final URI uri;
		private final String content;

		StringHtmlSource(URI uri, String content) {
			this.uri = uri;
			this.content = content;
		}

		public boolean exists() {
			return true;
		}

		public boolean isInputStream() {
			return true;
		}

		public InputStream getInputStream() {
			return new java.io.ByteArrayInputStream(this.content.getBytes(StandardCharsets.UTF_8));
		}

		public boolean isReader() {
			return true;
		}

		public Reader getReader() {
			return new StringReader(this.content);
		}

		public boolean isFile() {
			return false;
		}

		public File getFile() {
			throw new UnsupportedOperationException();
		}

		public SourceValidity getValidity() {
			return UnknownSourceValidity.SHARED_INSTANCE;
		}

		public URI getURI() {
			return this.uri;
		}

		public String getMimeType() {
			return "text/html";
		}

		public String getEncoding() {
			return "UTF-8";
		}

		public long getLength() {
			return -1;
		}

		public void close() {
			// メモリ内容なので何もしない
		}
	}
}
