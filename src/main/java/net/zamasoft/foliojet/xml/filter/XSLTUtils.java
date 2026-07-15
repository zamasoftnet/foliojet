package net.zamasoft.foliojet.xml.filter;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import net.zamasoft.zstream.resolver.Source;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XSLTUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class XSLTUtils {
	private XSLTUtils() {
		// unused
	}

	public static javax.xml.transform.Source toTrAXSource(Source source) throws IOException {
		StreamSource traxSource;
		if (source.isReader()) {
			traxSource = new StreamSource(source.getReader(), source.getURI().toString());
		} else {
			traxSource = new StreamSource(source.getInputStream(), source.getURI().toString());
		}
		return traxSource;
	}

	public static SAXTransformerFactory createTransformerFactory() {
		return new net.sf.saxon.TransformerFactoryImpl();
	}

	public static TransformerHandler createIdentityTransformerHandler() throws TransformerConfigurationException {
		return createTransformerFactory().newTransformerHandler();
	}

	public static StreamSource toStreamSource(Source source) throws IOException {
		StreamSource streamSource;
		if (source.isReader()) {
			streamSource = new StreamSource(new BufferedReader(source.getReader()));
		} else {
			streamSource = new StreamSource(new BufferedInputStream(source.getInputStream()));
		}
		streamSource.setSystemId(source.getURI().toString());
		return streamSource;
	}
}
