package net.zamasoft.balancer;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.balancer.ElementProps.ElementProp;

import org.apache.xerces.util.XMLAttributesImpl;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLDocumentHandler;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLComponentManager;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLDocumentFilter;
import org.apache.xerces.xni.parser.XMLDocumentSource;
import org.cyberneko.html.HTMLComponent;
import org.cyberneko.html.HTMLElements;
import org.cyberneko.html.filters.DefaultFilter;
import org.cyberneko.html.filters.NamespaceBinder;
import org.cyberneko.html.xercesbridge.XercesBridge;

/**
 * Balances tags in an HTML document. This component receives document events
 * and tries to correct many common mistakes that human (and computer) HTML
 * document authors make. This tag balancer can:
 * <ul>
 * <li>add missing parent elements;
 * <li>automatically close elements with optional end tags; and
 * <li>handle mis-matched inline element tags.
 * </ul>
 * <p>
 * This component recognizes the following features:
 * <ul>
 * <li>http://cyberneko.org/html/features/augmentations
 * <li>http://cyberneko.org/html/features/report-errors
 * <li>http://cyberneko.org/html/features/balance-tags/document-fragment
 * <li>http://cyberneko.org/html/features/balance-tags/ignore-outside-content
 * </ul>
 * <p>
 * This component recognizes the following properties:
 * <ul>
 * <li>http://cyberneko.org/html/properties/names/elems
 * <li>http://cyberneko.org/html/properties/names/attrs
 * <li>http://cyberneko.org/html/properties/error-reporter
 * </ul>
 * 
 * @see HTMLElements
 * 
 * @author Andy Clark
 * @author Marc Guillemot
 * @author Tatsuhiko Miyabe
 * 
 * @version $Id: TagBalancer.java 1539 2018-01-20 06:37:23Z miyabe $
 */
public class TagBalancer implements XMLDocumentFilter, HTMLComponent {
	//
	// Constants
	//

	// features

	/** Namespaces. */
	protected static final String NAMESPACES = "http://xml.org/sax/features/namespaces";

	/** Document fragment balancing only. */
	protected static final String DOCUMENT_FRAGMENT = "http://cyberneko.org/html/features/balance-tags/document-fragment";

	/** Recognized features. */
	private static final String[] RECOGNIZED_FEATURES = { NAMESPACES, DOCUMENT_FRAGMENT, };

	/** Recognized features defaults. */
	private static final Boolean[] RECOGNIZED_FEATURES_DEFAULTS = { null, Boolean.FALSE };

	// properties

	/** Modify HTML element names: { "upper", "lower", "default" }. */
	protected static final String NAMES_ELEMS = "http://cyberneko.org/html/properties/names/elems";

	/** Modify HTML attribute names: { "upper", "lower", "default" }. */
	protected static final String NAMES_ATTRS = "http://cyberneko.org/html/properties/names/attrs";

	/** Recognized properties. */
	private static final String[] RECOGNIZED_PROPERTIES = { NAMES_ELEMS, NAMES_ATTRS };

	/** Recognized properties defaults. */
	private static final Object[] RECOGNIZED_PROPERTIES_DEFAULTS = { null, null, };

	// modify HTML names

	/** Don't modify HTML names. */
	protected static final short NAMES_NO_CHANGE = 0;

	/** Match HTML element names. */
	protected static final short NAMES_MATCH = 0;

	/** Uppercase HTML names. */
	protected static final short NAMES_UPPERCASE = 1;

	/** Lowercase HTML names. */
	protected static final short NAMES_LOWERCASE = 2;

	//
	// Data
	//

	// features

	/** Namespaces. */
	protected boolean fNamespaces;

	/** Document fragment balancing only. */
	protected boolean fDocumentFragment;

	// properties

	private final XMLAttributes fEmptyAttrs = new XMLAttributesImpl();

	/** Modify HTML element names. */
	protected short fNamesElems;

	/** Modify HTML attribute names. */
	protected short fNamesAttrs;

	protected ElementProps fElementProps;

	// connections

	/** The document source. */
	protected XMLDocumentSource fDocumentSource;

	/** The document handler. */
	protected DefaultFilter fDocumentHandler = new DefaultFilter();

	// state

