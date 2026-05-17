package jp.cssj.homare.css;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/* NoAndroid begin */
import org.apache.xerces.xni.XMLLocator;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import jp.cssj.homare.css.html.HTMLCodes;
import jp.cssj.homare.css.html.HTMLStyleUtils;
import jp.cssj.homare.css.style.StyleBuilder;
import jp.cssj.homare.css.value.DisplayValue;
import jp.cssj.homare.impl.css.property.Display;
import jp.cssj.homare.impl.css.property.internal.CSSJInternalImage;
import jp.cssj.homare.impl.css.property.internal.CSSJInternalLink;
import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.style.imposition.Imposition;
import jp.cssj.homare.ua.DocumentContext;
import jp.cssj.homare.ua.UserAgent;
import jp.cssj.homare.ua.props.OutputPdfHyperlinksHref;
import jp.cssj.homare.ua.props.UAProps;
import jp.cssj.homare.xml.Constants;
import jp.cssj.homare.xml.StyleSheetSelector;
import jp.cssj.homare.xml.XMLHandler;
import jp.cssj.homare.xml.ext.CSSJML;
import jp.cssj.homare.xml.util.XMLUtils;
import jp.cssj.homare.xml.xhtml.XHTML;
import jp.cssj.plugin.PluginLoader;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.sac.css.CSSException;
import net.zamasoft.pdfg2d.sac.css.InputSource;
import net.zamasoft.pdfg2d.sac.parser.Parser;

