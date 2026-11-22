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
 * A filter for extended markup.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJMLHandlerFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJMLHandlerFilter extends DefaultXMLHandlerFilter {
	/**
	 * The system property that turns on {@code <cssj:fail type="...">}, the element that makes a conversion fail
	 * on purpose (an error, an exception or an abort, by its type) for the tests of error handling. It is off
	 * unless the JVM runs with this property set to {@code true}: left on, any document sent to a server could end
	 * its conversion with an unexpected error, and one without the type attribute did so with a
	 * NullPointerException. Off, the element is an unknown element like any other.
	 */
	public static final String FAIL_ELEMENT_PROPERTY = "net.zamasoft.foliojet.test.fail";

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
			} else if (lName.equals(CSSJML.FAIL_ELEM.lName) && Boolean.getBoolean(FAIL_ELEMENT_PROPERTY)) {
				String type = atts.getValue("type");
				if ("error".equals(type)) {
					throw new AssertionError("FAIL");
				} else if ("graphics".equals(type)) {
					throw new GraphicsException("FAIL");
				} else if ("runtime".equals(type)) {
					throw new NullPointerException("FAIL");
				} else if ("sax".equals(type)) {
					throw new SAXException("FAIL");
				} else if ("abort-soft".equals(type)) {
					this.ua.message(CTIMessageCodes.INFO_ABORT);
					throw new AbortException(AbortException.ABORT_NORMAL);
				} else if ("abort-hard".equals(type)) {
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
