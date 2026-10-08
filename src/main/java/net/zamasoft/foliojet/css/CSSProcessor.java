package net.zamasoft.foliojet.css;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import net.zamasoft.foliojet.css.html.HTMLStyleUtils;
import net.zamasoft.foliojet.css.style.StyleBuilder;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalLink;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPdfHyperlinksHref;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Constants;
import net.zamasoft.foliojet.xml.SourceLocator;
import net.zamasoft.foliojet.xml.StyleSheetSelector;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.vocab.CSSJML;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.foliojet.plugin.PluginRegistry;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.parser.CSSException;
import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.foliojet.ua.CompatibleMode;

/**
 * Handles CSS processing instructions.
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSProcessor implements XMLHandler {
	private static final Logger LOG = Logger.getLogger(CSSProcessor.class.getName());
	private static final Attributes EMPTY_ATTRS = new AttributesImpl();

	/**
	 * An object for overriding attributes.
	 */
	private final AttributesImpl attsi = new AttributesImpl();

	private final UserAgent ua;

	private final Imposition imposition;

	private final CSSStyleSheetBuilder styleSheetBuilder;

	private final StyleApplier applier;

	/**
	 * The interface for selecting alternative styles.
	 */
	private StyleSheetSelector ssh = null;

	/**
	 * The document's default styling method.
	 */
	private String defaultStyleType = Constants.CSS_MIME_TYPE;
	private boolean firstChild = true;

	private CSSElement precedingElement = null;

	private StyleBuilder builder = null;

	private int noneStack = 0;

	/**
	 * The next ElementKey to assign (sequence number in document order, zero-based). Used
	 * as a stable key across passes in {@code CSSElement.elementKey}
	 * (see the {@code CSSElement} Javadoc).
	 */
	private long nextElementKey;

	// Inline object
	private InlineObject inlineObject = null;

	private int inlineObjectDepth = 0;

	private CSSStyle inlineObjectStyle;

	private Map<String, String> namespaces = new HashMap<String, String>();

	private SourceLocator sourceLocator;

	private Locator saxLocator;

	public CSSProcessor(UserAgent ua, Imposition imposition) {
		this.ua = ua;
		this.imposition = imposition;
		// Stylesheet carried across passes (2026-08-08). In one pass, <style> inside body
		// cannot apply retroactively to earlier elements, but with pass-count>=2, rules collected
		// in the previous pass (including STRUCTURE_SCAN) can apply from the beginning.
		// For its lifetime, see the Javadoc for UAContext.getCarriedStyleSheet.
		// One carried stylesheet per document (2026-10-08): the spine items of an EPUB are separate documents.
		final java.net.URI document = this.ua.getDocumentContext().getDocumentURI();
		CSSStyleSheet carried = this.ua.getUAContext().getCarriedStyleSheet(document);
		if (carried == null) {
			carried = new CSSStyleSheet();
			this.ua.getUAContext().setCarriedStyleSheet(document, carried);
		}
		// Element keys continue across the documents of a pass (see PassContext.getElementKeyBase)
		this.nextElementKey = this.ua.getPassContext().getElementKeyBase();
		StyleContext styleContext = new StyleContext(carried, this.ua.getUAContext().getSelectorFacts(),
				this.ua.getUAContext().getContainerFacts());

		this.styleSheetBuilder = new CSSStyleSheetBuilder(this.ua);
		this.styleSheetBuilder.setCSSStyleSheet(styleContext.styleSheet);

		this.applier = new StyleApplier(ua, styleContext);

		// UA default stylesheet (html-ua.css, cascade origin=USER_AGENT).
		// Always ranks below author stylesheets, regardless of specificity or source order.
		// (Added on 2026-07-18 to gradually move defaults expressible in CSS out of the
		// switch statement in HTMLStyle.java. See PLAN.md for details.)
		try (Reader uaStyleReader = new InputStreamReader(
				CSSProcessor.class.getResourceAsStream("html/html-ua.css"), StandardCharsets.UTF_8)) {
			InputSource uaInputSource = new InputSource(uaStyleReader);
			uaInputSource.setURI("net/zamasoft/foliojet/css/html/html-ua.css");
			uaInputSource.setEncoding("UTF-8");
			this.styleSheetBuilder.setOrigin(Origin.USER_AGENT);
			this.styleSheetBuilder.parse(uaInputSource);
		} catch (IOException | CSSException e) {
			// Not expected at runtime because this is a bundled resource
			throw new IllegalStateException("UAデフォルトスタイルシートの読み込みに失敗しました", e);
		} finally {
			this.styleSheetBuilder.setOrigin(Origin.AUTHOR);
		}

		// Default stylesheet
		String defaultStyle = UAProps.INPUT_DEFAULT_STYLESHEET.getString(this.ua);
		if (defaultStyle != null) {
			try {
				URI defaultStyleURI = URIHelper.create(this.ua.getDocumentContext().getEncoding(), defaultStyle);
				Source styleSource = this.ua.resolve(defaultStyleURI);
				try {
					InputSource inputSource = XMLUtils.toCSSInputSource(styleSource, styleSource.getEncoding());
					try {
						this.styleSheetBuilder.parse(inputSource);
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

	/** The key the next element would get: after the document, the first key of the next document. */
	public long getNextElementKey() {
		return this.nextElementKey;
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
			uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(), uri, href);
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
		if (apply) {
			try {
				final Source source = this.ua.resolve(uri);
				try {
					if (charset == null) {
						charset = this.ua.getDocumentContext().getEncoding();
					}
					this.parseStyleSheet(XMLUtils.toCSSInputSource(source, charset));
				} finally {
					this.ua.release(source);
				}
			} catch (IOException e) {
				this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, href);
			}
		}
	}

	private void parseStyleSheet(InputSource inputSource) throws IOException {
		try {
			this.styleSheetBuilder.parse(inputSource);
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

	/**
	 * Cleanup at the end of conversion (E-6 increment 3b-2). Closes the layout source spill
	 * store (temporary file). Called from the formatter's finally block
	 * (via TranscoderHandler.dispose), on both success and failure. Idempotent.
	 */
	public void dispose() {
		if (this.builder != null) {
			this.builder.closeLayoutSource();
		}
	}

	public void setDocumentLocator(Locator locator) {
		if (locator instanceof SourceLocator source) {
			this.sourceLocator = source;
		}
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
		if (this.builder != null) {
			return;
		}
		if (publicId != null) {
			if (DTDs.contains(publicId.toLowerCase())) {
				this.ua.getDocumentContext().setCompatibleMode(CompatibleMode.STRICT);
			}
		} else if (systemId == null && "html".equalsIgnoreCase(name)) {
			// The short HTML5 DOCTYPE (<!DOCTYPE html>). It has no publicId/systemId, but
			// almost all real-world HTML5 documents use it, so treat it as standards mode
			// (the tag nesting rules in html4.xml). Otherwise, legacy.xml has no definitions for
			// HTML5 elements such as VIDEO/SOURCE/TRACK, treats them as UNKNOWN, and breaks nesting.
			// (Found on 2026-07-18 through a bug where video>source+track swallowed subsequent elements.)
			this.ua.getDocumentContext().setCompatibleMode(CompatibleMode.STRICT);
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
			// External stylesheet (SPEC ASSX1.0)
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
			// Equivalent to an embedded stylesheet (<STYLE>...)
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
					inputSource.setURI(this.applier.getBaseURI().toString());
					try {
						this.parseStyleSheet(inputSource);
					} catch (IOException e) {
						throw new SAXException(e);
					}
				}
			} catch (java.text.ParseException e) {
				this.ua.message(MessageCodes.WARN_BAD_PI_SYNTAX, CSSJML.PI_STYLESHEET, data);
			}
			this.attsi.clear();
		} else if (target.equals(CSSJML.PI_DOCUMENT_INFO)) {
			// Document information (equivalent to <TITLE>..., <META name="...)
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
			// Default document encoding (equivalent to <META http-equiv="Content-Type"...)
			try {
				this.ua.getDocumentContext().setEncoding(data);
			} catch (UnsupportedEncodingException e) {
				this.ua.message(MessageCodes.WARN_UNSUPPORTED_ENCODING, data);
			}
		} else if (target.equals(CSSJML.PI_DEFAULT_STYLE_TYPE)) {
			// Default stylesheet format (equivalent to <META http-equiv="Content-Style-Type"...)
			this.defaultStyleType = data;
		} else if (target.equals(CSSJML.PI_BASE_URI)) {
			// Document base URI (equivalent to <BASE href="...)
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
			// Start of content
			this.builder = new StyleBuilder(this.applier.getStyleContext(), this.ua, this.imposition);
		}
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		this.requireBuilder();

		int charOffset = this.sourceLocator == null ? -1 : this.sourceLocator.getCharacterOffset();

		// None
		if (this.noneStack > 0) {
			this.noneStack++;
			// Consume every ElementKey in document order, regardless of visibility.
			// (STRUCTURE_SCAN includes hidden subtrees.
			// Even elements for which no CSSElement is created here must consume a key;
			// otherwise, keys for sibling elements following this display:none subtree
			// would differ from those assigned by STRUCTURE_SCAN.)
			++this.nextElementKey;
			return;
		}

		// Inline object <
		if (this.inlineObjectDepth > 0) {
			try {
				this.inlineObject.startElement(uri, lName, qName, atts);
			} catch (Exception e) {
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
			this.inlineObjectDepth++;
			// Consume every ElementKey for the same reason as noneStack
			// (see the comment below).
			++this.nextElementKey;
			return;
		}

		// Link
		String href = Constants.XLINK_HREF_ATTR.getValue(atts);

		// Classes
		String styleClass = XHTML.getAttr(atts, XHTML.CLASS_ATTR.lName);
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

		// Pseudo-classes
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
		}
		this.firstChild = true;

		// ID
		String id = XHTML.getAttr(atts, XHTML.ID_ATTR.lName);

		// Language
		String lang = XHTML.getAttr(atts, XHTML.LANG_ATTR.lName);
		if (lang != null) {
			lang = lang.trim().toLowerCase();
		}

		// Element
		if (atts.getLength() == 0) {
			atts = EMPTY_ATTRS;
		} else {
			atts = new AttributesImpl(atts);
		}

		// Link inheritance
		URI link = null;
		CSSStyle parentStyle = this.builder.getCurrentStyle();
		if (href != null) {
			try {
				OutputPdfHyperlinksHref conf = UAProps.OUTPUT_PDF_HYPERLINKS_HREF.get(ua);
				if (conf == OutputPdfHyperlinksHref.RELATIVE || href.startsWith("#")) {
					// Relative address
					link = URIHelper.create(this.ua.getDocumentContext().getEncoding(), href);
					if (link.isAbsolute()) {
						link = this.ua.getDocumentContext().getBaseURI().relativize(link);
					}
				} else {
					// Absolute address
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
			// Language is inherited through the document tree (xml:lang / :lang() scope is the subtree).
			loca = parentStyle == null ? null : parentStyle.getCSSElement().lang;
		} else if (lang.equals("ja")) {
			loca = Locale.JAPANESE;
		} else if (lang.equals("en")) {
			loca = Locale.ENGLISH;
		} else {
			// Interpret as BCP-47 (2026-08-31). `new Locale("zh-hans")` did not decompose
			// the tag and instead set the language to "zh-hans", so neither `:lang(zh)`,
			// language-specific font chains, nor `en-US` hyphenation applied.
			// Treat unparseable values as language names as before.
			final Locale tagged = Locale.forLanguageTag(lang);
			loca = tagged.getLanguage().isEmpty() ? new Locale(lang) : tagged;
		}

		// Directionality for :dir(). Determining the first strong directional character for dir="auto"
		// requires lookahead and is out of scope (falls through to the inherited value; see CSS-SUPPORT.md).
		String dirAttr = XHTML.getAttr(atts, XHTML.DIR_ATTR.lName);
		final String dir;
		if (dirAttr != null && dirAttr.equalsIgnoreCase("ltr")) {
			dir = "ltr";
		} else if (dirAttr != null && dirAttr.equalsIgnoreCase("rtl")) {
			dir = "rtl";
		} else {
			dir = parentStyle == null ? null : parentStyle.getCSSElement().dir;
		}

		CSSElement ce = new CSSElement(uri, lName, id, styleClasses, pseudoClasses, loca, dir, atts,
				this.precedingElement, charOffset, this.nextElementKey++);
		this.precedingElement = null;

		// Build the style
		CSSStyle style = CSSStyle.getCSSStyle(this.ua, parentStyle, ce);
		if (parentStyle == null) {
			this.ua.getDocumentContext().setRootStyle(style);
		}
		this.applier.startStyle(style);
		if (link != null) {
			CSSJInternalLink.set(style, link);
		}

		// display: none;
		short display = Display.get(style);
		if (display == DisplayValue.NONE) {
			// Do not display
			this.applier.endStyle();
			this.noneStack = 1;
			return;
		}

		// Inline object
		InlineObjectFactory factory = PluginRegistry.getInstance().search(InlineObjectFactory.class, ce);
		if (factory != null) {
			if (this.inlineObject == null) {
				this.inlineObject = factory.createInlineObject();
				this.inlineObject.setDocumentLocator(this.saxLocator);
			}
			// **Always include the default namespace declaration** (2026-08-06).
			// SVG written directly in HTML omits `xmlns`, because the HTML parser
			// is required to supply it implicitly. However, the downstream builder
			// (Batik's SAXSVGDocumentFactory) determines element types by **examining
			// the xmlns attribute**. Without the declaration, its contents become
			// GenericElement instances, and `GenericElement cannot be cast to
			// SVGOMSVGElement` prevents the entire SVG from rendering.
			// This caused an inconsistency where only markup with an explicit `xmlns` worked.
			final boolean hasDefaultNamespace = this.namespaces.containsKey("")
					|| atts.getIndex("http://www.w3.org/2000/xmlns/", "xmlns") >= 0
					|| atts.getIndex("xmlns") >= 0;
			final boolean addDefaultNamespace = !hasDefaultNamespace && uri != null && !uri.isEmpty();
			if (!this.namespaces.isEmpty() || addDefaultNamespace) {
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
				if (addDefaultNamespace) {
					this.attsi.addAttribute("http://www.w3.org/2000/xmlns/", "xmlns", "xmlns", "CDATA", uri);
				}
			}
			// Resolve currentColor in inline SVG (2026-08-07). Write the computed color
			// from the HTML cascade to the root of the extracted SVG document
			// as a presentation attribute. The SVG document is detached
			// from the HTML document's style context, so without this,
			// icons with fill="currentColor" lose their inherited color and turn black.
			if ("svg".equalsIgnoreCase(lName) && atts.getValue("color") == null) {
				if (atts != this.attsi) {
					this.attsi.clear();
					this.attsi.setAttributes(atts);
					atts = this.attsi;
				}
				final net.zamasoft.pdfg2d.gc.paint.Color color = net.zamasoft.foliojet.css.impl.property.text.CSSColor
						.get(style);
				this.attsi.addAttribute("", "color", "color", "CDATA",
						"rgb(" + Math.round(color.getRed() * 255f) + "," + Math.round(color.getGreen() * 255f) + ","
								+ Math.round(color.getBlue() * 255f) + ")");
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
			if (this.inlineObject instanceof StyleAwareInlineObject styleAware) {
				// Style context for resolving var() in author CSS with the custom
				// properties at this SVG's location (SVGAuthorCss.toCssText)
				styleAware.setHostStyle(style);
			}
			return;
		}

		this.builder.startStyle(style);
		{
			// Alternative text
			String text = CSSJInternalImage.getText(style);
			if (text != null && text.length() > 0) {
				char[] ch = text.toCharArray();
				this.builder.characters(-1, ch, 0, ch.length);
			}
			if (CSSJInternalImage.getImage(style) != null) {
				// Ignore the contents of image tags
				this.noneStack = 1;
			} else if (net.zamasoft.foliojet.css.impl.property.box.ContentVisibility
					.get(style) == net.zamasoft.foliojet.css.value.ContentVisibilityValue.HIDDEN) {
				// content-visibility:hidden: retain the element's own box
				// and omit only its contents from layout (the same mechanism used to ignore image tag contents;
				// paired with the fall-through condition in endElement).
				this.noneStack = 1;
			}
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (len == 0) {
			return;
		}
		int charOffset = this.sourceLocator == null ? -1 : this.sourceLocator.getCharacterOffset();
		if (charOffset != -1) {
			charOffset -= len;
		}
		if (this.inlineObjectDepth > 0) {
			// Inline markup such as SVG
			try {
				this.inlineObject.characters(ch, off, len);
			} catch (Exception e) {
				LOG.log(Level.FINE, "", e);
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
		} else if (this.noneStack <= 0 && len > 0) {
			// Normal text
			if (this.builder != null) {
				this.builder.characters(charOffset, ch, off, len);
			}
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (this.inlineObjectDepth > 0) {
			try {
				this.inlineObject.endElement(uri, lName, qName);
			} catch (Exception e) {
				LOG.log(Level.FINE, "", e);
				this.ua.message(MessageCodes.WARN_BAD_INLINE_OBJECT, e.getMessage());
			}
			this.inlineObjectDepth--;
			if (this.inlineObjectDepth == 0) {
				// End the plugin
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
					if (this.inlineObject instanceof net.zamasoft.foliojet.objects.svg.SVGInlineObject svg
							&& svg.getRunningSource() != null) {
						this.inlineObjectStyle.set(CSSJInternalImage.INFO,
								new net.zamasoft.foliojet.css.value.internal.CSSJImageValue(image, svg.getRunningSource()));
					}
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
					// Occurs when the root element is none
					return;
				}
				if (CSSJInternalImage.getImage(currentStyle) == null
						&& net.zamasoft.foliojet.css.impl.property.box.ContentVisibility
								.get(currentStyle) != net.zamasoft.foliojet.css.value.ContentVisibilityValue.HIDDEN) {
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
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
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

	public void endPrefixMapping(String prefix) throws SAXException {
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


}
