package net.zamasoft.foliojet.impl.formatter.document;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.zamasoft.foliojet.css.CSSProcessor;
import net.zamasoft.foliojet.impl.ua.NUpImposition;
import net.zamasoft.foliojet.impl.ua.NopImposition;
import net.zamasoft.foliojet.impl.ua.SinglePageImposition;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.style.imposition.Imposition;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.StyleSheetSelector;
import net.zamasoft.foliojet.xml.XMLHandlerFilter;
import net.zamasoft.foliojet.xml.ext.CSSJML;
import net.zamasoft.foliojet.xml.ext.CSSJMLHandlerFilter;
import net.zamasoft.foliojet.xml.util.SAXEventRecorder;
import net.zamasoft.foliojet.xml.util.SAXEventRecorder.SAXEvent;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.foliojet.xml.xhtml.XHTMLNSFilter;
import net.zamasoft.foliojet.xml.xhtml.XHTMLPreprocessFilter;
import net.zamasoft.foliojet.xml.xslt.XSLTProcessorFilter;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * 
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TranscoderHandler.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TranscoderHandler extends DefaultXMLHandlerFilter {
	protected final UserAgent ua;

	private final AttributesImpl ATTS = new AttributesImpl();

	private List<SAXEventRecorder.SAXEvent> events = new ArrayList<SAXEventRecorder.SAXEvent>();

	public TranscoderHandler(UserAgent ua) {
		this.ua = ua;
	}

	public void startDocument() throws SAXException {
		// ignore
	}

	public void setDocumentLocator(Locator locator) {
		if (this.events == null) {
			super.setDocumentLocator(locator);
		} else {
			this.events.add(SAXEventRecorder.setDocumentLocator(locator));
		}
	}

	public void startCDATA() throws SAXException {
		if (this.events == null) {
			super.startCDATA();
		} else {
			this.events.add(SAXEventRecorder.startCDATA());
		}
	}

	public void endCDATA() throws SAXException {
		if (this.events == null) {
			super.endCDATA();
		} else {
			this.events.add(SAXEventRecorder.endCDATA());
		}
	}

	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		if (this.events == null) {
			super.startDTD(name, publicId, systemId);
		} else {
			this.events.add(SAXEventRecorder.startDTD(name, publicId, systemId));
		}
	}

	public void endDTD() throws SAXException {
		if (this.events == null) {
			super.endDTD();
		} else {
			this.events.add(SAXEventRecorder.endDTD());
		}
	}

	public void startEntity(String name) throws SAXException {
		if (this.events == null) {
			super.startEntity(name);
		} else {
			this.events.add(SAXEventRecorder.startEntity(name));
		}
	}

	public void endEntity(String name) throws SAXException {
		if (this.events == null) {
			super.endEntity(name);
		} else {
			this.events.add(SAXEventRecorder.endEntity(name));
		}
	}

	public void comment(char[] ch, int off, int len) throws SAXException {
		if (this.events == null) {
			super.comment(ch, off, len);
		} else {
			this.events.add(SAXEventRecorder.comment(ch, off, len));
		}
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		if (this.events == null) {
			super.startPrefixMapping(prefix, uri);
		} else {
			this.events.add(SAXEventRecorder.startPrefixMapping(prefix, uri));
		}
	}

	public void endPrefixMapping(String prefix) throws SAXException {
		if (this.events == null) {
			super.endPrefixMapping(prefix);
		} else {
			this.events.add(SAXEventRecorder.endPrefixMapping(prefix));
		}
	}

	public void skippedEntity(String name) throws SAXException {
		if (this.events == null) {
			super.skippedEntity(name);
		} else {
			this.events.add(SAXEventRecorder.skippedEntity(name));
		}
	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		if (this.events == null) {
			super.ignorableWhitespace(ch, start, length);
		} else {
			this.events.add(SAXEventRecorder.ignorableWhitespace(ch, start, length));
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (this.events == null) {
			super.characters(ch, off, len);
		} else {
			this.events.add(SAXEventRecorder.characters(ch, off, len));
		}
	}

	public void processingInstruction(String target, String data) throws SAXException {
		// jp.cssj.property PI の処理
		if (CSSJML.PI_PROPERTY.equals(target)) {
			if (UAProps.INPUT_PROPERTY_PI.getBoolean(this.ua)) {
				try {
					XMLUtils.parsePseudoAttributes(data.toCharArray(), 0, data.length(), this.ATTS);
					String name = this.ATTS.getValue("name");
					String value = this.ATTS.getValue("value");
					if (name != null) {
						this.ua.setProperty(name, value);
					} else {
						this.ua.message(MessageCodes.WARN_BAD_PI_SYNTAX, UAProps.INPUT_PROPERTY_PI.name, data);
					}
				} catch (ParseException e) {
					this.ua.message(MessageCodes.WARN_BAD_PI_SYNTAX, CSSJML.PI_PROPERTY, data);
				}
				this.ATTS.clear();
			} else {
				this.ua.message(MessageCodes.WARN_CANNOT_OVERRIDE_PROPERTY);
			}
		}
		if (this.events == null) {
			super.processingInstruction(target, data);
		} else {
			this.events.add(SAXEventRecorder.processingInstruction(target, data));
		}
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (this.events != null) {
			// 設定の適用
			this.ua.getDocumentContext().setCompatibleMode(DocumentContext.CM_NORMAL);

			// フィルタ
			XMLHandlerFilter entryPoint = new CSSJMLHandlerFilter(this.ua);
			this.setXMLHandler(entryPoint);
			XMLHandlerFilter exitPoint = entryPoint;

			// スタイルシートの選択
			String stylesheets = UAProps.INPUT_STYLESHEET_TITLES.getString(this.ua);
			StyleSheetSelector ssh;
			if (stylesheets != null) {
				ssh = new StyleSheetSelectorImpl(stylesheets);
			} else {
				ssh = null;
			}

			// フィルタ
			String filters = UAProps.INPUT_FILTERS.getString(this.ua);
			for (StringTokenizer i = new StringTokenizer(filters); i.hasMoreTokens();) {
				String filter = i.nextToken();
				if (filter.equals("loose-html")) {
					// html補正
					XMLHandlerFilter htmlFilter = new XHTMLPreprocessFilter(this.ua);
					exitPoint.setXMLHandler(htmlFilter);
					exitPoint = htmlFilter;
				} else if (filter.equals("xslt")) {
					// XSLT
					XSLTProcessorFilter xsltFilter = new XSLTProcessorFilter();
					xsltFilter.setup(this.ua);
					if (ssh != null) {
						xsltFilter.setStyleSheetSelector(ssh);
					}
					exitPoint.setXMLHandler(xsltFilter);
					exitPoint = xsltFilter;
				} else if (filter.equals("default-to-xhtml")) {
					// namespace置き換え
					XHTMLNSFilter xhtmlnsFilter = new XHTMLNSFilter();
					exitPoint.setXMLHandler(xhtmlnsFilter);
					exitPoint = xhtmlnsFilter;
				} else {
					this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.INPUT_FILTERS.name, filters);
				}
			}

			// CSSの処理
			Imposition imposition;
			if (this.ua.isMeasurePass()) {
				imposition = new NopImposition(this.ua);
			} else {
				int nUp = UAProps.OUTPUT_N_UP.getInteger(this.ua);
				if (nUp > 1) {
					imposition = new NUpImposition(this.ua, nUp,
							(short) UAProps.OUTPUT_N_UP_ORDER.getCode(this.ua));
				} else {
					if (nUp < 1) {
						this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_N_UP.name,
								String.valueOf(nUp));
					}
					imposition = new SinglePageImposition(this.ua);
				}
			}
			CSSProcessor cssProcessor = new CSSProcessor(this.ua, imposition);
			if (ssh != null) {
				cssProcessor.setStyleSheetSelector(ssh);
			}
			exitPoint.setXMLHandler(cssProcessor);

			// 再開
			for (int i = 0; i < this.events.size(); ++i) {
				SAXEvent event = (SAXEvent) this.events.get(i);
				event.doEvent(entryPoint);
			}
			this.events = null;
		}

		super.startElement(uri, lName, qName, atts);
	}
}

class StyleSheetSelectorImpl implements StyleSheetSelector {
	private String titles;

	public StyleSheetSelectorImpl(String titles) {
		this.titles = titles;
	}

	public boolean stylesheet(URI uri, String type, String title, String media, boolean alternate) {
		return this.titles.indexOf(title) != -1;
	}
}