	/** The element stack. */
	protected final InfoStack fElementStack = new InfoStack();

	/** True if seen anything. Important for xml declaration. */
	protected boolean fSeenAnything;

	/** True if root element has been seen. */
	protected boolean fSeenDoctype;

	/** True if root element has been seen. */
	protected boolean fSeenRootElement;

	/** True if seen &lt;head&lt; element. */
	protected boolean fSeenHeadElement;

	/** True if seen &lt;body&lt; element. */
	protected boolean fSeenBodyElement;

	// temp vars

	private XNIRecorder fRecorder = new XNIRecorder();

	public TagBalancer() {
		this.fElementProps = ElementProps.getElementProps("legacy.xml");
	}

	public void setElementProps(ElementProps props) {
		this.fElementProps = props;
	}

	public ElementProps getElementProps() {
		return this.fElementProps;
	}

	//
	// HTMLComponent methods
	//

	/** Returns the default state for a feature. */
	public Boolean getFeatureDefault(String featureId) {
		int length = RECOGNIZED_FEATURES != null ? RECOGNIZED_FEATURES.length : 0;
		for (int i = 0; i < length; i++) {
			if (RECOGNIZED_FEATURES[i].equals(featureId)) {
				return RECOGNIZED_FEATURES_DEFAULTS[i];
			}
		}
		return null;
	} // getFeatureDefault(String):Boolean

	/** Returns the default state for a property. */
	public Object getPropertyDefault(String propertyId) {
		int length = RECOGNIZED_PROPERTIES != null ? RECOGNIZED_PROPERTIES.length : 0;
		for (int i = 0; i < length; i++) {
			if (RECOGNIZED_PROPERTIES[i].equals(propertyId)) {
				return RECOGNIZED_PROPERTIES_DEFAULTS[i];
			}
		}
		return null;
	} // getPropertyDefault(String):Object

	//
	// XMLComponent methods
	//

	/** Returns recognized features. */
	public String[] getRecognizedFeatures() {
		return RECOGNIZED_FEATURES;
	} // getRecognizedFeatures():String[]

	/** Returns recognized properties. */
	public String[] getRecognizedProperties() {
		return RECOGNIZED_PROPERTIES;
	} // getRecognizedProperties():String[]

	/** Resets the component. */
	public void reset(XMLComponentManager manager) throws XMLConfigurationException {
		// get features
		this.fNamespaces = manager.getFeature(NAMESPACES);
		this.fDocumentFragment = manager.getFeature(DOCUMENT_FRAGMENT);

		// get properties
		this.fNamesElems = getNamesValue(String.valueOf(manager.getProperty(NAMES_ELEMS)));
		this.fNamesAttrs = getNamesValue(String.valueOf(manager.getProperty(NAMES_ATTRS)));
	} // reset(XMLComponentManager)

	/** Sets a feature. */
	public void setFeature(String featureId, boolean state) throws XMLConfigurationException {

	} // setFeature(String,boolean)

	/** Sets a property. */
	public void setProperty(String propertyId, Object value) throws XMLConfigurationException {

		if (propertyId.equals(NAMES_ELEMS)) {
			this.fNamesElems = getNamesValue(String.valueOf(value));
			return;
		}

		if (propertyId.equals(NAMES_ATTRS)) {
			this.fNamesAttrs = getNamesValue(String.valueOf(value));
			return;
		}

	} // setProperty(String,Object)

	//
	// XMLDocumentSource methods
	//

	/** Sets the document handler. */
	public void setDocumentHandler(XMLDocumentHandler handler) {
		final RecoderFilter filter = new RecoderFilter();
		filter.setDocumentHandler(handler);
		this.fDocumentHandler = filter;
	} // setDocumentHandler(XMLDocumentHandler)

	// @since Xerces 2.1.0

	/** Returns the document handler. */
	public XMLDocumentHandler getDocumentHandler() {
		return this.fDocumentHandler.getDocumentHandler();
	} // getDocumentHandler():XMLDocumentHandler

	//
	// XMLDocumentHandler methods
	//

	// since Xerces-J 2.2.0

