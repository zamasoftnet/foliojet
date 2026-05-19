package net.zamasoft.foliojet.xml.html;

import net.zamasoft.foliojet.xml.xhtml.XHTML;

import org.htmlunit.cyberneko.xerces.xni.Augmentations;
import org.htmlunit.cyberneko.xerces.xni.QName;
import org.htmlunit.cyberneko.xerces.xni.XMLAttributes;
import org.htmlunit.cyberneko.xerces.xni.XMLString;
import org.htmlunit.cyberneko.xerces.xni.XNIException;
import org.htmlunit.cyberneko.filters.DefaultFilter;

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
		if (XHTML.STYLE_ELEM.equals(element.getUri(), element.getLocalpart())) {
			this.inStyle = true;
		}
		if (element.getLocalpart().length() == 0) {
			element.setValues(element.getPrefix(), DEFAULT, element.getRawname() + DEFAULT, element.getUri());
		}
		for (int i = 0; i < atts.getLength(); ++i) {
			QName attrName = atts.getName(i);
			String prefix = attrName.getPrefix();
			if (prefix == null) {
				prefix = "";
			}
			String localName = attrName.getLocalpart();
			if (localName == null) {
				localName = "";
			}
			String rawName = attrName.getRawname();
			if (rawName == null) {
				rawName = localName;
			}
			String uri = attrName.getUri();
			if (uri == null) {
				uri = "";
			}
			if (localName.length() == 0) {
				if (prefix.length() == 0) {
					rawName = DEFAULT;
				} else {
					rawName = prefix + ":" + DEFAULT;
				}
				localName = DEFAULT;
			}
			attrName.setValues(prefix, localName, rawName, uri);
			atts.setName(i, attrName);
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
		if (element.getLocalpart().length() == 0) {
			element.setValues(element.getPrefix(), DEFAULT, element.getRawname() + DEFAULT, element.getUri());
		}
		super.endElement(element, augs);
	}
}
