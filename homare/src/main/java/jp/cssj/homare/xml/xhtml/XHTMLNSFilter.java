package jp.cssj.homare.xml.xhtml;

import jp.cssj.homare.xml.DefaultXMLHandlerFilter;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * デフォルトの名前空間の要素、およびそれに属するデフォルトの名前空間の属性をXHTML名前空間にします。
 * <p>
 * その上でXHTMLに所属する要素名、属性名を小文字にします。
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XHTMLNSFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class XHTMLNSFilter extends DefaultXMLHandlerFilter {
	private final AttributesImpl ATTSI = new AttributesImpl();
	private int stack = 0;

	public void startDocument() throws SAXException {
		super.startDocument();
		super.startPrefixMapping("", XHTML.URI);
	}

	public void endDocument() throws SAXException {
		super.endPrefixMapping("");
		super.endDocument();
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		if (prefix.length() == 0) {
			++this.stack;
		}
		if (uri == null || uri.length() == 0) {
			uri = XHTML.URI;
		}
		super.startPrefixMapping(prefix, uri);
	}

	public void endPrefixMapping(String prefix) throws SAXException {
		if (prefix.length() == 0) {
			--this.stack;
		}
		super.endPrefixMapping(prefix);
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		boolean isHTML;
		if (uri == null || uri.length() == 0 || (this.stack == 0 && lName.equalsIgnoreCase(qName))) {
			uri = XHTML.URI;
			lName = qName = qName.toLowerCase();
			isHTML = true;
		} else if (uri.equals(XHTML.URI)) {
			isHTML = true;
			lName = lName.toLowerCase();
			int colon = qName.indexOf(':');
			if (colon == -1) {
				qName = qName.toLowerCase();
			} else {
				qName = qName.substring(0, colon + 1) + lName;
			}
		} else {
			isHTML = false;
		}
		if (isHTML) {
			this.ATTSI.clear();
			for (int i = 0; i < atts.getLength(); ++i) {
				String attUri = atts.getURI(i);
				String attLName = atts.getLocalName(i);
				String attQName = atts.getQName(i);
				if (attUri == null || attUri.length() == 0 || attLName.equalsIgnoreCase(attQName)) {
					attUri = XHTML.URI;
					attLName = attQName = attLName.toLowerCase();
				} else if (attUri.equals(XHTML.URI)) {
					attLName = attLName.toLowerCase();
					int colon = attQName.indexOf(':');
					if (colon == -1) {
						attQName = attQName.toLowerCase();
					} else {
						attQName = attQName.substring(0, colon + 1) + attLName;
					}
				}
				this.ATTSI.addAttribute(attUri, attLName, attQName, atts.getType(i), atts.getValue(i));
			}
			atts = this.ATTSI;
		}
		super.startElement(uri, lName, qName, atts);
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (uri == null || uri.length() == 0 || (this.stack == 0 && lName.equalsIgnoreCase(qName))) {
			uri = XHTML.URI;
			lName = qName = lName.toLowerCase();
		} else if (uri.equals(XHTML.URI)) {
			lName = lName.toLowerCase();
			int colon = qName.indexOf(':');
			if (colon == -1) {
				qName = qName.toLowerCase();
			} else {
				qName = qName.substring(0, colon + 1) + lName;
			}
		}
		super.endElement(uri, lName, qName);
	}
}