	/** Start document. */
	public void startDocument(XMLLocator locator, String encoding, NamespaceContext nscontext, Augmentations augs)
			throws XNIException {
		// reset state
		this.fElementStack.top = 0;
		this.fSeenAnything = false;
		this.fSeenDoctype = false;
		this.fSeenRootElement = false;
		this.fSeenHeadElement = false;
		this.fSeenBodyElement = false;
		if (!this.fDocumentFragment) {
			this.fRecorder.mark();
		}

		// pass on event
		XercesBridge.getInstance().XMLDocumentHandler_startDocument(this.fDocumentHandler, locator, encoding, nscontext,
				augs);

	} // startDocument(XMLLocator,String,Augmentations)

	// old methods

	/** XML declaration. */
	public void xmlDecl(String version, String encoding, String standalone, Augmentations augs) throws XNIException {
		this.fDocumentHandler.getDocumentHandler().xmlDecl(version, encoding, standalone, augs);
	} // xmlDecl(String,String,String,Augmentations)

	/** Doctype declaration. */
	public void doctypeDecl(String rootElementName, String publicId, String systemId, Augmentations augs)
			throws XNIException {
		this.fSeenAnything = true;
		if (!this.fSeenRootElement && !this.fSeenDoctype) {
			this.fSeenDoctype = true;
			this.fDocumentHandler.getDocumentHandler().doctypeDecl(rootElementName, publicId, systemId, augs);
		}
	} // doctypeDecl(String,String,String,Augmentations)

	/** End document. */
	public void endDocument(Augmentations augs) throws XNIException {
		// handle empty document
		if (!this.fSeenBodyElement && !this.fDocumentFragment) {
			final QName body = this.createQName("body");
			this.startElement(body, null, null);
		}

		// pop all remaining elements
		int length = this.fElementStack.top;
		for (int i = 0; i < length; i++) {
			final Info info = this.fElementStack.pop();
			this.callEndElement(info.qname, null);
		}

		// call handler
		this.fDocumentHandler.getDocumentHandler().endDocument(augs);
	} // endDocument(Augmentations)

	/** Comment. */
	public void comment(XMLString text, Augmentations augs) throws XNIException {
		this.fSeenAnything = true;
		if (!this.fSeenBodyElement) {
			this.fDocumentHandler.getDocumentHandler().comment(text, augs);
			return;
		}
		this.fDocumentHandler.comment(text, augs);
	} // comment(XMLString,Augmentations)

	/** Processing instruction. */
	public void processingInstruction(String target, XMLString data, Augmentations augs) throws XNIException {
		this.fSeenAnything = true;
		if (!this.fSeenBodyElement) {
			this.fDocumentHandler.getDocumentHandler().processingInstruction(target, data, augs);
			return;
		}

		this.fDocumentHandler.processingInstruction(target, data, augs);
	} // processingInstruction(String,XMLString,Augmentations)

	private boolean inHead() {
		for (int i = this.fElementStack.top - 1; i >= 0; --i) {
			Info parent = this.fElementStack.data[i];
			if (parent.prop.code == HTMLElements.HEAD) {
				return true;
			}
		}
		return false;
	}

