package net.zamasoft.foliojet.xml.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * 標準JAXPベースのXMLパーサー補助クラスです。
 */
public final class XMLParsers {
	private static final Logger LOG = Logger.getLogger(XMLParsers.class.getName());

	private XMLParsers() {
		// unused
	}

	public static XMLReader createXMLReader() throws ParserConfigurationException, SAXException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
		setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
		setFeature(factory, "http://xml.org/sax/features/namespaces", true);
		setFeature(factory, "http://xml.org/sax/features/validation", false);
		setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

		XMLReader reader = factory.newSAXParser().getXMLReader();
		configureXMLReader(reader);
		return reader;
	}

	public static void configureXMLReader(XMLReader reader) {
		setFeature(reader, "http://xml.org/sax/features/external-general-entities", false);
		setFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false);
		setFeature(reader, "http://xml.org/sax/features/namespaces", true);
		setFeature(reader, "http://xml.org/sax/features/validation", false);
		setFeature(reader, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
	}

	private static void setFeature(SAXParserFactory factory, String key, boolean value) {
		try {
			factory.setFeature(key, value);
		} catch (Exception e) {
			LOG.log(Level.FINE, "サポートされない機能です", e);
		}
	}

	private static void setFeature(XMLReader reader, String key, boolean value) {
		try {
			if (reader.getFeature(key) == value) {
				return;
			}
			reader.setFeature(key, value);
		} catch (Exception e) {
			LOG.log(Level.FINE, "サポートされない機能です", e);
		}
	}
}
