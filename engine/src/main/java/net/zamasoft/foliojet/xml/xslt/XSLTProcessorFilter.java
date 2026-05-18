package net.zamasoft.foliojet.xml.xslt;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Constants;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.StyleSheetSelector;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.XMLHandlerWrapper;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * デフォルトのスタイルシートおよびxml-stylesheet PIを解釈してXSLTによる変換を行います。
 * 
 * @see Declaration
 * @author MIYABE Tatsuhiko
 * @version $Id: XSLTProcessorFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class XSLTProcessorFilter extends DefaultXMLHandlerFilter implements URIResolver, ErrorListener {
	private static final Logger LOG = Logger.getLogger(XSLTProcessorFilter.class.getName());

	/**
	 * 最初のTransformerHandlerです。
	 */
	private TransformerHandler entryTransformerHandler = null;

	/**
	 * XSLTを使う場合にoutHandlerを退避させるために使います。
	 */
	private XMLHandler outHandlerSave = null;

	protected StyleSheetSelector ssh = null;

	protected UserAgent ua;

	protected Locator locator;

	/**
	 * XSLT適用後に送られるPIのリストです。
	 */
	private List<String[]> pis = null;

	private int contentDepth = 0;

	private AttributesImpl ATTS = new AttributesImpl();

	private TransformerException te = null;

	private boolean applyXSLT = true;

	public XSLTProcessorFilter() {
		// ignore
	}

	public void setup(UserAgent ua) {
		this.ua = ua;

	}

	public javax.xml.transform.Source resolve(String href, String base) throws TransformerException {
		try {
			if (base != null) {
				// HACK
				// XSLTプロセサが勝手にbaseをファイルパスにしてしまうのでその対策
				URI baseURI = URI.create(base);
				if ("file".equals(baseURI.getScheme())) {
					String filebase = new File(".").getCanonicalFile().toURI().toString();
					base = new File(baseURI.toURL().getFile()).getCanonicalFile().toURI().toString();
					if (base.startsWith(filebase)) {
						base = base.substring(filebase.length());
					}
				}
			}
			URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(), base, href);
			// 後で不定期にアクセスされることがあるので、メモリにキャッシュする
			Source source = this.ua.resolve(uri);
			try {
				DOMParser parser = new DOMParser();
				parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
				parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				parser.setFeature("http://xml.org/sax/features/namespaces", true);
				parser.setFeature("http://xml.org/sax/features/validation", false);
				try {
					parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				} catch (Exception e) {
					LOG.log(Level.WARNING, "Xercesではありません", e);
				}

				parser.parse(XMLUtils.toSAXInputSource(source));
				Document doc = parser.getDocument();
				return new DOMSource(doc, uri.toString());
			} finally {
				this.ua.release(source);
			}
		} catch (Exception e) {
			throw new TransformerException(e);
		}
	}

	public void warning(TransformerException te) throws TransformerException {
		// Thread.dumpStack();
		this.ua.message(MessageCodes.ERROR_XSLT_WARN, this.locator.getSystemId(), te.getMessage());
		throw te;
	}

	public void error(TransformerException te) throws TransformerException {
		// Thread.dumpStack();
		this.ua.message(MessageCodes.ERROR_XSLT_ERROR, this.locator.getSystemId(), te.getMessage());
		throw te;
	}

	public void fatalError(TransformerException te) throws TransformerException {
		this.ua.message(MessageCodes.FATAL_XSLT_FATAL, this.locator.getSystemId(), te.getMessage());
		LOG.log(Level.WARNING, "XSLTプロセッサエラー", te);
		this.te = te;
		throw te;
	}

	public void setStyleSheetSelector(StyleSheetSelector ssh) {
		this.ssh = ssh;
	}

	public void setDocumentLocator(Locator locator) {
		super.setDocumentLocator(locator);
		this.locator = locator;
	}

	public void processingInstruction(String target, String data) throws SAXException {
		if (target.equals(Constants.LINK_PI)) {
			// 外部スタイルシート (SPEC ASSX1.0)
			try {
				XMLUtils.parsePseudoAttributes(data.toCharArray(), 0, data.length(), this.ATTS);
				String type = this.ATTS.getValue("type");

				if (type != null && MimeTypeHelper.equals(type, Constants.XSLT_MIME_TYPE)) {
					// XSLT
					String href = this.ATTS.getValue("href");
					if (href != null) {
						String title = this.ATTS.getValue("title");
						String mediaTypes = this.ATTS.getValue("media");
						String alternateStr = this.ATTS.getValue("alternate");
						boolean alternate;
						if (alternateStr != null && alternateStr.equals("yes")) {
							alternate = true;
						} else {
							alternate = false;
						}
						try {
							this.linkXSLT(href, title, mediaTypes, alternate);
						} catch (URISyntaxException e) {
							this.ua.message(MessageCodes.WARN_MISSING_XSLT_STYLESHEET, href);
						} catch (IOException e) {
							this.ua.message(MessageCodes.WARN_MISSING_XSLT_STYLESHEET, href);
						} catch (TransformerConfigurationException e) {
							this.ua.message(MessageCodes.ERROR_BAD_XSLT_STYLESHEET, href);
						}
					}
				}
			} catch (ParseException e) {
				this.ua.message(MessageCodes.WARN_BAD_PI_SYNTAX, Constants.LINK_PI, data);
			}
			this.ATTS.clear();
		}
		if (this.pis == null) {
			this.pis = new ArrayList<String[]>();
		}
		this.pis.add(new String[] { target, data });
	}

	private void linkXSLT(String href, String title, String mediaTypes, boolean alternate)
			throws URISyntaxException, SAXException, IOException, TransformerConfigurationException {
		if (!this.applyXSLT) {
			return;
		}
		URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(),
				this.ua.getDocumentContext().getBaseURI(), href);
		boolean apply;
		if (this.ssh != null) {
			apply = this.ssh.stylesheet(uri, Constants.XSLT_MIME_TYPE, title, mediaTypes, alternate);
		} else {
			apply = !alternate;
		}
		if (apply && !this.ua.is(mediaTypes)) {
			apply = false;
		}
		if (apply) {
			this.applyXSLT(uri);
			this.applyXSLT = false;
		}
	}

	private void applyXSLT(URI uri) throws SAXException, IOException, TransformerConfigurationException {
		SAXTransformerFactory tf;
		// tf = (SAXTransformerFactory)
		// javax.xml.transform.TransformerFactory.newInstance();
		tf = new org.apache.xalan.processor.TransformerFactoryImpl();
		// tf = new net.sf.saxon.TransformerFactoryImpl();
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("Using transformer: " + tf);
		}

		tf.setURIResolver(this);
		tf.setErrorListener(this);
		Source source = this.ua.resolve(uri);
		TransformerHandler th;
		try {
			th = tf.newTransformerHandler(XSLTUtils.toTrAXSource(source));
		} finally {
			this.ua.release(source);
		}
		Transformer t = th.getTransformer();
		t.setURIResolver(this);
		t.setErrorListener(this);

		if (this.outHandlerSave == null) {
			this.outHandlerSave = this.outHandler;
			this.outHandler = new TrimDocumentFilter(this.outHandler);
		}
		assert this.outHandler != null;
		th.setResult(new SAXResult(this.outHandler));
		this.outHandler = new XMLHandlerWrapper(th, th);
		assert this.locator != null;
		th.setDocumentLocator(this.locator);
		th.startDocument();
		this.entryTransformerHandler = th;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		try {
			if (this.contentDepth == 0) {
				String defaultXSLT = UAProps.INPUT_XSLT_DEFAULT_STYLESHEET.getString(this.ua);
				if (defaultXSLT != null) {
					try {
						this.applyXSLT(URIHelper.create(this.ua.getDocumentContext().getEncoding(), defaultXSLT));
					} catch (URISyntaxException e) {
						this.ua.message(MessageCodes.WARN_MISSING_XSLT_STYLESHEET, defaultXSLT);
					} catch (IOException e) {
						this.ua.message(MessageCodes.WARN_MISSING_XSLT_STYLESHEET, defaultXSLT);
					} catch (TransformerConfigurationException e) {
						this.ua.message(MessageCodes.ERROR_BAD_XSLT_STYLESHEET, defaultXSLT);
					}
				}
				if (this.pis != null) {
					for (int i = 0; i < this.pis.size(); ++i) {
						String[] pi = (String[]) this.pis.get(i);
						super.processingInstruction(pi[0], pi[1]);
					}
					this.pis = null;
				}
				this.applyXSLT = false;
			}
			++this.contentDepth;
			super.startElement(uri, lName, qName, atts);
		} catch (RuntimeException e) {
			this.checkTe(e);
		}
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		try {
			super.characters(ch, start, length);
		} catch (RuntimeException e) {
			this.checkTe(e);
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		try {
			super.endElement(uri, lName, qName);
			--this.contentDepth;
		} catch (RuntimeException e) {
			this.checkTe(e);
		}
	}

	public void endDocument() throws SAXException {
		try {
			if (this.outHandlerSave != null) {
				this.entryTransformerHandler.endDocument();
				this.outHandler = this.outHandlerSave;
				this.outHandlerSave = null;
				this.entryTransformerHandler = null;
			}
			super.endDocument();
		} catch (RuntimeException e) {
			this.checkTe(e);
		}
	}

	private void checkTe(RuntimeException e) throws SAXException {
		if (this.te == null) {
			throw e;
		}
		short code = MessageCodes.ERROR_BAD_XSLT_STYLESHEET;
		String[] args = new String[] { this.te.getLocator().getSystemId() };
		String mes = MessageCodeUtils.toString(code, args);
		this.ua.message(code, args);
		LOG.log(Level.WARNING, mes, this.te);
		throw new SAXException(this.te);
	}
}