	/** Start element. */
	public void startElement(final QName element, XMLAttributes attrs, final Augmentations augs) throws XNIException {
		this.fSeenAnything = true;
		final ElementProp prop = this.getElementProp(element);

		if (prop.code == HTMLElements.HTML) {
			if (this.fSeenRootElement) {
				return;
			}
			this.directStartElement(prop, element, attrs, augs);
			this.fSeenRootElement = true;
			return;
		}
		if (prop.code == HTMLElements.HEAD) {
			if (this.fSeenHeadElement) {
				return;
			}
			if (!this.fSeenRootElement) {
				final QName html = this.createQName("html");
				this.directStartElement(this.getElementProp(html), html, this.emptyAttributes(), null);
				this.fSeenRootElement = true;
			}
			this.directStartElement(prop, element, attrs, augs);
			this.fSeenHeadElement = true;
			return;
		}

		if (prop.is(ElementProps.FLAG_BODY)) {
			// bodyの開始
			if (this.fSeenBodyElement) {
				return;
			}
			if (this.fDocumentFragment) {
				this.directStartElement(prop, element, attrs, augs);
				return;
			}
			if (!this.fSeenRootElement) {
				final QName html = this.createQName("html");
				this.directStartElement(this.getElementProp(html), html, this.emptyAttributes(), null);
				this.fSeenRootElement = true;
			}
			if (!this.fSeenHeadElement) {
				XMLDocumentHandler handler = this.fDocumentHandler.getDocumentHandler();
				final QName head = this.createQName("head");
				handler.startElement(head, this.emptyAttributes(), null);
				handler.endElement(head, null);
				this.fSeenHeadElement = true;
			}
			while (this.fElementStack.peek().prop.code != HTMLElements.HTML) {
				this.directEndElement(null);
			}
			this.directStartElement(prop, element, attrs, augs);
			this.fSeenBodyElement = true;
			this.fRecorder.refeed(this.fDocumentHandler);
			return;
		}

		// ヘッダを補完する
		if (this.fSeenHeadElement) {
			if (!this.fDocumentFragment && !prop.is(ElementProps.FLAG_HEAD)) {
				final QName body = this.createQName("body");
				this.startElement(body, null, null);
			}
		} else if (prop.is(ElementProps.FLAG_HEAD)) {
			final QName head = this.createQName("head");
			this.startElement(head, this.emptyAttributes(), null);
		}

		// 親要素のチェック
		if (this.fElementStack.top >= 1) {
			{
				Info parent = this.fElementStack.peek();
				if (!this.fSeenBodyElement && (this.inHead() || parent.prop.is(ElementProps.FLAG_HEAD))) {
					this.directStartElement(prop, element, attrs, augs);
					return;
				}
			}

			if (prop.contains(ElementProps.SET_DIGS_FOR)) {
				// 必要な親が存在するまで掘り下げる
				int close = 0;
				for (int i = this.fElementStack.top - 1; i >= 0; --i) {
					final Info info = this.fElementStack.data[i];
					if (info.prop.code == HTMLElements.HTML || info.prop.code == HTMLElements.HEAD) {
						// HTMLタグはDocumentEndまで終わらせない
						// HEADタグは内容の開始まで終わらせない
						break;
					}
					if (prop.contains(ElementProps.SET_DIGS_FOR, info.prop.code)) {
						close = this.fElementStack.top - i - 1;
						break;
					}
				}
				for (int i = 0; i < close; ++i) {
					final Info info = this.fElementStack.pop();
					this.callEndElement(info.qname, null);
				}
			}

			if (prop.contains(ElementProps.SET_OPEN_CLOSES)) {
				// 指定した親を閉じる
				int close = 0;
				for (int i = this.fElementStack.top - 1; i >= 0; --i) {
					final Info info = this.fElementStack.data[i];
					if (info.prop.code == HTMLElements.HTML || info.prop.code == HTMLElements.HEAD) {
						// HTMLタグはDocumentEndまで終わらせない
						// HEADタグは内容の開始まで終わらせない
						break;
					}
					if (prop.contains(ElementProps.SET_OPEN_CLOSES, info.prop.code)) {
						close = this.fElementStack.top - i;
					}
					if (prop.contains(ElementProps.SET_STOP_CLOSE_BY)
							&& prop.contains(ElementProps.SET_STOP_CLOSE_BY, info.prop.code)) {
						// 探索中止
						break;
					}
					if (prop.contains(ElementProps.SET_DIGS_FOR)
							&& prop.contains(ElementProps.SET_DIGS_FOR, info.prop.code)) {
						// 探索中止
						break;
					}
				}
				for (int i = 0; i < close; ++i) {
					final Info info = this.fElementStack.pop();
					this.callEndElement(info.qname, null);
				}
			}
		}
		if (this.fElementStack.top >= 1) {
			{
				Info parent = this.fElementStack.peek();
				// 親要素の直下の開始タグを無視
				if (parent.prop.contains(ElementProps.SET_DISCARDS_OPEN)
						&& parent.prop.contains(ElementProps.SET_DISCARDS_OPEN, prop.code)) {
					return;
				}
			}

			{
				final Info parent = this.fElementStack.peek();
				// 必要な親を補完する
				if (prop.contains(ElementProps.SET_INSERT_PARENTS)) {
					if (!prop.contains(ElementProps.SET_INSERT_PARENTS, parent.prop.code)) {
						String parentName = HTMLElements.getElement(prop.tags[ElementProps.SET_INSERT_PARENTS][0]).name;
						final QName parentTag = this.createQName(parentName);
						this.startElement(parentTag, this.emptyAttributes(), null);
					}
				}
			}
		}

		// 閉じて開く
		List<Info> continueTags = null;
		if (prop.contains(ElementProps.SET_OPEN_SPLITS)) {
			while (this.fElementStack.top >= 1) {
				Info info = this.fElementStack.peek();
				if (!prop.contains(ElementProps.SET_OPEN_SPLITS, info.prop.code)) {
					break;
				}
				info = this.fElementStack.pop();
				this.callEndElement(info.qname, null);
				if (continueTags == null) {
					continueTags = new ArrayList<Info>();
				}
				continueTags.add(info);
			}
		}

		// call handler
		if (prop.is(ElementProps.FLAG_EMPTY)) {
			if (attrs == null) {
				attrs = this.emptyAttributes();
			}
			this.fDocumentHandler.emptyElement(element, attrs, augs);
		} else {
			this.fElementStack.push(new Info(prop, element, attrs));
			if (attrs == null) {
				attrs = this.emptyAttributes();
			}
			this.fDocumentHandler.getDocumentHandler().startElement(element, attrs, augs);
		}

		if (continueTags != null) {
			for (int i = continueTags.size() - 1; i >= 0; --i) {
				Info info = (Info) continueTags.get(i);
				this.startElement(info.qname, info.atts, null);
			}
		}
	}// startElement(QName,XMLAttributes,Augmentations)

