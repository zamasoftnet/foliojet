package net.zamasoft.foliojet.xml.html;

import net.zamasoft.foliojet.xml.xhtml.XHTML;

import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.cyberneko.html.filters.DefaultFilter;

/**
 * STYLEの中身はテキストとして解釈する。 lNameが空の場合は適当な値を入れる
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJProcessFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class CSSJPreprocessFilter extends DefaultFilter {
	private boolean inStyle = false;

	private static final String DEFAULT = "XXXXX";

	public void startElement(QName element, XMLAttributes atts, Augmentations augs) throws XNIException {
		// System.out.println(element);
		if (XHTML.STYLE_ELEM.equals(element.uri, element.localpart)) {
			this.inStyle = true;
		}
		if (element.localpart.length() == 0) {
			element.localpart = DEFAULT;
			element.rawname += element.localpart;
		}
		for (int i = 0; i < atts.getLength(); ++i) {
			if (atts.getLocalName(i).length() == 0) {
				QName qName = new QName();
				qName.prefix = atts.getPrefix(i);
				qName.localpart = DEFAULT;
				if (qName.prefix.length() == 0) {
					qName.rawname = qName.localpart;
				} else {
					qName.rawname = qName.prefix + ":" + qName.localpart;
				}
				qName.uri = atts.getURI(i);
				atts.setName(i, qName);
			}
		}
		super.startElement(element, atts, augs);
	}

	public void comment(XMLString text, Augmentations augs) throws XNIException {
		if (this.inStyle) {
			this.characters(text, augs);
			return;
		}
		super.comment(text, augs);
	}

	public void endElement(QName element, Augmentations augs) throws XNIException {
		this.inStyle = false;
		if (element.localpart.length() == 0) {
			element.localpart = DEFAULT;
			element.rawname += element.localpart;
		}
		super.endElement(element, augs);
	}
}