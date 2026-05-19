package net.zamasoft.foliojet.epub.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XHtmlMetaHandler extends DefaultHandler {
	private Map<String, String> meta = new HashMap<String, String>();

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (lName.equals("meta")) {
			String name = atts.getValue("name");
			if (name != null) {
				String content = atts.getValue("content");
				if (content != null) {
					this.meta.put(name, content);
				}
			}
		} else if (lName.equals("body")) {
			throw new SAXException();
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (lName.equals("head")) {
			throw new SAXException();
		}
	}

	public Map<String, String> getMetaMap() {
		return Collections.unmodifiableMap(this.meta);
	}
}