	private QName createQName(String tagName) {
		tagName = modifyName(tagName, this.fNamesElems);
		return new QName(null, tagName, tagName, null);
	}

	/** Empty element. */
	public void emptyElement(final QName elem, XMLAttributes attrs, Augmentations augs) throws XNIException {
		this.startElement(elem, attrs, augs);
		final ElementProp prop = this.getElementProp(elem);
		if (!prop.is(ElementProps.FLAG_EMPTY)) {
			this.endElement(elem, augs);
		}
	} // emptyElement(QName,XMLAttributes,Augmentations)

	/** Start entity. */
	public void startGeneralEntity(String name, XMLResourceIdentifier id, String encoding, Augmentations augs)
			throws XNIException {
		this.fSeenAnything = true;
		// call handler
		this.fDocumentHandler.startGeneralEntity(name, id, encoding, augs);
	} // startGeneralEntity(String,XMLResourceIdentifier,String,Augmentations)

	/** Text declaration. */
	public void textDecl(String version, String encoding, Augmentations augs) throws XNIException {
		this.fSeenAnything = true;
		// call handler
		this.fDocumentHandler.textDecl(version, encoding, augs);
	} // textDecl(String,String,Augmentations)

	/** End entity. */
	public void endGeneralEntity(String name, Augmentations augs) throws XNIException {
		// call handler
		this.fDocumentHandler.endGeneralEntity(name, augs);
	} // endGeneralEntity(String,Augmentations)

	/** Start CDATA section. */
	public void startCDATA(Augmentations augs) throws XNIException {
		this.fSeenAnything = true;
		if (!this.fSeenBodyElement && this.fElementStack.top >= 1) {
			Info parent = this.fElementStack.peek();
			if (parent.prop.is(ElementProps.FLAG_HEAD)) {
				this.fDocumentHandler.getDocumentHandler().startCDATA(augs);
				return;
			}
		}

		// call handler
		this.fDocumentHandler.startCDATA(augs);
	} // startCDATA(Augmentations)

	/** End CDATA section. */
	public void endCDATA(Augmentations augs) throws XNIException {
		if (!this.fSeenBodyElement && this.fElementStack.top >= 1) {
			Info parent = this.fElementStack.peek();
			if (parent.prop.is(ElementProps.FLAG_HEAD)) {
				this.fDocumentHandler.getDocumentHandler().endCDATA(augs);
				return;
			}
		}

		// call handler
		this.fDocumentHandler.endCDATA(augs);
	} // endCDATA(Augmentations)