/**
 * CSSに関する処理命令を処理します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSProcessor.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class CSSProcessor implements XMLHandler {
	private static final Logger LOG = Logger.getLogger(CSSProcessor.class.getName());
	private static final Attributes EMPTY_ATTRS = new AttributesImpl();

	/**
	 * 属性を上書きするためのオブジェクトです。
	 */
	private final AttributesImpl attsi = new AttributesImpl();

	private final UserAgent ua;

	private final Imposition imposition;

	private final CSSStyleSheetBuilder styleSheetBuilder;

	private final Parser styleSheetParser;

	private final StyleApplier applier;

	/**
	 * alternativeスタイルを選択するインターフェースです。
	 */
	private StyleSheetSelector ssh = null;

	/**
	 * ドキュメントのデフォルトのスタイル付け方式です。
	 */
	private String defaultStyleType = Constants.CSS_MIME_TYPE;
	private boolean firstChild = true;

	private CSSElement precedingElement = null;

	private StyleBuilder builder = null;

	private int noneStack = 0;

	// Legacy
	private List<Event> events = null;

	private List<StartElement> eventStack = null;

	private int eventIndex;

	private boolean cssjFirstChild = false;

	// インラインオブジェクト
	private InlineObject inlineObject = null;

	private int inlineObjectDepth = 0;

	private CSSStyle inlineObjectStyle;

	private Map<String, String> namespaces = new HashMap<String, String>();

	private XMLLocator xniLocator;

	private Locator saxLocator;

	public CSSProcessor(UserAgent ua, Imposition imposition) {
		this.ua = ua;
		this.imposition = imposition;
		StyleContext styleContext = new StyleContext(new CSSStyleSheet());

		this.styleSheetBuilder = new CSSStyleSheetBuilder(this.ua);
		this.styleSheetBuilder.setCSSStyleSheet(styleContext.styleSheet);

		this.applier = new StyleApplier(ua, styleContext);
		this.styleSheetParser = new Parser();
		this.styleSheetParser.setDocumentHandler(this.styleSheetBuilder);

		// デフォルトのスタイルシート
		String defaultStyle = UAProps.INPUT_DEFAULT_STYLESHEET.getString(this.ua);
		if (defaultStyle != null) {
			try {
				URI defaultStyleURI = URIHelper.create(this.ua.getDocumentContext().getEncoding(), defaultStyle);
				Source styleSource = this.ua.resolve(defaultStyleURI);
				try {
					InputSource inputSource = XMLUtils.toSACInputSource(styleSource, styleSource.getEncoding(), null,
							null);
					try {
						this.styleSheetParser.parseStyleSheet(inputSource);
					} catch (CSSException e) {
						this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, inputSource.getURI(), e.getMessage());
					}
				} finally {
					this.ua.release(styleSource);
				}
			} catch (URISyntaxException e) {
				this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, defaultStyle);
			} catch (IOException e) {
				this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, defaultStyle);
			}
		}
	}

	private void linkCSS(String href, String type, String title, String mediaTypes, String charset, boolean alternate) {
		if (href == null) {
			return;
		}
		if (type == null) {
			type = this.defaultStyleType;
		}
		if (!MimeTypeHelper.equals(type, Constants.CSS_MIME_TYPE)) {
			return;
		}

		URI uri;
		try {
			uri = this.applier.getBaseURI();
			// System.err.println(uri+";"+href);
			uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(), uri, href);
			// System.err.println(uri+";"+href);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, href);
			return;
		}
		boolean apply;
		if (this.ssh != null) {
			apply = this.ssh.stylesheet(uri, Constants.CSS_MIME_TYPE, title, mediaTypes, alternate);
		} else {
			apply = !alternate;
		}
		if (apply && !this.ua.is(mediaTypes)) {
			apply = false;
		}
		// System.err.println("LINK: "+uri+";"+mediaTypes+";"+apply);
		if (apply) {
			try {
				final Source source = this.ua.resolve(uri);
				try {
					if (charset == null) {
						charset = this.ua.getDocumentContext().getEncoding();
					}
					this.parseStyleSheet(XMLUtils.toSACInputSource(source, source.getEncoding(), mediaTypes, title),
							charset);
				} finally {
					this.ua.release(source);
				}
			} catch (IOException e) {
				this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, href);
			}
		}
	}

	private void parseStyleSheet(InputSource inputSource, String defaultCharset) throws IOException {
		try {
			if (defaultCharset != null) {
				this.styleSheetParser.setDefaultCharset(defaultCharset);
			}
			this.styleSheetParser.parseStyleSheet(inputSource);
		} catch (CSSException e) {
			this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, inputSource.getURI(), e.getMessage());
		}
	}

	public void setStyleSheetSelector(StyleSheetSelector ssh) {
		this.ssh = ssh;
	}

	public void startDocument() throws SAXException {
		// unused
	}

	public void endDocument() throws SAXException {
		this.requireBuilder();
		this.builder.finish();
	}

	public void setDocumentLocator(Locator locator) {
		// SAXのLocatorではCharacterOffsetを取得できないのでJNIのLocatorを使う
		this.xniLocator = jp.cssj.homare.xml.Parser.XML_LOCATOR.get();
		this.saxLocator = locator;
	}

	private static final Set<String> DTDs = new HashSet<String>();

	static {
		DTDs.add("-//W3C//DTD HTML 4.01//EN".toLowerCase());
		DTDs.add("-//W3C//DTD HTML 4.01 Transitional//EN".toLowerCase());
		DTDs.add("-//W3C//DTD XHTML 1.0 Transitional//EN".toLowerCase());
		DTDs.add("-//W3C//DTD XHTML 1.0 Strict//EN".toLowerCase());
		DTDs.add("-//W3C//DTD HTML 4.01//EN".toLowerCase());
		DTDs.add("-//W3C//DTD XHTML 1.1//EN".toLowerCase());
	}

	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		if (publicId == null) {
			return;
		}
		if (this.builder == null) {
			if (DTDs.contains(publicId.toLowerCase())) {
				this.ua.getDocumentContext().setCompatibleMode(DocumentContext.CM_STRICT);
			}
		}
	}

	public void endDTD() throws SAXException {
		// ignore
	}

	public void comment(char[] ch, int off, int len) throws SAXException {
		// ignore
	}

	public void startCDATA() throws SAXException {
		// ignore
	}

	public void endCDATA() throws SAXException {
		// ignore
	}

	public void startEntity(String name) throws SAXException {
		// ignore
	}

	public void skippedEntity(String name) throws SAXException {
		// ignore
	}

	public void endEntity(String name) throws SAXException {
		// ignore
	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		// ignore
	}

	public void processingInstruction(String target, String data) throws SAXException {
		if (target.equals(Constants.LINK_PI)) {
			// 外部スタイルシート (SPEC ASSX1.0)
			try {
				XMLUtils.parsePseudoAttributes(data.toCharArray(), 0, data.length(), this.attsi);
				String type = this.attsi.getValue("type");
				String href = this.attsi.getValue("href");
				String title = this.attsi.getValue("title");
				String mediaTypes = this.attsi.getValue("media");
				String charset = this.attsi.getValue("charset");
				String alternateStr = this.attsi.getValue("alternate");

				boolean alternate;
				if (alternateStr != null && alternateStr.equals("yes")) {
					alternate = true;
				} else {
					alternate = false;
				}
				this.linkCSS(href, type, title, mediaTypes, charset, alternate);
			} catch (java.text.ParseException e) {
				this.ua.message(MessageCodes.WARN_BAD_PI_SYNTAX, Constants.LINK_PI, data);
			}
			this.attsi.clear();
		} else if (target.equals(CSSJML.PI_STYLESHEET)) {
			// 文書内スタイルシート(<STYLE>...)に相当
			try {
				String styleSheet = XMLUtils.parsePseudoAttributes(data.toCharArray(), 0, data.length(), this.attsi);
				String media = this.attsi.getValue("media");
				String type = this.attsi.getValue("type");

				if (type == null) {
					type = this.defaultStyleType;
				}
				if (type.equals(Constants.CSS_MIME_TYPE) && this.ua.is(media)) {
					InputSource inputSource = new InputSource(new StringReader(styleSheet));
					inputSource.setEncoding(this.ua.getDocumentContext().getEncoding());
					inputSource.setMedia(media);
					inputSource.setURI(this.applier.getBaseURI().toString());
					try {
						this.parseStyleSheet(inputSource, null);
					} catch (IOException e) {
						throw new SAXException(e);
					}
				}
			} catch (java.text.ParseException e) {
				this.ua.message(MessageCodes.WARN_BAD_PI_SYNTAX, CSSJML.PI_STYLESHEET, data);
			}
			this.attsi.clear();
		} else if (target.equals(CSSJML.PI_DOCUMENT_INFO)) {
			// 文書情報(<TITLE>..., <META name="... に相当)
			try {
				XMLUtils.parsePseudoAttributes(data.toCharArray(), 0, data.length(), this.attsi);
				String name = this.attsi.getValue("name");
				String value = this.attsi.getValue("value");
				this.ua.meta(name, value);
			} catch (java.text.ParseException e) {
				this.ua.message(MessageCodes.WARN_BAD_PI_SYNTAX, CSSJML.PI_DOCUMENT_INFO, data);
			}
			this.attsi.clear();
		} else if (target.equals(CSSJML.PI_DEFAULT_ENCODING)) {
			// デフォルトの文書エンコーディング(<META http-equiv="Content-Type"... に相当)
			try {
				this.ua.getDocumentContext().setEncoding(data);
			} catch (UnsupportedEncodingException e) {
				this.ua.message(MessageCodes.WARN_UNSUPPORTED_ENCODING, data);
			}
		} else if (target.equals(CSSJML.PI_DEFAULT_STYLE_TYPE)) {
			// デフォルトのスタイルシート形式(<META http-equiv="Content-Style-Type"... に相当)
			this.defaultStyleType = data;
		} else if (target.equals(CSSJML.PI_BASE_URI)) {
			// 文書のベースURI(<BASE href="... に相当)
			URI uri;
			try {
				uri = this.applier.getBaseURI();
				uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(), uri, data);
				this.ua.getDocumentContext().setBaseURI(uri);
				this.applier.setBaseURI(uri);
			} catch (URISyntaxException e) {
				ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
			}
		}
	}

	private void requireBuilder() {
		if (this.builder == null) {
			// 内容開始
			this.builder = new StyleBuilder(this.applier.getStyleContext(), this.ua, this.imposition);
		}
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		// System.err.println("CSSP: "+qName);
		this.requireBuilder();

		int charOffset = this.xniLocator.getCharacterOffset();
		// System.out.println(charOffset+"/"+qName);

		if (this.events != null) {
			StartElement e = new StartElement(charOffset, uri, lName, qName, atts);
			this.eventStack.add(e);
			this.events.add(e);
		} else {
			this._startElement(charOffset, uri, lName, qName, atts);
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (len == 0) {
			return;
		}

		int charOffset = this.xniLocator.getCharacterOffset() - len;
		// System.out.println(charOffset+"/"+new String(ch, off, len));

		if (this.events != null) {
			this.events.add(new Characters(charOffset, ch, off, len));
		} else {
			this._characters(charOffset, ch, off, len);
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		// System.err.println("CSSP: /"+qName);
		if (this.events != null) {
			if (this.eventStack.size() == 1) {
				for (this.eventIndex = 0; this.eventIndex < this.events.size(); ++this.eventIndex) {
					Event e = (Event) this.events.get(this.eventIndex);
					e.doEvent(this);
				}
				this._endElement(uri, lName, qName);
				this.events = null;
				this.eventStack = null;
			} else {
				StartElement e = (StartElement) this.eventStack.remove(this.eventStack.size() - 1);
				e.endIndex = this.events.size();
				this.events.add(new EndElement(e));
			}
		} else {
			this._endElement(uri, lName, qName);
		}
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		if (this.events != null) {
			this.events.add(new StartPrefixMapping(prefix, uri));
		} else {
			this._startPrefixMapping(prefix, uri);
		}
	}

	public void endPrefixMapping(String prefix) throws SAXException {
		if (this.events != null) {
			this.events.add(new EndPrefixMapping(prefix));
		} else {
			this._endPrefixMapping(prefix);
		}
	}

	void _startElement(int charOffset, String uri, String lName, String qName, Attributes atts) throws SAXException {
		// System.err.println("CSSP_: "+lName+"/"+qName);

		// None
		if (this.noneStack > 0) {
			this.noneStack++;
			return;
		}

		// インラインオブジェクト<
		if (this.inlineObjectDepth > 0) {
			try {
				this.inlineObject.startElement(uri, lName, qName, atts);
			} catch (Exception e) {
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
			this.inlineObjectDepth++;
			return;
		}

		// リンク
		String href = Constants.XLINK_HREF_ATTR.getValue(atts);

		// クラス
		String styleClass = atts.getValue(XHTML.URI, XHTML.CLASS_ATTR.lName);
		String styleClasses[];
		if (styleClass == null) {
			styleClasses = null;
		} else if (styleClass.indexOf(' ') == -1) {
			styleClasses = new String[] { styleClass };
		} else {
			List<String> list = new ArrayList<String>();
			for (StringTokenizer i = new StringTokenizer(styleClass, " "); i.hasMoreTokens();) {
				list.add(i.nextToken());
			}
			styleClasses = (String[]) list.toArray(new String[list.size()]);
		}

		// 擬似クラス
		byte[] pseudoClasses;
		{
			int len = 0;
			boolean htmlRoot = false;
			if (XHTML.HTML_ELEM.equals(uri, lName)) {
				htmlRoot = true;
				++len;
			}
			if (this.firstChild) {
				++len;
			}
			if (href != null) {
				++len;
			}
			if (this.cssjFirstChild) {
				++len;
			}
			if (len == 0) {
				pseudoClasses = null;
			} else {
				pseudoClasses = new byte[len];
			}

			if (htmlRoot) {
				pseudoClasses[--len] = CSSElement.PC_ROOT;
			}
			if (this.firstChild) {
				pseudoClasses[--len] = CSSElement.PC_FIRST_CHILD;
			}
			if (href != null) {
				pseudoClasses[--len] = CSSElement.PC_LINK;
			}
			if (this.cssjFirstChild) {
				pseudoClasses[--len] = CSSElement.PC_CSSJ_FIRST_CHILD;
			}
		}
		this.firstChild = true;

		// ID
		String id = atts.getValue(XHTML.URI, XHTML.ID_ATTR.lName);

		// 言語
		String lang = atts.getValue(XHTML.URI, XHTML.LANG_ATTR.lName);
		if (lang != null) {
			lang = lang.trim().toLowerCase();
		}

		// 要素
		if (atts.getLength() == 0) {
			atts = EMPTY_ATTRS;
		} else {
			atts = new AttributesImpl(atts);
		}

		// リンクの継承
		URI link = null;
		CSSStyle parentStyle = this.builder.getCurrentStyle();
		if (href != null) {
			try {
				short conf = UAProps.OUTPUT_PDF_HYPERLINKS_HREF.getCode(ua);
				if (conf == OutputPdfHyperlinksHref.RELATIVE || href.startsWith("#")) {
					// 相対アドレス
					link = URIHelper.create(this.ua.getDocumentContext().getEncoding(), href);
					if (link.isAbsolute()) {
						link = this.ua.getDocumentContext().getBaseURI().relativize(link);
					}
				} else {
					// 絶対アドレス
					URI base;
					String str = UAProps.OUTPUT_PDF_HYPERLINKS_BASE.getString(this.ua);
					if (str == null) {
						base = this.ua.getDocumentContext().getBaseURI();
					} else {
						try {
							base = URIHelper.create(this.ua.getDocumentContext().getEncoding(), str);
						} catch (URISyntaxException e) {
							this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PDF_HYPERLINKS_BASE.name,
									str);
							base = this.ua.getDocumentContext().getBaseURI();
						}
					}
					link = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(), base, href);
				}

				AttributesImpl attsi = (AttributesImpl) atts;
				Constants.XLINK_HREF_ATTR.removeValue(attsi);
			} catch (URISyntaxException e) {
				this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
			}
		} else if (parentStyle != null) {
			link = CSSJInternalLink.get(parentStyle);
			if (atts.getLength() == 0) {
				atts = new AttributesImpl(atts);
			}
		}
		if (link != null) {
			AttributesImpl attsi = (AttributesImpl) atts;
			Constants.XLINK_HREF_ATTR.addValue(attsi, link.toASCIIString());
		}

		final Locale loca;
		if (lang == null) {
			loca = null;
		} else if (lang.equals("ja")) {
			loca = Locale.JAPANESE;
		} else if (lang.equals("en")) {
			loca = Locale.ENGLISH;
		} else {
			loca = new Locale(lang);
		}

		CSSElement ce = new CSSElement(uri, lName, id, styleClasses, pseudoClasses, loca, atts, this.precedingElement,
				charOffset);
		this.precedingElement = null;

		// スタイル構築
		CSSStyle style = CSSStyle.getCSSStyle(this.ua, parentStyle, ce);
		this.applier.startStyle(style);
		if (link != null) {
			CSSJInternalLink.set(style, link);
		}

		// display: none;
		short display = Display.get(style);
		if (display == DisplayValue.NONE) {
			// 表示しない
			this.applier.endStyle();
			this.noneStack = 1;
			return;
		}

		// インラインオブジェクト
		InlineObjectFactory factory = (InlineObjectFactory) PluginLoader.getPluginLoader()
				.search(InlineObjectFactory.class, ce);
		if (factory != null) {
			if (this.inlineObject == null) {
				this.inlineObject = factory.createInlineObject();
				this.inlineObject.setDocumentLocator(this.saxLocator);
			}
			if (!this.namespaces.isEmpty()) {
				this.attsi.clear();
				this.attsi.setAttributes(atts);
				atts = this.attsi;
				for (Iterator<?> i = this.namespaces.entrySet().iterator(); i.hasNext();) {
					Entry<?, ?> entry = (Entry<?, ?>) i.next();
					String prefix = (String) entry.getKey();
					String namespaceURI = (String) entry.getValue();
					this.attsi.addAttribute("http://www.w3.org/2000/xmlns/", prefix,
							prefix.length() == 0 ? "xmlns" : "xmlns:" + prefix, "CDATA", namespaceURI);
				}
			}
			try {
				this.inlineObject.startDocument();
				this.inlineObject.startElement(uri, lName, qName, atts);
			} catch (Exception e) {
				LOG.log(Level.FINE, "", e);
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
			this.inlineObjectDepth = 1;
			HTMLStyleUtils.applyWidthHeight(lName, style);
			this.inlineObjectStyle = style;
			return;
		}

		this.builder.startStyle(style);
		{
			// 代替テキスト
			String text = CSSJInternalImage.getText(style);
			if (text != null && text.length() > 0) {
				char[] ch = text.toCharArray();
				this.builder.characters(-1, ch, 0, ch.length);
			}
			if (CSSJInternalImage.getImage(style) != null) {
				// 画像タグの内部は無視する
				this.noneStack = 1;
			}
		}
	}

	void _characters(int charOffset, char[] ch, int off, int len) throws SAXException {
		assert len > 0;
		if (this.inlineObjectDepth > 0) {
			// SVG等のインラインマークアップ
			try {
				this.inlineObject.characters(ch, off, len);
			} catch (Exception e) {
				LOG.log(Level.FINE, "", e);
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
		} else if (this.noneStack <= 0 && len > 0) {
			// 通常のテキスト
			if (this.cssjFirstChild) {
				for (int i = 0; i < len; ++i) {
					if (!Character.isWhitespace(ch[i + off])) {
						this.cssjFirstChild = true;
						break;
					}
				}
			}
			if (this.builder != null) {
				this.builder.characters(charOffset, ch, off, len);
			}
		}
	}

	void _endElement(String uri, String lName, String qName) throws SAXException {
		// System.err.println("CSSP_: /"+lName+"/"+this.ignoreIndex);
		if (this.inlineObjectDepth > 0) {
			try {
				this.inlineObject.endElement(uri, lName, qName);
			} catch (Exception e) {
				LOG.log(Level.FINE, "", e);
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
			this.inlineObjectDepth--;
			if (this.inlineObjectDepth == 0) {
				// プラグイン終了
				Image image = null;
				try {
					this.inlineObject.endDocument();
					image = this.inlineObject.getImage(this.ua);
				} catch (Exception e) {
					LOG.log(Level.FINE, "", e);
					this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
				}
				if (image != null) {
					CSSJInternalImage.setImage(this.inlineObjectStyle, image);
					this.builder.startStyle(this.inlineObjectStyle);
					this.builder.endStyle();
				} else {
					this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, "インラインオブジェクトを読み込めませんでした");
				}
				this.inlineObjectStyle = null;
				this.inlineObject = null;
				this.applier.endStyle();
			}
			return;
		}
		CSSStyle currentStyle = this.builder.getCurrentStyle();
		if (this.noneStack > 0) {
			--this.noneStack;
			if (this.noneStack == 0) {
				if (currentStyle == null) {
					// ルート要素がnoneの場合に発生する
					return;
				}
				if (CSSJInternalImage.getImage(currentStyle) == null) {
					return;
				}
			} else {
				return;
			}
		}

		this.builder.endStyle();
		this.applier.endStyle();
		this.precedingElement = currentStyle.getExplicitStyle().getCSSElement();
		this.firstChild = false;
		this.cssjFirstChild = false;
	}

	void _startPrefixMapping(String prefix, String uri) throws SAXException {
		this.namespaces.put(prefix, uri);
		if (this.inlineObjectDepth > 0) {
			try {
				this.inlineObject.startPrefixMapping(prefix, uri);
			} catch (Exception e) {
				LOG.log(Level.FINE, "", e);
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
		}
	}

	void _endPrefixMapping(String prefix) throws SAXException {
		this.namespaces.remove(prefix);
		if (this.inlineObjectDepth > 0) {
			try {
				this.inlineObject.endPrefixMapping(prefix);
			} catch (Exception e) {
				LOG.log(Level.FINE, "", e);
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
		}
	}

	/**
	 * MSIEがタグを無視する条件 TODO: タグバランサに移行
	 * 
	 * @param parentStyle
	 * @param ce
	 * @return
	 */
	static boolean msieIgnore(CSSStyle parentStyle, CSSElement ce) {
		if (parentStyle == null) {
			return false;
		}
		short code = HTMLCodes.code(ce);
		if (code == HTMLCodes.ANY) {
			return true;
		}
		switch (code) {
		case HTMLCodes.IMG:
		case HTMLCodes.OBJECT:
		case HTMLCodes.EMBED:
		case HTMLCodes.INPUT:
		case HTMLCodes.FORM:
		case HTMLCodes.SELECT:
		case HTMLCodes.TEXTAREA:
			return false;
		}
		switch (Display.get(parentStyle)) {
		case DisplayValue.INLINE_TABLE:
		case DisplayValue.TABLE:
			switch (code) {
			case HTMLCodes.CAPTION:
			case HTMLCodes.COLGROUP:
			case HTMLCodes.COL:
			case HTMLCodes.TBODY:
			case HTMLCodes.THEAD:
			case HTMLCodes.TFOOT:
			case HTMLCodes.TR:
			case HTMLCodes.TH:
			case HTMLCodes.TD:
				return false;
			default:
				return true;
			}

		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
			switch (code) {
			case HTMLCodes.CAPTION:
			case HTMLCodes.TR:
			case HTMLCodes.TH:
			case HTMLCodes.TD:
				return false;
			default:
				return true;
			}

		case DisplayValue.TABLE_COLUMN_GROUP:
		case DisplayValue.TABLE_COLUMN:
			switch (code) {
			case HTMLCodes.CAPTION:
			case HTMLCodes.COL:
			case HTMLCodes.COLGROUP:
				return false;
			default:
				return true;
			}

		case DisplayValue.TABLE_ROW:
			switch (code) {
			case HTMLCodes.CAPTION:
			case HTMLCodes.TD:
			case HTMLCodes.TH:
				return false;
			default:
				return true;
			}
		}
		switch (code) {
		case HTMLCodes.TD:
		case HTMLCodes.TH:
			switch (Display.get(parentStyle)) {
			case DisplayValue.INLINE_TABLE:
			case DisplayValue.TABLE:
			case DisplayValue.TABLE_ROW_GROUP:
			case DisplayValue.TABLE_HEADER_GROUP:
			case DisplayValue.TABLE_FOOTER_GROUP:
			case DisplayValue.TABLE_ROW:
				return false;
			default:
				return true;

			}
		case HTMLCodes.TR:
			switch (Display.get(parentStyle)) {
			case DisplayValue.INLINE_TABLE:
			case DisplayValue.TABLE:
			case DisplayValue.TABLE_ROW_GROUP:
			case DisplayValue.TABLE_HEADER_GROUP:
			case DisplayValue.TABLE_FOOTER_GROUP:
				return false;
			default:
				return true;

			}
		case HTMLCodes.TBODY:
		case HTMLCodes.THEAD:
		case HTMLCodes.TFOOT:
			switch (Display.get(parentStyle)) {
			case DisplayValue.INLINE_TABLE:
			case DisplayValue.TABLE:
				return false;
			default:
				return true;

			}
		case HTMLCodes.COL:
		case HTMLCodes.COLGROUP:
			switch (Display.get(parentStyle)) {
			case DisplayValue.INLINE_TABLE:
			case DisplayValue.TABLE:
			case DisplayValue.TABLE_COLUMN_GROUP:
			case DisplayValue.TABLE_COLUMN:
				return false;
			default:
				return true;

			}
		}
		return false;
	}

	static interface Event {
		static final byte START_ELEMENT = 1;
		static final byte CHARACTERS = 2;
		static final byte END_ELEMENT = 3;
		static final byte START_PREFIX_MAPPING = 4;
		static final byte END_PREFIX_MAPPING = 5;

		byte getType();

		void doEvent(CSSProcessor p) throws SAXException;
	}

	static class StartElement implements Event {
		final int charOffset;
		final String uri, lName, qName;
		final Attributes atts;
		int endIndex;

		StartElement(int charOffset, String uri, String lName, String qName, Attributes atts) {
			this.charOffset = charOffset;
			this.uri = uri;
			this.lName = lName;
			this.qName = qName;
			this.atts = atts.getLength() == 0 ? EMPTY_ATTRS : new AttributesImpl(atts);
		}

		public void doEvent(CSSProcessor p) throws SAXException {
			p._startElement(this.charOffset, this.uri, this.lName, this.qName, this.atts);
		}

		public byte getType() {
			return START_ELEMENT;
		}
	}

	static class Characters implements Event {
		final int charOffset;
		final char[] ch;

		Characters(int charOffset, char[] ch, int off, int len) {
			this.charOffset = charOffset;
			this.ch = new char[len];
			System.arraycopy(ch, off, this.ch, 0, len);
		}

		public void doEvent(CSSProcessor p) throws SAXException {
			p._characters(this.charOffset, this.ch, 0, this.ch.length);
		}

		public boolean isWhitespace() {
			for (int i = 0; i < this.ch.length; ++i) {
				if (!Character.isWhitespace(this.ch[i])) {
					return false;
				}
			}
			return true;
		}

		public byte getType() {
			return CHARACTERS;
		}
	}

	static class EndElement implements Event {
		final StartElement e;

		EndElement(StartElement e) {
			this.e = e;
		}

		public void doEvent(CSSProcessor p) throws SAXException {
			p._endElement(e.uri, e.lName, e.qName);
		}

		public byte getType() {
			return END_ELEMENT;
		}
	}

	static class StartPrefixMapping implements Event {
		final String prefix, uri;

		StartPrefixMapping(String prefix, String uri) {
			this.uri = uri;
			this.prefix = prefix;
		}

		public void doEvent(CSSProcessor p) throws SAXException {
			p._startPrefixMapping(this.prefix, this.uri);
		}

		public byte getType() {
			return START_PREFIX_MAPPING;
		}
	}

	static class EndPrefixMapping implements Event {
		final String prefix;

		EndPrefixMapping(String prefix) {
			this.prefix = prefix;
		}

		public void doEvent(CSSProcessor p) throws SAXException {
			p._endPrefixMapping(this.prefix);
		}

		public byte getType() {
			return END_PREFIX_MAPPING;
		}
	}
}
