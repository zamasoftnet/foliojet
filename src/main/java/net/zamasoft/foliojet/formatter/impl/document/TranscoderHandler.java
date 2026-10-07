package net.zamasoft.foliojet.formatter.impl.document;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.zamasoft.foliojet.css.CSSProcessor;
import net.zamasoft.foliojet.css.scan.StructureScanHandler;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.ua.impl.Impositions;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.StyleSheetSelector;
import net.zamasoft.foliojet.xml.XMLHandlerFilter;
import net.zamasoft.foliojet.xml.vocab.CSSJML;
import net.zamasoft.foliojet.xml.filter.CSSJMLHandlerFilter;
import net.zamasoft.foliojet.xml.filter.DocumentFilters;
import net.zamasoft.foliojet.xml.util.SAXEventRecorder;
import net.zamasoft.foliojet.xml.util.SAXEventRecorder.SAXEvent;
import net.zamasoft.foliojet.xml.util.XMLUtils;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import net.zamasoft.foliojet.ua.CompatibleMode;

/**
 * 
 * 
 * @author MIYABE Tatsuhiko
 */
public class TranscoderHandler extends DefaultXMLHandlerFilter {
	protected final UserAgent ua;

	private final AttributesImpl ATTS = new AttributesImpl();

	private List<SAXEventRecorder.SAXEvent> events = new ArrayList<SAXEventRecorder.SAXEvent>();

	/**
	 * The object to clean up when conversion ends (E-6 increment 3b-2). Created only in the LAYOUT pass
	 * (remains null in the STRUCTURE_SCAN pass).
	 */
	private CSSProcessor cssProcessor = null;

	public TranscoderHandler(UserAgent ua) {
		this.ua = ua;
	}

	/**
	 * Cleans up the conversion (E-6 increment 3b-2). Closes the layout source's spill store
	 * (temporary file). Call from the finally block of the formatter that drives parsing,
	 * on both success and exception. Idempotent.
	 */
	public void dispose() {
		if (this.cssProcessor != null) {
			this.cssProcessor.dispose();
		}
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
		// Process the jp.cssj.property PI
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
			// Apply settings
			this.ua.getDocumentContext().setCompatibleMode(CompatibleMode.NORMAL);

			// Filters
			XMLHandlerFilter entryPoint = new CSSJMLHandlerFilter(this.ua);
			this.setXMLHandler(entryPoint);
			XMLHandlerFilter exitPoint = entryPoint;

			// Select stylesheets
			String stylesheets = UAProps.INPUT_STYLESHEET_TITLES.getString(this.ua);
			StyleSheetSelector ssh;
			if (stylesheets != null) {
				ssh = new StyleSheetSelectorImpl(stylesheets);
			} else {
				ssh = null;
			}

			// Filters
			String filters = UAProps.INPUT_FILTERS.getString(this.ua);
			for (StringTokenizer i = new StringTokenizer(filters); i.hasMoreTokens();) {
				String filter = i.nextToken();
				XMLHandlerFilter handlerFilter = DocumentFilters.create(filter, this.ua, ssh);
				if (handlerFilter == null) {
					this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.INPUT_FILTERS.name, filters);
					continue;
				}
				exitPoint.setXMLHandler(handlerFilter);
				exitPoint = handlerFilter;
			}

			if (this.ua.isStructureScanPass()) {
				// STRUCTURE_SCAN: A lightweight preliminary scan that performs no box construction
				// or layout. Bypasses CSSProcessor (style resolution and box construction)
				// and receives events directly through a dedicated lightweight walker
				// (see the development plan "2パス制御モード"). Shares the upstream filter
				// chain (CSSJML and input filters) with the LAYOUT pass
				// so that ElementKey numbering stays consistent between the two passes.
				exitPoint.setXMLHandler(new StructureScanHandler(this.ua.getUAContext().getSelectorFacts()));
			} else {
				// Process CSS
				Imposition imposition = Impositions.createImposition(this.ua);
				CSSProcessor cssProcessor = new CSSProcessor(this.ua, imposition);
				if (ssh != null) {
					cssProcessor.setStyleSheetSelector(ssh);
				}
				exitPoint.setXMLHandler(cssProcessor);
				// E-6 increment 3b-2: Retain for dispose (cleanup of spill temporary files)
				this.cssProcessor = cssProcessor;
			}

			// Resume
			for (int i = 0; i < this.events.size(); ++i) {
				SAXEvent event = (SAXEvent) this.events.get(i);
				event.doEvent(entryPoint);
			}
			this.events = null;
		}

		super.startElement(uri, lName, qName, atts);
	}
}

/**
 * Selects via {@code input.stylesheet.titles}. Untitled stylesheets (HTML persistent sheets) always apply;
 * titled sheets apply only if their names exactly match a selected name. Separate names with spaces or commas
 * (2026-10-05; previously, untitled sheets failed the conversion at {@code String.indexOf(null)}, and names used substring matching).
 */
final class StyleSheetSelectorImpl implements StyleSheetSelector {
	private final java.util.Set<String> titles = new java.util.HashSet<>();

	StyleSheetSelectorImpl(final String titles) {
		for (final String title : titles.split("[\\s,]+")) {
			if (!title.isEmpty()) {
				this.titles.add(title);
			}
		}
	}

	public boolean stylesheet(final URI uri, final String type, final String title, final String media,
			final boolean alternate) {
		if (title == null || title.isBlank()) {
			// Alternates require names, so do not apply unnamed alternates
			return !alternate;
		}
		return this.titles.contains(title.trim());
	}
}