	/** Characters. */
	public void characters(final XMLString text, final Augmentations augs) throws XNIException {
		if (!this.fDocumentFragment) {
			// handle bare characters
			if (!this.fSeenAnything) {
				if (isWhitespace(text)) {
					return;
				}
			}
		}
		this.fSeenAnything = true;

		// 親要素をチェック
		if (this.fElementStack.top >= 1) {
			Info parent = this.fElementStack.peek();
			// 必要な親を補完する
			if (parent.prop.contains(ElementProps.SET_INSERT_BY_TEXT)) {
				if (!isWhitespace(text)) {
					final String parentName = HTMLElements
							.getElement(parent.prop.tags[ElementProps.SET_INSERT_BY_TEXT][0]).name;
					final QName parentTag = this.createQName(parentName);
					this.startElement(parentTag, this.emptyAttributes(), null);
				}
			}
			if (parent.prop.is(ElementProps.FLAG_IGNORE_TEXT)) {
				if (!isWhitespace(text)) {
					return;
				}
			}
			if (parent.prop.is(ElementProps.FLAG_CLOSE_BY_TEXT)) {
				if (!isWhitespace(text)) {
					parent = this.fElementStack.pop();
					this.callEndElement(parent.qname, null);
				}
			}
			if (!this.fSeenBodyElement) {
				if (parent.prop.is(ElementProps.FLAG_HEAD)) {
					this.fDocumentHandler.getDocumentHandler().characters(text, augs);
					return;
				}
				if (parent.prop.code == HTMLElements.HTML || this.inHead()) {
					if (isWhitespace(text)) {
						this.fDocumentHandler.getDocumentHandler().characters(text, augs);
						return;
					}
				}
			}
		}

		// call handler
		this.fDocumentHandler.characters(text, augs);
	} // characters(XMLString,Augmentations)

	/** Ignorable whitespace. */
	public void ignorableWhitespace(XMLString text, Augmentations augs) throws XNIException {
		this.characters(text, augs);
	} // ignorableWhitespace(XMLString,Augmentations)

	/** End element. */
	public void endElement(QName element, final Augmentations augs) throws XNIException {
		// get element information
		ElementProp prop = this.getElementProp(element);

		if (prop.code == HTMLElements.HTML || prop.code == HTMLElements.HEAD) {
			// HTMLタグはDocumentEndまで終わらせない
			// HEADタグは内容の開始まで終わらせない
			return;
		}

		if (!this.fSeenBodyElement && this.fElementStack.top > 0) {
			// ルート要素の中でBODYの外の処理
			Info parent = this.fElementStack.peek();
			if (this.inHead() || (parent.prop.is(ElementProps.FLAG_HEAD) && !parent.prop.is(ElementProps.FLAG_EMPTY))) {
				boolean match = false;
				for (int i = this.fElementStack.top - 1; i >= 0; i--) {
					Info info = this.fElementStack.data[i];
					if (info.prop.code == HTMLElements.HEAD) {
						break;
					}
					if (info.prop.code == prop.code) {
						match = true;
						break;
					}
				}
				if (!match) {
					return;
				}
				for (;;) {
					this.directEndElement(augs);
					if (parent.prop.code == prop.code) {
						break;
					}
					parent = this.fElementStack.peek();
				}
				return;
			}
		}

		// 対応する開始タグを調べる
		int close = 0;
		for (int i = this.fElementStack.top - 1; i >= 0; i--) {
			final Info info = this.fElementStack.data[i];
			if (info.prop.code == HTMLElements.HTML || info.prop.code == HTMLElements.HEAD) {
				// HTMLタグはDocumentEndまで終わらせない
				// HEADタグは内容の開始まで終わらせない
				break;
			}
			if (info.prop.code == prop.code) {
				// 同じタグ
				close = this.fElementStack.top - i;
				break;
			}
			if (prop.contains(ElementProps.SET_ALTERNATES)
					&& prop.contains(ElementProps.SET_ALTERNATES, info.prop.code)) {
				// 代替可能タグ
				close = this.fElementStack.top - i;
				prop = info.prop;
				element = info.qname;
				break;
			}
			if (prop.contains(ElementProps.SET_CLOSE_CLOSES)
					&& prop.contains(ElementProps.SET_CLOSE_CLOSES, info.prop.code)) {
				// 親タグを閉じる
				continue;
			}
			if (prop.contains(ElementProps.SET_STOP_CLOSE_BY)
					&& prop.contains(ElementProps.SET_STOP_CLOSE_BY, info.prop.code)) {
				// 探索中止
				break;
			}
			if (prop.contains(ElementProps.SET_DIGS_FOR) && prop.contains(ElementProps.SET_DIGS_FOR, info.prop.code)) {
				// 探索中止
				break;
			}
		}

		// 対応する開始タグがない場合
		if (close == 0) {
			if (prop.is(ElementProps.FLAG_END_TO_EMPTY)) {
				// 終了タグを空タグに置換する
				this.startElement(element, null, null);
				if (!prop.is(ElementProps.FLAG_EMPTY)) {
					this.endElement(element, augs);
				}
			}
			return;
		}

		// 終了タグを無視する
		Info parent = this.fElementStack.peek();
		if (parent.prop.code != prop.code) {
			if (parent.prop.contains(ElementProps.SET_DISCARDS_CLOSE)
					&& parent.prop.contains(ElementProps.SET_DISCARDS_CLOSE, prop.code)) {
				return;
			}
		}

		// タグの終了
		List<Info> continueTags = null;
		for (int i = 0; i < close; i++) {
			final Info info = this.fElementStack.pop();
			this.callEndElement(info.qname, null);
			if (i == close - 1) {
				break;
			}
			// 親タグを閉じる
			if (prop.contains(ElementProps.SET_CLOSE_CLOSES)
					&& prop.contains(ElementProps.SET_CLOSE_CLOSES, parent.prop.code)) {
				continue;
			}
			if (continueTags == null) {
				continueTags = new ArrayList<Info>();
			}
			continueTags.add(info);
		}
		if (continueTags != null) {
			for (int i = continueTags.size() - 1; i >= 0; --i) {
				Info info = (Info) continueTags.get(i);
				this.startElement(info.qname, info.atts, null);
			}
		}
	} // endElement(QName,Augmentations)

