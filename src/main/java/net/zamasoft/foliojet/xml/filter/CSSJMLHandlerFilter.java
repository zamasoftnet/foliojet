package net.zamasoft.foliojet.xml.filter;

import jp.cssj.cti2.helpers.CTIMessageCodes;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.pdfg2d.gc.GraphicsException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import net.zamasoft.foliojet.xml.vocab.CSSJML;

/**
 * 拡張マークアップのためのフィルタです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJMLHandlerFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJMLHandlerFilter extends DefaultXMLHandlerFilter {
	protected final UserAgent ua;

	public CSSJMLHandlerFilter(UserAgent ua) {
		this.ua = ua;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (uri.equals(CSSJML.URI)) {
			// cssj:make-toc
			if (lName.equals(CSSJML.MAKE_TOC_ELEM.lName)) {
				PageRef pageRef = this.ua.getUAContext().getPageRef();
				if (pageRef != null) {
					String counter = atts.getValue("counter");
					if (counter != null) {
						short type;
						String typeStr = atts.getValue("type");
						if (typeStr == null) {
							type = ListStyleTypeValue.DECIMAL;
						} else {
							ListStyleTypeValue listStyle = GeneratedValueUtils.toListStyleType(typeStr);
							if (listStyle == null) {
								type = ListStyleTypeValue.DECIMAL;
							} else {
								type = listStyle.getListStyleType();
							}
						}
						pageRef.toSAX(this.outHandler, counter, type);
					}
				}
				return;
			} else if (lName.equals(CSSJML.FAIL_ELEM.lName)) {
				String type = atts.getValue("type");
				if (type.equals("error")) {
					throw new AssertionError("FAIL");
				} else if (type.equals("graphics")) {
					throw new GraphicsException("FAIL");
				} else if (type.equals("runtime")) {
					throw new NullPointerException("FAIL");
				} else if (type.equals("sax")) {
					throw new SAXException("FAIL");
				} else if (type.equals("abort-soft")) {
					this.ua.message(CTIMessageCodes.INFO_ABORT);
					throw new AbortException(AbortException.ABORT_NORMAL);
				} else if (type.equals("abort-hard")) {
					this.ua.message(CTIMessageCodes.INFO_ABORT);
					throw new AbortException(AbortException.ABORT_FORCE);
				}
			}
		}
		super.startElement(uri, lName, qName, atts);
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (uri.equals(CSSJML.URI)) {
			if (lName.equals(CSSJML.MAKE_TOC_ELEM.lName)) {
				return;
			}
		}
		super.endElement(uri, lName, qName);
	}
}
