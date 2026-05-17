package jp.cssj.homare.xml.xerces;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jp.cssj.homare.ua.UserAgent;
import jp.cssj.homare.xml.DefaultXMLHandlerFilter;
import jp.cssj.homare.xml.Parser;
import jp.cssj.homare.xml.XMLHandler;
import jp.cssj.homare.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;

/* NoAndroid begin */
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.apache.xerces.parsers.AbstractSAXParser;
import org.apache.xerces.parsers.XML11Configuration;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XNIException;
import javax.xml.parsers.ParserConfigurationException;
/* NoAndroid end */
/* Android begin *//*
					import mf.org.apache.xerces.jaxp.SAXParserFactoryImpl;
					import mf.org.apache.xerces.parsers.AbstractSAXParser;
					import mf.org.apache.xerces.parsers.XML11Configuration;
					import mf.org.apache.xerces.xni.Augmentations;
					import mf.org.apache.xerces.xni.NamespaceContext;
					import mf.org.apache.xerces.xni.XMLLocator;
					import mf.org.apache.xerces.xni.XNIException;
					import mf.javax.xml.parsers.ParserConfigurationException;
					*//* Android end */

import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Locator2;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Xerces2Parser.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Xerces2Parser implements Parser {
	private static final Logger LOG = Logger.getLogger(Xerces2Parser.class.getName());

	public static XMLReader createXMLReader() throws ParserConfigurationException, SAXException {
		SAXParserFactoryImpl pf = new SAXParserFactoryImpl();
		pf.setValidating(false);
		XMLReader reader = pf.newSAXParser().getXMLReader();
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

	private static void setFeature(XMLReader pf, String key, boolean b) {
		try {
			if (pf.getFeature(key) == b) {
				return;
			}
			pf.setFeature(key, b);
		} catch (Exception e) {
			LOG.log(Level.FINE, "サポートされない機能です", e);
		}
	}

	protected class CopperParser extends AbstractSAXParser {
		public CopperParser() {
			super(new XML11Configuration());
		}

		public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
				Augmentations augs) throws XNIException {
			super.startDocument(locator, encoding, namespaceContext, augs);
			Parser.XML_LOCATOR.set(locator);
		}
	}

	public void parse(final UserAgent ua, final Source source, final XMLHandler xmlHandler)
			throws SAXException, IOException {
		String encoding = source.getEncoding();
		if (encoding != null) {
			ua.getDocumentContext().setEncoding(encoding);
		}
		XMLReader reader = new CopperParser();
		configureXMLReader(reader);
		final InputSource inputSource = XMLUtils.toInputSource(source);

		reader.setProperty("http://xml.org/sax/properties/lexical-handler", xmlHandler);
		reader.setContentHandler(new DefaultXMLHandlerFilter(xmlHandler) {
			public void setDocumentLocator(Locator locator) {
				super.setDocumentLocator(locator);
				try {
					if (locator instanceof Locator2) {
						String encoding = ((Locator2) locator).getEncoding();
						if (encoding != null) {
							ua.getDocumentContext().setEncoding(encoding);
						}
					} else {
						ua.getDocumentContext().setEncoding("UTF-8");
					}
				} catch (UnsupportedEncodingException ex) {
					throw new RuntimeException(ex);
				}
			}
		});
		try {
			reader.parse(inputSource);
		} finally {
			Parser.XML_LOCATOR.remove();
		}
	}
}