	// @since Xerces 2.1.0

	/** Sets the document source. */
	public void setDocumentSource(XMLDocumentSource source) {
		fDocumentSource = source;
	} // setDocumentSource(XMLDocumentSource)

	/** Returns the document source. */
	public XMLDocumentSource getDocumentSource() {
		return this.fDocumentSource;
	} // getDocumentSource():XMLDocumentSource

	// removed since Xerces-J 2.3.0

	/** Start document. */
	public void startDocument(XMLLocator locator, String encoding, Augmentations augs) throws XNIException {
		this.startDocument(locator, encoding, null, augs);
	} // startDocument(XMLLocator,String,Augmentations)

	/** Start prefix mapping. */
	public void startPrefixMapping(String prefix, String uri, Augmentations augs) throws XNIException {
		throw new UnsupportedOperationException();
	} // startPrefixMapping(String,String,Augmentations)

	/** End prefix mapping. */
	public void endPrefixMapping(String prefix, Augmentations augs) throws XNIException {
		throw new UnsupportedOperationException();
	} // endPrefixMapping(String,Augmentations)

	//
	// Protected methods
	//

	/** Returns an HTML element. */
	protected ElementProp getElementProp(final QName elementName) {
		String name = elementName.rawname;
		if (this.fNamespaces && NamespaceBinder.XHTML_1_0_URI.equals(elementName.uri)) {
			int index = name.indexOf(':');
			if (index != -1) {
				name = name.substring(index + 1);
			}
		}
		HTMLElements.Element element = HTMLElements.getElement(name);
		ElementProp prop = this.fElementProps.getElementProp(element.code);
		return prop;
	}

	/** Call document handler start element. */
	protected final void callStartElement(final QName element, XMLAttributes attrs, final Augmentations augs)
			throws XNIException {
		if (attrs == null) {
			attrs = this.emptyAttributes();
		}
		this.fDocumentHandler.startElement(element, attrs, augs);
	} // callStartElement(QName,XMLAttributes,Augmentations)

	/** Call document handler end element. */
	protected final void callEndElement(QName element, Augmentations augs) throws XNIException {
		this.fDocumentHandler.endElement(element, augs);
	} // callEndElement(QName,Augmentations)

	protected final void directStartElement(final ElementProp prop, final QName element, XMLAttributes attrs,
			final Augmentations augs) throws XNIException {
		XMLDocumentHandler handler = this.fDocumentHandler.getDocumentHandler();
		if (attrs == null) {
			attrs = this.emptyAttributes();
		}
		if (!prop.is(ElementProps.FLAG_EMPTY)) {
			this.fElementStack.push(new Info(prop, element, null));
			handler.startElement(element, attrs, augs);
		} else {
			handler.emptyElement(element, attrs, augs);
		}
		return;
	}

	protected final void directEndElement(Augmentations augs) throws XNIException {
		final Info info = this.fElementStack.pop();
		final XMLDocumentHandler handler = this.fDocumentHandler.getDocumentHandler();
		handler.endElement(info.qname, augs);
		return;
	}

	/** Returns a set of empty attributes. */
	protected final XMLAttributes emptyAttributes() {
		this.fEmptyAttrs.removeAllAttributes();
		return this.fEmptyAttrs;
	} // emptyAttributes():XMLAttributes

	//
	// Protected static methods
	//

	/** Modifies the given name based on the specified mode. */
	protected static final String modifyName(String name, short mode) {
		switch (mode) {
		case NAMES_UPPERCASE:
			return name.toUpperCase();
		case NAMES_LOWERCASE:
			return name.toLowerCase();
		}
		return name;
	} // modifyName(String,short):String

	/**
	 * Converts HTML names string value to constant value.
	 * 
	 * @see #NAMES_NO_CHANGE
	 * @see #NAMES_LOWERCASE
	 * @see #NAMES_UPPERCASE
	 */
	protected static final short getNamesValue(String value) {
		if (value.equals("lower")) {
			return NAMES_LOWERCASE;
		}
		if (value.equals("upper")) {
			return NAMES_UPPERCASE;
		}
		return NAMES_NO_CHANGE;
	} // getNamesValue(String):short

	protected static boolean isWhitespace(final XMLString text) {
		for (int i = 0; i < text.length; i++) {
			if (!Character.isWhitespace(text.ch[text.offset + i])) {
				return false;
			}
		}
		return true;
	}

	private class RecoderFilter extends DefaultFilter {
		public void comment(XMLString text, Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.comment(text, augs);
				return;
			}
			super.comment(text, augs);
		}

		public void processingInstruction(String target, XMLString data, Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.processingInstruction(target, data, augs);
				return;
			}
			super.processingInstruction(target, data, augs);
		}

		public void startGeneralEntity(String name, XMLResourceIdentifier id, String encoding, Augmentations augs)
				throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.startGeneralEntity(name, id, encoding, augs);
				return;
			}
			super.startGeneralEntity(name, id, encoding, augs);
		}

		public void textDecl(String version, String encoding, Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.textDecl(version, encoding, augs);
				return;
			}
			super.textDecl(version, encoding, augs);
		}

		public void endGeneralEntity(String name, Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.endGeneralEntity(name, augs);
				return;
			}
			super.endGeneralEntity(name, augs);
		}

		public void startCDATA(Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.startCDATA(augs);
				return;
			}
			super.startCDATA(augs);
		}

		public void endCDATA(Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.endCDATA(augs);
				return;
			}
			super.endCDATA(augs);
		}

		public void emptyElement(final QName element, XMLAttributes attrs, final Augmentations augs)
				throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.emptyElement(element, attrs, augs);
				return;
			}
			if (attrs == null) {
				attrs = emptyAttributes();
			}
			super.emptyElement(element, attrs, augs);
		}

		public void startElement(final QName element, XMLAttributes attrs, final Augmentations augs)
				throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.startElement(element, attrs, augs);
				return;
			}
			if (attrs == null) {
				attrs = emptyAttributes();
			}
			super.startElement(element, attrs, augs);
		}

		public void characters(final XMLString text, final Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.characters(text, augs);
				return;
			}
			super.characters(text, augs);
		}

		public void endElement(QName element, Augmentations augs) throws XNIException {
			if (fRecorder.isRecording()) {
				fRecorder.endElement(element, augs);
				return;
			}
			super.endElement(element, augs);
		}

	}

} // class HTMLTagBalancer
